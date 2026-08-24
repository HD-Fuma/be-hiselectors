package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.detector.AiViolationDetector;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.PreprocessingResult;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ContentInspectionExecutionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsOneExtractionVersionAndRejectsTheOldVersionAfterward() {
        Fixture fixture = fixture();
        Content content = content(1L);
        ContentVersion requested = version(1L, 1L, ContentVersionCreationReason.INITIAL);
        ContentMedia legacyVideo = ContentMedia.create(
                1L, MediaType.VIDEO, null, "abc123", 0,
                Map.of("stt", List.of(), "ocr", List.of()));
        ReflectionTestUtils.setField(legacyVideo, "id", 11L);
        InspectionPolicy policy = mock(InspectionPolicy.class);
        when(policy.getId()).thenReturn(9L);

        when(fixture.versions.findById(1L)).thenReturn(Optional.of(requested));
        when(fixture.contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(fixture.versions.findByIdForUpdate(1L)).thenReturn(Optional.of(requested));
        when(fixture.policies.requireActive(SnsPlatform.YOUTUBE)).thenReturn(policy);
        when(fixture.selectors.findById(7L)).thenReturn(Optional.of(selectors()));
        when(fixture.media.findByContentVersionIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(legacyVideo));
        when(fixture.preprocessing.requiresNewVersion(
                content, List.of(legacyVideo), policy)).thenReturn(true);
        when(fixture.versions.save(any(ContentVersion.class))).thenAnswer(invocation -> {
            ContentVersion saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        when(fixture.media.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.preprocessing.preprocess(any(), any(), any())).thenReturn(
                new PreprocessingResult(
                        Optional.of(new AiInspectionResponse(
                                ContentReportData.empty(), List.of())),
                        Optional.empty()));
        when(fixture.merger.mergeRuleFirst(any(), any())).thenReturn(List.of());
        when(fixture.normalizer.normalize(any(), any())).thenReturn(List.of());
        when(fixture.versions.findByIdForUpdate(2L)).thenAnswer(invocation ->
                fixture.versions.findById(2L));
        when(fixture.versions.findById(2L)).thenAnswer(invocation -> Optional.ofNullable(
                fixture.savedVersion));

        // save 시 생성된 엔티티를 persist 단계에서 다시 반환한다.
        when(fixture.versions.save(any(ContentVersion.class))).thenAnswer(invocation -> {
            ContentVersion saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            fixture.savedVersion = saved;
            return saved;
        });

        var result = fixture.service.inspect(1L);

        assertThat(result.requestedContentVersionId()).isEqualTo(1L);
        assertThat(result.inspectedContentVersionId()).isEqualTo(2L);
        assertThat(result.versionCreated()).isTrue();
        assertThat(result.creationReason())
                .isEqualTo(ContentVersionCreationReason.EXTRACTION_CHANGE);
        assertThat(content.getLastVersionNo()).isEqualTo(2L);
        verify(fixture.versions).save(any(ContentVersion.class));

        assertThatThrownBy(() -> fixture.service.inspect(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED));
    }

    @Test
    void rejectsHistoricalVersionWithConflictError() {
        Fixture fixture = fixture();
        Content content = content(2L);
        ContentVersion requested = version(1L, 1L, ContentVersionCreationReason.INITIAL);
        when(fixture.versions.findById(1L)).thenReturn(Optional.of(requested));
        when(fixture.contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(fixture.versions.findByIdForUpdate(1L)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> fixture.service.inspect(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED);
                    assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(409);
                });
    }

    private Fixture fixture() {
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentRepository contents = mock(ContentRepository.class);
        ContentMediaRepository media = mock(ContentMediaRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        SelectorsRepository selectors = mock(SelectorsRepository.class);
        InspectionPolicyService policies = mock(InspectionPolicyService.class);
        MediaPreprocessingService preprocessing = mock(MediaPreprocessingService.class);
        AiViolationDetector ai = mock(AiViolationDetector.class);
        ViolationResultMerger merger = mock(ViolationResultMerger.class);
        EvidenceLocationNormalizer normalizer = mock(EvidenceLocationNormalizer.class);
        ViolationReconciliationService reconciliation =
                mock(ViolationReconciliationService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        Fixture fixture = new Fixture(
                versions, contents, media, policies, selectors, preprocessing,
                merger, normalizer, null);
        fixture.service = new ContentInspectionExecutionService(
                versions, contents, media, reports, selectors, policies, preprocessing,
                List.of(), ai, merger, normalizer, reconciliation, transactions, CLOCK);
        return fixture;
    }

    private Content content(Long lastVersionNo) {
        Content content = Content.builder()
                .selectorsId(7L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("abc123")
                .contentUrl("https://youtu.be/abc123")
                .contentType(com.fuma.hiselectors.content.model.ContentType.LONG_FORM)
                .lastVersionNo(lastVersionNo)
                .build();
        ReflectionTestUtils.setField(content, "id", 10L);
        return content;
    }

    private ContentVersion version(
            Long id, Long versionNo, ContentVersionCreationReason reason) {
        ContentVersion version = ContentVersion.create(
                10L, versionNo, "hash", reason,
                CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime());
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }

    private Selectors selectors() {
        return Selectors.builder()
                .selectorsRoleId("SELECTORS")
                .selectorsCode("SEL-1")
                .build();
    }

    private static final class Fixture {
        private final ContentVersionRepository versions;
        private final ContentRepository contents;
        private final ContentMediaRepository media;
        private final InspectionPolicyService policies;
        private final SelectorsRepository selectors;
        private final MediaPreprocessingService preprocessing;
        private final ViolationResultMerger merger;
        private final EvidenceLocationNormalizer normalizer;
        private ContentInspectionExecutionService service;
        private ContentVersion savedVersion;

        private Fixture(
                ContentVersionRepository versions,
                ContentRepository contents,
                ContentMediaRepository media,
                InspectionPolicyService policies,
                SelectorsRepository selectors,
                MediaPreprocessingService preprocessing,
                ViolationResultMerger merger,
                EvidenceLocationNormalizer normalizer,
                ContentInspectionExecutionService service) {
            this.versions = versions;
            this.contents = contents;
            this.media = media;
            this.policies = policies;
            this.selectors = selectors;
            this.preprocessing = preprocessing;
            this.merger = merger;
            this.normalizer = normalizer;
            this.service = service;
        }
    }
}
