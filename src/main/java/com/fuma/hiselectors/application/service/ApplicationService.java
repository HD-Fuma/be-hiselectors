package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationCreateRequest;
import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private static final Pattern INSTAGRAM_USERNAME = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final Pattern YOUTUBE_CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern YOUTUBE_HANDLE = Pattern.compile("@[^/\\s]{1,100}");
    private static final Set<String> INSTAGRAM_ROUTES = Set.of(
            "accounts", "direct", "explore", "p", "reel", "reels", "stories");

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final GenerationRepository generationRepository;
    private final SelectorsRepository selectorsRepository;
    private final List<ContentFetcher> contentFetchers;
    private final OAuthStateProvider oAuthStateProvider;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Long createTest(String profileUrl) {
        TestAccount account = parseTestAccount(profileUrl);
        LocalDateTime now = LocalDateTime.now(clock);
        Generation generation = generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        now, now, GenerationStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_GENERATION_NOT_FOUND));
        ContentFetcher.Profile profile = fetchPublicProfile(account);

        String token = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.save(User.builder()
                .hiId(token.substring(0, 20))
                .hiPassword(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(clip(account.accountId(), 50))
                .alimtalk("N")
                .build());
        Application application = Application.builder()
                .userId(user.getId())
                .generationId(generation.getId())
                .snsCode(account.platform())
                .snsAccountId(account.accountId())
                .profileUrl(account.profileUrl())
                .followerCount(profile.followerCount())
                .contentCount(profile.contentCount())
                .alarmYn(false)
                .policyAgreedAt(now)
                .status(ApplicationStatus.PENDING)
                .build();
        application.updateProfileImageUrl(profile.imageUrl());
        applicationRepository.save(application);
        return application.getId();
    }

    private ContentFetcher.Profile fetchPublicProfile(TestAccount account) {
        try {
            return contentFetchers.stream()
                    .filter(fetcher -> fetcher.supports() == account.platform())
                    .findFirst()
                    .map(fetcher -> fetcher.fetchProfile(account.accountId()))
                    .orElseGet(() -> new ContentFetcher.Profile(null, null, null));
        } catch (RuntimeException e) {
            log.warn("테스트 지원자 공개 프로필 조회 실패: platform={}, cause={}",
                    account.platform(), e.getClass().getSimpleName());
            return new ContentFetcher.Profile(null, null, null);
        }
    }

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
            Application saved = applicationRepository.save(application);
            if (request.alarmAgreed()) {
                eventPublisher.publishEvent(new ApplicationSubmittedEvent(
                        user.getId(), saved.getId(), user.getName()));
            }
            // 스케줄러(최대 수십 초 지연)를 기다리지 않고 커밋 직후 미디어 수집·분석을 즉시 트리거한다.
            eventPublisher.publishEvent(new ApplicationCreatedEvent(saved.getId()));
            return ApplicationResponse.from(saved);
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

    private TestAccount parseTestAccount(String value) {
        if (value == null) {
            throw invalidTestUrl();
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw invalidTestUrl();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getPort() != -1) {
            throw invalidTestUrl();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath().replaceFirst("/$", "");
        if (host.equals("instagram.com") || host.equals("www.instagram.com")) {
            String username = singleSegment(path);
            if (!INSTAGRAM_USERNAME.matcher(username).matches()
                    || INSTAGRAM_ROUTES.contains(username.toLowerCase(Locale.ROOT))) {
                throw invalidTestUrl();
            }
            return new TestAccount(
                    SnsPlatform.INSTAGRAM, username,
                    "https://www.instagram.com/" + username + "/");
        }
        if (host.equals("youtube.com") || host.equals("www.youtube.com")
                || host.equals("m.youtube.com")) {
            String[] segments = path.split("/");
            if (segments.length == 2 && YOUTUBE_HANDLE.matcher(segments[1]).matches()) {
                return new TestAccount(
                        SnsPlatform.YOUTUBE, segments[1],
                        "https://www.youtube.com/" + segments[1]);
            }
            if (segments.length == 3 && "channel".equals(segments[1])
                    && YOUTUBE_CHANNEL_ID.matcher(segments[2]).matches()) {
                return new TestAccount(
                        SnsPlatform.YOUTUBE, segments[2],
                        "https://www.youtube.com/channel/" + segments[2]);
            }
        }
        throw invalidTestUrl();
    }

    private String singleSegment(String path) {
        String[] segments = path.split("/");
        if (segments.length != 2 || segments[1].isBlank()) {
            throw invalidTestUrl();
        }
        return segments[1];
    }

    private BusinessException invalidTestUrl() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT,
                "Instagram 프로필 또는 YouTube 채널 URL만 등록할 수 있습니다.");
    }

    private String clip(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record TestAccount(SnsPlatform platform, String accountId, String profileUrl) {
    }
}
