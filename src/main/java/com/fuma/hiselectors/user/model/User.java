package com.fuma.hiselectors.user.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 로그인 아이디 (하이셀렉터스 HI 아이디). */
    @Column(name = "hi_id", length = 20)
    private String hiId;

    /** BCrypt 암호화된 비밀번호. */
    @Column(name = "hi_password", length = 255)
    private String hiPassword;

    @Column(length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 2)
    private String gender;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    /** 알림톡 수신 동의 여부 (Y/N). */
    @Column(name = "alimtalk", length = 1)
    private String alimtalk;

    @Builder
    private User(String hiId, String hiPassword, String name,
                 LocalDate birthDate, String gender, String email, String phone,
                 String alimtalk) {
        this.hiId = hiId;
        this.hiPassword = hiPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.email = email;
        this.phone = phone;
        this.alimtalk = alimtalk == null ? "N" : alimtalk;
    }

    public void updateAlimtalkAgreement(boolean agreed) {
        this.alimtalk = agreed ? "Y" : "N";
    }
}
