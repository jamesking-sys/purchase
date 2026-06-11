package com.gov.procurement.modules.stocktake.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.stocktake.domain.StocktakeItem;

/**
 * stocktake_item Mapper。发起时逐条 insert（回填生成 id 供响应），录入实盘时按 id 更新差异。
 */
public interface StocktakeItemMapper extends BaseMapper<StocktakeItem> {
}
