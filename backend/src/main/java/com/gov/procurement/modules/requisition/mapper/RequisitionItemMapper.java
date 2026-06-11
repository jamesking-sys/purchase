package com.gov.procurement.modules.requisition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.requisition.domain.RequisitionItem;
import com.gov.procurement.modules.requisition.dto.RequisitionItemRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * requisition_item Mapper（批量写明细 + 关联库存项查明细及当前库存）。
 */
public interface RequisitionItemMapper extends BaseMapper<RequisitionItem> {

    /**
     * 单条多行 INSERT 批量写入领用明细。
     *
     * @param items 明细列表（requisitionId/stockItemId/qty）
     * @return 写入行数
     */
    @Insert("<script>INSERT INTO requisition_item(requisition_id, stock_item_id, qty) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.requisitionId}, #{it.stockItemId}, #{it.qty})"
            + "</foreach></script>")
    int insertBatch(@Param("items") List<RequisitionItem> items);

    /**
     * 查领用单明细及对应库存项的物料名与当前库存量（供待办核库存、详情展示）。
     *
     * @param requisitionId 领用单 id
     * @return 明细行（含 currentQuantity）
     */
    @Select("SELECT ri.stock_item_id, si.material_name, ri.qty, si.quantity AS current_quantity "
            + "FROM requisition_item ri "
            + "JOIN stock_item si ON ri.stock_item_id = si.id "
            + "WHERE ri.requisition_id = #{requisitionId} "
            + "ORDER BY ri.id ASC")
    List<RequisitionItemRow> selectItemsWithStock(@Param("requisitionId") Long requisitionId);
}
