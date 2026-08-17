package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentMediaRepository extends JpaRepository<ContentMedia, Long> {

    List<ContentMedia> findAllByContentVersionIdOrderById(Long contentVersionId);
}
