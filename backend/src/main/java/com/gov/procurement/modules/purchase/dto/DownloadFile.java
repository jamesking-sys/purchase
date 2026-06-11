package com.gov.procurement.modules.purchase.dto;

import org.springframework.core.io.Resource;

/**
 * 下载文件载体：资源 + 展示文件名，供控制器组装 Content-Disposition。
 *
 * @param resource 文件资源
 * @param fileName 下载展示文件名
 */
public record DownloadFile(Resource resource, String fileName) {
}
