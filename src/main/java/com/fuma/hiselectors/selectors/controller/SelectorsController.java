package com.fuma.hiselectors.selectors.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 셀렉터스")
@RestController
@RequestMapping("/api/admin/selectors")
public class SelectorsController {

    /*
     * 예정: GET /api/admin/selectors/{selectorsId}/contents?limit=4
     * 셀렉터스의 최근 업로드 콘텐츠를 반환한다. 콘텐츠 도메인 구현 후 추가한다.
     */
}
