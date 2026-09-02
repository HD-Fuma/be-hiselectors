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
    private static final Path ECS_TEMPLATE = Path.of("infra/prod/template.yaml");

    @Test
    void migrationDefinesPlainLanguageDescriptions() throws IOException {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "SET NAMES utf8mb4;",
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
                "fetch-depth: 0",
                "BEFORE_SHA: ${{ github.event.before }}",
                "[[ \"$GITHUB_EVENT_NAME\" == \"workflow_dispatch\" ]]",
                "! git diff --quiet \"$BEFORE_SHA\" \"$GITHUB_SHA\"",
                "should_run=true",
                "APPLY_MIGRATION_016: ${{ steps.migration.outputs.should_run }}",
                "if [[ \"$APPLY_MIGRATION_016\" == \"true\" ]]; then",
                "migration_sql=\"$(< src/main/resources/db/016_violation_type_descriptions.sql)\"",
                "mysql:8.4@sha256:",
                "select(.name == \"DB_HOST\")] | length == 1",
                "select(.name == \"DB_PORT\")] | length == 1",
                "select(.name == \"DB_NAME\")] | length == 1",
                "select(.name == \"DB_USERNAME\")] | length == 1",
                "select(.name == \"DB_PASSWORD\")] | length == 1",
                "HEX(CONVERT(description USING utf8mb4))",
                "EAB491EAB3A0C2B7EC8898EC8898EBA38C20EC9588EB82B420EBACB8EAB5AC20ED9995EC9DB820ED9584EC9A94",
                "ECA09CED9CB420EBA781ED81AC20ED9995EC9DB820ED9584EC9A94",
                "--ssl-mode=VERIFY_IDENTITY",
                "https://truststore.pki.rds.amazonaws.com/ap-northeast-2/",
                "sha256sum --check --status",
                "aws ecs run-task",
                "violation-type-descriptions=verified");
        assertThat(workflow.split("--default-character-set=utf8mb4", -1)).hasSize(3);
        assertThat(workflow).doesNotContain(
                "description = '광고·수수료 안내 문구 확인 필요'",
                "description = '제휴 링크 확인 필요'");
        assertThat(oldWorkflow.split("--default-character-set=utf8mb4", -1)).hasSize(4);
        assertThat(oldWorkflow)
                .contains("HEX(CONVERT(description USING utf8mb4))")
                .doesNotContain("push:\n    branches: [dev]");
    }

    @Test
    void ecsBlueGreenKeepsRollbackSafetyWhileFinishingFaster() throws IOException {
        String workflow = Files.readString(ECS_DEPLOY_WORKFLOW);
        String template = Files.readString(ECS_TEMPLATE);

        assertThat(workflow).contains(
                "cancel-in-progress: false",
                "./gradlew test --tests '*ContractTest' --tests 'com.fuma.hiselectors.taskrun.*' bootJar --no-daemon",
                "--action ROLLBACK",
                "--stop-type ROLLBACK",
                "--action CONTINUE");
        assertThat(template).contains(
                "Strategy: BLUE_GREEN",
                "BakeTimeInMinutes: 3",
                "HealthCheckIntervalSeconds: 10",
                "DeploymentCircuitBreaker:",
                "Rollback: true",
                "TargetType: PAUSE",
                "LifecycleStages:\n                - POST_TEST_TRAFFIC_SHIFT");
        assertThat(template).doesNotContain(
                "BakeTimeInMinutes: 5",
                "BakeTimeInMinutes: 10");
    }
}
