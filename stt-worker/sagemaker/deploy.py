"""whisper large-v3 를 SageMaker Async Inference(GPU, scale-to-zero)로 배포. boto3 만 사용
(sagemaker SDK 는 pyiceberg→C++빌드 필요라 회피). 사전: pip install boto3, AWS 자격증명,
./package_and_upload.sh hi-selectors-stt 로 model.tar.gz 업로드.

재실행(이미 존재) 시 정리:
  aws sagemaker delete-endpoint        --endpoint-name whisper-large-v3-async
  aws sagemaker delete-endpoint-config --endpoint-config-name whisper-large-v3-async-cfg
  aws sagemaker delete-model           --model-name whisper-large-v3
"""
import boto3

REGION = "ap-northeast-2"
ROLE = "arn:aws:iam::167595589232:role/hiselectors-sagemaker-exec"
BUCKET = "hi-selectors-stt"
INSTANCE = "ml.g4dn.xlarge"  # T4 GPU
ENDPOINT = "whisper-large-v3-async"
CONFIG = "whisper-large-v3-async-cfg"
MODEL = "whisper-large-v3"

# AWS PyTorch 추론 DLC (서울). 태그 안 맞으면 ValidationException → 존재하는 태그로 교체.
IMAGE = ("763104351884.dkr.ecr.ap-northeast-2.amazonaws.com/"
         "pytorch-inference:2.3.0-gpu-py311-cu121-ubuntu20.04-sagemaker")

sm = boto3.client("sagemaker", region_name=REGION)

# 1) 모델 — model.tar.gz 안의 code/inference.py 를 DLC 가 실행하도록 env 지정.
sm.create_model(
    ModelName=MODEL,
    ExecutionRoleArn=ROLE,
    PrimaryContainer={
        "Image": IMAGE,
        "ModelDataUrl": f"s3://{BUCKET}/whisper/model.tar.gz",
        "Environment": {
            "SAGEMAKER_PROGRAM": "inference.py",
            "SAGEMAKER_SUBMIT_DIRECTORY": "/opt/ml/model/code",
            "SAGEMAKER_CONTAINER_LOG_LEVEL": "20",
            "SAGEMAKER_REGION": REGION,
        },
    },
)

# 2) Async 엔드포인트 설정 — 결과는 S3 로.
sm.create_endpoint_config(
    EndpointConfigName=CONFIG,
    ProductionVariants=[{
        "VariantName": "AllTraffic",
        "ModelName": MODEL,
        "InstanceType": INSTANCE,
        "InitialInstanceCount": 1,
    }],
    AsyncInferenceConfig={
        "OutputConfig": {"S3OutputPath": f"s3://{BUCKET}/whisper/output/"},
    },
)

# 3) 엔드포인트 생성 → InService 될 때까지 대기(~5-10분).
sm.create_endpoint(EndpointName=ENDPOINT, EndpointConfigName=CONFIG)
print("엔드포인트 생성 중... InService 대기")
sm.get_waiter("endpoint_in_service").wait(EndpointName=ENDPOINT)

# 4) scale-to-zero: 백로그 없으면 인스턴스 0 → GPU 과금 정지.
aas = boto3.client("application-autoscaling", region_name=REGION)
rid = f"endpoint/{ENDPOINT}/variant/AllTraffic"
aas.register_scalable_target(
    ServiceNamespace="sagemaker",
    ResourceId=rid,
    ScalableDimension="sagemaker:variant:DesiredInstanceCount",
    MinCapacity=0,   # ← 유휴 시 0
    MaxCapacity=2,
)
aas.put_scaling_policy(
    PolicyName="whisper-backlog-scaling",
    ServiceNamespace="sagemaker",
    ResourceId=rid,
    ScalableDimension="sagemaker:variant:DesiredInstanceCount",
    PolicyType="TargetTrackingScaling",
    TargetTrackingScalingPolicyConfiguration={
        "TargetValue": 1.0,
        "CustomizedMetricSpecification": {
            "MetricName": "ApproximateBacklogSizePerInstance",
            "Namespace": "AWS/SageMaker",
            "Dimensions": [{"Name": "EndpointName", "Value": ENDPOINT}],
            "Statistic": "Average",
        },
        "ScaleInCooldown": 300,
        "ScaleOutCooldown": 60,
    },
)
print("deployed:", ENDPOINT)
