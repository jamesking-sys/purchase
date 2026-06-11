package com.gov.procurement.common.storage;

import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件系统存储实现。文件落 {@code app.upload.dir} 下的业务子目录，以 UUID 子目录隔离避免重名覆盖；
 * 读取做目录穿越防护。存储介质后续可替换为对象存储而不影响业务层（详设 U8 TBD-1）。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final String uploadDir;

    public LocalFileStorage(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String store(MultipartFile file, String subDir) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/]", "_").replace("..", "_");
        // UUID 子目录隔离 → basename 即原文件名，便于下载时还原文件名。
        String relativePath = subDir + "/" + UUID.randomUUID() + "/" + safeName;
        try {
            Path target = Paths.get(uploadDir).resolve(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("文件存储失败 path={}", relativePath, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件存储失败");
        }
        return relativePath;
    }

    @Override
    public Resource load(String relativePath) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            // 目录穿越防护：拒绝越出存储根的路径
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        Resource resource = new FileSystemResource(target);
        if (!resource.exists() || !resource.isReadable()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return resource;
    }
}
