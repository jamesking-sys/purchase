package com.gov.procurement.modules.requisition.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 仓管待办视图（详设 U11 §6.2）：待审领用单 + 明细的当前库存与是否充足。
 *
 * @param id               领用单 id
 * @param projectGroupName 项目组名
 * @param applicantName    申请人姓名
 * @param createdAt        发起时间
 * @param items            明细（含库存核验）
 */
public record RequisitionTodoVO(
        Long id,
        String projectGroupName,
        String applicantName,
        OffsetDateTime createdAt,
        List<TodoLineVO> items) {

    /**
     * 待办明细行。
     *
     * @param stockItemId     库存项 id
     * @param materialName    物料名
     * @param qty             申请数量
     * @param currentQuantity 当前库存
     * @param enough          库存是否充足（currentQuantity >= qty）
     */
    public record TodoLineVO(
            Long stockItemId,
            String materialName,
            BigDecimal qty,
            BigDecimal currentQuantity,
            boolean enough) {
    }
}
