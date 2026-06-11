package com.gov.procurement.modules.purchase.dto;

import java.time.OffsetDateTime;

/**
 * 到货单视图。fileName 取相对路径 basename（= 原文件名）；downloadUrl 为按 id 的下载地址。
 *
 * @param id             到货单 id
 * @param fileName       文件名（原名）
 * @param uploadedBy     上传人用户 id
 * @param uploadedByName 上传人姓名
 * @param uploadedAt     上传时间
 * @param downloadUrl    下载地址
 */
public record DeliveryNoteVO(
        Long id,
        String fileName,
        Long uploadedBy,
        String uploadedByName,
        OffsetDateTime uploadedAt,
        String downloadUrl) {
}
