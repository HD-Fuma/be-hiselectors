package com.fuma.hiselectors.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AnalysisDeploymentWorkflowContractTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/deploy-analysis-worker.yml");
    private static final Path TEMPLATE = Path.of("infra/analysis-fargate/template.yaml");

    @Test
    void followsSuccessfulDevDeploymentsUsingTheSameShaAndSerializesStackUpdates() throws IOException {
        String workflow = Files.readString(WORKFLOW);
        assertThat(workflow).contains(
                "workflows: [\"Deploy production to ECS (blue-green)\", \"Deploy production\"]",
                "types: [completed]", "branches: [dev]",
                "github.event.workflow_run.conclusion == 'success'",
                "IMAGE_TAG: ${{ inputs.image_tag || github.event.workflow_run.head_sha || github.sha }}",
                "ref: ${{ github.event.workflow_run.head_sha || github.sha }}",
                "group: production-analysis", "cancel-in-progress: false",
                "default: KEEP", "options: [KEEP, ENABLED, DISABLED]",
                "SCHEDULE_STATE: ${{ inputs.schedule_state || 'KEEP' }}");
    }

    @Test
    void keepOmitsScheduleOverridesAndOnlyAnExplicitChoiceChangesState() throws Exception {
        String workflow = Files.readString(WORKFLOW);
        String step = workflow.substring(workflow.indexOf("      - name: Deploy Fargate analysis stack"),
                workflow.indexOf("      - name: Show stack outputs"));
        String script = step.substring(step.indexOf("        run: |\n") + "        run: |\n".length())
                .stripIndent();
        for (String state : new String[]{"KEEP", "ENABLED", "DISABLED", "INVALID"}) {
            // Shadow the AWS CLI: this contract checks arguments without connecting to AWS.
            ProcessBuilder builder = new ProcessBuilder("bash", "-c",
                    "aws() { printf '%s\\n' \"$@\"; }\n" + script).redirectErrorStream(true);
            var environment = builder.environment();
            environment.put("SCHEDULE_STATE", state);
            environment.put("SUBNET_IDS", "test-subnet");
            environment.put("SECURITY_GROUP_IDS", "test-security-group");
            environment.put("RUNTIME_SECRET_ARN", "test-secret");
            environment.put("ASSIGN_PUBLIC_IP", "DISABLED");
            environment.put("STACK_NAME", "test-analysis");
            environment.put("ECR_REGISTRY", "example.invalid");
            environment.put("ECR_REPOSITORY", "backend");
            environment.put("IMAGE_TAG", "test-sha");
            Process process = builder.start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Fake analysis deployment timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (state.equals("INVALID")) {
                assertThat(process.exitValue()).isNotZero();
                assertThat(output).doesNotContain("cloudformation");
                continue;
            }
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("cloudformation\ndeploy\n",
                    "ApiImageUri=example.invalid/backend:test-sha",
                    "WorkerImageUri=example.invalid/backend:test-sha-worker");
            assertThat(output).doesNotContain("ScheduleExpression=");
            if (state.equals("KEEP")) {
                assertThat(output).doesNotContain("ScheduleState=");
            } else {
                assertThat(output.lines().filter(line -> line.startsWith("ScheduleState=")))
                        .containsExactly("ScheduleState=" + state);
            }
        }
    }

    @Test
    void firstCreationDisablesTheTriggerAndAnalysisNeverStartsGeneralSchedulers() throws IOException {
        String template = Files.readString(TEMPLATE);
        assertThat(template.substring(template.indexOf("  ScheduleState:"),
                template.indexOf("  ScheduleExpression:"))).contains("Default: DISABLED");
        assertThat(template).contains("- Name: SCHEDULING_ENABLED\n              Value: 'false'");
    }
}
