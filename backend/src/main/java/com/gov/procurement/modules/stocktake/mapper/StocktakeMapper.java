package com.gov.procurement.modules.stocktake.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.stocktake.domain.Stocktake;

/**
 * stocktake Mapper。确认时以 LambdaQueryWrapper + {@code FOR UPDATE} 锁盘点单行，串行化并发重复确认。
 */
public interface StocktakeMapper extends BaseMapper<Stocktake> {
}
