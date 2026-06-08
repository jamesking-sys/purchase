package com.gov.procurement.support;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 集成测试启用条件：本地 PostgreSQL 是否就绪（localhost:5432 可连）。
 * 用于 {@code @EnabledIf}：本机/IDEA 有本地库则运行集成测试，CI/无库环境则优雅跳过——不依赖 Docker。
 */
public final class LocalPg {

    private static final int PORT = 5432;
    private static final int TIMEOUT_MS = 500;

    private LocalPg() {
    }

    public static boolean available() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", PORT), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
