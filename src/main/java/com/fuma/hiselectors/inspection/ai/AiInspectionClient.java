package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;

public interface AiInspectionClient {

    AiInspectionResult inspect(InspectionContext context, InspectionPolicy policy);

    AiInspectionResult inspectText(String text);
}
