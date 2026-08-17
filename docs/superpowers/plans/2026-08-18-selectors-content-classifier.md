# Selectors Content Classifier Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an explainable two-stage classifier that hard-confirms decisive Selectors evidence, scores review candidates, and preserves the existing boolean API.

**Architecture:** Four immutable public contract types describe the result. A package-private URL extractor owns URL scanning and trusted-path rules; a package-private text extractor owns referral, hashtag, and soft-signal rules; the Spring classifier only normalizes input, composes both extractors, and chooses the final decision. No repository, entity, service, migration, or database change is included.

**Tech Stack:** Java 21, Spring Boot component, JUnit 5, AssertJ, Gradle

**Design reference:** `docs/superpowers/specs/2026-08-18-selectors-content-classifier-design.md`

---

## Chunk 1: Classification contract and evidence extractors

### Task 1: Add the immutable classification contract

**Files:**
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsContentDecision.java`
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsContentReviewTier.java`
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsContentEvidence.java`
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsContentClassification.java`
- Create: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsContentClassificationTest.java`

- [ ] **Step 1: Write one complete failing immutability test**

```java
@Test
void copiesAndOrdersMutableInputs() {
    Set<SelectorsContentEvidence> evidence = new HashSet<>(List.of(
            SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
            SelectorsContentEvidence.REFERRAL_CODE));
    List<String> codes = new ArrayList<>(List.of("rc000005105t", "RC000005105T"));
    List<String> urls = new ArrayList<>(List.of(
            "https://hi.thehyundai.com/product/B",
            "https://hi.thehyundai.com/product/A"));

    SelectorsContentClassification result = new SelectorsContentClassification(
            SelectorsContentDecision.CONFIRMED, 3,
            SelectorsContentReviewTier.NONE, evidence, codes, urls,
            "selectors-text-v1");

    evidence.clear();
    codes.clear();
    urls.clear();

    assertThat(result.evidence()).containsExactly(
            SelectorsContentEvidence.REFERRAL_CODE,
            SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
    assertThat(result.referralCodes()).containsExactly("RC000005105T");
    assertThat(result.matchedUrls()).containsExactly(
            "https://hi.thehyundai.com/product/A",
            "https://hi.thehyundai.com/product/B");
    assertThatThrownBy(() -> result.evidence().add(
            SelectorsContentEvidence.PURCHASE_CTA))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.referralCodes().add("RC999999999T"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.matchedUrls().add("https://example.com"))
            .isInstanceOf(UnsupportedOperationException.class);
}
```

- [ ] **Step 2: Run RED**

Run `./gradlew test --tests '*SelectorsContentClassificationTest.copiesAndOrdersMutableInputs'`.
Expected: compilation fails because the contract types do not exist.

- [ ] **Step 3: Add the four types and minimal copying constructor**

Create the three enums exactly as listed in design section 4. Create the record with fields:

```java
SelectorsContentDecision decision,
int score,
SelectorsContentReviewTier reviewTier,
Set<SelectorsContentEvidence> evidence,
List<String> referralCodes,
List<String> matchedUrls,
String ruleVersion
```

Copy evidence safely even when empty:

```java
EnumSet<SelectorsContentEvidence> evidenceCopy = evidence.isEmpty()
        ? EnumSet.noneOf(SelectorsContentEvidence.class)
        : EnumSet.copyOf(evidence);
evidence = Collections.unmodifiableSet(evidenceCopy);
```

Uppercase codes with `Locale.ROOT`, deduplicate and sort both lists, then assign `List.copyOf`.

- [ ] **Step 4: Run GREEN**

Run the Step 2 command. Expected: one passing test.

- [ ] **Step 5: Add runnable constructor-invariant tests**

```java
@Test
void rejectsInconsistentStates() {
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.CONFIRMED, 0,
            SelectorsContentReviewTier.NONE,
            Set.of(SelectorsContentEvidence.PUBLIC_PRODUCT_URL)));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.CONFIRMED, 0,
            SelectorsContentReviewTier.NORMAL,
            Set.of(SelectorsContentEvidence.REFERRAL_CODE)));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.REVIEW_REQUIRED, 2,
            SelectorsContentReviewTier.NORMAL, Set.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.REVIEW_REQUIRED, 5,
            SelectorsContentReviewTier.STRONG, Set.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.REVIEW_REQUIRED, 6,
            SelectorsContentReviewTier.NORMAL, Set.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.NOT_SELECTORS, 3,
            SelectorsContentReviewTier.NONE, Set.of()));
}

@Test
void rejectsNullAndNegativeValues() {
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            null, 0, SelectorsContentReviewTier.NONE, Set.of(),
            List.of(), List.of(), "selectors-text-v1"));
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0, null, Set.of(),
            List.of(), List.of(), "selectors-text-v1"));
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0,
            SelectorsContentReviewTier.NONE, null,
            List.of(), List.of(), "selectors-text-v1"));
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0,
            SelectorsContentReviewTier.NONE, Set.of(),
            null, List.of(), "selectors-text-v1"));
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0,
            SelectorsContentReviewTier.NONE, Set.of(),
            List.of(), null, "selectors-text-v1"));
    assertThatNullPointerException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0,
            SelectorsContentReviewTier.NONE, Set.of(),
            List.of(), List.of(), null));
    assertThatIllegalArgumentException().isThrownBy(() -> classification(
            SelectorsContentDecision.NOT_SELECTORS, -1,
            SelectorsContentReviewTier.NONE, Set.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> new SelectorsContentClassification(
            SelectorsContentDecision.NOT_SELECTORS, 0,
            SelectorsContentReviewTier.NONE, Set.of(),
            List.of(), List.of(), " "));
}

private SelectorsContentClassification classification(
        SelectorsContentDecision decision,
        int score,
        SelectorsContentReviewTier tier,
        Set<SelectorsContentEvidence> evidence) {
    return new SelectorsContentClassification(
            decision, score, tier, evidence, List.of(), List.of(),
            "selectors-text-v1");
}
```

- [ ] **Step 6: Run invariant tests and confirm RED**

Run `./gradlew test --tests '*SelectorsContentClassificationTest'`.
Expected: inconsistent states are accepted, so the new tests fail.

- [ ] **Step 7: Implement explicit invariant checks**

Add the exact decision/score/tier and decisive-evidence checks from design section 4. Use `Objects.requireNonNull` for every reference field and require a non-blank rule version.

- [ ] **Step 8: Run the contract suite and confirm GREEN**

Run the Step 6 command. Expected: all contract tests pass.

- [ ] **Step 9: Commit Task 1 only**

Stage the four new production types and `SelectorsContentClassificationTest.java` explicitly. Commit `Feat: 셀렉터스 판별 결과 모델 추가`.

### Task 2: Extract and mask URL candidates

**Files:**
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractor.java`
- Create: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractorTest.java`

The extractor is package-private. Its nested package-private result record is:

```java
record Result(
        String textWithoutUrls,
        Set<SelectorsContentEvidence> evidence,
        Set<String> referralCodes,
        List<String> matchedUrls,
        List<String> trustedUrls) {}
```

`matchedUrls` contains every extracted HTTP(S) candidate after repeated trailing-punctuation removal, even if URI parsing later rejects it. `trustedUrls` is an internal-only list of candidates that pass scheme, host, user-info, and port validation, enabling focused extractor tests. Only `matchedUrls` is propagated to the public result.

- [ ] **Step 1: Write failing extraction tests**

Test one input containing duplicate URLs and repeated punctuation:

```text
앞 https://hi.thehyundai.com/product/A... 뒤
https://hi.thehyundai.com/product/A
https://hi.thehyundai.com/product/B)]
```

Assert URLs are deduplicated and sorted as `A`, `B`, and that `textWithoutUrls` contains no URL text.

- [ ] **Step 2: Run extraction test and confirm RED**

Run `./gradlew test --tests '*SelectorsUrlEvidenceExtractorTest'`. Expected: missing extractor type.

- [ ] **Step 3: Implement candidate scanning and masking**

Implement HTTP(S)-only regex extraction, span masking with spaces, repeated removal of `. , ! ; : ) ' \" ] } >`, and deterministic copied result collections. Leave `trustedUrls` empty.

- [ ] **Step 4: Run extraction test and confirm GREEN**

Run the Step 2 command. Expected: extraction test passes.

- [ ] **Step 5: Add failing trusted-URL boundary tests**

Assert absent/default ports and mixed-case `HI.THEHYUNDAI.COM` are present in `trustedUrls`, while user-info, non-default ports, evil/subdomain hosts, unsupported schemes, and malformed URI candidates are absent. Assert scanning continues to a later valid URL after a malformed candidate.

- [ ] **Step 6: Run trusted-boundary tests and confirm RED**

Run the Step 2 command. Expected: `trustedUrls` remains empty.

- [ ] **Step 7: Implement strict `URI` validation**

Accept `http`/`https`, exact case-insensitive host, no user-info, and only absent or scheme-default port. Catch URI parse errors per candidate.

- [ ] **Step 8: Run trusted-boundary tests and confirm GREEN**

Run the Step 2 command. Expected: all Task 2 tests pass.

- [ ] **Step 9: Commit extraction boundary**

Stage only the URL extractor and its test. Commit `Feat: 셀렉터스 URL 후보 안전 추출`.

### Task 3: Classify trusted product URLs

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractor.java`
- Modify: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractorTest.java`

- [ ] **Step 1: Write failing product-path tests**

Assert `/product/A_1-2` and optional trailing slash add `PUBLIC_PRODUCT_URL`; blank ID, extra segment, dot segment, and encoded path do not.

- [ ] **Step 2: Run product-path tests and confirm RED**

Run `./gradlew test --tests '*SelectorsUrlEvidenceExtractorTest'`. Expected: no public product evidence.

- [ ] **Step 3: Implement exact raw-path grammar**

Use an anchored ASCII product-path pattern with ID length 1–100 and reject `%` in the path.

- [ ] **Step 4: Run product-path tests and confirm GREEN**

Run the Step 2 command. Expected: path tests pass.

- [ ] **Step 5: Write failing referral-query tests**

Assert:

- exactly one case-insensitive `ptrsRefCd=RC000005105T` adds `PRODUCT_URL_WITH_REFERRAL`, `REFERRAL_CODE`, canonical code, and keeps `PUBLIC_PRODUCT_URL`;
- lowercase valid value canonicalizes to uppercase;
- absent, invalid, encoded, or duplicate values do not add decisive evidence but keep public URL evidence;
- an unrelated encoded parameter plus one valid raw referral still confirms.

- [ ] **Step 6: Run referral-query tests and confirm RED**

Run the Step 2 command. Expected: decisive product referral evidence is absent.

- [ ] **Step 7: Implement raw-query parsing**

Split raw query on `&`, split each pair once on `=`, count case-insensitive referral names, never URL-decode values, and accept decisive evidence only when exactly one raw valid value exists.

- [ ] **Step 8: Run referral-query tests and confirm GREEN**

Run the Step 2 command. Expected: all Task 3 tests pass.

- [ ] **Step 9: Commit product URL rules**

Stage the same two files. Commit `Feat: 셀렉터스 상품 URL 근거 판별`.

### Task 4: Classify trusted shop and group URLs

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractor.java`
- Modify: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsUrlEvidenceExtractorTest.java`

- [ ] **Step 1: Write failing positive and negative path tests**

Positive paths:

```text
/sellectors/manage/shop/RC000005105T
/sellectors/manage/shop/RC000005105T/1
/sellectors/67
```

Each may have one trailing slash. For manage/shop paths, assert `SELECTORS_SHOP_URL`, `REFERRAL_CODE`, and canonical `referralCodes`; for the short group path, assert only `SELECTORS_SHOP_URL`. Negative paths cover invalid/blank code, blank ID, extra segments, `.`/`..`, and encoded path content.

- [ ] **Step 2: Run shop-path tests and confirm RED**

Run `./gradlew test --tests '*SelectorsUrlEvidenceExtractorTest'`. Expected: shop evidence is absent.

- [ ] **Step 3: Implement two anchored raw-path grammars**

Add `SELECTORS_SHOP_URL`; when a manage/shop path has a valid code, also add `REFERRAL_CODE` and return its canonical form.

- [ ] **Step 4: Run shop-path tests and confirm GREEN**

Run the Step 2 command. Expected: all Task 4 tests pass.

- [ ] **Step 5: Commit shop URL rules**

Stage the same two files. Commit `Feat: 셀렉터스 샵 URL 근거 판별`.

### Task 5: Extract standalone codes and hashtags

**Files:**
- Create: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractor.java`
- Create: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractorTest.java`

The package-private extractor accepts normalized full text, URL-masked text, and the URL evidence set. Its nested result record contains evidence, referral codes, soft score, and extracted hashtags.

- [ ] **Step 1: Write failing referral-boundary tests**

Accept URL-masked standalone `RC000005105T`, lowercase, and the ASCII result of an upstream NFKC normalization. Reject `XRC000005105TY`, invalid lengths, Unicode letter/number/underscore adjacency, and any code removed with a malicious URL. Raw full-width input is tested later through the public classifier, which owns normalization.

- [ ] **Step 2: Run referral-boundary tests and confirm RED**

Run `./gradlew test --tests '*SelectorsTextEvidenceExtractorTest'`. Expected: missing extractor type.

- [ ] **Step 3: Implement the Unicode-boundary ASCII-code matcher**

Use negative lookbehind/ahead for Unicode letter, number, and underscore around `RC[0-9]{9}T`; uppercase and deduplicate matches.

- [ ] **Step 4: Run referral-boundary tests and confirm GREEN**

Run the Step 2 command. Expected: referral tests pass.

- [ ] **Step 5: Write failing designated-hashtag tests**

Confirm the pair for `#더현대서울 #셀렉터스` and `#더현대 #셀렉터스`; reject plain text, either tag alone, `#셀렉터스몰`, and single `#더현대셀렉터스`.

- [ ] **Step 6: Run hashtag tests and confirm RED**

Run the Step 2 command. Expected: designated hashtag evidence is absent.

- [ ] **Step 7: Implement hashtag tokenization and pair evidence**

Extract only `#` plus Unicode letters/numbers/underscore. Add `DESIGNATED_HASHTAG_PAIR` only when two qualifying tokens exist.

- [ ] **Step 8: Run hashtag tests and confirm GREEN**

Run the Step 2 command. Expected: all Task 5 tests pass.

- [ ] **Step 9: Commit hard text rules**

Stage only the text extractor and its test. Commit `Feat: 셀렉터스 코드와 해시태그 근거 판별`.

## Chunk 2: Soft scoring, orchestration, and verification

### Task 6: Score name and brand signals

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractor.java`
- Modify: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractorTest.java`

- [ ] **Step 1: Write runnable name-family score tests**

```java
@Test
void returnsAllDetectedNameEvidenceButScoresOnlyTheMaximum() {
    Result phrase = extract("더현대 셀렉터스");
    assertThat(phrase.score()).isEqualTo(6);
    assertThat(phrase.evidence()).containsExactlyInAnyOrder(
            SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
            SelectorsContentEvidence.SELECTORS_NAME,
            SelectorsContentEvidence.THE_HYUNDAI_MENTION);

    Result shop = extract("셀렉터스 샵");
    assertThat(shop.score()).isEqualTo(4);
    assertThat(shop.evidence()).containsExactlyInAnyOrder(
            SelectorsContentEvidence.SELECTORS_NAME,
            SelectorsContentEvidence.SELECTORS_SHOP_NAME);

    Result combinedHashtag = extract("#더현대셀렉터스");
    assertThat(combinedHashtag.score()).isEqualTo(6);
    assertThat(combinedHashtag.evidence()).containsExactlyInAnyOrder(
            SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
            SelectorsContentEvidence.THE_HYUNDAI_MENTION);
}
```

Add a second test for independent `셀렉터스 + 더현대` = 5, case-insensitive standalone `Selectors` = 4, and boundary negatives `셀렉터스몰`, `MySelectors`, `SelectorsMall` = 0.

- [ ] **Step 2: Write runnable Hyundai mention forms test**

Use `@ValueSource(strings = {"더현대", "현대백화점", "THE HYUNDAI", "#더현대서울"})`. For each value assert score 1 and exactly `THE_HYUNDAI_MENTION`.

- [ ] **Step 3: Run name/brand tests and confirm RED**

Run `./gradlew test --tests '*SelectorsTextEvidenceExtractorTest'`. Expected: name and brand scores are zero.

- [ ] **Step 4: Implement name-family and brand matchers**

Return every detected evidence enum, add only the maximum of phrase/name/shop points, and add the Hyundai point independently. Implement all exact boundaries from design section 3.3.

- [ ] **Step 5: Run name/brand tests and confirm GREEN**

Run the Step 3 command. Expected: all Task 6 tests pass.

- [ ] **Step 6: Commit name scoring**

Stage the same two files. Commit `Feat: 셀렉터스 명칭 점수 추가`.

### Task 7: Score disclosure and CTA dictionaries

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractor.java`
- Modify: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsTextEvidenceExtractorTest.java`

- [ ] **Step 1: Write runnable disclosure dictionary tests**

Use a parameterized positive test with all of:

```text
#광고, #협찬, #ad, 유료광고, 유료 광고, 광고입니다, 협찬받아,
제휴 링크, 판매 수수료, PAID PARTNERSHIP, Paid Link
```

For each, assert score 1 and exactly `ECONOMIC_DISCLOSURE`. Use a parameterized negative test for `#광고주`, `#협찬사`, `#advice`, asserting score 0 and empty evidence. Add one repetition test that still expects one point.

- [ ] **Step 2: Run disclosure tests and confirm RED**

Run `./gradlew test --tests '*SelectorsTextEvidenceExtractorTest'`. Expected: disclosure evidence is absent.

- [ ] **Step 3: Implement fixed disclosure dictionaries**

Match the three hashtag terms against complete extracted hashtag tokens and the remaining normalized fixed phrases as literal substrings. Add one point and one evidence regardless of repetitions.

- [ ] **Step 4: Run disclosure tests and confirm GREEN**

Run the Step 2 command. Expected: disclosure tests pass.

- [ ] **Step 5: Write runnable CTA dictionary tests**

Use a parameterized positive test with every fixed phrase:

```text
프로필 링크, 링크 확인, 링크 클릭, 구매하기, 지금 구매, 바로 구매,
구매 링크, 주문하기, 지금 주문, 예약하기, 쿠폰, 할인 코드, DM 문의
```

For each, assert score 1 and exactly `PURCHASE_CTA`. Use a negative test for `구매력`, `주문진`, `예약어`, and a repetition test that still expects one point.

- [ ] **Step 6: Run CTA tests and confirm RED**

Run the Step 2 command. Expected: CTA evidence is absent.

- [ ] **Step 7: Implement the fixed CTA dictionary**

Match normalized literal substrings case-insensitively for English and add one point/evidence regardless of repetitions.

- [ ] **Step 8: Run CTA tests and confirm GREEN**

Run the Step 2 command. Expected: all Task 7 tests pass.

- [ ] **Step 9: Commit dictionary scoring**

Stage the same two files. Commit `Feat: 셀렉터스 광고와 전환 점수 추가`.

### Task 8: Compose the public classifier

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/content/classifier/SelectorsContentClassifier.java`
- Replace: `src/test/java/com/fuma/hiselectors/content/classifier/SelectorsContentClassifierTest.java`

- [ ] **Step 1: Write failing decision-boundary tests with exact public results**

Use complete `RawContent` fixtures and assert these exact results:

| Input | Score / Decision / Tier | Exact evidence | Codes / URLs |
| --- | --- | --- | --- |
| `더현대 #광고` | `2 / NOT_SELECTORS / NONE` | `THE_HYUNDAI_MENTION`, `ECONOMIC_DISCLOSURE` | empty / empty |
| `더현대 #광고 프로필 링크` | `3 / REVIEW_REQUIRED / NORMAL` | previous two + `PURCHASE_CTA` | empty / empty |
| `셀렉터스 더현대` | `5 / REVIEW_REQUIRED / NORMAL` | `SELECTORS_NAME`, `THE_HYUNDAI_MENTION` | empty / empty |
| `더현대 셀렉터스` | `6 / REVIEW_REQUIRED / STRONG` | `SELECTORS_BRAND_PHRASE`, `SELECTORS_NAME`, `THE_HYUNDAI_MENTION` | empty / empty |
| `https://hi.thehyundai.com/product/A 더현대 #광고 구매하기` | `6 / REVIEW_REQUIRED / STRONG` | `PUBLIC_PRODUCT_URL`, `THE_HYUNDAI_MENTION`, `ECONOMIC_DISCLOSURE`, `PURCHASE_CTA` | empty / exact URL |
| `https://hi.thehyundai.com/product/A?ptrsRefCd=rc000005105t` | `3 / CONFIRMED / NONE` | `REFERRAL_CODE`, `PRODUCT_URL_WITH_REFERRAL`, `PUBLIC_PRODUCT_URL` | `RC000005105T` / exact URL |

Every result must also assert `ruleVersion="selectors-text-v1"`.

- [ ] **Step 2: Add failing integration edge tests**

Assert raw full-width `ＲＣ０００００５１０５Ｔ` becomes confirmed after NFKC normalization; signals split across multiple `texts` combine; `classify(null)` throws `IllegalArgumentException`; empty text returns score 0/`NOT_SELECTORS`/`NONE`; and `isSelectorsContent` is true only for a confirmed fixture.

- [ ] **Step 3: Run classifier tests and confirm RED**

Run `./gradlew test --tests '*SelectorsContentClassifierTest'`.
Expected: the legacy classifier lacks the new composition API.

- [ ] **Step 4: Implement thin orchestration**

Guard null with `IllegalArgumentException`, normalize with NFKC, run URL extractor, run text extractor with URL-masked text, merge all enum evidence and canonical codes, propagate URL extractor `matchedUrls`, add 3 product points when URL evidence contains `PUBLIC_PRODUCT_URL`, and choose:

```java
CONFIRMED / NONE       if decisive evidence exists
REVIEW_REQUIRED/STRONG if score >= 6
REVIEW_REQUIRED/NORMAL if score >= 3
NOT_SELECTORS/NONE     otherwise
```

Construct `SelectorsContentClassification` with `selectors-text-v1`. Keep `isSelectorsContent` as a wrapper around `classify`.

- [ ] **Step 5: Run focused classifier tests and confirm GREEN**

Run the Step 3 command. Expected: all public classifier tests pass.

- [ ] **Step 6: Run the classifier-package regression suite**

Run `./gradlew test --tests 'com.fuma.hiselectors.content.classifier.*'`.
Expected: all classifier package tests pass.

- [ ] **Step 7: Commit orchestration**

Stage only `SelectorsContentClassifier.java` and its test. Commit `Feat: 셀렉터스 2단계 콘텐츠 판별 적용`.

### Task 9: Verify compatibility without touching dirty service files

**Files:**
- No production or test file changes expected
- Do not modify or stage `ContentCollectionService.java` or `ContentCollectionServiceTest.java`

- [ ] **Step 1: Record the current dirty-service baseline**

Run `git diff -- src/main/java/com/fuma/hiselectors/content/service/ContentCollectionService.java src/test/java/com/fuma/hiselectors/content/service/ContentCollectionServiceTest.java | shasum -a 256` and retain the printed digest in the execution notes.

- [ ] **Step 2: Run existing service coverage unchanged**

Run `./gradlew test --tests '*ContentCollectionServiceTest'`.
Expected: existing mocked false-classifier path continues to prove that non-confirmed items are not saved.

- [ ] **Step 3: Run the full suite**

Run `./gradlew test`. Expected: `BUILD SUCCESSFUL` and no failed tests.

- [ ] **Step 4: Verify scoped ownership and whitespace**

Run:

```bash
git diff --check -- src/main/java/com/fuma/hiselectors/content/classifier \
  src/test/java/com/fuma/hiselectors/content/classifier \
  docs/superpowers/specs/2026-08-18-selectors-content-classifier-design.md \
  docs/superpowers/plans/2026-08-18-selectors-content-classifier.md
git status --short
git diff -- src/main/java/com/fuma/hiselectors/content/classifier \
  src/test/java/com/fuma/hiselectors/content/classifier
```

Expected: feature changes are confined to the classifier package/tests and approved docs. Existing unrelated modified/untracked files remain untouched and unstaged.

- [ ] **Step 5: Confirm dirty service files are byte-for-byte unchanged**

Rerun the Step 1 digest command. Expected: the digest exactly matches the pre-execution value.
