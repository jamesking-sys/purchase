package com.gov.procurement.modules.purchase.service;

import com.gov.procurement.modules.purchase.dto.DeliveryNoteVO;
import com.gov.procurement.modules.purchase.dto.DownloadFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 到货单服务（U8）：上传（多文件）、按采购单查询、按 id 下载。
 */
public interface DeliveryNoteService {

    /**
     * 为采购单上传一张或多张到货单。采购单须存在且处于 executing；整体事务，任一失败回滚。
     *
     * @param orderId 采购单 id
     * @param files   上传文件（≥1）
     * @return 该采购单当前全部到货单
     */
    List<DeliveryNoteVO> upload(Long orderId, List<MultipartFile> files);

    /**
     * 查询采购单的全部到货单（含上传人、上传时间、下载地址）。
     *
     * @param orderId 采购单 id
     * @return 到货单列表
     */
    List<DeliveryNoteVO> listByOrder(Long orderId);

    /**
     * 按到货单 id 取下载资源与文件名。
     *
     * @param noteId 到货单 id
     * @return 下载载体
     */
    DownloadFile download(Long noteId);
}
