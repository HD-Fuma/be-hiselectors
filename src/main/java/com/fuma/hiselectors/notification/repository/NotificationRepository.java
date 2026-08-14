package com.fuma.hiselectors.notification.repository;

import com.fuma.hiselectors.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
