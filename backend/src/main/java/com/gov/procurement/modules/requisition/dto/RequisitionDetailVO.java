package com.gov.procurement.modules.requisition.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 领用单详情（详设 U11 §6.5）：领用单 + 明细 + 若已出库含出库单/明细。
 *
 * @param id             领用单 id
 * @param status         状态
 * @param projectGroupId 项目组 id
 * @param applicantId    申请人 id
 * @param applicantName  申请人姓名
 * @param purpose        用途
 * @param rejectOpinion  驳回意见（驳回时）
 * @param createdAt      发起时间
 * @param items          领用明细
 * @param outbound       出库单（已出库时，否则 null）
 */
public record RequisitionDetailVO(
        Long id,
        String status,
        Long projectGroupId,
        Long applicantId,
        String applicantName,
        String purpose,
        String rejectOpinion,
        OffsetDateTime createdAt,
        List<DetailItemVO> items,
        OutboundVO outbound) {

    /**
     * 领用明细行。
     *
     * @param stockItemId  库存项 id
     * @param materialName 物料名
     * @param qty          申请数量
     */
    public record DetailItemVO(Long stockItemId, String materialName, BigDecimal qty) {
    }

    /**
     * 出库单视图。
     *
     * @param outboundOrderId 出库单 id
     * @param approverId      审批人 id
     * @param outboundAt      出库时间
     * @param items           出库明细
     */
    public record OutboundVO(
            Long outboundOrderId,
            Long approverId,
            OffsetDateTime outboundAt,
            List<OutboundLineVO> items) {

        /**
         * 出库明细行。
         *
         * @param stockItemId  库存项 id
         * @param materialName 物料名
         * @param qty          出库数量
         */
        public record OutboundLineVO(Long stockItemId, String materialName, BigDecimal qty) {
        }
    }
}
