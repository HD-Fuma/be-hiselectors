package com.fuma.hiselectors.user.repository;

import com.fuma.hiselectors.user.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHiId(String hiId);
}
