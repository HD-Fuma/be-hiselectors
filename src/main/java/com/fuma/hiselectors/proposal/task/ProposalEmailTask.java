package com.fuma.hiselectors.proposal.task;

import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.service.ProposalService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;

public class ProposalEmailTask implements TrackedTask {

    private final ProposalService proposalService;
    private final String adminLoginId;
    private final ProposalCreateRequest request;

    public ProposalEmailTask(
            ProposalService proposalService,
            String adminLoginId,
            ProposalCreateRequest request) {
        this.proposalService = proposalService;
        this.adminLoginId = adminLoginId;
        this.request = request;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("PROPOSAL_EMAIL_SEND", 1);
        try {
            proposalService.propose(adminLoginId, request);
            context.progress().advance(1, 0, 0);
        } catch (RuntimeException exception) {
            try {
                context.progress().advance(0, 1, 0);
            } catch (RuntimeException progressFailure) {
                exception.addSuppressed(progressFailure);
            }
            throw exception;
        }
    }
}
