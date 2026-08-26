package com.fuma.hiselectors.creator.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.task.CreatorSyncTask;
import com.fuma.hiselectors.creator.task.InstagramCreatorSyncTask;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 발굴 실행 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@Tag(name = "크리에이터 발굴 실행", description = "등록된 키워드로 YouTube 를 검색해 크리에이터를 발굴 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/discovery")
@RequiredArgsConstructor
public class DiscoveryAdminController {

    private final DiscoveryPipelineService discoveryPipelineService;
    private final InstagramDiscoveryService instagramDiscoveryService;
    private final TaskRunExecutionService taskRunExecutionService;
    private final CreatorSyncTask creatorSyncTask;
    private final InstagramCreatorSyncTask instagramCreatorSyncTask;
    private final AdminRepository adminRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "YouTube·Instagram 크리에이터 일괄 발굴",
            description = "관리자가 크리에이터 모집을 시작할 때 활성 키워드를 "
                    + "우선순위·마지막 실행 시각 순으로 일괄 실행한다. "
                    + "YouTube 발굴 후 추출된 Instagram 계정을 자동으로 이어서 발굴하며, "
                    + "개별 계정이 실패해도 나머지 계정은 계속 실행한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "일괄 발굴 작업 접수"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "409", description = "같은 작업이 이미 실행 중이거나 멱등 키 충돌")
    })
    @PostMapping("/youtube/run")
    public ResponseEntity<TaskRunResponse> runYoutubeBatch(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestParam(defaultValue = "false") boolean test,
            Principal principal) {
        return test
                ? submitTask(idempotencyKey, principal, "youtube-test",
                        creatorSyncTask::executeTest)
                : submitTask(idempotencyKey, principal, "youtube", creatorSyncTask);
    }

    @Operation(summary = "선택 카테고리 크리에이터 발굴",
            description = "선택 카테고리의 활성 키워드와 해당 출처의 Instagram 후보만 발굴한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "카테고리 발굴 작업 접수"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음"),
            @ApiResponse(responseCode = "409", description = "같은 작업이 이미 실행 중")
    })
    @PostMapping("/categories/{categoryId}/run")
    public ResponseEntity<TaskRunResponse> runCategoryBatch(
            @PathVariable Long categoryId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Principal principal) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return submitTask(idempotencyKey, principal, "youtube-category", categoryId,
                context -> creatorSyncTask.executeCategory(context, categoryId));
    }

    @Operation(summary = "Instagram 크리에이터 일괄 발굴",
            description = "관리자가 크리에이터 모집을 시작할 때 YouTube 채널에서 추출한 "
                    + "Instagram 사용자명을 Meta Graph API로 조회한다. "
                    + "공개 프로필에 이메일이 있는 계정만 저장·갱신하며, "
                    + "일부 계정이 실패해도 나머지 계정은 계속 실행한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "일괄 발굴 작업 접수"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "409", description = "같은 작업이 이미 실행 중이거나 멱등 키 충돌")
    })
    @PostMapping("/instagram/run")
    public ResponseEntity<TaskRunResponse> runInstagramBatch(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, Principal principal) {
        return submitTask(idempotencyKey, principal, "instagram", instagramCreatorSyncTask);
    }

    private ResponseEntity<TaskRunResponse> submitTask(
            UUID idempotencyKey, Principal principal, String source, TrackedTask task) {
        return submitTask(idempotencyKey, principal, source, null, task);
    }

    private ResponseEntity<TaskRunResponse> submitTask(
            UUID idempotencyKey, Principal principal, String source,
            Long categoryId, TrackedTask task) {
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        var payload = objectMapper.createObjectNode().put("source", source);
        if (categoryId != null) {
            payload.put("categoryId", categoryId);
        }
        TaskStartResult result = taskRunExecutionService.submit(new TaskStartCommand(
                TaskType.CREATOR_SYNC, TriggerType.ADMIN_TRIGGERED, admin.getId(), idempotencyKey,
                payload), task);
        if (result instanceof TaskStartResult.ActiveConflict) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        TaskRun run = result instanceof TaskStartResult.Created created
                ? created.run() : ((TaskStartResult.Replayed) result).run();
        return ResponseEntity.accepted().body(TaskRunResponse.from(
                run, Collections.singletonMap(admin.getId(), admin.getName())));
    }

    @Operation(summary = "키워드로 발굴 실행",
            description = "등록된 키워드 하나로 YouTube 를 검색해 채널을 발굴하고 저장한다. "
                    + "약 102 units 를 소모하며 일일 한도는 10,000 units 다. "
                    + "현재 공개 이메일 없는 채널은 제외하고, 브랜드 계정이나 구독자 미달 "
                    + "계정의 제외는 조회 API 조건으로 한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발굴 성공"),
            @ApiResponse(responseCode = "404", description = "키워드 없음", content = @Content),
            @ApiResponse(responseCode = "502", description = "YouTube API 호출 실패 (쿼터 초과 등)",
                    content = @Content)
    })
    @PostMapping("/keywords/{keywordId}/run")
    public ResponseEntity<DiscoveryRunResult> runByKeyword(
            @PathVariable Long keywordId,

            @Parameter(description = "검색할 영상 수. 많을수록 채널을 더 찾지만 쿼터는 동일하다",
                    example = "25")
            @RequestParam(required = false, defaultValue = "25") Integer maxResults) {

        return ResponseEntity.ok(
                discoveryPipelineService.runByKeyword(keywordId, maxResults));
    }

    @Operation(summary = "YouTube 크리에이터의 Instagram 계정 발굴",
            description = "YouTube 채널 설명에서 추출해 둔 Instagram 사용자명을 Meta Graph API로 "
                    + "조회하고, 공개 지표를 수집해 별도의 INSTAGRAM 크리에이터로 저장한다. "
                    + "공개 프로필에 이메일이 있는 계정만 저장·갱신한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instagram 발굴 및 저장 성공"),
            @ApiResponse(responseCode = "404",
                    description = "YouTube 크리에이터, Instagram 사용자명 또는 조회 가능 계정 없음",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "공개 이메일 없음",
                    content = @Content),
            @ApiResponse(responseCode = "500",
                    description = "Meta Graph API 설정 누락",
                    content = @Content),
            @ApiResponse(responseCode = "502",
                    description = "Meta Graph API 호출 실패",
                    content = @Content)
    })
    @PostMapping("/creators/{youtubeCreatorId}/instagram")
    public ResponseEntity<InstagramDiscoveryResult> discoverInstagram(
            @Parameter(description = "Instagram 사용자명이 추출된 YouTube creator_pool ID",
                    example = "101")
            @PathVariable Long youtubeCreatorId) {

        return ResponseEntity.ok(
                instagramDiscoveryService.discoverFromYoutubeCreator(youtubeCreatorId));
    }
}
