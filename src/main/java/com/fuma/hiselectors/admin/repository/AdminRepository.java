package com.fuma.hiselectors.admin.repository;

import com.fuma.hiselectors.admin.model.Admin;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByLoginId(String loginId);

    @Query("select admin.id as id, admin.name as name from Admin admin where admin.id in :ids")
    List<AdminNameProjection> findNamesByIdIn(@Param("ids") Collection<Long> ids);

    interface AdminNameProjection {

        Long getId();

        String getName();
    }
}
