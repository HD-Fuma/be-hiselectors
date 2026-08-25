package com.fuma.hiselectors.taskrun.dto;

import java.time.Instant;
import java.util.List;

public record TaskRunPanelResponse(List<TaskRunResponse> items, Instant serverTime) {
}
