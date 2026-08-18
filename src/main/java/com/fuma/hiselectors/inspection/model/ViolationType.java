package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "violation_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViolationType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "violation_type_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private ViolationTypeCode code;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    public static ViolationType create(ViolationTypeCode code, String description) {
        ViolationType type = new ViolationType();
        type.code = code;
        type.description = description;
        return type;
    }
}
