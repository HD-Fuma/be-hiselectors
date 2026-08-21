package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentType;

/** 콘텐츠 유형별 건수 조회 projection. */
public interface ContentFormatCountProjection {
    ContentType getContentType();
    long getCount();
}
