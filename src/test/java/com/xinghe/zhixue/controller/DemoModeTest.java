package com.xinghe.zhixue.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DemoModeTest {
    @Autowired MockMvc http;
    @Autowired AuthController auth;
    @Autowired JdbcTemplate db;

    @Test void startsWithOnlyThreeWorkingAccounts() throws Exception {
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        http.perform(get("/api/auth/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.registrationEnabled").value(true));
        for (String role : new String[]{"student", "teacher", "admin"}) {
            http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + role + "\",\"password\":\"123456\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(role))
                    .andExpect(jsonPath("$.user.password_hash").doesNotExist());
            String token = auth.login(new AuthController.LoginRequest(role, "123456")).get("token").toString();
            http.perform(get("/api/auth/me").header("X-Auth-Token", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
            auth.logout(token);
            http.perform(get("/api/auth/me").header("X-Auth-Token", token)).andExpect(status().isUnauthorized());
        }
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        http.perform(get("/api/education/overview")).andExpect(status().isOk());
        http.perform(get("/api/auth/classes")).andExpect(status().isOk());
    }

    @Test void adminPagesWorkButAdditionalAccountsAreBlocked() throws Exception {
        String token = auth.login(new AuthController.LoginRequest("admin", "123456")).get("token").toString();
        for (String path : new String[]{"users", "base-data", "operations", "audit"}) {
            http.perform(get("/api/admin/" + path).header("X-Auth-Token", token)).andExpect(status().isOk());
        }
        http.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newstudent\",\"password\":\"123456\",\"role\":\"student\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false)).andExpect(jsonPath("$.message").exists());
        http.perform(post("/api/admin/users").header("X-Auth-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newteacher\",\"displayName\":\"Teacher\",\"role\":\"teacher\",\"enabled\":true,\"password\":\"123456\"}"))
                .andExpect(status().isForbidden());
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }
}
