package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.stt.SttResult;

public record IntegratedInspectionResult(
        SttResult extraction,
        AiInspectionResult inspection
) {
}
