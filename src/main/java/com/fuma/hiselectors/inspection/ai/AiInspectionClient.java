package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.InspectionContext;

public interface AiInspectionClient {

    AiInspectionResult inspect(InspectionContext context);

    AiInspectionResult inspectText(String text);
}
