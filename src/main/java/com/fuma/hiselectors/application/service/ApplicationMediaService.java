package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationMediaCollectionResponse;
import com.fuma.hiselectors.application.dto.ApplicationMediaResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentPlatformClient;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ApplicationMediaService {

    private static final int COLLECTION_DAYS = 90;
    private static final int STORE_LIMIT = 10;

    private final ApplicationRepository applicationRepository;
    private final ApplicationMediaRepository mediaRepository;
    private final List<ContentPlatformClient> contentClients;
    private final TransactionTemplate transactionTemplate;

    public ApplicationMediaCollectionResponse collect(Long applicationId) {
        Application application = findApplication(applicationId);
        LocalDateTime collectedAfter = LocalDateTime.now().minusDays(COLLECTION_DAYS);

        ContentPlatformClient.CollectionResult result = findClient(application)
                .collect(application.getSnsAccountId(), collectedAfter);
        List<ApplicationMedia> snapshot = createSnapshot(application, result.contents(), collectedAfter);

        List<ApplicationMedia> saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
            mediaRepository.deleteByApplicationId(applicationId);
            mediaRepository.flush();
            return mediaRepository.saveAll(snapshot);
        }));

        return new ApplicationMediaCollectionResponse(
                applicationId,
                application.getSnsCode(),
                result.fetchedCount(),
                saved.size(),
                saved.stream().map(ApplicationMediaResponse::from).toList());
    }

    public List<ApplicationMediaResponse> findLatest(Long applicationId) {
        findApplication(applicationId);
        return mediaRepository.findTop3ByApplicationIdOrderBySequenceNoAsc(applicationId).stream()
                .map(ApplicationMediaResponse::from)
                .toList();
    }

    private Application findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
    }

    private ContentPlatformClient findClient(Application application) {
        return contentClients.stream()
                .filter(client -> client.supports() == application.getSnsCode())
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "지원하지 않는 SNS 플랫폼입니다."));
    }

    private List<ApplicationMedia> createSnapshot(
            Application application, List<RawContent> contents, LocalDateTime collectedAfter) {
        Map<String, RawContent> latestById = contents.stream()
                .filter(content -> content != null
                        && content.snsCode() == application.getSnsCode()
                        && content.snsContentId() != null
                        && !content.snsContentId().isBlank()
                        && content.contentUrl() != null
                        && !content.contentUrl().isBlank()
                        && content.createdAt() != null
                        && !content.createdAt().isBefore(collectedAfter))
                .sorted(Comparator.comparing(RawContent::createdAt).reversed())
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<RawContent> selected = latestById.values().stream().limit(STORE_LIMIT).toList();
        return IntStream.range(0, selected.size())
                .mapToObj(index -> toEntity(application, selected.get(index), index))
                .toList();
    }

    private ApplicationMedia toEntity(Application application, RawContent content, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(application.getId())
                .snsCode(application.getSnsCode())
                .snsContentId(content.snsContentId())
                .mediaUrl(content.contentUrl())
                .sequenceNo(sequenceNo)
                .publishedAt(content.createdAt())
                .build();
    }
}
