package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.ai.AiInspectionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiViolationDetector {

    private final AiInspectionClient client;

    public AiInspectionResult inspect(InspectionContext context) {
        return client.inspect(context);
    }

    public AiInspectionResult inspectText(String text) {
        return client.inspectText(text);
    }
}
