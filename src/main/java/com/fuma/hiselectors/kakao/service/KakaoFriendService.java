package com.fuma.hiselectors.kakao.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.client.KakaoApiException;
import com.fuma.hiselectors.kakao.client.KakaoFriendClient;
import com.fuma.hiselectors.kakao.dto.KakaoFriendResponse;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoFriendService {

    private static final int PAGE_SIZE = 100;

    private final KakaoFriendClient friendClient;
    private final KakaoTokenService tokenService;
    private final UserKakaoRecipientRepository recipientRepository;

    public List<KakaoFriendResponse.Friend> getAllFriends(Long senderConnectionId) {
        String accessToken = tokenService.getValidAccessToken(senderConnectionId);
        try {
            return fetchAll(accessToken);
        } catch (KakaoApiException e) {
            if (e.isInvalidToken()) {
                try {
                    return fetchAll(tokenService.forceRefresh(senderConnectionId));
                } catch (KakaoApiException retryException) {
                    throw translate(retryException);
                }
            }
            throw translate(e);
        }
    }

    public Optional<KakaoFriendResponse.Friend> findFriend(Long senderConnectionId,
                                                            Long kakaoUserId) {
        return getAllFriends(senderConnectionId).stream()
                .filter(friend -> kakaoUserId.equals(friend.id()))
                .findFirst();
    }

    @Transactional
    public int syncRecipients(Long senderConnectionId) {
        int updated = 0;
        for (KakaoFriendResponse.Friend friend : getAllFriends(senderConnectionId)) {
            Optional<UserKakaoRecipient> recipient = recipientRepository.findByKakaoUserId(friend.id());
            if (recipient.isPresent()) {
                recipient.get().updateConnection(friend.id(), friend.uuid());
                updated++;
            }
        }
        return updated;
    }

    private List<KakaoFriendResponse.Friend> fetchAll(String accessToken) {
        List<KakaoFriendResponse.Friend> friends = new ArrayList<>();
        int offset = 0;
        while (true) {
            KakaoFriendResponse page = friendClient.getFriends(accessToken, offset, PAGE_SIZE);
            friends.addAll(page.elements());
            offset += page.elements().size();
            if (page.elements().isEmpty() || offset >= page.totalCount()) {
                return friends;
            }
        }
    }

    private BusinessException translate(KakaoApiException e) {
        if (e.isInsufficientScope()) {
            return new BusinessException(ErrorCode.KAKAO_REQUIRED_SCOPE_MISSING);
        }
        return new BusinessException(ErrorCode.KAKAO_API_CALL_FAILED);
    }
}
