package com.xinghe.zhixue.controller;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StorageMode {
    private final boolean demo;

    public StorageMode(HikariDataSource source) {
        demo = source.getJdbcUrl().startsWith("jdbc:h2:mem:");
    }

    public boolean isDemo() { return demo; }

    public void requireAccountCreation() {
        if (demo) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "体验模式仅提供三个初始账号，连接数据库后可注册或新增账号");
    }
}
