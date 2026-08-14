package com.fuma.hiselectors.category.bootstrap;

import java.util.List;

/**
 * 더현대Hi 카테고리 체계를 기준으로 만든 기본 크리에이터 발굴 카탈로그.
 *
 * <p>웹 페이지를 실행 시점마다 크롤링하지 않고 검토한 스냅샷을 코드로 관리한다.
 * 전체보기, 아울렛, 기획전 문구, e쿠폰처럼 크리에이터 발굴 검색어로 적합하지 않은
 * 항목은 제외했다. 의미가 지나치게 넓은 일부 항목은 검색 문맥이 드러나도록 보정했다.
 *
 * <p>기준 페이지: https://hi.thehyundai.com/category?ctgr=000001
 * 기준일: 2026-08-14
 */
public final class DefaultDiscoveryCatalog {

    public static final String SNAPSHOT_DATE = "2026-08-14";

    private static final int DEFAULT_PRIORITY = 0;

    public static final List<DefaultCategory> CATEGORIES = List.of(
            category("BEAUTY", "뷰티", 0,
                    "스킨케어", "메이크업", "바디케어", "헤어케어",
                    "프레그런스", "뷰티소품", "홈케어관리기", "남성화장품"),
            category("FASHION", "패션", 1,
                    "패션 상의", "패션 하의", "아우터", "원피스",
                    "이너웨어 홈웨어", "슈즈", "가방", "패션소품", "워치 쥬얼리"),
            category("FOOD", "푸드", 2,
                    "신선식품", "가공식품", "건강식품", "디저트", "음료 주류", "선물세트"),
            category("LIVING_LIFE", "리빙/라이프", 3,
                    "가전", "디지털", "가구", "홈데코", "키친", "패브릭", "생활용품"),
            category("KIDS_FAMILY", "유아동/패밀리", 4,
                    "임신 출산용품", "외출용품", "유아 실내용품", "신생아패션",
                    "유아패션", "토들러패션", "아동패션", "주니어패션",
                    "유아동 잡화", "유아동 가구 인테리어", "완구 도서"),
            category("CULTURE_SERVICE", "컬처/서비스", 5,
                    "취미", "문화 서비스", "컬처", "아트"),
            category("SPORTS_LEISURE", "스포츠/레저", 6,
                    "데일리 스포츠", "피트니스", "요가 필라테스", "캠핑 아웃도어",
                    "골프", "수상 스포츠", "라이딩", "구기 스포츠",
                    "스포츠 디바이스", "모빌리티"),
            category("TRAVEL", "여행", 7,
                    "국내여행", "해외여행"),
            category("PET_LIFE", "반려생활", 8,
                    "강아지", "고양이", "반려동물 서비스")
    );

    private DefaultDiscoveryCatalog() {
    }

    private static DefaultCategory category(
            String code, String name, int displayOrder, String... keywords) {
        List<DefaultKeyword> defaults = List.of(keywords).stream()
                .map(keyword -> new DefaultKeyword(keyword, DEFAULT_PRIORITY))
                .toList();
        return new DefaultCategory(code, name, displayOrder, defaults);
    }

    public record DefaultCategory(
            String code,
            String name,
            int displayOrder,
            List<DefaultKeyword> keywords
    ) {
    }

    public record DefaultKeyword(String keyword, int priority) {
    }
}
