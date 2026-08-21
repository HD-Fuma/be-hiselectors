package com.fuma.hiselectors.generation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Generation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_id")
    private Long id;

    @Column(name = "generation_name", length = 30)
    private String generationName;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "activity_start_date", nullable = false)
    private LocalDateTime activityStartDate;

    @Column(name = "activity_end_date", nullable = false)
    private LocalDateTime activityEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private GenerationStatus status;

    @Builder
    private Generation(String generationName, LocalDateTime startDate,
                       LocalDateTime endDate, LocalDateTime activityStartDate,
                       LocalDateTime activityEndDate, GenerationStatus status) {
        this.generationName = generationName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.activityStartDate = activityStartDate;
        this.activityEndDate = activityEndDate;
        this.status = status;
    }

    public void update(String generationName, LocalDateTime startDate, LocalDateTime endDate,
                       LocalDateTime activityStartDate, LocalDateTime activityEndDate) {
        if (generationName != null) {
            this.generationName = generationName;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
        if (activityStartDate != null) {
            this.activityStartDate = activityStartDate;
        }
        if (activityEndDate != null) {
            this.activityEndDate = activityEndDate;
        }
    }

    public void changeStatus(GenerationStatus status) {
        this.status = status;
    }
}
