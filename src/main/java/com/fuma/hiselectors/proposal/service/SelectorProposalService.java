package com.fuma.hiselectors.proposal.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 셀렉터스 다건 제안 메일 발송. 발송 자체는 트랜잭션 밖에서 수신자별로 이뤄지므로
 * (한 명 실패가 전체를 롤백하지 않도록) 조회만 트랜잭션으로 묶는다.
 *
 * <p>ponytail: 셀렉터스 제안 이력은 별도 테이블 없이 TaskRun 페이로드(selectorIds)와
 * 성공/실패 카운트로 남긴다. 영속 이력·목록 조회가 필요해지면 그때 테이블을 추가한다.
 */
@Service
@RequiredArgsConstructor
public class SelectorProposalService {

    private final AdminRepository adminRepository;
    private final SelectorsRepository selectorsRepository;
    private final UserRepository userRepository;
    private final ProposalMailService proposalMailService;

    public Admin requireAdmin(String adminLoginId) {
        return adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
    }

    /** 이메일을 확인할 수 있는 미삭제 셀렉터스만 수신자로 추린다. 없으면 예외를 던진다. */
    @Transactional(readOnly = true)
    public List<Recipient> resolveRecipients(List<Long> selectorIds) {
        List<Selectors> selectors = selectorsRepository.findAllById(selectorIds).stream()
                .filter(selector -> !selector.isDeleted())
                .filter(selector -> selector.getUserId() != null)
                .toList();
        Map<Long, String> emailByUserId = userRepository.findAllById(
                        selectors.stream().map(Selectors::getUserId).toList()).stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .collect(Collectors.toMap(User::getId, User::getEmail, (first, ignored) -> first));

        List<Recipient> recipients = selectors.stream()
                .filter(selector -> emailByUserId.containsKey(selector.getUserId()))
                .map(selector -> new Recipient(
                        selector.getId(),
                        emailByUserId.get(selector.getUserId()),
                        recipientName(selector)))
                .toList();
        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.SELECTOR_EMAIL_REQUIRED);
        }
        return recipients;
    }

    /** 수신자 1명에게 발송한다. 실패하면 예외를 던져 호출자가 실패로 집계한다. */
    public void send(Recipient recipient, Admin admin, String subject, String body) {
        proposalMailService.sendToSelector(
                recipient.email(), recipient.nickname(), admin, subject, body);
    }

    private static String recipientName(Selectors selector) {
        String nickname = selector.getSelectorsNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return selector.getSelectorsCode() == null ? "" : selector.getSelectorsCode();
    }

    public record Recipient(Long selectorId, String email, String nickname) {
    }
}
