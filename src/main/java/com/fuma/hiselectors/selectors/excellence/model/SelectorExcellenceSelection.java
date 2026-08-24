package com.fuma.hiselectors.selectors.excellence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "selector_excellence_selection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_selector_excellence_generation_selector_type",
                columnNames = {"generation_id", "selectors_id", "selection_type"}),
        indexes = {
                @Index(
                        name = "idx_selector_excellence_selector_generation",
                        columnList = "selectors_id, generation_id"),
                @Index(
                        name = "idx_selector_excellence_generation_type_rank",
                        columnList = "generation_id, selection_type, rank_no")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelectorExcellenceSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "selection_id")
    private Long selectionId;

    @Column(name = "generation_id", nullable = false)
    private Long generationId;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 30)
    private SelectorExcellenceSelectionType selectionType;

    @Column(name = "generation_sales", nullable = false, precision = 19, scale = 2)
    private BigDecimal generationSales;

    @Column(name = "confirmed_order_count", nullable = false)
    private long confirmedOrderCount;

    @Column(name = "rank_no")
    private Integer rankNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 30)
    private SelectorExcellenceRewardType rewardType;

    @Column(name = "reward_value", nullable = false)
    private long rewardValue;

    @Column(name = "reward_quantity", nullable = false)
    private int rewardQuantity;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;

    public static SelectorExcellenceSelection create(
            Long generationId,
            Long selectorsId,
            SelectorExcellenceSelectionType selectionType,
            BigDecimal generationSales,
            long confirmedOrderCount,
            Integer rankNo,
            SelectorExcellenceRewardType rewardType,
            long rewardValue,
            int rewardQuantity,
            LocalDateTime selectedAt) {
        SelectorExcellenceSelection selection = new SelectorExcellenceSelection();
        selection.generationId = generationId;
        selection.selectorsId = selectorsId;
        selection.selectionType = selectionType;
        selection.generationSales = generationSales;
        selection.confirmedOrderCount = confirmedOrderCount;
        selection.rankNo = rankNo;
        selection.rewardType = rewardType;
        selection.rewardValue = rewardValue;
        selection.rewardQuantity = rewardQuantity;
        selection.selectedAt = selectedAt;
        return selection;
    }
}
