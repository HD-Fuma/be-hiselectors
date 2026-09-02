"""Offline checks for the additive standalone stack; no AWS calls or production mutations."""

from pathlib import Path
import re
import sys
import unittest

import yaml

# Reuse the existing CloudFormation parser and conditional helper, not a second parser.
INFRA = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(INFRA / "prod"))
from test_batch_worker_template import CfnLoader, conditional  # noqa: E402


class StandaloneTaskQueueTemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = yaml.load((INFRA / "task-queue/template.yaml").read_text(), Loader=CfnLoader)
        cls.prod = yaml.load((INFRA / "prod/template.yaml").read_text(), Loader=CfnLoader)
        cls.resources = cls.template["Resources"]

    def properties(self, name):
        return self.resources[name]["Properties"]

    def test_stack_owns_only_new_queue_worker_and_named_additive_policies(self):
        allowed_types = {"AWS::SQS::Queue", "AWS::CloudWatch::Alarm", "AWS::IAM::Role",
                         "AWS::IAM::Policy", "AWS::ECS::TaskDefinition", "AWS::ECS::Service"}
        self.assertEqual(len(self.resources), 10)
        self.assertTrue(all(resource["Type"] in allowed_types for resource in self.resources.values()))
        roles = [name for name, value in self.resources.items() if value["Type"] == "AWS::IAM::Role"]
        self.assertEqual(roles, ["BatchWorkerTaskRole"])
        for kind in ("AWS::ECS::TaskDefinition", "AWS::ECS::Service"):
            self.assertEqual(sum(value["Type"] == kind for value in self.resources.values()), 1)
        for name in ("TaskQueuePublisherPolicy", "GitHubBatchWorkerDeployPolicy", "TaskExecutionMailPolicy"):
            self.assertTrue(self.properties(name)["PolicyName"]["Fn::Sub"].startswith("${ResourcePrefix}-"))
        self.assertEqual(self.properties("TaskQueuePublisherPolicy")["Roles"], [{"Ref": "ApiTaskRoleName"}])
        self.assertEqual(self.properties("GitHubBatchWorkerDeployPolicy")["Roles"], [{"Ref": "GitHubDeployRoleName"}])

    def test_initial_stack_never_starts_worker_or_changes_publishers(self):
        parameters = self.template["Parameters"]
        self.assertEqual(parameters["ResourcePrefix"]["Default"], "hiselectors-task-queue")
        self.assertEqual(parameters["BatchWorkerDesiredCount"]["Default"], 0)
        self.assertNotIn("EnableTaskQueuePublishing", parameters)
        service = self.properties("BatchWorkerService")
        self.assertEqual(service["ServiceName"], {"Fn::Sub": "${ResourcePrefix}-worker"})
        self.assertEqual(service["DesiredCount"], {"Ref": "BatchWorkerDesiredCount"})
        self.assertEqual(service["Cluster"], {"Ref": "ClusterName"})
        network = service["NetworkConfiguration"]["AwsvpcConfiguration"]
        self.assertEqual(network["SecurityGroups"], [{"Ref": "TaskSecurityGroupId"}])
        self.assertEqual(network["Subnets"], {"Ref": "SubnetIds"})
        self.assertNotIn("LoadBalancers", service)
        self.assertEqual(self.resources["BatchWorkerLiveTaskCountLowAlarm"]["Condition"], "BatchWorkerServiceEnabled")

    def test_queue_retention_encryption_redrive_and_alarm_match_original_contract(self):
        queue = self.properties("TaskQueue")
        self.assertEqual((queue["VisibilityTimeout"], queue["ReceiveMessageWaitTimeSeconds"]), (300, 20))
        self.assertEqual(queue["MessageRetentionPeriod"], 4 * 86400)
        self.assertEqual(queue["RedrivePolicy"], {
            "deadLetterTargetArn": {"Fn::GetAtt": "TaskDeadLetterQueue.Arn"}, "maxReceiveCount": 5})
        self.assertEqual(self.properties("TaskDeadLetterQueue")["MessageRetentionPeriod"], 14 * 86400)
        for name in ("TaskQueue", "TaskDeadLetterQueue"):
            self.assertIs(self.properties(name)["SqsManagedSseEnabled"], True)
            self.assertFalse(self.properties(name).get("FifoQueue", False))
            self.assertEqual(self.resources[name]["DeletionPolicy"], "Retain")
            self.assertEqual(self.resources[name]["UpdateReplacePolicy"], "Retain")
        for name in ("TaskDeadLetterAlarm", "BatchWorkerLiveTaskCountLowAlarm"):
            self.assertEqual(self.properties(name)["AlarmActions"], [{"Ref": "AlertTopicArn"}])

    def test_worker_reuses_exact_environment_secrets_health_and_capacity(self):
        task = conditional(self.properties("BatchWorkerTaskDefinition"))
        baseline = conditional(self.prod["Resources"]["BatchWorkerTaskDefinition"]["Properties"])
        for field in ("Cpu", "Memory", "NetworkMode", "RequiresCompatibilities", "RuntimePlatform", "TaskRoleArn"):
            self.assertEqual(task[field], baseline[field])
        self.assertEqual(task["ExecutionRoleArn"], {"Ref": "TaskExecutionRoleArn"})
        worker = task["ContainerDefinitions"][0]
        original = baseline["ContainerDefinitions"][0]
        for field in ("Name", "Image", "Essential", "StopTimeout", "Environment", "Secrets", "HealthCheck"):
            self.assertEqual(worker[field], original[field])
        self.assertNotIn("PortMappings", worker)
        self.assertEqual(worker["LogConfiguration"]["Options"]["awslogs-group"], {"Ref": "LogGroupName"})
        deployment = self.properties("BatchWorkerService")["DeploymentConfiguration"]
        self.assertEqual((deployment["MinimumHealthyPercent"], deployment["MaximumPercent"]), (100, 200))

    def test_producer_is_send_only_worker_is_scoped_and_github_only_gains_worker_control(self):
        producer = self.properties("TaskQueuePublisherPolicy")["PolicyDocument"]["Statement"]
        self.assertEqual(producer, [{"Effect": "Allow", "Action": "sqs:SendMessage",
                                     "Resource": {"Fn::GetAtt": "TaskQueue.Arn"}}])
        worker = self.properties("BatchWorkerTaskRole")["Policies"][0]["PolicyDocument"]["Statement"]
        self.assertEqual(worker, self.prod["Resources"]["BatchWorkerTaskRole"]["Properties"]["Policies"][0]["PolicyDocument"]["Statement"])
        self.assertTrue(all(statement["Resource"] != "*" for statement in worker))
        deploy = self.properties("GitHubBatchWorkerDeployPolicy")["PolicyDocument"]["Statement"]
        self.assertEqual(len(deploy), 3)
        self.assertEqual(deploy[0]["Resource"], {"Fn::GetAtt": "BatchWorkerService.ServiceArn"})
        self.assertEqual(set(deploy[0]["Action"]), {"ecs:DescribeServices", "ecs:UpdateService"})
        self.assertEqual(deploy[1]["Action"], "ecs:ListTasks")
        self.assertEqual(deploy[1]["Condition"]["ArnEquals"]["ecs:cluster"], {
            "Fn::Sub": "arn:${AWS::Partition}:ecs:${AWS::Region}:${AWS::AccountId}:cluster/${ClusterName}"})
        self.assertEqual(deploy[2]["Action"], "iam:PassRole")
        self.assertEqual(deploy[2]["Resource"], {"Fn::GetAtt": "BatchWorkerTaskRole.Arn"})
        self.assertEqual(deploy[2]["Condition"]["StringEquals"]["iam:PassedToService"], "ecs-tasks.amazonaws.com")

    def test_optional_mail_permission_does_not_replace_existing_execution_policy(self):
        params = self.template["Parameters"]
        self.assertEqual(params["BatchWorkerMailSecretArn"]["Default"], "")
        self.assertEqual(params["BatchWorkerSttBaseUrl"]["Default"], "")
        mail = self.resources["TaskExecutionMailPolicy"]
        self.assertEqual(mail["Condition"], "BatchWorkerMailConfigured")
        self.assertEqual(mail["Properties"]["Roles"], [{"Ref": "TaskExecutionRoleName"}])
        self.assertEqual(mail["Properties"]["PolicyDocument"]["Statement"], [{
            "Effect": "Allow", "Action": "secretsmanager:GetSecretValue", "Resource": {"Ref": "BatchWorkerMailSecretArn"}}])
        worker = conditional(self.properties("BatchWorkerTaskDefinition")["ContainerDefinitions"][0])
        self.assertFalse(any(item["Name"].startswith("MAIL_") for item in worker["Secrets"]))
        self.assertFalse(any(item["Name"] == "STT_WORKER_BASE_URL" for item in worker["Environment"]))

    def test_stt_sidecar_is_optional_local_healthy_gated_and_does_not_raise_cpu(self):
        params = self.template["Parameters"]
        self.assertEqual(params["SttWorkerImageUri"]["Default"], "")
        self.assertEqual(params["SttBucket"]["Default"], "hi-selectors-stt")
        self.assertEqual(params["SttEndpoint"]["Default"], "whisper-large-v3-async")
        default = conditional(self.properties("BatchWorkerTaskDefinition"))
        self.assertEqual(default["Memory"], "1024")
        self.assertEqual(len(default["ContainerDefinitions"]), 1)
        self.assertEqual(len(conditional(self.properties("BatchWorkerTaskRole"))["Policies"]), 1)
        configured = conditional(self.properties("BatchWorkerTaskDefinition"), ("SttSidecarEnabled",))
        self.assertEqual((configured["Cpu"], configured["Memory"]), ("512", "2048"))
        worker, sidecar = configured["ContainerDefinitions"]
        self.assertEqual(worker["DependsOn"], [{"ContainerName": "stt-worker", "Condition": "HEALTHY"}])
        self.assertEqual(next(item["Value"] for item in worker["Environment"]
                              if item["Name"] == "STT_WORKER_BASE_URL"), "http://127.0.0.1:8900")
        self.assertEqual(sidecar["Name"], "stt-worker")
        self.assertEqual(sidecar["Image"], {"Ref": "SttWorkerImageUri"})
        self.assertIs(sidecar["Essential"], True)
        self.assertEqual(sidecar["Command"], ["uvicorn", "serve:app", "--host", "127.0.0.1", "--port", "8900"])
        self.assertEqual({item["Name"]: item["Value"] for item in sidecar["Environment"]}, {
            "STT_BACKEND": "sagemaker", "STT_S3_BUCKET": {"Ref": "SttBucket"},
            "STT_ENDPOINT": {"Ref": "SttEndpoint"}, "AWS_REGION": {"Ref": "AWS::Region"}})
        self.assertIn("http://127.0.0.1:8900/health", sidecar["HealthCheck"]["Command"][-1])
        self.assertNotIn("PortMappings", sidecar)
        self.assertEqual(sidecar["LogConfiguration"]["Options"]["awslogs-group"], {"Ref": "LogGroupName"})
        self.assertEqual(sidecar["LogConfiguration"]["Options"]["awslogs-stream-prefix"], "stt")

    def test_stt_external_url_and_sidecar_image_are_mutually_exclusive(self):
        rule = self.template["Rules"]["OnlyOneSttSource"]["Assertions"][0]["Assert"]
        self.assertEqual(rule, {"Fn::Or": [
            {"Fn::Equals": [{"Ref": "SttWorkerImageUri"}, ""]},
            {"Fn::Equals": [{"Ref": "BatchWorkerSttBaseUrl"}, ""]}]})
        external = conditional(self.properties("BatchWorkerTaskDefinition"), ("BatchWorkerSttConfigured",))
        self.assertEqual(external["Memory"], "1024")
        self.assertEqual(len(external["ContainerDefinitions"]), 1)
        environment = external["ContainerDefinitions"][0]["Environment"]
        self.assertEqual([item["Value"] for item in environment if item["Name"] == "STT_WORKER_BASE_URL"],
                         [{"Ref": "BatchWorkerSttBaseUrl"}])

    def test_sidecar_permission_is_conditional_and_only_covers_endpoint_and_whisper_prefix(self):
        policies = conditional(self.properties("BatchWorkerTaskRole"), ("SttSidecarEnabled",))["Policies"]
        self.assertEqual(len(policies), 2)
        statements = policies[1]["PolicyDocument"]["Statement"]
        self.assertEqual(statements, [
            {"Effect": "Allow", "Action": "sagemaker:InvokeEndpointAsync", "Resource": {
                "Fn::Sub": "arn:${AWS::Partition}:sagemaker:${AWS::Region}:${AWS::AccountId}:endpoint/${SttEndpoint}"}},
            {"Effect": "Allow", "Action": "s3:PutObject", "Resource": {
                "Fn::Sub": "arn:${AWS::Partition}:s3:::${SttBucket}/whisper/input/*"}},
            {"Effect": "Allow", "Action": "s3:GetObject", "Resource": {
                "Fn::Sub": "arn:${AWS::Partition}:s3:::${SttBucket}/whisper/*"}},
            {"Effect": "Allow", "Action": "s3:ListBucket", "Resource": {
                "Fn::Sub": "arn:${AWS::Partition}:s3:::${SttBucket}"},
             "Condition": {"StringLike": {"s3:prefix": "whisper/*"}}},
        ])

    def test_all_references_resolve_without_any_legacy_stack_dependency(self):
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
                if "Fn::Sub" in value and isinstance(value["Fn::Sub"], str):
                    for name in re.findall(r"\$\{([^}]+)}", value["Fn::Sub"]):
                        if not name.startswith("AWS::"):
                            self.assertIn(name.split(".")[0], known)
                self.assertNotIn("Fn::ImportValue", value)
                for item in value.values():
                    visit(item)
            elif isinstance(value, list):
                for item in value:
                    visit(item)

        visit(self.template)


if __name__ == "__main__":
    unittest.main()
