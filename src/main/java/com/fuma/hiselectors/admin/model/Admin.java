package com.fuma.hiselectors.admin.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long id;

    @Column(name = "login_id", length = 30)
    private String loginId;

    @Column(length = 255)
    private String password;

    @Column(length = 50)
    private String name;

    @Column(length = 20)
    private String role;


    @Builder
    private Admin(String loginId, String password, String name, String role) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.role = role;
    }
}
