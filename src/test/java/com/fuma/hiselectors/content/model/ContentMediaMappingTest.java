package com.fuma.hiselectors.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import java.lang.reflect.Field;
import org.hibernate.annotations.ColumnTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentMediaMappingTest {

    @Test
    @DisplayName("미디어 URL을 길이 제한 없는 TEXT 컬럼에 저장한다")
    void mapMediaUrlAsText() throws NoSuchFieldException, NoSuchMethodException {
        Field mediaUrl = ContentMedia.class.getDeclaredField("mediaUrl");
        Column column = mediaUrl.getAnnotation(Column.class);
        int defaultLength = (int) Column.class.getDeclaredMethod("length").getDefaultValue();

        assertThat(column.name()).isEqualTo("media_url");
        assertThat(column.columnDefinition()).isEqualTo("TEXT");
        assertThat(column.length()).isEqualTo(defaultLength);
    }

    @Test
    @DisplayName("본문 문자열을 JSON 컬럼에 인용해 저장하고 문자열로 조회한다")
    void mapBodyAsJsonString() throws NoSuchFieldException {
        Field body = ContentMedia.class.getDeclaredField("body");
        Column column = body.getAnnotation(Column.class);
        ColumnTransformer transformer = body.getAnnotation(ColumnTransformer.class);

        assertThat(column.name()).isEqualTo("body");
        assertThat(column.columnDefinition()).isEqualTo("JSON");
        assertThat(transformer.read()).isEqualTo("json_unquote(body)");
        assertThat(transformer.write()).isEqualTo("json_quote(?)");
        assertThat(body.isAnnotationPresent(Lob.class)).isFalse();
    }
}
