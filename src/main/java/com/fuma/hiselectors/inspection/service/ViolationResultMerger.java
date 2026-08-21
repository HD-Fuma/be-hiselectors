package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ViolationResultMerger {

    public List<DetectedViolation> mergeRuleFirst(
            List<DetectedViolation> ruleViolations,
            List<DetectedViolation> aiViolations) {
        Map<ViolationTypeCode, DetectedViolation> merged = new LinkedHashMap<>();
        ruleViolations.forEach(violation -> merged.putIfAbsent(violation.type(), violation));
        aiViolations.forEach(violation -> merged.putIfAbsent(violation.type(), violation));
        return new ArrayList<>(merged.values());
    }
}
