package com.fuma.hiselectors.proposal.task;

import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProposalEmailTaskFactory {

    private final ProposalService proposalService;

    public ProposalEmailTask create(String adminLoginId, ProposalCreateRequest request) {
        return new ProposalEmailTask(proposalService, adminLoginId, request);
    }
}
