package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.ApplicationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationReportRepository extends JpaRepository<ApplicationReport, Long> {
}
