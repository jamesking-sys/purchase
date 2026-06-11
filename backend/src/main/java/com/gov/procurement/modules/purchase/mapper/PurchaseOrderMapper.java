package com.gov.procurement.modules.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.purchase.domain.PurchaseOrder;

/**
 * purchase_order Mapper（基础 CRUD + 分页，分页/条件查询走 LambdaQueryWrapper）。
 */
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
