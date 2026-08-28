"""콘텐츠 검수 전용 SageMaker Async 엔드포인트를 생성한다.

필수 환경변수: SAGEMAKER_ROLE_ARN
선택 환경변수: AWS_REGION, CONTENT_STT_S3_BUCKET, CONTENT_STT_ENDPOINT,
CONTENT_STT_MODEL_NAME, CONTENT_STT_ENDPOINT_CONFIG, CONTENT_STT_INSTANCE_TYPE
"""
import os

import boto3

REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
ROLE = os.environ["SAGEMAKER_ROLE_ARN"]
BUCKET = os.environ.get("CONTENT_STT_S3_BUCKET", "hi-selectors-stt")
ENDPOINT = os.environ.get("CONTENT_STT_ENDPOINT", "content-whisper-large-v3-async")
MODEL = os.environ.get("CONTENT_STT_MODEL_NAME", "content-whisper-large-v3")
CONFIG = os.environ.get(
    "CONTENT_STT_ENDPOINT_CONFIG", "content-whisper-large-v3-async-cfg")
INSTANCE = os.environ.get("CONTENT_STT_INSTANCE_TYPE", "ml.g4dn.xlarge")
IMAGE = os.environ.get(
    "CONTENT_STT_INFERENCE_IMAGE",
    "763104351884.dkr.ecr.ap-northeast-2.amazonaws.com/"
    "pytorch-inference:2.3.0-gpu-py311-cu121-ubuntu20.04-sagemaker",
)

client = boto3.client("sagemaker", region_name=REGION)
client.create_model(
    ModelName=MODEL,
    ExecutionRoleArn=ROLE,
    PrimaryContainer={
        "Image": IMAGE,
        "ModelDataUrl": f"s3://{BUCKET}/content-whisper/model.tar.gz",
        "Environment": {
            "SAGEMAKER_PROGRAM": "inference.py",
            "SAGEMAKER_SUBMIT_DIRECTORY": "/opt/ml/model/code",
            "SAGEMAKER_CONTAINER_LOG_LEVEL": "20",
            "SAGEMAKER_REGION": REGION,
        },
    },
)
client.create_endpoint_config(
    EndpointConfigName=CONFIG,
    ProductionVariants=[{
        "VariantName": "AllTraffic",
        "ModelName": MODEL,
        "InstanceType": INSTANCE,
        "InitialInstanceCount": 1,
    }],
    AsyncInferenceConfig={
        "OutputConfig": {
            "S3OutputPath": f"s3://{BUCKET}/content-whisper/output/",
            "S3FailurePath": f"s3://{BUCKET}/content-whisper/failure/",
        },
    },
)
client.create_endpoint(EndpointName=ENDPOINT, EndpointConfigName=CONFIG)
print(f"creating endpoint: {ENDPOINT}")
