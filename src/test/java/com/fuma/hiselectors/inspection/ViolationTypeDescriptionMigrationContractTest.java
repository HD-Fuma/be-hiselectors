package com.fuma.hiselectors.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ViolationTypeDescriptionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/016_violation_type_descriptions.sql");
    private static final Path DEPLOY_WORKFLOW = Path.of(".github/workflows/deploy-prod.yml");

    @Test
    void migrationDefinesPlainLanguageDescriptions() throws IOException {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                """
                UPDATE violation_type
                SET description = '광고·수수료 안내 문구 확인 필요'
                WHERE code = 'AD_DISCLOSURE_INVALID';
                """.trim(),
                """
                UPDATE violation_type
                SET description = '제휴 링크 확인 필요'
                WHERE code = 'AFFILIATE_LINK_INVALID';
                """.trim());
    }

    @Test
    void productionWorkflowAppliesAndVerifiesDescriptionMigration() throws IOException {
        String workflow = Files.readString(DEPLOY_WORKFLOW);

        assertThat(workflow).contains(
                "VIOLATION_TYPE_MIGRATION_B64=\"$(gzip -c "
                        + "src/main/resources/db/016_violation_type_descriptions.sql | base64 -w0)\"",
                "printf '%s' \"$VIOLATION_TYPE_MIGRATION_B64\" | base64 -d | gzip -d "
                        + "> \"$VIOLATION_TYPE_MIGRATION_FILE\"",
                "cat \"$VIOLATION_TYPE_MIGRATION_FILE\" | mysql_apply_file",
                "violation-type-descriptions=verified");
    }
}
