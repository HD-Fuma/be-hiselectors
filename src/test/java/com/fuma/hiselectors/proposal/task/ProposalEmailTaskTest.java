package com.fuma.hiselectors.proposal.task;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.service.ProposalService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProposalEmailTaskTest {

    @Test
    void reportsOneSuccessAfterProposalServiceCompletes() {
        ProposalService service = mock(ProposalService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        ProposalEmailTask task = new ProposalEmailTask(
                service, "admin-login", new ProposalCreateRequest(7L));

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("PROPOSAL_EMAIL_SEND", 1);
        order.verify(service).propose("admin-login", new ProposalCreateRequest(7L));
        order.verify(progress).advance(1, 0, 0);
    }

    @Test
    void reportsOneFailureAndReThrowsProposalServiceException() {
        ProposalService service = mock(ProposalService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        IllegalStateException failure = new IllegalStateException("mail failed");
        when(service.propose("admin-login", new ProposalCreateRequest(7L)))
                .thenThrow(failure);
        ProposalEmailTask task = new ProposalEmailTask(
                service, "admin-login", new ProposalCreateRequest(7L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> task.execute(
                        new TaskExecutionContext(mock(TaskLease.class), progress)))
                .isSameAs(failure);

        verify(progress).start("PROPOSAL_EMAIL_SEND", 1);
        verify(progress).advance(0, 1, 0);
    }

    @Test
    void preservesProposalFailureWhenFailureProgressReportingAlsoFails() {
        ProposalService service = mock(ProposalService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        IllegalStateException proposalFailure = new IllegalStateException("mail failed");
        IllegalStateException progressFailure = new IllegalStateException("lease lost");
        when(service.propose("admin-login", new ProposalCreateRequest(7L)))
                .thenThrow(proposalFailure);
        doThrow(progressFailure).when(progress).advance(0, 1, 0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new ProposalEmailTask(service, "admin-login", new ProposalCreateRequest(7L))
                                .execute(new TaskExecutionContext(mock(TaskLease.class), progress)))
                .isSameAs(proposalFailure);
        org.assertj.core.api.Assertions.assertThat(proposalFailure.getSuppressed())
                .containsExactly(progressFailure);
    }
}
