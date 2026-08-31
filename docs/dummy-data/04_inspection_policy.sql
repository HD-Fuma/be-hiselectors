-- hi_selectors 로컬 테스트용 콘텐츠 검수 정책 데이터
--
-- 기준일: 2026-08-31
-- 구성: YOUTUBE/INSTAGRAM 플랫폼별 현재 활성 정책 1건과 이전 비활성 정책 1건, 총 4건
--
-- InspectionPolicyService는 애플리케이션 시작 시 두 플랫폼의 현재 설정을 해시로 비교한다.
-- 아래 활성 정책 2건은 현재 코드의 기본 설정과 프롬프트 원문으로 동일한 해시를 계산한다.
-- 비활성 정책 2건은 모델 및 광고 표시 문구가 갱신되기 전의 가상 이력이다.
--
-- 주의:
-- 1. content_report, content_media, violation_evidence_history 등에서 이 테이블을
--    참조하는 데이터가 없어야 DELETE 및 PK 초기화가 가능하다.
-- 2. 실행 환경에서 CONTENT_INSPECTION_*, INSTAGRAM_* 설정을 기본값과 다르게
--    오버라이드하면 애플리케이션 시작 시 새 정책 이력이 추가되는 것이 정상이다.
-- 3. 프롬프트 파일이 현재 작업 환경에서 CRLF이므로, 리터럴의 줄바꿈도 CRLF로
--    정규화한 뒤 해시를 계산한다.

USE `hi_selectors`;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DELETE FROM `inspection_policy`;
ALTER TABLE `inspection_policy` AUTO_INCREMENT = 1;

-- ObjectMapper가 InspectionRuleConfig 레코드 선언 순서대로 생성하는 compact JSON과 동일하다.
SET @historical_rule_json =
    '{"disclosurePhrases":["광고","유료광고","수수료를 받을 수 있습니다","일정액의 수수료","경제적 이해관계"],"affiliateAllowedHosts":["hi.thehyundai.com","hiselectors.shop"],"affiliateCodeParameter":"ptrsRefCd"}';

SET @current_rule_json =
    '{"disclosurePhrases":["광고","유료광고","수수료를 받을 수 있습니다","일정액의 수수료","경제적 이해관계","본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로 링크를 통해 구매가 발생할 경우 일정 수수료를 제공받습니다","본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로 셀렉터스샵을 통해 구매가 발생할 경우 일정 수수료를 제공받습니다"],"affiliateAllowedHosts":["hi.thehyundai.com","hiselectors.shop"],"affiliateCodeParameter":"ptrsRefCd"}';

-- SQL 파일 자체의 LF/CRLF 여부와 무관하게 현재 프롬프트 원본의 CRLF로 맞춘다.
SET @ai_prompt = REPLACE(
    REPLACE('당신은 현대백화점 셀렉터스 콘텐츠 검수자입니다.
입력으로 주어진 텍스트·STT·OCR만 보고 판단하세요. 입력에 없는 장면, 의도, 브랜드는 만들지 마세요.
광고 표시와 제휴 링크는 별도 규칙이 검수하므로 AD_DISCLOSURE_INVALID, AFFILIATE_LINK_INVALID를 반환하지 마세요.
반드시 지정된 JSON 스키마로만 응답하세요.

위반이 없으면 violations는 빈 배열입니다.
같은 근거는 가장 구체적인 유형 하나만 사용하세요.
reason에는 입력에서 확인한 표현을 넣고, 추정으로 채우지 마세요.
직접 인용할 수 있으면 confidence 0.8 이상, 해석이 필요하면 0.5~0.8, 애매하면 위반으로 내지 마세요.

다음 유형만 판단하세요.

ABUSIVE_LANGUAGE: 욕설, 비속어, 상대를 모욕하는 말. 가벼운 감탄·인터넷 유행어는 제외.
HATE_DISCRIMINATION: 인종, 국적, 성별, 장애, 종교, 성정체성, 출신에 대한 혐오·차별. 단순 욕설은 ABUSIVE_LANGUAGE.
VIOLENCE_THREAT: 폭력 조장, 위협, 잔혹 묘사, 자해·타해 암시. 스포츠·요리·게임 등 일상 관용 표현은 제외.
SEXUAL_CONTENT: 성적 행위, 노출, 성적 대상화. 패션·뷰티의 일반적인 신체 노출은 제외.
POLITICAL_CONTENT: 특정 정당, 후보자, 선거, 정파 선동. 정책·시사 일반 언급은 SOCIAL_CONTROVERSY를 검토.
SOCIAL_CONTROVERSY: 사회적 갈등 이슈를 선동하거나 특정 집단을 낙인찍는 내용. 단순 정보 안내는 제외.
FALSE_EXAGGERATED_CLAIM: 효과 보장, 의료·건강 단정, 검증되지 않은 효능, "무조건/전부/완치" 같은 최상급 단정. 주관적 후기는 제외.
BRAND_REPUTATION_DAMAGE: 현대백화점, 셀렉터스, 입력에서 확인된 협업·언급 브랜드에 대한 비방, 허위 품질 주장, 조롱. 가벼운 취향 차이는 제외.

리포트 요약, 목적, 스타일, 강점은 작성하지 마세요. violations만 반환하세요.

violations에는 위반 유형, 판단 근거, 0~1 신뢰도, 근거 위치를 작성하세요.
reason은 400자 이내입니다.
locations는 문자열 배열이 아니라 객체 배열로 반환하세요.
각 location에는 입력에 있는 contentMediaId, mediaType, 비어 있지 않은 excerpt를 반드시 넣으세요.
TEXT body의 근거는 targetKind=TEXT_BODY, coordinateSpace=UTF16_CODE_UNIT로 작성하고
body.text의 UTF-16 [startIndex, endIndex)와 그 범위에 정확히 일치하는 excerpt를 넣으세요.
STT/OCR 근거는 각각 targetKind=STT_SEGMENT/OCR_SEGMENT,
coordinateSpace=CONTENT_MEDIA_SEGMENT로 작성하고 body에 실제 존재하는 segmentId를 넣으세요.
segment 근거의 excerpt는 해당 segment의 text에 포함된 문자열이어야 합니다.
시간과 bbox는 location에 복사하거나 변환하지 마세요. segmentId가 가리키는 body의 startMs/endMs와
NORMALIZED bbox가 유일한 좌표 원본입니다.
위치 없는 미디어 수준 규칙 근거만 targetKind=MEDIA, coordinateSpace=NONE을 사용하세요.
정확한 위치를 판단할 수 없으면 locations를 빈 배열로 반환하세요.

검수 대상:
%s
', CONCAT(CHAR(13), CHAR(10)), CHAR(10)),
    CHAR(10),
    CONCAT(CHAR(13), CHAR(10))
);

SET @youtube_extraction_prompt = REPLACE(
    REPLACE('당신은 콘텐츠 검수에 사용할 근거와 상세 리포트를 추출하는 엔진입니다.
정책 위반 판단과 위반 유형은 작성하지 마세요.

공개 YouTube 영상에서 사실과 리포트만 시간 순서대로 추출하세요.
발화와 화면 문구는 빠뜨리지 마세요. 짧은 조각으로 나누지 말고 발화·문구 단위로 합치세요.

1. stt.segments
- 실제로 들리는 발화를 가능한 한 그대로 전사합니다.
- 문맥을 요약하거나 교정하지 않습니다.
- 같은 화자의 이어지는 말은 한 segment로 합칩니다. 문장이 끝나거나 1초 이상 쉬면 다음 segment로 나눕니다.
- 단어·음절 단위로 쪼개지 마세요.
- startMs와 endMs는 밀리초 단위의 [시작, 끝) 구간입니다.
- segmentId는 stt-001부터 순서대로 부여합니다.

2. ocr.segments
- 영상 화면에 실제로 표시되는 제목, 자막, 가격, 브랜드, 라벨, 경고 문구를 추출합니다.
- 같은 문구가 유지되면 하나의 시간 구간으로 합칩니다.
- 재생 버튼, 조회수, 구독, 워터마크, 플랫폼 UI는 추출하지 않습니다.
- bbox는 전체 화면의 좌상단을 (0, 0), 우하단을 (1, 1)로 하는 NORMALIZED 좌표입니다.
- startMs와 endMs는 밀리초 단위입니다.
- segmentId는 ocr-001부터 순서대로 부여합니다.

3. report
- overview에는 콘텐츠 요약, 목적, 전개 흐름, 브랜드 협업 관점의 전체 평가를 작성합니다.
- summary는 400자, purpose는 200자, flow와 overallAssessment는 각 500자 이내입니다.
- insight에는 콘텐츠 스타일, 어조, 강점, 주의점, 위험, 혐오 표현 확인 여부,
  확인 가능한 협업 브랜드를 작성합니다. 추정한 브랜드는 포함하지 마세요.
- contentStyle는 200자, tone은 120자, 배열 항목은 각 120자 이내입니다.
- 영상에서 확인한 내용만 쓰고, 위반 유형은 넣지 마세요.

확인할 수 없는 내용은 생성하지 말고 각 segments 배열을 비워 두세요.
모든 시간 구간은 startMs < endMs여야 하고, bbox는 화면 범위를 벗어나면 안 됩니다.
이 영상의 실제 길이는 durationMs=%s 입니다. 모든 startMs와 endMs는 0 이상 durationMs 이하여야 하며, 확인되지 않은 시간을 지어내지 마세요.
', CONCAT(CHAR(13), CHAR(10)), CHAR(10)),
    CHAR(10),
    CONCAT(CHAR(13), CHAR(10))
);

SET @historical_rule_hash = LOWER(SHA2(@historical_rule_json, 256));
SET @current_rule_hash = LOWER(SHA2(@current_rule_json, 256));

-- 이전 정책: 모델 교체 및 공식 광고 표시 문구 2종 추가 전의 가상 스냅샷
SET @historical_ai_model = 'gemini-3.1-flash-lite';
SET @historical_ai_hash = LOWER(SHA2(
    CONCAT(
        @historical_ai_model, CHAR(10),
        'content-inspection-v8', CHAR(10),
        @ai_prompt, CHAR(10),
        '8192'
    ),
    256
));

SET @historical_youtube_model = 'gemini-3.5-flash';
SET @historical_youtube_extraction_hash = LOWER(SHA2(
    CONCAT(
        'YOUTUBE', CHAR(10),
        @historical_youtube_model, CHAR(10),
        @historical_youtube_model, CHAR(10),
        'youtube-extraction-v6', CHAR(10),
        @youtube_extraction_prompt, CHAR(10),
        '32768'
    ),
    256
));

SET @historical_instagram_stt_model =
    'whisper-large-v3-structured-20260801-090000';
SET @instagram_ocr_model = 'rapidocr-ppocrv4-ko-mobile';
SET @historical_instagram_extraction_hash = LOWER(SHA2(
    CONCAT(
        'INSTAGRAM', CHAR(10),
        @historical_instagram_stt_model, CHAR(10),
        @instagram_ocr_model, CHAR(10),
        CHAR(10),
        CHAR(10)
    ),
    256
));

SET @historical_youtube_config_hash = LOWER(SHA2(
    CONCAT(
        'YOUTUBE', CHAR(10),
        @historical_rule_hash, CHAR(10),
        @historical_ai_hash, CHAR(10),
        @historical_youtube_extraction_hash
    ),
    256
));

SET @historical_instagram_config_hash = LOWER(SHA2(
    CONCAT(
        'INSTAGRAM', CHAR(10),
        @historical_rule_hash, CHAR(10),
        @historical_ai_hash, CHAR(10),
        @historical_instagram_extraction_hash
    ),
    256
));

-- 현재 정책: application.yaml 및 각 Properties 클래스의 기본값
SET @current_ai_model = 'gemini-3.5-flash-lite';
SET @current_ai_hash = LOWER(SHA2(
    CONCAT(
        @current_ai_model, CHAR(10),
        'content-inspection-v8', CHAR(10),
        @ai_prompt, CHAR(10),
        '8192'
    ),
    256
));

SET @current_youtube_model = 'gemini-3.6-flash';
SET @current_youtube_extraction_hash = LOWER(SHA2(
    CONCAT(
        'YOUTUBE', CHAR(10),
        @current_youtube_model, CHAR(10),
        @current_youtube_model, CHAR(10),
        'youtube-extraction-v6', CHAR(10),
        @youtube_extraction_prompt, CHAR(10),
        '32768'
    ),
    256
));

SET @current_instagram_stt_model =
    'whisper-large-v3-structured-20260829-112443';
SET @current_instagram_extraction_hash = LOWER(SHA2(
    CONCAT(
        'INSTAGRAM', CHAR(10),
        @current_instagram_stt_model, CHAR(10),
        @instagram_ocr_model, CHAR(10),
        CHAR(10),
        CHAR(10)
    ),
    256
));

SET @current_youtube_config_hash = LOWER(SHA2(
    CONCAT(
        'YOUTUBE', CHAR(10),
        @current_rule_hash, CHAR(10),
        @current_ai_hash, CHAR(10),
        @current_youtube_extraction_hash
    ),
    256
));

SET @current_instagram_config_hash = LOWER(SHA2(
    CONCAT(
        'INSTAGRAM', CHAR(10),
        @current_rule_hash, CHAR(10),
        @current_ai_hash, CHAR(10),
        @current_instagram_extraction_hash
    ),
    256
));

INSERT INTO `inspection_policy` (
    `inspection_policy_id`,
    `platform`,
    `version`,
    `rule_config`,
    `rule_config_hash`,
    `ai_model_name`,
    `ai_prompt_version`,
    `ai_prompt`,
    `ai_config_hash`,
    `stt_model_name`,
    `ocr_model_name`,
    `extraction_prompt_version`,
    `extraction_prompt`,
    `extraction_config_hash`,
    `config_hash`,
    `is_active`,
    `activated_at`,
    `created_at`,
    `updated_at`
)
VALUES
    (
        1,
        'YOUTUBE',
        CONCAT('youtube-policy-', LEFT(@historical_youtube_config_hash, 12)),
        @historical_rule_json,
        @historical_rule_hash,
        @historical_ai_model,
        'content-inspection-v8',
        @ai_prompt,
        @historical_ai_hash,
        @historical_youtube_model,
        @historical_youtube_model,
        'youtube-extraction-v6',
        @youtube_extraction_prompt,
        @historical_youtube_extraction_hash,
        @historical_youtube_config_hash,
        0,
        '2026-07-01 09:00:00',
        '2026-07-01 09:00:00',
        '2026-08-29 12:00:00'
    ),
    (
        2,
        'INSTAGRAM',
        CONCAT('instagram-policy-', LEFT(@historical_instagram_config_hash, 12)),
        @historical_rule_json,
        @historical_rule_hash,
        @historical_ai_model,
        'content-inspection-v8',
        @ai_prompt,
        @historical_ai_hash,
        @historical_instagram_stt_model,
        @instagram_ocr_model,
        NULL,
        NULL,
        @historical_instagram_extraction_hash,
        @historical_instagram_config_hash,
        0,
        '2026-07-01 09:00:00',
        '2026-07-01 09:00:00',
        '2026-08-29 12:00:00'
    ),
    (
        3,
        'YOUTUBE',
        CONCAT('youtube-policy-', LEFT(@current_youtube_config_hash, 12)),
        @current_rule_json,
        @current_rule_hash,
        @current_ai_model,
        'content-inspection-v8',
        @ai_prompt,
        @current_ai_hash,
        @current_youtube_model,
        @current_youtube_model,
        'youtube-extraction-v6',
        @youtube_extraction_prompt,
        @current_youtube_extraction_hash,
        @current_youtube_config_hash,
        1,
        '2026-08-29 12:00:00',
        '2026-08-29 12:00:00',
        '2026-08-29 12:00:00'
    ),
    (
        4,
        'INSTAGRAM',
        CONCAT('instagram-policy-', LEFT(@current_instagram_config_hash, 12)),
        @current_rule_json,
        @current_rule_hash,
        @current_ai_model,
        'content-inspection-v8',
        @ai_prompt,
        @current_ai_hash,
        @current_instagram_stt_model,
        @instagram_ocr_model,
        NULL,
        NULL,
        @current_instagram_extraction_hash,
        @current_instagram_config_hash,
        1,
        '2026-08-29 12:00:00',
        '2026-08-29 12:00:00',
        '2026-08-29 12:00:00'
    );

ALTER TABLE `inspection_policy` AUTO_INCREMENT = 5;

-- 검증 1: 플랫폼마다 활성 1건, 비활성 1건이어야 한다.
SELECT
    `platform`,
    COUNT(*) AS `policy_count`,
    SUM(`is_active` = 1) AS `active_count`,
    SUM(`is_active` = 0) AS `inactive_count`
FROM `inspection_policy`
GROUP BY `platform`
ORDER BY `platform`;

-- 검증 2: 현재 코드 기본값으로 계산한 활성 정책과 다른 행이 없어야 한다.
SELECT COUNT(*) AS `active_policy_mismatch_count`
FROM `inspection_policy`
WHERE (`platform` = 'YOUTUBE'
       AND `is_active` = 1
       AND `config_hash` <> @current_youtube_config_hash)
   OR (`platform` = 'INSTAGRAM'
       AND `is_active` = 1
       AND `config_hash` <> @current_instagram_config_hash);

