package com.gov.procurement.modules.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.purchase.domain.DeliveryNote;
import com.gov.procurement.modules.purchase.dto.DeliveryNoteRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * delivery_note Mapper：基础 CRUD + 按采购单查到货单（关联上传人姓名，按 id 升序）。
 */
public interface DeliveryNoteMapper extends BaseMapper<DeliveryNote> {

    /**
     * 查询采购单的全部到货单及上传人姓名，按 id 升序。
     *
     * @param orderId 采购单 id
     * @return 到货单行（含 uploadedByName）；无则空列表
     */
    @Select("SELECT dn.id, dn.purchase_order_id, dn.file_path, dn.uploaded_by, u.name AS uploaded_by_name, "
            + "dn.created_at "
            + "FROM delivery_note dn "
            + "JOIN sys_user u ON dn.uploaded_by = u.id "
            + "WHERE dn.purchase_order_id = #{orderId} "
            + "ORDER BY dn.id ASC")
    List<DeliveryNoteRow> selectRowsByOrder(@Param("orderId") Long orderId);
}
