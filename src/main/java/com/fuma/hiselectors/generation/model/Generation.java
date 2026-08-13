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

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", length = 20)
    private GenerationStatus generationStatus;

    @Builder
    private Generation(String generationName, LocalDateTime startDate,
                       LocalDateTime endDate, GenerationStatus generationStatus) {
        this.generationName = generationName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.generationStatus = generationStatus;
    }
}
