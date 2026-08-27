package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.application.model.ApplicationContentAnalysis;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Gemini 입력 예산 안에서 콘텐츠·위치 다양성과 중요 신호를 보존하는 추출식 선택기. */
final class WeightedTranscriptSelector {

    private static final int MAX_SEGMENT_CHARS = 280;
    private static final int MAX_EMBEDDING_SEGMENTS = 64;
    private static final int FULL_PASS_MAX_CHARS = 2_000;
    private static final Set<String> SIGNALS = Set.of(
            "광고", "협찬", "브랜드", "제품", "가격", "구매", "추천", "비교", "리뷰",
            "효과", "효능", "부작용", "주의", "위험", "정치", "종교", "건강", "욕설");

    private WeightedTranscriptSelector() { }

    static Selection select(List<ApplicationContentAnalysis> analyses,
                            List<ApplicationMedia> media,
                            int maxChars) {
        return select(analyses, media, maxChars, texts -> List.of());
    }

    static Selection select(List<ApplicationContentAnalysis> analyses,
                            List<ApplicationMedia> media,
                            int maxChars,
                            SemanticRanker semanticRanker) {
        Map<String, Set<String>> keywords = keywordsByContent(analyses);
        Map<String, Double> priorities = prioritiesByContent(media);
        List<Document> documents = documents(analyses, media);
        int rawChars = documents.stream().mapToInt(document -> document.text().length()).sum();
        int totalContents = Math.toIntExact(
                documents.stream().map(Document::contentKey).distinct().count());
        List<String> fullDocuments = documents.stream().map(Document::text).distinct().toList();
        String fullText = String.join("\n\n", fullDocuments);

        // 예산 안의 콘텐츠는 줄이지 않는다. 누락 위험도 없고 임베딩 워커 왕복도 필요 없다.
        if (fullText.length() <= Math.min(FULL_PASS_MAX_CHARS, maxChars)) {
            return new Selection(fullText, rawChars, fullDocuments.size(), fullDocuments.size(),
                    totalContents, totalContents, false, false, false);
        }

        Set<String> suspectedContents = new HashSet<>();
        analyses.stream().filter(ApplicationContentAnalysis::isHateSuspected)
                .map(ApplicationContentAnalysis::getContentKey)
                .forEach(suspectedContents::add);

        List<Segment> candidates = new ArrayList<>();
        int order = 0;
        for (Document document : documents) {
            List<String> parts = split(document.text());
            for (int i = 0; i < parts.size(); i++) {
                String text = parts.get(i);
                double score = document.sourceWeight()
                        + priorities.getOrDefault(document.contentKey(), 0.0)
                        + keywordScore(text, keywords.getOrDefault(document.contentKey(), Set.of()))
                        + signalScore(text)
                        + ((i == 0 || i == parts.size() - 1) ? 0.75 : 0.0)
                        + (text.length() >= 30 ? 0.25 : 0.0);
                boolean safety = suspectedContents.contains(document.contentKey())
                        || hasSafetySignal(text, keywords.getOrDefault(document.contentKey(), Set.of()));
                candidates.add(new Segment(document.id(), document.contentKey(), text,
                        order++, score, i == 0, i == parts.size() - 1, safety));
            }
        }
        candidates = deduplicate(candidates);

        List<Segment> selected = new ArrayList<>();
        int used = 0;

        Map<String, List<Segment>> byDocument = groupBy(candidates, Segment::documentId);

        // 검수 신호는 단순 가점이 아니라 강제 보존한다. 앞뒤 한 구간도 같이 넣어 문맥 절단을 막는다.
        for (Segment safety : candidates.stream().filter(Segment::safety)
                .sorted(Comparator.comparingDouble(Segment::score).reversed()).toList()) {
            used = add(safety, selected, used, maxChars);
        }
        for (List<Segment> document : byDocument.values()) {
            for (int i = 0; i < document.size(); i++) {
                if (!document.get(i).safety()) {
                    continue;
                }
                used = add(i > 0 ? document.get(i - 1) : null, selected, used, maxChars);
                used = add(i + 1 < document.size() ? document.get(i + 1) : null,
                        selected, used, maxChars);
            }
        }

        // 콘텐츠 하나가 통째로 사라지지 않도록 콘텐츠별 최고 점수를 먼저 확보한다.
        Map<String, List<Segment>> byContent = groupBy(candidates, Segment::contentKey);
        for (List<Segment> content : byContent.values()) {
            Segment best = content.stream().max(Comparator.comparingDouble(Segment::score)).orElseThrow();
            used = add(best, selected, used, maxChars);
        }

        // 각 STT/OCR/게시물 텍스트의 처음과 끝을 보존해 head-only 절단의 맹점을 막는다.
        for (List<Segment> document : byDocument.values()) {
            Segment first = document.stream().filter(Segment::first).findFirst().orElse(null);
            Segment last = document.stream().filter(Segment::last).findFirst().orElse(null);
            used = add(first, selected, used, maxChars);
            used = add(last, selected, used, maxChars);
        }

        // 필수 구간을 제외한 실제 초과분만 워커에 보낸다.
        Set<Segment> remaining = new LinkedHashSet<>(candidates);
        remaining.removeAll(selected);
        // 일반 구간은 2,000자까지만 채우고, 앞에서 확보한 검수 필수 구간만 최종 상한까지 허용한다.
        int rankingLimit = Math.max(used, Math.min(FULL_PASS_MAX_CHARS, maxChars));
        boolean rankingAttempted = remaining.stream().anyMatch(fits(used, rankingLimit));
        List<Segment> semanticOrder = rankingAttempted
                ? semanticOrder(semanticRanker, remaining) : List.of();
        boolean semanticRanking = !semanticOrder.isEmpty();

        // 남은 예산은 워커의 임베딩 MMR 순서로 채우고, 워커 장애 시 보수적으로 예산을 끝까지 채운다.
        for (Segment candidate : semanticOrder) {
            if (remaining.remove(candidate)) {
                used = add(candidate, selected, used, rankingLimit);
            }
        }
        while (!remaining.isEmpty()) {
            Segment best = remaining.stream()
                    .max(Comparator.comparingDouble(candidate ->
                            candidate.score() - 2.0 * maxSimilarity(candidate, selected)))
                    .orElseThrow();
            remaining.remove(best);
            int next = add(best, selected, used, rankingLimit);
            used = next;
        }

        selected.sort(Comparator.comparingInt(Segment::order));
        String text = String.join("\n\n", selected.stream().map(Segment::text).toList());
        long contentCount = selected.stream().map(Segment::contentKey).distinct().count();
        return new Selection(text, rawChars, candidates.size(), selected.size(),
                totalContents, Math.toIntExact(contentCount), rankingAttempted,
                semanticRanking, text.length() < fullText.length());
    }

    private static List<Segment> semanticOrder(SemanticRanker ranker, Set<Segment> remaining) {
        List<Segment> candidates = remaining.stream()
                .sorted(Comparator.comparingDouble(Segment::score).reversed())
                .limit(MAX_EMBEDDING_SEGMENTS).toList();
        List<Integer> order = ranker.rank(
                candidates.stream().map(Segment::text).toList());
        if (order == null) {
            return List.of();
        }
        return order.stream().filter(java.util.Objects::nonNull)
                .filter(index -> index >= 0 && index < candidates.size())
                .map(candidates::get).distinct().toList();
    }

    private static List<Document> documents(List<ApplicationContentAnalysis> analyses,
                                            List<ApplicationMedia> media) {
        List<Document> result = new ArrayList<>();
        int id = 0;
        for (ApplicationContentAnalysis row : analyses) {
            id = addDocument(result, id, row.getContentKey(), "STT", row.getStt(), 2.5);
            id = addDocument(result, id, row.getContentKey(), "OCR", row.getOcr(), 1.5);
        }
        for (ApplicationMedia item : media) {
            String key = item.getSnsContentId();
            id = addDocument(result, id, key, "TITLE", item.getTitle(), 3.5);
            id = addDocument(result, id, key, "CAPTION", item.getCaption(), 3.0);
            id = addDocument(result, id, key, "DESCRIPTION", item.getDescription(), 2.0);
        }
        return result;
    }

    private static int addDocument(List<Document> result, int id, String contentKey,
                                   String source, String value, double sourceWeight) {
        String text = normalize(value);
        if (!text.isEmpty()) {
            String key = contentKey == null || contentKey.isBlank() ? "unknown-" + id : contentKey;
            result.add(new Document(id + ":" + source, key, text, sourceWeight));
            return id + 1;
        }
        return id;
    }

    private static Map<String, Set<String>> keywordsByContent(List<ApplicationContentAnalysis> analyses) {
        Map<String, Set<String>> result = new HashMap<>();
        for (ApplicationContentAnalysis row : analyses) {
            if (row.getKeywords() == null || row.getKeywords().isBlank()) {
                continue;
            }
            Set<String> values = result.computeIfAbsent(row.getContentKey(), ignored -> new HashSet<>());
            for (String keyword : row.getKeywords().split(",")) {
                String value = keyword.trim();
                if (value.length() >= 2) {
                    values.add(value);
                }
            }
        }
        return result;
    }

    private static Map<String, Double> prioritiesByContent(List<ApplicationMedia> media) {
        Map<String, Double> raw = new HashMap<>();
        for (ApplicationMedia item : media) {
            double popularity = Math.log1p(value(item.getViewCount()))
                    + 2 * Math.log1p(value(item.getLikeCount()))
                    + 3 * Math.log1p(value(item.getCommentCount()));
            double recency = 1.0 / (1.0 + Math.max(0, item.getSequenceNo()));
            raw.merge(item.getSnsContentId(), popularity + recency, Math::max);
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (max == 0.0) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>();
        raw.forEach((key, value) -> normalized.put(key, 1.5 * value / max));
        return normalized;
    }

    private static long value(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static double keywordScore(String text, Set<String> keywords) {
        return Math.min(2.0, keywords.stream().filter(text::contains).count() * 0.75);
    }

    private static double signalScore(String text) {
        return Math.min(3.0, SIGNALS.stream().filter(text::contains).count() * 1.0);
    }

    private static boolean hasSafetySignal(String text, Set<String> keywords) {
        return SIGNALS.stream().anyMatch(text::contains)
                || keywords.stream().anyMatch(text::contains)
                || text.chars().anyMatch(Character::isDigit)
                || text.contains("http://") || text.contains("https://")
                || text.contains("@") || text.contains("#");
    }

    private static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.KOREAN);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            chunk(text.substring(start, end).trim(), result);
        }
        if (result.isEmpty()) {
            chunk(text, result);
        }
        return result;
    }

    private static void chunk(String text, List<String> result) {
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + MAX_SEGMENT_CHARS);
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start + MAX_SEGMENT_CHARS / 2) {
                    end = space;
                }
            }
            String part = text.substring(start, end).trim();
            if (!part.isEmpty()) {
                result.add(part);
            }
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
    }

    private static List<Segment> deduplicate(List<Segment> candidates) {
        Map<String, Segment> unique = new LinkedHashMap<>();
        for (Segment candidate : candidates) {
            unique.merge(candidate.text(), candidate,
                    (first, second) -> first.score() >= second.score() ? first : second);
        }
        return new ArrayList<>(unique.values());
    }

    private static <K> Map<K, List<Segment>> groupBy(
            List<Segment> segments, java.util.function.Function<Segment, K> key) {
        Map<K, List<Segment>> result = new LinkedHashMap<>();
        for (Segment segment : segments) {
            result.computeIfAbsent(key.apply(segment), ignored -> new ArrayList<>()).add(segment);
        }
        return result;
    }

    private static int add(Segment segment, List<Segment> selected, int used, int maxChars) {
        if (segment == null || selected.contains(segment) || !fits(segment, used, maxChars)) {
            return used;
        }
        selected.add(segment);
        return used + (selected.size() == 1 ? 0 : 2) + segment.text().length();
    }

    private static boolean fits(Segment segment, int used, int maxChars) {
        return segment != null && used + (used == 0 ? 0 : 2) + segment.text().length() <= maxChars;
    }

    private static Predicate<Segment> fits(int used, int maxChars) {
        return segment -> fits(segment, used, maxChars);
    }

    private static double maxSimilarity(Segment candidate, List<Segment> selected) {
        Set<String> words = words(candidate.text());
        return selected.stream().mapToDouble(segment -> jaccard(words, words(segment.text())))
                .max().orElse(0.0);
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        for (String word : text.split(" ")) {
            if (word.length() >= 2) {
                result.add(word);
            }
        }
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    record Selection(String text, int rawChars, int candidateSegments, int selectedSegments,
                     int totalContents, int selectedContents, boolean rankingAttempted,
                     boolean semanticRanking, boolean truncated) { }

    @FunctionalInterface
    interface SemanticRanker {
        List<Integer> rank(List<String> texts);
    }

    private record Document(String id, String contentKey, String text, double sourceWeight) { }

    private record Segment(String documentId, String contentKey, String text, int order,
                           double score, boolean first, boolean last, boolean safety) { }
}
