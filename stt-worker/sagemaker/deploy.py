"""whisper large-v3 를 SageMaker Async Inference(GPU, scale-to-zero)로 배포.
사전: pip install sagemaker boto3 / AWS 자격증명 / SageMaker 실행 role / S3 버킷.
패키징: code/inference.py + code/requirements.txt 를 model.tar.gz 로 묶어 S3 업로드 후 이 스크립트 실행.

  tar -czf model.tar.gz -C sagemaker inference.py requirements.txt   # (실제론 code/ 하위로)
  aws s3 cp model.tar.gz s3://<BUCKET>/whisper/model.tar.gz
"""
import boto3
from sagemaker.async_inference import AsyncInferenceConfig
from sagemaker.pytorch import PyTorchModel

REGION = "ap-northeast-2"
ROLE = "arn:aws:iam::167595589232:role/hiselectors-sagemaker-exec"
BUCKET = "<YOUR_BUCKET>"
INSTANCE = "ml.g4dn.xlarge"  # T4 GPU. large-v3 float16 넉넉.
ENDPOINT = "whisper-large-v3-async"

model = PyTorchModel(
    model_data=f"s3://{BUCKET}/whisper/model.tar.gz",
    role=ROLE,
    framework_version="2.3",
    py_version="py311",
    entry_point="inference.py",
)

predictor = model.deploy(
    initial_instance_count=1,
    instance_type=INSTANCE,
    endpoint_name=ENDPOINT,
    async_inference_config=AsyncInferenceConfig(
        output_path=f"s3://{BUCKET}/whisper/output/",
        # 완료 알림 원하면 SNS: notification_config={"SuccessTopic": ..., "ErrorTopic": ...}
    ),
)

# ── scale-to-zero: 백로그 없으면 인스턴스 0 으로 내려 GPU 과금 정지 ──
aas = boto3.client("application-autoscaling", region_name=REGION)
resource_id = f"endpoint/{ENDPOINT}/variant/AllTraffic"
aas.register_scalable_target(
    ServiceNamespace="sagemaker",
    ResourceId=resource_id,
    ScalableDimension="sagemaker:variant:DesiredInstanceCount",
    MinCapacity=0,   # ← 핵심: 유휴 시 0
    MaxCapacity=2,
)
aas.put_scaling_policy(
    PolicyName="whisper-backlog-scaling",
    ServiceNamespace="sagemaker",
    ResourceId=resource_id,
    ScalableDimension="sagemaker:variant:DesiredInstanceCount",
    PolicyType="TargetTrackingScaling",
    TargetTrackingScalingPolicyConfiguration={
        "TargetValue": 1.0,  # 인스턴스당 대기 요청 1건 목표
        "CustomizedMetricSpecification": {
            "MetricName": "ApproximateBacklogSizePerInstance",
            "Namespace": "AWS/SageMaker",
            "Dimensions": [{"Name": "EndpointName", "Value": ENDPOINT}],
            "Statistic": "Average",
        },
        "ScaleInCooldown": 300,   # 5분 유휴 후 축소
        "ScaleOutCooldown": 60,
    },
)
print("deployed:", ENDPOINT)
