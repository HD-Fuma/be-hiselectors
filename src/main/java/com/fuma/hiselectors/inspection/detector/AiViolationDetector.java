package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.ai.AiInspectionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiViolationDetector {

    private final AiInspectionClient client;

    public AiInspectionResponse inspect(InspectionContext context, InspectionPolicy policy) {
        return client.inspect(context, policy);
    }

    public AiInspectionResponse inspectText(String text) {
        return client.inspectText(text);
    }
}
