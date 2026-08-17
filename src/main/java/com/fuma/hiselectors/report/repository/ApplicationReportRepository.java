package com.fuma.hiselectors.report.repository;

import com.fuma.hiselectors.report.model.ApplicationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationReportRepository extends JpaRepository<ApplicationReport, Long> {
}
