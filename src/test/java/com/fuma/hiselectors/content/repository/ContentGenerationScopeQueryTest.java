package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class ContentGenerationScopeQueryTest {

    @Test
    void contentQueriesUseSelectorsGenerationScope() throws NoSuchMethodException {
        assertSelectorsGenerationQuery(
                ContentBatchAccountRepository.class.getMethod(
                        "findAllByGenerationId", Long.class));
        assertSelectorsGenerationQuery(
                ContentRepository.class.getMethod(
                        "findAllByGenerationId", Long.class));
    }

    private void assertSelectorsGenerationQuery(Method method) {
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("SelectorsGeneration")
                .contains("sg.generationId = :generationId")
                .doesNotContain("Application application");
    }
}
