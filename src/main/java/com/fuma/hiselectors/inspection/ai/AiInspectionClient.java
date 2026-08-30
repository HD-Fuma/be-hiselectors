package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;

public interface AiInspectionClient {

    AiInspectionResponse inspect(InspectionContext context, InspectionPolicy policy);

    AiInspectionResponse inspectText(String text);

    ContentReportAnalysis generateReport(InspectionContext context);

    ContentReportAnalysis generateReportFromText(String text);
}
