package com.fuma.hiselectors.creator.discovery;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 브랜드·기업 채널인지 점수로 판정한다. 2점 이상이면 브랜드로 본다.
 *
 * <p><b>여기서 걸러내지 않는다.</b> 점수만 매겨 저장하고, 실제로 빼는 일은 조회 API 의
 * {@code maxBrandScore} 조건이 한다. 판정 기준은 반드시 한 번은 틀리는데, 수집 시점에
 * 버리면 기준을 고쳐도 재수집이 필요하고 그게 API 쿼터를 또 쓰기 때문이다.
 *
 * <p>설명 본문을 그대로 검사하면 안 된다. 인스타 핸들에 {@code official} 이 들어간
 * 개인 크리에이터({@code @dietunni_official})가 통째로 브랜드로 걸린다.
 * URL·이메일·추출된 핸들을 지운 뒤 검사한다.
 */
@Component
public class BrandScoreCalculator {

    private static final int BRAND_THRESHOLD = 2;

    /** 설명에 한 번만 나와도 기업 채널로 볼 수 있는 표현. */
    private static final List<String> STRONG_HINTS = List.of(
            "주식회사", "(주)", "inc.", "corp", "고객센터", "본사");

    /** 채널명에 있으면 강한 신호, 설명에만 있으면 약한 신호. */
    private static final List<String> WEAK_HINTS = List.of(
            "공식", "official", "브랜드", "entertainment", "엔터테인먼트", "뉴스", "news");

    /**
     * '공식 채널' 은 사이에 단어가 끼는 변형이 많다.
     * 실측: {@code 롬앤 공식 유튜브 채널입니다}, {@code Disney Korea 공식 채널}
     */
    private static final List<Pattern> STRONG_PATTERNS = List.of(
            Pattern.compile("공식\\s*\\S{0,4}\\s*(채널|계정)"),
            Pattern.compile("official\\s+(youtube\\s+)?(channel|account)",
                    Pattern.CASE_INSENSITIVE));

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ANY_URL = Pattern.compile("https?://\\S+|www\\.\\S+",
            Pattern.CASE_INSENSITIVE);

    /**
     * YouTube 가 음원 배포용으로 자동 생성하는 채널. 사람이 아니라 발굴 대상이 아니다.
     * 실측: {@code ILLIT - Topic}
     */
    private static final Pattern AUTO_GENERATED = Pattern.compile("\\s-\\s*Topic$");

    /**
     * @param igHandle 이미 추출된 인스타 핸들. 검사 전에 본문에서 지운다. 없으면 null
     */
    public BrandScore calculate(String channelTitle, String description, String igHandle) {
        String title = channelTitle == null ? "" : channelTitle.toLowerCase(Locale.ROOT);

        // 자동 생성 채널은 다른 신호를 볼 것도 없이 제외 대상이다
        if (channelTitle != null && AUTO_GENERATED.matcher(channelTitle).find()) {
            return new BrandScore(BRAND_THRESHOLD, List.of("자동 생성 채널(- Topic)"));
        }

        String body = description == null ? "" : description;
        body = EMAIL.matcher(body).replaceAll(" ");
        body = ANY_URL.matcher(body).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (igHandle != null) {
            body = body.replace(igHandle.toLowerCase(Locale.ROOT), " ");
        }

        int score = 0;
        Set<String> hits = new LinkedHashSet<>();

        for (String hint : STRONG_HINTS) {
            String normalizedHint = hint.toLowerCase(Locale.ROOT);
            if (title.contains(normalizedHint) || body.contains(normalizedHint)) {
                score += 2;
                hits.add(hint);
            }
        }
        for (Pattern pattern : STRONG_PATTERNS) {
            Matcher inTitle = pattern.matcher(channelTitle == null ? "" : channelTitle);
            Matcher inBody = pattern.matcher(body);

            if (inTitle.find()) {
                score += 2;
                hits.add(inTitle.group().strip());
            } else if (inBody.find()) {
                score += 2;
                hits.add(inBody.group().strip());
            }
        }
        for (String hint : WEAK_HINTS) {
            String normalizedHint = hint.toLowerCase(Locale.ROOT);
            if (title.contains(normalizedHint)) {
                score += 2;
                hits.add(hint + "(채널명)");
            } else if (body.contains(normalizedHint)) {
                score += 1;
                hits.add(hint + "(설명)");
            }
        }

        return new BrandScore(score, new ArrayList<>(hits));
    }

    /**
     * @param score 브랜드 신호 점수
     * @param hits  판정 근거. 나중에 오탐을 확인할 때 쓴다
     */
    public record BrandScore(int score, List<String> hits) {

        public boolean isBrand() {
            return score >= BRAND_THRESHOLD;
        }

        /** 저장용 문자열. 컬럼 길이(200자)를 넘지 않게 자른다. */
        public String hitsAsText() {
            if (hits.isEmpty()) {
                return null;
            }
            String joined = String.join(", ", hits);
            return joined.length() <= 200 ? joined : joined.substring(0, 200);
        }
    }
}
