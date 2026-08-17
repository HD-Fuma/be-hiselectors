package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {
}
