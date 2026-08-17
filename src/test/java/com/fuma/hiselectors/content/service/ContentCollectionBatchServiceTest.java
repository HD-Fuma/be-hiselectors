package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentCollectionBatchResponse;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContentCollectionBatchServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private SelectorsSnsAccountRepository accountRepository;

    @Mock
    private ContentCollectionService contentCollectionService;

    private ContentCollectionBatchService service;

    @BeforeEach
    void setUp() {
        service = new ContentCollectionBatchService(
                generationService, accountRepository, contentCollectionService);
    }

    @Test
    void aggregatesSuccessfulCollectionResultsInAccountOrder() {
        Generation generation = generation(10L, "1기");
        SelectorsSnsAccount first = account(101L);
        SelectorsSnsAccount second = account(102L);
        SelectorsSnsAccount third = account(103L);
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllForGenerationOrderByIdAsc(10L))
                .thenReturn(List.of(first, second, third));
        LocalDateTime generationStartedAt = generation.getStartDate();
        LocalDateTime generationEndedAt = generation.getEndDate();
        when(contentCollectionService.collectForAccount(
                101L, generationStartedAt, generationEndedAt)).thenReturn(2);
        when(contentCollectionService.collectForAccount(
                102L, generationStartedAt, generationEndedAt)).thenReturn(0);
        when(contentCollectionService.collectForAccount(
                103L, generationStartedAt, generationEndedAt)).thenReturn(4);

        ContentCollectionBatchResponse response = service.run();

        assertThat(response).isEqualTo(new ContentCollectionBatchResponse(
                10L, "1기", 3, 3, 0, 6));
        verify(generationService).getActive();
        verify(accountRepository).findAllForGenerationOrderByIdAsc(10L);
        InOrder order = inOrder(contentCollectionService);
        order.verify(contentCollectionService).collectForAccount(
                101L, generationStartedAt, generationEndedAt);
        order.verify(contentCollectionService).collectForAccount(
                102L, generationStartedAt, generationEndedAt);
        order.verify(contentCollectionService).collectForAccount(
                103L, generationStartedAt, generationEndedAt);
    }

    @Test
    void countsFailureAndContinuesWithLaterAccounts(CapturedOutput output) {
        Generation generation = generation(10L, "1기");
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllForGenerationOrderByIdAsc(10L))
                .thenReturn(List.of(account(101L), account(102L), account(103L)));
        LocalDateTime generationStartedAt = generation.getStartDate();
        LocalDateTime generationEndedAt = generation.getEndDate();
        when(contentCollectionService.collectForAccount(
                101L, generationStartedAt, generationEndedAt)).thenReturn(2);
        when(contentCollectionService.collectForAccount(
                102L, generationStartedAt, generationEndedAt))
                .thenThrow(new RuntimeException("collection failed"));
        when(contentCollectionService.collectForAccount(
                103L, generationStartedAt, generationEndedAt)).thenReturn(5);

        ContentCollectionBatchResponse response = service.run();

        assertThat(response).isEqualTo(new ContentCollectionBatchResponse(
                10L, "1기", 3, 2, 1, 7));
        InOrder order = inOrder(contentCollectionService);
        order.verify(contentCollectionService).collectForAccount(
                101L, generationStartedAt, generationEndedAt);
        order.verify(contentCollectionService).collectForAccount(
                102L, generationStartedAt, generationEndedAt);
        order.verify(contentCollectionService).collectForAccount(
                103L, generationStartedAt, generationEndedAt);
        assertThat(output).contains("accountId=102");
    }

    @Test
    void returnsZeroCountsWhenActiveGenerationHasNoTargetAccounts() {
        Generation generation = generation(20L, "2기");
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllForGenerationOrderByIdAsc(20L))
                .thenReturn(List.of());

        ContentCollectionBatchResponse response = service.run();

        assertThat(response).isEqualTo(new ContentCollectionBatchResponse(
                20L, "2기", 0, 0, 0, 0));
        verifyNoInteractions(contentCollectionService);
    }

    @Test
    void propagatesActiveGenerationFailureWithoutQueryingAccounts() {
        RuntimeException failure = new RuntimeException("active generation lookup failed");
        when(generationService.getActive()).thenThrow(failure);

        assertThatThrownBy(service::run).isSameAs(failure);

        verifyNoInteractions(accountRepository, contentCollectionService);
    }

    @Test
    void propagatesTargetAccountQueryFailureWithoutCollectingContent() {
        Generation generation = generation(10L, "1기");
        RuntimeException failure = new RuntimeException("account query failed");
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllForGenerationOrderByIdAsc(10L)).thenThrow(failure);

        assertThatThrownBy(service::run).isSameAs(failure);

        verifyNoInteractions(contentCollectionService);
    }

    private Generation generation(Long id, String name) {
        Generation generation = Generation.builder()
                .generationName(name)
                .startDate(LocalDateTime.of(2026, 8, 1, 0, 0))
                .endDate(LocalDateTime.of(2026, 8, 31, 23, 59))
                .status(GenerationStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(generation, "id", id);
        return generation;
    }

    private SelectorsSnsAccount account(Long id) {
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(id + 1_000)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("account-" + id)
                .deleted(false)
                .build();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
