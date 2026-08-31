package com.fuma.hiselectors.proposal.task;

import com.fuma.hiselectors.proposal.dto.SelectorProposalRequest;
import com.fuma.hiselectors.proposal.service.SelectorProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SelectorProposalEmailTaskFactory {

    private final SelectorProposalService selectorProposalService;

    public SelectorProposalEmailTask create(String adminLoginId, SelectorProposalRequest request) {
        return new SelectorProposalEmailTask(selectorProposalService, adminLoginId, request);
    }
}
