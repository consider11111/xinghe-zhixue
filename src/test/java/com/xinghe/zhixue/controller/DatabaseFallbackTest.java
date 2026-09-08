package com.xinghe.zhixue.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable", "spring.datasource.username=test", "spring.datasource.password=test"})
class DatabaseFallbackTest {
    @Autowired StorageMode mode;
    @Autowired AuthController auth;
    @Autowired JdbcTemplate db;
    @Test void unavailableDatabaseFallsBackWithoutCreatingRegisteredUsers() {
        assertTrue(mode.isDemo());
        assertEquals(false,auth.register(new AuthController.RegisterRequest("newuser","123456","student","New",null)).get("registered"));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM sys_user",Integer.class));
        for(String name:new String[]{"student","teacher","admin"})assertNotNull(auth.login(new AuthController.LoginRequest(name,"123456")).get("token"));
    }
}
