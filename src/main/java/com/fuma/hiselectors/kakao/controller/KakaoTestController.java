package com.fuma.hiselectors.kakao.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.dto.KakaoTestFriendResponse;
import com.fuma.hiselectors.kakao.dto.KakaoTestMessageRequest;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import com.fuma.hiselectors.kakao.service.KakaoFriendService;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/admin/test/kakao")
@RequiredArgsConstructor
public class KakaoTestController {

    private final AdminRepository adminRepository;
    private final UserKakaoRecipientRepository recipientRepository;
    private final KakaoFriendService friendService;
    private final NotificationService notificationService;

    @GetMapping("/friends")
    public ResponseEntity<List<KakaoTestFriendResponse>> friends(Principal principal) {
        Long connectionId = senderConnectionId(principal.getName());
        List<KakaoTestFriendResponse> response = friendService.getAllFriends(connectionId).stream()
                .map(friend -> {
                    UserKakaoRecipient recipient = recipientRepository.findByKakaoUserId(friend.id())
                            .orElse(null);
                    return new KakaoTestFriendResponse(
                            recipient == null ? null : recipient.getUserId(),
                            friend.id(), friend.uuid(), friend.profileNickname(), friend.favorite(),
                            recipient != null);
                }).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/friends/sync")
    public ResponseEntity<SyncResponse> sync(Principal principal) {
        return ResponseEntity.ok(new SyncResponse(
                friendService.syncRecipients(senderConnectionId(principal.getName()))));
    }

    @PostMapping("/messages/me")
    public ResponseEntity<NotificationSendResponse> sendMe(
            @Valid @RequestBody KakaoTestMessageRequest request, Principal principal) {
        return ResponseEntity.ok(notificationService.sendToMe(
                principal.getName(), command(request)));
    }

    @PostMapping("/messages/friend")
    public ResponseEntity<NotificationSendResponse> sendFriend(
            @Valid @RequestBody KakaoTestMessageRequest request, Principal principal) {
        if (request.userId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId가 필요합니다.");
        }
        return ResponseEntity.ok(notificationService.sendToFriend(
                principal.getName(), command(request)));
    }

    private NotificationMessageCommand command(KakaoTestMessageRequest request) {
        return new NotificationMessageCommand(null, request.userId(), request.referenceId(),
                request.receiverName(), request.detail(), request.notificationType());
    }

    private Long senderConnectionId(String loginId) {
        Admin admin = adminRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (admin.getKakaoSenderConnectionId() == null) {
            throw new BusinessException(ErrorCode.KAKAO_SENDER_NOT_CONNECTED);
        }
        return admin.getKakaoSenderConnectionId();
    }

    public record SyncResponse(int updatedCount) {
    }
}
