package com.fuma.hiselectors.creator.discovery;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 유튜브 채널 설명에서 인스타그램 핸들을 추출한다.
 *
 * <p>YouTube Data API 는 채널 페이지의 '링크' 섹션을 응답에 담아주지 않는다.
 * 화면에 인스타 아이콘이 보여도 API 로는 못 받으므로, {@code snippet.description}
 * 텍스트를 훑는 것이 유일한 방법이다.
 *
 * <p><b>실측 결과(뷰티 키워드 22개 채널)</b>: 14개(64%)는 설명에 인스타 관련 문자열이
 * 아예 없었다. 한국 크리에이터는 채널 설명을 거의 비워두기 때문이며, 정규식 문제가
 * 아니다. 핸들을 못 찾는 것이 정상 경로이므로 호출부는 빈 결과를 실패로 다루면 안 된다.
 */
@Component
public class IgHandleExtractor {

    /** URL 경로에서 핸들이 아닌 것들. instagram.com/p/... 같은 게시물 링크. */
    private static final Set<String> RESERVED_PATHS = Set.of(
            "p", "reel", "reels", "tv", "stories", "explore", "accounts",
            "direct", "about", "developer", "legal", "privacy");

    /** 핸들처럼 생겼지만 URL·메일 잔해인 토큰. */
    private static final Set<String> STOP_WORDS = Set.of(
            "https", "http", "www", "com", "net", "org", "co", "kr", "me", "io",
            "gmail", "naver", "daum", "hanmail", "kakao", "youtube", "youtu", "be",
            "email", "mail", "contact", "business");

    private static final Pattern HANDLE_FORMAT = Pattern.compile("^[A-Za-z0-9._]{1,30}$");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ANY_URL = Pattern.compile("https?://\\S+|www\\.\\S+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IG_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?instagram\\.com/(?:#!/)?@?([A-Za-z0-9._]{1,30})",
            Pattern.CASE_INSENSITIVE);

    /**
     * "인스타 : handle" 형태. 앞뒤로 영숫자가 붙으면 안 된다.
     * (안 그러면 "big data" 의 ig 가 걸려 data 를 핸들로 잡는다)
     *
     * <p>실측된 표기 변형: {@code Instagram\n@handle}, {@code instagram  :  handle},
     * {@code Insta handle}, {@code [Insta:@handle]}
     */
    private static final Pattern IG_LABELED = Pattern.compile(
            "(?<![A-Za-z0-9])(?:인스타그램|인스타|instagram|insta|ig)(?![A-Za-z0-9])"
                    + "\\s*[:：\\-\\]\\[]?\\s*@?([A-Za-z0-9._]{2,30})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BARE_MENTION = Pattern.compile("(?<![\\w./@])@([A-Za-z0-9._]{2,30})");

    /**
     * 추출 결과. 못 찾으면 {@link Optional#empty()}.
     *
     * <p>여러 개가 잡히면 신뢰도가 가장 높은 것을 돌려준다.
     */
    public Optional<IgHandle> extract(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }

        IgHandle best = null;

        // 1) 이메일 제거. 안 지우면 contact@fitgpt.co.kr 의 @fitgpt 가 핸들로 잡힌다.
        String text = EMAIL.matcher(description).replaceAll(" ");

        // 2) instagram.com/<handle> 을 먼저 뽑고 해당 URL 을 텍스트에서 지운다.
        //    순서가 중요하다. 안 지우면 www.instagram.com 의 'instagram' 이 라벨 패턴에
        //    걸려 뒤따르는 '.com' 을 핸들로 오인한다. (@com, @https 오탐)
        StringBuilder withoutIgUrl = new StringBuilder();
        Matcher urlMatcher = IG_URL.matcher(text);
        while (urlMatcher.find()) {
            IgHandle found = build(urlMatcher.group(1), IgHandleSource.URL);
            best = better(best, found);
            urlMatcher.appendReplacement(withoutIgUrl, " ");
        }
        urlMatcher.appendTail(withoutIgUrl);
        text = withoutIgUrl.toString();

        // 3) 남은 URL 도 제거. 도메인 조각이 핸들로 오인되는 것을 막는다.
        text = ANY_URL.matcher(text).replaceAll(" ");

        // 4) 라벨 형태. 실측상 가장 흔하다.
        Matcher labeled = IG_LABELED.matcher(text);
        while (labeled.find()) {
            best = better(best, build(labeled.group(1), IgHandleSource.LABELED));
        }

        // 5) 맨 @멘션. 다른 사람을 태그한 것일 수 있어 신뢰도가 낮다.
        Matcher mention = BARE_MENTION.matcher(text);
        while (mention.find()) {
            best = better(best, build(mention.group(1), IgHandleSource.MENTION));
        }

        return Optional.ofNullable(best);
    }

    private IgHandle build(String raw, IgHandleSource source) {
        String handle = raw.strip().replaceAll("^\\.+|\\.+$", "").toLowerCase();
        return isValid(handle) ? new IgHandle(handle, source) : null;
    }

    private boolean isValid(String handle) {
        return HANDLE_FORMAT.matcher(handle).matches()
                && !RESERVED_PATHS.contains(handle)
                && !STOP_WORDS.contains(handle)
                && handle.chars().anyMatch(Character::isLetterOrDigit);
    }

    /** 신뢰도가 더 높은 쪽을 남긴다. 같으면 먼저 찾은 것을 유지한다. */
    private IgHandle better(IgHandle current, IgHandle candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.confidence().compareTo(current.confidence()) > 0 ? candidate : current;
    }

    /** 추출된 핸들과 그 근거. */
    public record IgHandle(String handle, IgHandleSource source) {

        public BigDecimal confidence() {
            return source.confidence();
        }
    }
}
