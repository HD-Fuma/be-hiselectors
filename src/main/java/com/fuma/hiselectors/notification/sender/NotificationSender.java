package com.fuma.hiselectors.notification.sender;

import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;

public interface NotificationSender {

    void sendToMe(Long senderConnectionId, KakaoMessageTemplate template);

    void sendToFriend(Long senderConnectionId, String receiverUuid,
                      KakaoMessageTemplate template);
}
