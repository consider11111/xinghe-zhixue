package com.xinghe.zhixue.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {AdminController.class, AuthController.class})
public class AdminErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()==null?"请求失败":e.getReason()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> database(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("message", "数据库暂不可用，请检查数据库服务及管理表初始化状态"));
    }
}
