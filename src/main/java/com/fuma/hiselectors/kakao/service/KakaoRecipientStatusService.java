package com.fuma.hiselectors.kakao.service;

import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoRecipientStatusService {

    private final UserKakaoRecipientRepository recipientRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReauthRequired(Long recipientId) {
        recipientRepository.findById(recipientId)
                .ifPresent(UserKakaoRecipient::markReauthRequired);
    }
}
