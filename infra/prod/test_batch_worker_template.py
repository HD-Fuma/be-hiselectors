"""Offline CloudFormation contract checks: python3 -m unittest discover -s infra/prod -p 'test_*.py'."""

from pathlib import Path
import unittest

import yaml


class CfnLoader(yaml.SafeLoader):
    pass


def cfn_tag(loader, tag, node):
    if isinstance(node, yaml.ScalarNode):
        value = loader.construct_scalar(node)
    elif isinstance(node, yaml.SequenceNode):
        value = loader.construct_sequence(node)
    else:
        value = loader.construct_mapping(node)
    key = tag if tag in ("Ref", "Condition") else f"Fn::{tag}"
    return {key: value}


CfnLoader.add_multi_constructor("!", cfn_tag)
OMIT = object()


def conditional(value, enabled=()):
    """Evaluate only Fn::If/NoValue; keep resource references symbolic for assertions."""
    if value == {"Ref": "AWS::NoValue"}:
        return OMIT
    if isinstance(value, dict):
        if "Fn::If" in value:
            condition, yes, no = value["Fn::If"]
            return conditional(yes if condition in enabled else no, enabled)
        return {key: conditional(item, enabled) for key, item in value.items()}
    if isinstance(value, list):
        items = (conditional(item, enabled) for item in value)
        return [item for item in items if item is not OMIT]
    return value


class BatchWorkerTemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        infra = Path(__file__).resolve().parent.parent
        cls.template = yaml.load((infra / "prod/template.yaml").read_text(), Loader=CfnLoader)
        cls.analysis = yaml.load((infra / "analysis-fargate/template.yaml").read_text(), Loader=CfnLoader)
        cls.resources = cls.template["Resources"]

    def properties(self, name):
        return self.resources[name]["Properties"]

    def container(self, name, enabled=()):
        return conditional(self.properties(name)["ContainerDefinitions"][0], enabled)

    def environment(self, name, enabled=()):
        return {item["Name"]: item["Value"] for item in self.container(name, enabled)["Environment"]}

    def test_queues_are_bounded_encrypted_retained_and_alarm_on_existing_topic(self):
        queue = self.properties("TaskQueue")
        self.assertEqual(queue["VisibilityTimeout"], 300)
        self.assertEqual(queue["ReceiveMessageWaitTimeSeconds"], 20)
        self.assertEqual(queue["MessageRetentionPeriod"], 4 * 86400)
        self.assertEqual(queue["RedrivePolicy"], {
            "deadLetterTargetArn": {"Fn::GetAtt": "TaskDeadLetterQueue.Arn"}, "maxReceiveCount": 5})
        self.assertEqual(self.properties("TaskDeadLetterQueue")["MessageRetentionPeriod"], 14 * 86400)
        for name in ("TaskQueue", "TaskDeadLetterQueue"):
            self.assertFalse(self.properties(name).get("FifoQueue", False))
            self.assertIs(self.properties(name)["SqsManagedSseEnabled"], True)
            self.assertEqual(self.resources[name]["DeletionPolicy"], "Retain")
            self.assertEqual(self.resources[name]["UpdateReplacePolicy"], "Retain")
        alarm = self.properties("TaskDeadLetterAlarm")
        self.assertEqual(alarm["Threshold"], 0)
        self.assertEqual(alarm["AlarmActions"], [{"Ref": "AlertTopicArn"}])

    def test_initial_rollout_does_not_enable_publishers_or_start_workers(self):
        self.assertEqual(self.template["Parameters"]["EnableTaskQueuePublishing"]["Default"], "false")
        self.assertEqual(self.template["Parameters"]["BatchWorkerDesiredCount"]["Default"], 0)
        for name, scheduling in (("ApiTaskDefinition", "false"), ("SchedulerTaskDefinition", "true")):
            before = self.environment(name)
            self.assertEqual(before["SCHEDULING_ENABLED"], scheduling)
            self.assertFalse(any(key.startswith("TASK_QUEUE_") for key in before))
            after = self.environment(name, ("TaskQueuePublishingEnabled",))
            self.assertEqual(after["TASK_QUEUE_ENABLED"], "true")
            self.assertEqual(after["TASK_QUEUE_WORKER_ENABLED"], "false")
            self.assertEqual(after["TASK_QUEUE_URL"], {"Ref": "TaskQueue"})
            self.assertEqual(after["TASK_QUEUE_DLQ_URL"], {"Ref": "TaskDeadLetterQueue"})

    def test_worker_is_small_isolated_and_completion_lease_settings_match(self):
        task = self.properties("BatchWorkerTaskDefinition")
        worker = self.container("BatchWorkerTaskDefinition")
        env = self.environment("BatchWorkerTaskDefinition")
        self.assertEqual((task["Cpu"], task["Memory"]), ("512", "1024"))
        self.assertEqual(worker["Image"], {"Ref": "ImageUri"})
        self.assertEqual(task["TaskRoleArn"], {"Fn::GetAtt": "BatchWorkerTaskRole.Arn"})
        for flag in ("SCHEDULING_ENABLED", "DISCOVERY_DEFAULTS_ENABLED", "INSPECTION_POLICY_SYNC_ENABLED",
                     "APPLICATION_CONTENT_ANALYSIS_SCHEDULER_ENABLED", "APPLICATION_CONTENT_ANALYSIS_RUN_ONCE"):
            self.assertEqual(env[flag], "false")
        self.assertEqual(env["TASK_QUEUE_ENABLED"], "true")
        self.assertEqual(env["TASK_QUEUE_WORKER_ENABLED"], "true")
        self.assertEqual(env["TASK_QUEUE_CONCURRENCY"], "1")
        self.assertEqual(env["TASK_QUEUE_VISIBILITY_SECONDS"], "300")
        self.assertEqual(env["TASK_QUEUE_LEASE_SECONDS"], "120")
        self.assertEqual(env["TASK_QUEUE_HEARTBEAT_SECONDS"], "30")
        self.assertEqual(env["TASK_QUEUE_MAX_ATTEMPTS"], "3")
        self.assertEqual(env["DB_POOL_MAX"], "3")
        self.assertEqual((env["TASK_RUN_CORE_SIZE"], env["TASK_RUN_MAX_SIZE"]), ("2", "2"))
        self.assertEqual(env["SERVER_ADDRESS"], "127.0.0.1")
        self.assertEqual(env["MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE"], "readinessState,db,taskQueue")
        self.assertNotIn("PortMappings", worker)
        self.assertEqual(worker["StopTimeout"], 120)
        self.assertEqual(env["SHUTDOWN_PHASE_TIMEOUT"], "90s")
        self.assertIn("/actuator/health/readiness", worker["HealthCheck"]["Command"][1])
        service = self.properties("BatchWorkerService")
        self.assertNotIn("LoadBalancers", service)
        self.assertEqual(service["DesiredCount"], {"Ref": "BatchWorkerDesiredCount"})
        self.assertEqual(service["DeploymentConfiguration"]["MinimumHealthyPercent"], 100)
        self.assertEqual(service["DeploymentConfiguration"]["MaximumPercent"], 200)

    def test_queue_permissions_do_not_grant_consumption_to_api_or_unbounded_worker_access(self):
        api = self.properties("TaskRole")["Policies"][0]["PolicyDocument"]["Statement"]
        statements = [item for item in api if item["Resource"] == {"Fn::GetAtt": "TaskQueue.Arn"}]
        self.assertEqual([item["Action"] for item in statements], ["sqs:SendMessage"])
        worker = self.properties("BatchWorkerTaskRole")["Policies"][0]["PolicyDocument"]["Statement"]
        self.assertEqual(set(worker[0]["Action"]), {
            "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility",
            "sqs:GetQueueAttributes", "sqs:SendMessage"})
        self.assertEqual(worker[0]["Resource"], {"Fn::GetAtt": "TaskQueue.Arn"})
        self.assertEqual(worker[1]["Action"], "sqs:SendMessage")
        self.assertEqual(worker[1]["Resource"], {"Fn::GetAtt": "TaskDeadLetterQueue.Arn"})
        for item in worker:
            self.assertNotEqual(item["Resource"], "*")
            actions = item["Action"] if isinstance(item["Action"], list) else [item["Action"]]
            self.assertFalse(any(action in ("sqs:*", "sqs:PurgeQueue", "sqs:DeleteQueue")
                                 or action.startswith("s3:") for action in actions))

    def test_unknown_mail_keys_and_stt_endpoint_are_not_assumed(self):
        default = self.container("BatchWorkerTaskDefinition")
        self.assertNotIn("STT_WORKER_BASE_URL", self.environment("BatchWorkerTaskDefinition"))
        self.assertFalse(any(item["Name"].startswith("MAIL_") for item in default["Secrets"]))
        configured = self.container("BatchWorkerTaskDefinition", ("BatchWorkerMailConfigured", "BatchWorkerSttConfigured"))
        secrets = {item["Name"]: item["ValueFrom"] for item in configured["Secrets"]}
        self.assertEqual(secrets["MAIL_USERNAME"], {"Fn::Sub": "${BatchWorkerMailSecretArn}:MAIL_USERNAME::"})
        self.assertEqual(secrets["MAIL_PASSWORD"], {"Fn::Sub": "${BatchWorkerMailSecretArn}:MAIL_PASSWORD::"})

    def test_analysis_one_shot_does_not_register_general_cron_jobs(self):
        containers = self.analysis["Resources"]["TaskDefinition"]["Properties"]["ContainerDefinitions"]
        analysis = next(item for item in containers if item["Name"] == "analysis")
        env = {item["Name"]: item["Value"] for item in analysis["Environment"]}
        self.assertEqual(env["SCHEDULING_ENABLED"], "false")
        self.assertIn("--application.content-analysis.run-once=true", analysis["Command"])

    def test_resource_and_condition_references_resolve(self):
        known = set(self.resources) | set(self.template["Parameters"])

        def visit(value):
            if isinstance(value, dict):
                if "Ref" in value and not value["Ref"].startswith("AWS::"):
                    self.assertIn(value["Ref"], known)
                if "Fn::GetAtt" in value:
                    target = value["Fn::GetAtt"]
                    self.assertIn(target.split(".")[0] if isinstance(target, str) else target[0], self.resources)
                if "Fn::If" in value:
                    self.assertIn(value["Fn::If"][0], self.template["Conditions"])
                for item in value.values():
                    visit(item)
            elif isinstance(value, list):
                for item in value:
                    visit(item)

        visit(self.template)


if __name__ == "__main__":
    unittest.main()
