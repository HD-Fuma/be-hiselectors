package com.fuma.hiselectors.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class BatchWorkerDeploymentContractTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/deploy-ecs-blue-green.yml");
    private static final Path WORKER = Path.of(".github/scripts/deploy-batch-worker.sh");
    private static final Path MIGRATION = Path.of("src/main/resources/db/021_task_run_queue.sql");
    private static final String IMAGE = "example.invalid/backend:test-sha";
    private static final String TASK_DEFINITION = """
            {"taskDefinitionArn":"old-worker","revision":1,"taskRoleArn":"worker-role",
             "containerDefinitions":[{"name":"batch-worker","image":"old-image",
               "essential":true,"stopTimeout":120,"healthCheck":{"command":["CMD","true"]},
               "environment":[{"name":"TASK_QUEUE_ENABLED","value":"true"},
                 {"name":"TASK_QUEUE_WORKER_ENABLED","value":"true"},
                 {"name":"SCHEDULING_ENABLED","value":"false"}]}]}
            """;
    private static final String FAKE_AWS_DRIVER = """
            aws() {
              printf '%s\\n' "$*" >> "$TEST_TRACE"
              case "$1 $2" in
                'ecs describe-services')
                  if [[ -f "$TEST_UPDATED" ]]; then
                    printf '%s\\n' "$TEST_AFTER"
                  else
                    printf '%s\\n' "$TEST_BEFORE"
                  fi ;;
                'ecs describe-task-definition') printf '%s\\n' "$TEST_DEFINITION" ;;
                'ecs register-task-definition') printf '%s\\n' 'new-worker' ;;
                'ecs update-service') touch "$TEST_UPDATED"; printf '%s\\n' '{}' ;;
                'ecs wait') return 0 ;;
                'ecs list-tasks') printf '%s\\n' '{"taskArns":["worker-task"]}' ;;
                'ecs describe-tasks') printf '%s\\n' "$TEST_TASKS" ;;
                *) printf 'Unexpected fake AWS command: %s\\n' "$*" >&2; return 64 ;;
              esac
            }
            export -f aws
            bash "$TEST_WORKER_SCRIPT"
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void schemaIsAdditiveAndLegacyRowsStayLocal() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
                "ADD COLUMN business_payload TEXT NULL",
                "ADD COLUMN queue_managed BOOLEAN NOT NULL DEFAULT FALSE",
                "ADD COLUMN queue_attempts INT NOT NULL DEFAULT 0",
                "ADD COLUMN queue_available_at DATETIME(6) NULL",
                "ADD COLUMN queue_lease_until DATETIME(6) NULL",
                "ADD COLUMN last_enqueued_at DATETIME(6) NULL",
                "ADD INDEX idx_task_run_queue_pending (queue_managed, status, queue_available_at, last_enqueued_at)",
                "ADD INDEX idx_task_run_queue_heartbeat (queue_managed, heartbeat_at)",
                "information_schema.columns", "information_schema.statistics",
                "SET SESSION lock_wait_timeout = 15", "ALGORITHM=INPLACE, LOCK=NONE");
        assertThat(sql).doesNotContain("UPDATE task_run", "DELETE ", "DROP ", "MODIFY COLUMN");
        assertThat(sql.split("PREPARE task_run_queue_ddl FROM", -1)).hasSize(9);
        assertThat(sql.indexOf("ADD INDEX")).isGreaterThan(sql.lastIndexOf("ADD COLUMN"));
    }

    @Test
    void releaseOrderKeepsSchemaAndConsumerAheadOfPublishersWithoutEnablingThem() throws IOException {
        String workflow = Files.readString(WORKFLOW);
        int migration = workflow.indexOf("- name: Apply and verify additive database migrations");
        int worker = workflow.indexOf("- name: Deploy optional batch worker before API");
        int api = workflow.indexOf("- name: Register task definition and start deployment");
        int scheduler = workflow.indexOf("- name: Keep scheduler image aligned");
        assertThat(migration).isGreaterThan(0);
        assertThat(worker).isGreaterThan(migration);
        assertThat(api).isGreaterThan(worker);
        assertThat(scheduler).isGreaterThan(api);
        assertThat(workflow.substring(migration, worker)).contains(
                "src/main/resources/db/021_task_run_queue.sql",
                "idx_task_run_queue_pending", "idx_task_run_queue_heartbeat",
                "datetime_precision = 6", "GROUP_CONCAT(column_name ORDER BY seq_in_index)",
                "task-run-queue-schema=verified").doesNotContain("\n        if:");
        assertThat(workflow).contains(
                "Set both optional ECS_BATCH_WORKER_SERVICE and ECS_BATCH_WORKER_CONTAINER_NAME, or neither.",
                "if: env.ECS_BATCH_WORKER_SERVICE != ''",
                "API queue publishing is enabled but the new batch worker is not ready.",
                "Scheduler queue publishing is enabled but the new batch worker is not ready.",
                "--tests 'com.fuma.hiselectors.taskrun.*'",
                "--action ROLLBACK", "--stop-type ROLLBACK");
        assertThat(workflow).doesNotContain("TASK_QUEUE_ENABLED=true", "--desired-count");
    }

    @Test
    void workerDeploymentRejectsStandbyFalseReadinessRollbackAndUnhealthyTasks() throws Exception {
        DeploymentResult running = deploy("healthy", 1, "new-worker", "HEALTHY");
        assertThat(running.exitCode()).as(running.output()).isZero();
        assertThat(running.outputs()).contains("ready=true");
        assertThat(running.trace()).contains("ecs list-tasks").doesNotContain("--desired-count");
        var mapper = new ObjectMapper();
        var before = mapper.readTree(TASK_DEFINITION);
        var after = mapper.readTree(Files.readString(
                running.directory().resolve("ecs-batch-worker/new-task-definition.json")));
        assertThat(after.path("taskRoleArn")).isEqualTo(before.path("taskRoleArn"));
        assertThat(after.path("containerDefinitions").get(0).path("environment"))
                .isEqualTo(before.path("containerDefinitions").get(0).path("environment"));
        assertThat(after.path("containerDefinitions").get(0).path("image").asString()).isEqualTo(IMAGE);
        assertThat(after.has("taskDefinitionArn")).isFalse();

        DeploymentResult standby = deploy("standby", 0, "new-worker", "HEALTHY");
        assertThat(standby.exitCode()).as(standby.output()).isZero();
        assertThat(standby.outputs()).contains("ready=false").doesNotContain("ready=true");
        assertThat(standby.trace()).doesNotContain("ecs list-tasks");

        DeploymentResult rollback = deploy("rollback", 1, "old-worker", "HEALTHY");
        assertThat(rollback.exitCode()).isNotZero();
        assertThat(rollback.output()).contains("Worker rolled back or changed during rollout");
        assertThat(rollback.outputs()).doesNotContain("ready=true");

        DeploymentResult unhealthy = deploy("unhealthy", 1, "new-worker", "UNHEALTHY");
        assertThat(unhealthy.exitCode()).isNotZero();
        assertThat(unhealthy.outputs()).doesNotContain("ready=true");
    }

    private DeploymentResult deploy(String name, int desired, String finalDefinition, String health)
            throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(name));
        Path outputs = Files.createFile(directory.resolve("outputs"));
        Path trace = Files.createFile(directory.resolve("trace"));
        var processBuilder = new ProcessBuilder("bash", "-c", FAKE_AWS_DRIVER)
                .redirectErrorStream(true);
        var environment = processBuilder.environment();
        environment.put("ECS_CLUSTER", "test-cluster");
        environment.put("ECS_BATCH_WORKER_SERVICE", "test-worker");
        environment.put("ECS_BATCH_WORKER_CONTAINER_NAME", "batch-worker");
        environment.put("IMAGE_URI", IMAGE);
        environment.put("RUNNER_TEMP", directory.toString());
        environment.put("GITHUB_OUTPUT", outputs.toString());
        environment.put("GITHUB_STEP_SUMMARY", directory.resolve("summary").toString());
        environment.put("TEST_WORKER_SCRIPT", WORKER.toAbsolutePath().toString());
        environment.put("TEST_UPDATED", directory.resolve("updated").toString());
        environment.put("TEST_TRACE", trace.toString());
        environment.put("TEST_BEFORE", service(desired, "old-worker"));
        environment.put("TEST_AFTER", service(desired, finalDefinition));
        environment.put("TEST_DEFINITION", TASK_DEFINITION);
        environment.put("TEST_TASKS", """
                {"failures":[],"tasks":[{"taskDefinitionArn":"new-worker",
                    "lastStatus":"RUNNING","healthStatus":"%s"}]}
                """.formatted(health));
        Process process = processBuilder.start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Fake worker deployment timed out");
        }
        return new DeploymentResult(process.exitValue(),
                new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                Files.readString(outputs), Files.readString(trace), directory);
    }

    private String service(int desired, String taskDefinition) {
        return """
                {"failures":[],"services":[{"status":"ACTIVE","taskDefinition":"%s",
                  "desiredCount":%d,"runningCount":%d,"pendingCount":0,
                  "deploymentController":{"type":"ECS"},
                  "deploymentConfiguration":{"strategy":"ROLLING","minimumHealthyPercent":100,
                    "maximumPercent":200,"deploymentCircuitBreaker":{"enable":true,"rollback":true}},
                  "deployments":[{"taskDefinition":"%s"}]}]}
                """.formatted(taskDefinition, desired, desired, taskDefinition);
    }

    private record DeploymentResult(int exitCode, String output, String outputs,
            String trace, Path directory) {
    }
}
