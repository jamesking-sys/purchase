package com.gov.procurement.modules.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.purchase.domain.PurchaseItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * purchase_item Mapper（批量写明细 + 入库累加已收量）。received_qty 走 DB 默认 0，不在 INSERT 列内。
 */
public interface PurchaseItemMapper extends BaseMapper<PurchaseItem> {

    /**
     * 单条多行 INSERT 批量写入采购明细。
     *
     * @param items 明细列表（purchaseOrderId/subjectId/materialName/qty/amount）
     * @return 写入行数
     */
    @Insert("<script>INSERT INTO purchase_item(purchase_order_id, subject_id, material_name, qty, amount) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.purchaseOrderId}, #{it.subjectId}, #{it.materialName}, #{it.qty}, #{it.amount})"
            + "</foreach></script>")
    int insertBatch(@Param("items") List<PurchaseItem> items);

    /**
     * 累加采购明细的累计已收量（U9 入库）。CHECK(received_qty<=qty) 为最终兜底；
     * 调用方须先以 FOR UPDATE 锁行 + 不超收校验后再调用。
     *
     * @param id  采购明细 id
     * @param qty 本次实收增量（> 0）
     * @return 影响行数
     */
    @Update("UPDATE purchase_item SET received_qty = received_qty + #{qty} WHERE id = #{id}")
    int addReceivedQty(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
