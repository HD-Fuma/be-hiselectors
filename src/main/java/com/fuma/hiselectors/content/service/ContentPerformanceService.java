package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentInspectionMediaResponse;
import com.fuma.hiselectors.content.dto.ContentPerformanceQueryRow;
import com.fuma.hiselectors.content.dto.ContentPerformanceResponse;
import com.fuma.hiselectors.content.dto.ContentPerformanceSummaryResponse;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentPerformanceService {

    private final GenerationService generationService;
    private final ContentRepository contentRepository;
    private final ContentMediaRepository mediaRepository;
    private final ContentEngagementRepository engagementRepository;
    private final GenerationRepository generationRepository;

    public ContentPerformanceSummaryResponse getSummary() {
        Generation current = generationService.getActive();
        List<Generation> generations = generationRepository
                .findAllByOrderByStartDateDescIdDesc();
        Generation previous = null;
        for (int index = 0; index < generations.size(); index++) {
            if (generations.get(index).getId().equals(current.getId())
                    && index + 1 < generations.size()) {
                previous = generations.get(index + 1);
                break;
            }
        }

        return new ContentPerformanceSummaryResponse(
                contentRepository.countByDeletedFalse(),
                current.getGenerationName(),
                contentRepository.countByGenerationId(current.getId()),
                previous == null ? null : previous.getGenerationName(),
                previous == null ? 0L : contentRepository.countByGenerationId(previous.getId()),
                contentRepository.countAllByContentType().stream()
                        .map(item -> new ContentPerformanceSummaryResponse.FormatCount(
                                item.getContentType(), item.getCount()))
                        .toList());
    }

    public Page<ContentPerformanceResponse> getCurrentGenerationPerformance(int page, int size) {
        Generation generation = generationService.getActive();
        Page<ContentPerformanceQueryRow> rows = contentRepository
                .findPerformanceRowsByGenerationId(
                        generation.getId(), PageRequest.of(page, size));
        if (rows.isEmpty()) {
            return rows.map(row -> toResponse(
                    row, generation.getGenerationName(), List.of(), List.of()));
        }

        List<Long> versionIds = rows.getContent().stream()
                .map(ContentPerformanceQueryRow::latestVersionId)
                .toList();
        Map<Long, List<ContentMedia>> mediaByVersionId = new LinkedHashMap<>();
        for (ContentMedia media : mediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(versionIds)) {
            mediaByVersionId.computeIfAbsent(media.getContentVersionId(), ignored ->
                    new ArrayList<>()).add(media);
        }

        List<Long> contentIds = rows.getContent().stream()
                .map(ContentPerformanceQueryRow::contentId)
                .toList();
        Map<Long, List<ContentEngagement>> engagementByContentId = new LinkedHashMap<>();
        for (ContentEngagement engagement : engagementRepository
                .findAllByContentIdInOrderByContentIdAscCreatedAtAsc(contentIds)) {
            engagementByContentId.computeIfAbsent(engagement.getContentId(), ignored ->
                    new ArrayList<>()).add(engagement);
        }

        return rows.map(row -> toResponse(
                row,
                generation.getGenerationName(),
                mediaByVersionId.getOrDefault(row.latestVersionId(), List.of()),
                engagementByContentId.getOrDefault(row.contentId(), List.of())));
    }

    private ContentPerformanceResponse toResponse(
            ContentPerformanceQueryRow row,
            String generationName,
            List<ContentMedia> storedMedia,
            List<ContentEngagement> engagements) {
        List<String> texts = storedMedia.stream()
                .filter(media -> media.getMediaType() == MediaType.TEXT)
                .map(media -> media.bodyOrEmpty().get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        List<ContentInspectionMediaResponse> media = storedMedia.stream()
                .filter(stored -> stored.getMediaType() != MediaType.TEXT)
                .map(ContentInspectionMediaResponse::from)
                .toList();
        ContentEngagement latest = engagements.isEmpty()
                ? null
                : engagements.getLast();
        List<ContentPerformanceResponse.TrendPoint> trend = engagements.stream()
                .map(engagement -> new ContentPerformanceResponse.TrendPoint(
                        engagement.getCreatedAt(),
                        zero(engagement.getViewCount()),
                        zero(engagement.getLikeCount()),
                        zero(engagement.getCommentCount())))
                .toList();

        return new ContentPerformanceResponse(
                row.contentId(),
                row.selectorsId(),
                row.selectorsNickname(),
                generationName,
                row.snsCode(),
                row.snsContentId(),
                row.contentUrl(),
                row.contentType(),
                row.publishedAt(),
                row.accountId(),
                zero(row.followerCount()),
                row.profileImageUrl(),
                texts,
                media,
                latest == null ? 0L : zero(latest.getViewCount()),
                latest == null ? 0L : zero(latest.getLikeCount()),
                latest == null ? 0L : zero(latest.getCommentCount()),
                trend);
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }
}
