package com.fuma.hiselectors.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationMediaUrl {

    @Enumerated(EnumType.STRING)
    @Column(name = "url_type", nullable = false, length = 20)
    private Type type;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    ApplicationMediaUrl(Type type, String url) {
        this.type = type;
        this.url = url;
    }

    public enum Type {
        MEDIA,
        THUMBNAIL
    }
}
