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
