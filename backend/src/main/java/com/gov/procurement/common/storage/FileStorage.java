package com.gov.procurement.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：隔离存储介质（本地 FS / 对象存储）。库内仅存相对路径，由实现负责落盘/读取。
 * 详设 U8 §5.3（TBD-1）以此抽象隔离存储实现，业务层不感知物理介质。
 */
public interface FileStorage {

    /**
     * 存储文件，返回库内相对路径（用于回库与后续下载）。
     *
     * @param file   上传文件
     * @param subDir 业务子目录（如 {@code purchase/123/delivery}）
     * @return 相对路径（如 {@code purchase/123/delivery/<uuid>/原名.pdf}）
     */
    String store(MultipartFile file, String subDir);

    /**
     * 按相对路径加载为可读资源；不存在或越界返回 NOT_FOUND。
     *
     * @param relativePath 相对路径
     * @return 资源
     */
    Resource load(String relativePath);
}
