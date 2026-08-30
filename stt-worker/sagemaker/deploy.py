"""구조화 STT 모델을 기존 SageMaker Async 엔드포인트에 안전하게 배포한다.

package.py가 만든 release manifest를 읽고 새 Model/EndpointConfig를 생성한다.
엔드포인트가 있으면 UpdateEndpoint, 없으면 CreateEndpoint를 사용한다.
기존 EndpointConfig는 자동 삭제하지 않아 즉시 롤백할 수 있다.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
from typing import Any

import boto3
from botocore.exceptions import ClientError


HERE = Path(__file__).resolve().parent
DEFAULT_MANIFEST = HERE / "build" / "release.json"
DEFAULT_RESULT = HERE / "build" / "deploy-result.json"
DEFAULT_ENDPOINT = "whisper-large-v3-async"
DEFAULT_INSTANCE = "ml.g4dn.xlarge"
DEFAULT_IMAGE = (
    "763104351884.dkr.ecr.ap-northeast-2.amazonaws.com/"
    "pytorch-inference:2.3.0-gpu-py311-cu121-ubuntu20.04-sagemaker"
)


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="새 모델 버전을 만들고 기존 SageMaker Async 엔드포인트를 갱신합니다."
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path(os.environ.get("STT_RELEASE_MANIFEST", DEFAULT_MANIFEST)),
    )
    parser.add_argument(
        "--result",
        type=Path,
        default=Path(os.environ.get("STT_DEPLOY_RESULT", DEFAULT_RESULT)),
    )
    parser.add_argument(
        "--endpoint",
        default=os.environ.get("STT_ENDPOINT", DEFAULT_ENDPOINT),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="AWS 조회와 배포 계획 출력만 하고 리소스를 변경하지 않는다.",
    )
    return parser.parse_args()


def _not_found(error: ClientError) -> bool:
    return error.response.get("Error", {}).get("Code") in {
        "ValidationException",
        "ResourceNotFound",
        "ResourceNotFoundException",
    }


def _describe_or_none(client: Any, operation: str, **kwargs: Any) -> dict | None:
    try:
        return getattr(client, operation)(**kwargs)
    except ClientError as error:
        if _not_found(error):
            return None
        raise


def _resource_name(prefix: str, release_id: str, suffix: str = "") -> str:
    raw = f"{prefix}-{release_id}{suffix}"
    value = re.sub(r"[^A-Za-z0-9-]", "-", raw).strip("-")
    value = re.sub(r"-+", "-", value)
    if not value or len(value) > 63:
        raise ValueError(f"SageMaker 리소스 이름이 유효하지 않습니다: {value!r}")
    return value


def _existing_context(client: Any, endpoint_name: str) -> dict | None:
    endpoint = _describe_or_none(
        client, "describe_endpoint", EndpointName=endpoint_name
    )
    if endpoint is None:
        return None
    if endpoint["EndpointStatus"] != "InService":
        raise RuntimeError(
            f"기존 엔드포인트가 InService가 아닙니다: "
            f"{endpoint_name}={endpoint['EndpointStatus']}"
        )
    config = client.describe_endpoint_config(
        EndpointConfigName=endpoint["EndpointConfigName"]
    )
    variant = config["ProductionVariants"][0]
    model = client.describe_model(ModelName=variant["ModelName"])
    return {
        "endpoint": endpoint,
        "config": config,
        "variant": variant,
        "model": model,
    }


def _create_model_if_needed(
    client: Any,
    *,
    name: str,
    role: str,
    image: str,
    model_data_url: str,
    region: str,
) -> None:
    existing = _describe_or_none(client, "describe_model", ModelName=name)
    if existing is not None:
        container = existing["PrimaryContainer"]
        actual = container.get("ModelDataUrl")
        if (
            actual != model_data_url
            or container.get("Image") != image
            or existing.get("ExecutionRoleArn") != role
        ):
            raise RuntimeError(
                f"기존 Model {name}의 아티팩트·이미지·실행 role이 다릅니다."
            )
        print(f"기존 Model 재사용: {name}")
        return
    client.create_model(
        ModelName=name,
        ExecutionRoleArn=role,
        PrimaryContainer={
            "Image": image,
            "ModelDataUrl": model_data_url,
            "Environment": {
                "SAGEMAKER_PROGRAM": "inference.py",
                "SAGEMAKER_SUBMIT_DIRECTORY": "/opt/ml/model/code",
                "SAGEMAKER_CONTAINER_LOG_LEVEL": "20",
                "SAGEMAKER_REGION": region,
            },
        },
    )
    print(f"Model 생성: {name}")


def _create_config_if_needed(
    client: Any,
    *,
    name: str,
    model_name: str,
    variant_name: str,
    instance_type: str,
    initial_count: int,
    output_path: str,
    failure_path: str,
) -> None:
    existing = _describe_or_none(
        client, "describe_endpoint_config", EndpointConfigName=name
    )
    if existing is not None:
        variant = existing["ProductionVariants"][0]
        output = existing.get("AsyncInferenceConfig", {}).get("OutputConfig", {})
        if (
            variant.get("ModelName") != model_name
            or variant.get("VariantName") != variant_name
            or variant.get("InstanceType") != instance_type
            or output.get("S3OutputPath") != output_path
            or output.get("S3FailurePath") != failure_path
        ):
            raise RuntimeError(
                f"기존 EndpointConfig {name}의 모델·인스턴스·S3 설정이 다릅니다."
            )
        print(f"기존 EndpointConfig 재사용: {name}")
        return
    client.create_endpoint_config(
        EndpointConfigName=name,
        ProductionVariants=[{
            "VariantName": variant_name,
            "ModelName": model_name,
            "InstanceType": instance_type,
            "InitialInstanceCount": initial_count,
        }],
        AsyncInferenceConfig={
            "OutputConfig": {
                "S3OutputPath": output_path,
                "S3FailurePath": failure_path,
            },
        },
    )
    print(f"EndpointConfig 생성: {name}")


def _ensure_autoscaling(
    *, region: str, endpoint_name: str, variant_name: str
) -> None:
    autoscaling = boto3.client("application-autoscaling", region_name=region)
    resource_id = f"endpoint/{endpoint_name}/variant/{variant_name}"
    current = autoscaling.describe_scalable_targets(
        ServiceNamespace="sagemaker",
        ResourceIds=[resource_id],
        ScalableDimension="sagemaker:variant:DesiredInstanceCount",
    ).get("ScalableTargets", [])
    if current:
        target = current[0]
        print(
            "기존 오토스케일링 유지: "
            f"min={target['MinCapacity']}, max={target['MaxCapacity']}"
        )
        return

    autoscaling.register_scalable_target(
        ServiceNamespace="sagemaker",
        ResourceId=resource_id,
        ScalableDimension="sagemaker:variant:DesiredInstanceCount",
        MinCapacity=int(os.environ.get("STT_MIN_CAPACITY", "0")),
        MaxCapacity=int(os.environ.get("STT_MAX_CAPACITY", "2")),
    )
    autoscaling.put_scaling_policy(
        PolicyName=f"{endpoint_name}-backlog-scaling",
        ServiceNamespace="sagemaker",
        ResourceId=resource_id,
        ScalableDimension="sagemaker:variant:DesiredInstanceCount",
        PolicyType="TargetTrackingScaling",
        TargetTrackingScalingPolicyConfiguration={
            "TargetValue": 1.0,
            "CustomizedMetricSpecification": {
                "MetricName": "ApproximateBacklogSizePerInstance",
                "Namespace": "AWS/SageMaker",
                "Dimensions": [{"Name": "EndpointName", "Value": endpoint_name}],
                "Statistic": "Average",
            },
            "ScaleInCooldown": 300,
            "ScaleOutCooldown": 60,
        },
    )
    scale_from_zero = autoscaling.put_scaling_policy(
        PolicyName=f"{endpoint_name}-scale-from-zero",
        ServiceNamespace="sagemaker",
        ResourceId=resource_id,
        ScalableDimension="sagemaker:variant:DesiredInstanceCount",
        PolicyType="StepScaling",
        StepScalingPolicyConfiguration={
            "AdjustmentType": "ChangeInCapacity",
            "MetricAggregationType": "Maximum",
            "Cooldown": 300,
            "StepAdjustments": [
                {"MetricIntervalLowerBound": 0, "ScalingAdjustment": 1}
            ],
        },
    )
    boto3.client("cloudwatch", region_name=region).put_metric_alarm(
        AlarmName=f"{endpoint_name}-has-backlog-without-capacity",
        Namespace="AWS/SageMaker",
        MetricName="HasBacklogWithoutCapacity",
        Dimensions=[{"Name": "EndpointName", "Value": endpoint_name}],
        Statistic="Maximum",
        Period=60,
        EvaluationPeriods=1,
        Threshold=1.0,
        ComparisonOperator="GreaterThanOrEqualToThreshold",
        TreatMissingData="missing",
        AlarmActions=[scale_from_zero["PolicyARN"]],
    )
    print("오토스케일링 생성: min=0, max=2, scale-from-zero 포함")


def main() -> None:
    args = _arguments()
    if not args.manifest.is_file():
        raise FileNotFoundError(
            f"release manifest가 없습니다: {args.manifest}. package.py를 먼저 실행하세요."
        )
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    release_id = manifest["releaseId"]
    region = os.environ.get("AWS_REGION", manifest.get("region", "ap-northeast-2"))
    bucket = manifest["bucket"]
    key = manifest["key"]
    model_data_url = manifest["modelDataUrl"]

    s3 = boto3.client("s3", region_name=region)
    s3.head_object(Bucket=bucket, Key=key)

    client = boto3.client("sagemaker", region_name=region)
    context = _existing_context(client, args.endpoint)
    old_config_name = (
        context["endpoint"]["EndpointConfigName"] if context else None
    )

    model_name = os.environ.get(
        "STT_MODEL_NAME",
        _resource_name("whisper-large-v3-structured", release_id),
    )
    config_name = os.environ.get(
        "STT_ENDPOINT_CONFIG",
        _resource_name("whisper-large-v3-structured", release_id, "-cfg"),
    )

    if context:
        variant = context["variant"]
        container = context["model"]["PrimaryContainer"]
        role = os.environ.get(
            "SAGEMAKER_ROLE_ARN", context["model"]["ExecutionRoleArn"]
        )
        image = os.environ.get("STT_INFERENCE_IMAGE", container["Image"])
        variant_name = variant["VariantName"]
        instance_type = os.environ.get(
            "STT_INSTANCE_TYPE", variant["InstanceType"]
        )
        initial_count = int(variant.get("InitialInstanceCount", 1))
        old_output = context["config"].get("AsyncInferenceConfig", {}).get(
            "OutputConfig", {}
        )
    else:
        role = os.environ.get("SAGEMAKER_ROLE_ARN")
        if not role:
            raise RuntimeError(
                "신규 엔드포인트에는 SAGEMAKER_ROLE_ARN 환경변수가 필요합니다."
            )
        image = os.environ.get("STT_INFERENCE_IMAGE", DEFAULT_IMAGE)
        variant_name = "AllTraffic"
        instance_type = os.environ.get("STT_INSTANCE_TYPE", DEFAULT_INSTANCE)
        initial_count = 1
        old_output = {}

    output_path = (
        old_output.get("S3OutputPath") or f"s3://{bucket}/whisper/output/"
    )
    failure_path = (
        old_output.get("S3FailurePath") or f"s3://{bucket}/whisper/failure/"
    )

    action = "update" if context else "create"
    plan = {
        "action": action,
        "endpointName": args.endpoint,
        "previousEndpointConfigName": old_config_name,
        "endpointConfigName": config_name,
        "modelName": model_name,
        "modelDataUrl": model_data_url,
        "instanceType": instance_type,
        "outputPath": output_path,
        "failurePath": failure_path,
        "region": region,
    }
    print("배포 계획:")
    print(json.dumps(plan, ensure_ascii=False, indent=2))
    if args.dry_run:
        print("dry-run 완료: AWS 리소스를 변경하지 않았습니다.")
        return

    _create_model_if_needed(
        client,
        name=model_name,
        role=role,
        image=image,
        model_data_url=model_data_url,
        region=region,
    )
    _create_config_if_needed(
        client,
        name=config_name,
        model_name=model_name,
        variant_name=variant_name,
        instance_type=instance_type,
        initial_count=initial_count,
        output_path=output_path,
        failure_path=failure_path,
    )

    if context:
        if old_config_name == config_name:
            action = "unchanged"
            print(f"엔드포인트가 이미 이 버전을 사용 중입니다: {args.endpoint}")
        else:
            client.update_endpoint(
                EndpointName=args.endpoint, EndpointConfigName=config_name
            )
            action = "updated"
            print(f"기존 엔드포인트 업데이트 중: {args.endpoint}")
    else:
        client.create_endpoint(
            EndpointName=args.endpoint, EndpointConfigName=config_name
        )
        action = "created"
        print(f"신규 엔드포인트 생성 중: {args.endpoint}")

    if action != "unchanged":
        client.get_waiter("endpoint_in_service").wait(EndpointName=args.endpoint)
        print(f"InService 확인: {args.endpoint}")

    _ensure_autoscaling(
        region=region,
        endpoint_name=args.endpoint,
        variant_name=variant_name,
    )

    result = {
        "schemaVersion": "1.0",
        "deployedAt": datetime.now(timezone.utc).isoformat(),
        "action": action,
        "endpointName": args.endpoint,
        "previousEndpointConfigName": old_config_name,
        "endpointConfigName": config_name,
        "modelName": model_name,
        "modelDataUrl": model_data_url,
        "releaseId": release_id,
        "region": region,
    }
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"배포 결과: {args.result.resolve()}")
    if old_config_name and old_config_name != config_name:
        print("롤백 명령:")
        print(
            "aws sagemaker update-endpoint "
            f"--endpoint-name {args.endpoint} "
            f"--endpoint-config-name {old_config_name} "
            f"--region {region}"
        )


if __name__ == "__main__":
    main()
