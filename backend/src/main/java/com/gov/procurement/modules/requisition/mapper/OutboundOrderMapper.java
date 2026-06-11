package com.gov.procurement.modules.requisition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.requisition.domain.OutboundOrder;

/**
 * outbound_order Mapper（基础 CRUD；按领用单查出库单走 LambdaQueryWrapper）。
 */
public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {
}
