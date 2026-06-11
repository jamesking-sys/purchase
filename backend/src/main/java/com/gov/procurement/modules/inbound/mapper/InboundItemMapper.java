package com.gov.procurement.modules.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.inbound.domain.InboundItem;
import com.gov.procurement.modules.inbound.dto.InboundItemRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * inbound_item Mapper：基础 CRUD + 按入库单查明细（关联采购明细物料名）。
 */
public interface InboundItemMapper extends BaseMapper<InboundItem> {

    /**
     * 查询某入库单的全部入库明细及物料名，按 id 升序。
     *
     * @param inboundOrderId 入库单 id
     * @return 入库明细行
     */
    @Select("SELECT ii.inbound_order_id, ii.purchase_item_id, pi.material_name, ii.received_qty, ii.stock_item_id "
            + "FROM inbound_item ii "
            + "JOIN purchase_item pi ON ii.purchase_item_id = pi.id "
            + "WHERE ii.inbound_order_id = #{inboundOrderId} "
            + "ORDER BY ii.id ASC")
    List<InboundItemRow> selectRowsByOrder(@Param("inboundOrderId") Long inboundOrderId);
}
