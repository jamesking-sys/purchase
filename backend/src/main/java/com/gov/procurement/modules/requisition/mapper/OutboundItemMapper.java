package com.gov.procurement.modules.requisition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.requisition.domain.OutboundItem;
import com.gov.procurement.modules.requisition.dto.OutboundItemRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * outbound_item Mapper（基础 CRUD + 关联库存项查出库明细物料名）。
 */
public interface OutboundItemMapper extends BaseMapper<OutboundItem> {

    /**
     * 查出库单明细及物料名。
     *
     * @param outboundOrderId 出库单 id
     * @return 出库明细行
     */
    @Select("SELECT oi.stock_item_id, si.material_name, oi.qty "
            + "FROM outbound_item oi "
            + "JOIN stock_item si ON oi.stock_item_id = si.id "
            + "WHERE oi.outbound_order_id = #{outboundOrderId} "
            + "ORDER BY oi.id ASC")
    List<OutboundItemRow> selectRowsByOrder(@Param("outboundOrderId") Long outboundOrderId);
}
