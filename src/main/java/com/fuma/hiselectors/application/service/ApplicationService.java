package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final GenerationRepository generationRepository;
    private final SelectorsRepository selectorsRepository;
    private final OAuthStateProvider oAuthStateProvider;
    private final Clock clock;

    @Transactional
    public ApplicationResponse create(String loginId, ApplicationCreateRequest request) {
        User user = userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
        if (selectorsRepository.findByUserId(user.getId())
                .filter(value -> value.isBlacklisted()).isPresent()) {
            throw new BusinessException(ErrorCode.BLACKLISTED_SELECTOR);
        }
        OAuthStateProvider.VerifiedAccount verifiedAccount = verifyAccount(
                request.verificationToken(), loginId);

        LocalDateTime now = LocalDateTime.now(clock);
        Generation generation = generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        now, now, GenerationStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_GENERATION_NOT_FOUND));

        if (applicationRepository.existsByUserIdAndGenerationId(user.getId(), generation.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION);
        }

        Application application = Application.builder()
                .userId(user.getId())
                .generationId(generation.getId())
                .snsCode(verifiedAccount.snsCode())
                .snsAccountId(verifiedAccount.snsAccountId())
                .profileUrl(profileUrl(verifiedAccount))
                .followerCount(verifiedAccount.followerCount())
                .contentCount(verifiedAccount.contentCount())
                .alarmYn(request.alarmAgreed())
                .policyAgreedAt(now)
                .status(ApplicationStatus.PENDING)
                .build();

        try {
            return ApplicationResponse.from(applicationRepository.save(application));
        } catch (DataIntegrityViolationException e) {
            // existsBy 체크와 save 사이 경쟁 상태: 유니크 제약 위반을 409로 변환
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION);
        }
    }

    private OAuthStateProvider.VerifiedAccount verifyAccount(String token, String loginId) {
        try {
            return oAuthStateProvider.resolveVerificationToken(token, loginId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_INVALID);
        }
    }

    private String profileUrl(OAuthStateProvider.VerifiedAccount account) {
        return switch (account.snsCode()) {
            case INSTAGRAM -> "https://www.instagram.com/%s/"
                    .formatted(account.snsAccountId().replaceFirst("^@", ""));
            case YOUTUBE -> "https://www.youtube.com/channel/%s"
                    .formatted(account.snsAccountId());
        };
    }
}
