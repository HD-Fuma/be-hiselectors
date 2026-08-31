package com.fuma.hiselectors.proposal.task;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.proposal.dto.SelectorProposalRequest;
import com.fuma.hiselectors.proposal.service.SelectorProposalService;
import com.fuma.hiselectors.proposal.service.SelectorProposalService.Recipient;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.List;

/**
 * 셀렉터스 다건 제안 메일 발송. 수신자별로 발송하고 성공/실패를 진행률에 집계한다.
 * 한 명이 실패해도 나머지는 계속 보내며, 전원 실패면 태스크를 실패로 만든다.
 */
public class SelectorProposalEmailTask implements TrackedTask {

    private final SelectorProposalService selectorProposalService;
    private final String adminLoginId;
    private final SelectorProposalRequest request;

    public SelectorProposalEmailTask(
            SelectorProposalService selectorProposalService,
            String adminLoginId,
            SelectorProposalRequest request) {
        this.selectorProposalService = selectorProposalService;
        this.adminLoginId = adminLoginId;
        this.request = request;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        Admin admin = selectorProposalService.requireAdmin(adminLoginId);
        List<Recipient> recipients = selectorProposalService.resolveRecipients(request.selectorIds());
        context.progress().start("SELECTOR_PROPOSAL_EMAIL_SEND", recipients.size());

        int succeeded = 0;
        for (Recipient recipient : recipients) {
            try {
                selectorProposalService.send(recipient, admin, request.subject(), request.body());
                succeeded++;
                context.progress().advance(1, 0, 0);
            } catch (RuntimeException exception) {
                context.progress().advance(0, 1, 0);
            }
        }
        if (succeeded == 0) {
            throw new BusinessException(ErrorCode.PROPOSAL_MAIL_SEND_FAILED);
        }
    }
}
