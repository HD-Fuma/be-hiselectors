package com.fuma.hiselectors.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

class ContentMediaMappingTest {

    @Test
    void mapsBodyAsJsonObject() throws NoSuchFieldException {
        Field body = ContentMedia.class.getDeclaredField("body");
        Column column = body.getAnnotation(Column.class);
        JdbcTypeCode jdbcTypeCode = body.getAnnotation(JdbcTypeCode.class);

        assertThat(body.getType()).isEqualTo(Map.class);
        assertThat(column.name()).isEqualTo("body");
        assertThat(column.columnDefinition()).isEqualToIgnoringCase("json");
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void createsImageWithSnsIdAndSequence() {
        Map<String, Object> body = Map.of();

        ContentMedia media = ContentMedia.create(
                1L,
                MediaType.IMAGE,
                "https://cdn.example.com/image.jpg",
                "media-1",
                2,
                body);

        assertThat(media.getContentVersionId()).isEqualTo(1L);
        assertThat(media.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(media.getMediaUrl()).isEqualTo("https://cdn.example.com/image.jpg");
        assertThat(media.getSnsMediaId()).isEqualTo("media-1");
        assertThat(media.getSequenceNo()).isEqualTo(2);
        assertThat(media.getBody()).containsExactlyEntriesOf(body);
    }
}
