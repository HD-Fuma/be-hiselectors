package com.fuma.hiselectors.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ViolationTypeDescriptionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/016_violation_type_descriptions.sql");
    private static final Path ECS_DEPLOY_WORKFLOW = Path.of(
            ".github/workflows/deploy-ecs-blue-green.yml");
    private static final Path EC2_DEPLOY_WORKFLOW = Path.of(".github/workflows/deploy-prod.yml");

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
        String workflow = Files.readString(ECS_DEPLOY_WORKFLOW);
        String oldWorkflow = Files.readString(EC2_DEPLOY_WORKFLOW);

        assertThat(workflow).contains(
                "push:\n    branches: [dev]",
                "migration_sql=\"$(< src/main/resources/db/016_violation_type_descriptions.sql)\"",
                "mysql:8.4@sha256:",
                "aws ecs run-task",
                "violation-type-descriptions=verified");
        assertThat(oldWorkflow).doesNotContain("push:\n    branches: [dev]");
    }
}
