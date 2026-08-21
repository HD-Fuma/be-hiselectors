package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import java.util.List;

public interface ViolationDetector {

    List<DetectedViolation> detect(InspectionContext context);
}
