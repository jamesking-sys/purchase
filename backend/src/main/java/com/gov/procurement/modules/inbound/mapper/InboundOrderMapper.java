package com.gov.procurement.modules.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.inbound.domain.InboundOrder;

/**
 * inbound_order Mapper（基础 CRUD + 分页，按采购单查入库历史走 LambdaQueryWrapper）。
 */
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {
}
