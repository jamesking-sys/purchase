package com.gov.procurement.modules.budget.dto;

/**
 * 附件留档响应。
 *
 * @param path     库内留档相对路径（写入 budget.source_doc_path）
 * @param fileName 原文件名
 * @param size     字节数
 */
public record AttachmentVO(String path, String fileName, long size) {
}
