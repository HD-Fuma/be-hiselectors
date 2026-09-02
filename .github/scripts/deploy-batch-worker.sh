#!/usr/bin/env bash
set -euo pipefail

: "${ECS_CLUSTER:?}"
: "${ECS_BATCH_WORKER_SERVICE:?}"
: "${ECS_BATCH_WORKER_CONTAINER_NAME:?}"
: "${IMAGE_URI:?}"
: "${RUNNER_TEMP:?}"
: "${GITHUB_OUTPUT:?}"
: "${GITHUB_STEP_SUMMARY:?}"

work_dir="$RUNNER_TEMP/ecs-batch-worker"
mkdir -p "$work_dir"
echo "ready=false" >> "$GITHUB_OUTPUT"

aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_BATCH_WORKER_SERVICE" \
  --output json > "$work_dir/service.json"
jq -e '
  (.failures | length == 0) and (.services | length == 1)
  and (.services[0] | .status == "ACTIVE"
    and .deploymentController.type == "ECS"
    and .deploymentConfiguration.strategy == "ROLLING"
    and .deploymentConfiguration.deploymentCircuitBreaker.enable == true
    and .deploymentConfiguration.deploymentCircuitBreaker.rollback == true
    and .deploymentConfiguration.minimumHealthyPercent == 100
    and .deploymentConfiguration.maximumPercent == 200
    and .pendingCount == 0 and .runningCount == .desiredCount
    and (.deployments | length) == 1)
' "$work_dir/service.json" > /dev/null
desired_count="$(jq -r '.services[0].desiredCount' "$work_dir/service.json")"
current_task_definition="$(jq -r '.services[0].taskDefinition' "$work_dir/service.json")"

aws ecs describe-task-definition --task-definition "$current_task_definition" \
  --query taskDefinition --output json > "$work_dir/current-task-definition.json"
jq -e --arg container "$ECS_BATCH_WORKER_CONTAINER_NAME" '
  [.containerDefinitions[] | select(.name == $container)] as $containers
  | ($containers | length) == 1
    and ($containers[0] | .essential == true and .stopTimeout == 120
      and .healthCheck != null
      and ([.environment[]? | select(.name == "TASK_QUEUE_ENABLED" and .value == "true")] | length) == 1
      and ([.environment[]? | select(.name == "TASK_QUEUE_WORKER_ENABLED" and .value == "true")] | length) == 1
      and ([.environment[]? | select(.name == "SCHEDULING_ENABLED" and .value == "false")] | length) == 1)
' "$work_dir/current-task-definition.json" > /dev/null || {
  echo "Expected an isolated queue consumer with a health check and 120-second stop timeout." >&2
  exit 1
}

# Keep manual feature flags, queue URLs, roles and desired count exactly as provisioned.
jq --arg container "$ECS_BATCH_WORKER_CONTAINER_NAME" --arg image "$IMAGE_URI" '
  del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities,
      .registeredAt, .registeredBy, .deregisteredAt)
  | (.containerDefinitions[] | select(.name == $container) | .image) = $image
' "$work_dir/current-task-definition.json" > "$work_dir/new-task-definition.json"
new_task_definition="$(aws ecs register-task-definition \
  --cli-input-json "file://$work_dir/new-task-definition.json" \
  --query taskDefinition.taskDefinitionArn --output text)"
aws ecs update-service --cluster "$ECS_CLUSTER" --service "$ECS_BATCH_WORKER_SERVICE" \
  --task-definition "$new_task_definition" \
  --query 'service.{serviceArn:serviceArn,desiredCount:desiredCount,taskDefinition:taskDefinition}'
if ! aws ecs wait services-stable --cluster "$ECS_CLUSTER" --services "$ECS_BATCH_WORKER_SERVICE"; then
  echo "Worker rollout did not stabilize. API deployment is blocked; inspect the worker before retrying." >&2
  exit 1
fi

# The ECS waiter also succeeds after rollback or at desired=0: neither proves readiness.
aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_BATCH_WORKER_SERVICE" \
  --output json > "$work_dir/service-final.json"
jq -e --arg task_definition "$new_task_definition" --argjson desired "$desired_count" '
  (.failures | length == 0) and (.services | length == 1)
  and (.services[0] | .status == "ACTIVE" and .taskDefinition == $task_definition
    and .desiredCount == $desired and .runningCount == $desired and .pendingCount == 0
    and (.deployments | length) == 1
    and .deployments[0].taskDefinition == $task_definition)
' "$work_dir/service-final.json" > /dev/null || {
  echo "Worker rolled back or changed during rollout. API deployment is blocked." >&2
  exit 1
}
echo "task_definition_arn=$new_task_definition" >> "$GITHUB_OUTPUT"

if (( desired_count > 0 )); then
  aws ecs list-tasks --cluster "$ECS_CLUSTER" --service-name "$ECS_BATCH_WORKER_SERVICE" \
    --desired-status RUNNING --output json > "$work_dir/task-arns.json"
  task_arns=()
  while IFS= read -r task_arn; do
    task_arns+=("$task_arn")
  done < <(jq -r '.taskArns[]' "$work_dir/task-arns.json")
  if (( ${#task_arns[@]} != desired_count )); then
    echo "Worker running-task count changed during readiness validation." >&2
    exit 1
  fi
  aws ecs describe-tasks --cluster "$ECS_CLUSTER" --tasks "${task_arns[@]}" \
    --output json > "$work_dir/tasks.json"
  jq -e --arg task_definition "$new_task_definition" --argjson desired "$desired_count" '
    (.failures | length == 0) and (.tasks | length == $desired)
    and all(.tasks[]; .taskDefinitionArn == $task_definition
      and .lastStatus == "RUNNING" and .healthStatus == "HEALTHY")
  ' "$work_dir/tasks.json" > /dev/null
  echo "ready=true" >> "$GITHUB_OUTPUT"
  readiness="Running tasks passed container readiness; verify queue consumption before the first manual publisher enable."
else
  readiness="Prepared only (desired count 0): no worker is ready, so queue publishing must remain disabled."
fi

{
  echo "### Batch worker release"
  echo "- Image: \`$IMAGE_URI\`"
  echo "- Task definition: \`$new_task_definition\`"
  echo "- Previous task definition: \`$current_task_definition\`"
  echo "- $readiness"
  echo "- Desired count and publisher/consumer flags remain unchanged."
} >> "$GITHUB_STEP_SUMMARY"
