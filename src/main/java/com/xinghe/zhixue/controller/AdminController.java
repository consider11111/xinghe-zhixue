package com.xinghe.zhixue.controller;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate db;
    private final AuthController auth;
    private final StorageMode storage;
    private static final Set<String> ROLES = Set.of("student", "teacher", "admin");
    private static final Set<String> KINDS = Set.of("class", "subject", "term");

    public AdminController(JdbcTemplate db, AuthController auth, StorageMode storage) { this.db = db; this.auth = auth; this.storage = storage; }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestHeader(value="X-Auth-Token", required=false) String token) {
        auth.requireAdmin(token);
        return db.queryForList("SELECT id,username,display_name,role,enabled,created_at FROM sys_user WHERE enabled>=0 ORDER BY id DESC");
    }

    @PostMapping("/users")
    @Transactional
    public Map<String, Object> createUser(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                         @RequestBody UserInput input) {
        long actor = auth.requireAdmin(token);
        validateUser(input);
        storage.requireAccountCreation();
        String username = required(input.username(), 50, "账号");
        password(input.password());
        try {
            db.update("INSERT INTO sys_user(username,password_hash,role,display_name,enabled) VALUES(?,?,?,?,?)",
                    username, Passwords.encode(input.password()), input.role(), input.displayName().trim(), input.enabled());
        } catch (DuplicateKeyException e) { throw conflict("账号已存在"); }
        long id = db.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        ensureProfile(id, input.role());
        log(actor, "创建账号", username);
        return Map.of("id", id);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public Map<String, String> updateUser(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                          @PathVariable long id, @RequestBody UserInput input) {
        long actor = auth.requireAdmin(token);
        validateUser(input);
        Map<String, Object> old = user(id);
        if (id == actor && (!input.enabled() || !"admin".equals(input.role())))
            throw conflict("不能停用自己或移除自己的管理员身份");
        // Lock active administrators so concurrent edits cannot remove the last one.
        List<Map<String, Object>> admins = db.queryForList("SELECT id FROM sys_user WHERE role='admin' AND enabled=1 FOR UPDATE");
        if (admins.size() == 1 && ((Number)admins.get(0).get("id")).longValue() == id
                && (!input.enabled() || !"admin".equals(input.role()))) throw conflict("至少保留一名启用的管理员");
        db.update("UPDATE sys_user SET display_name=?,role=?,enabled=? WHERE id=?",
                input.displayName().trim(), input.role(), input.enabled(), id);
        ensureProfile(id, input.role());
        log(actor, "更新账号权限", old.get("username").toString());
        return Map.of("message", "账号已更新");
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String,String> deleteUser(@RequestHeader(value="X-Auth-Token",required=false) String token,
                                         @PathVariable long id) {
        long actor = auth.requireAdmin(token);
        if (actor == id) throw conflict("不能删除当前登录的管理员账号");
        List<Map<String,Object>> admins = db.queryForList("SELECT id FROM sys_user WHERE role='admin' AND enabled=1 FOR UPDATE");
        Map<String,Object> target = user(id);
        if (admins.size()==1 && ((Number)admins.get(0).get("id")).longValue()==id)
            throw conflict("至少保留一名启用的管理员");
        // Retain the identity for audit history and prevent startup seeds from restoring deleted accounts.
        db.update("UPDATE sys_user SET enabled=-1 WHERE id=?", id);
        db.update("DELETE FROM user_class WHERE user_id=?", id);
        db.update("DELETE FROM training_progress WHERE user_id=?", id);
        log(actor, "删除账号", target.get("username").toString());
        auth.invalidate(id);
        return Map.of("message", "账号已删除，原登录状态已失效");
    }

    @PutMapping("/users/{id}/password")
    @Transactional
    public Map<String, String> resetPassword(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                             @PathVariable long id, @RequestBody PasswordInput input) {
        long actor = auth.requireAdmin(token);
        Map<String, Object> target = user(id);
        password(input.password());
        db.update("UPDATE sys_user SET password_hash=? WHERE id=?", Passwords.encode(input.password()), id);
        log(actor, "重置密码", target.get("username").toString());
        auth.invalidate(id);
        return Map.of("message", "密码已重置，该账号需重新登录");
    }

    @GetMapping("/base-data")
    public List<Map<String, Object>> baseData(@RequestHeader(value="X-Auth-Token", required=false) String token) {
        auth.requireAdmin(token);
        return db.queryForList("SELECT id,kind,code,name,details,enabled,updated_at FROM admin_base_data ORDER BY kind,code");
    }

    @GetMapping("/users/{id}/classes")
    public List<Map<String,Object>> userClasses(@RequestHeader(value="X-Auth-Token", required=false) String token, @PathVariable long id) {
        auth.requireAdmin(token); user(id);
        return db.queryForList("SELECT c.id,c.code,c.name,c.grade FROM user_class uc JOIN school_class c ON c.id=uc.class_id WHERE uc.user_id=? ORDER BY c.grade,c.name", id);
    }

    @PutMapping("/users/{id}/classes") @Transactional
    public Map<String,String> assignClasses(@RequestHeader(value="X-Auth-Token", required=false) String token, @PathVariable long id, @RequestBody ClassAssignment input) {
        long actor=auth.requireAdmin(token); Map<String,Object> target=user(id); String role=String.valueOf(target.get("role"));
        List<Long> ids=input==null||input.classIds()==null?List.of():input.classIds();
        int max="student".equals(role)?1:("teacher".equals(role)?2:0); if(ids.size()>max) throw bad("学生只能管理1个班级，教师最多管理2个班级");
        db.update("DELETE FROM user_class WHERE user_id=?",id);
        for(Long classId:ids) db.update("INSERT INTO user_class(user_id,class_id) SELECT ?,id FROM school_class WHERE id=? AND enabled=1",id,classId);
        log(actor,"设置班级归属",String.valueOf(target.get("username"))); return Map.of("message","班级归属已更新");
    }

    @PostMapping("/base-data")
    @Transactional
    public Map<String, String> createData(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                         @RequestBody BaseInput input) {
        long actor = auth.requireAdmin(token);
        validateBase(input);
        try {
            db.update("INSERT INTO admin_base_data(kind,code,name,details,enabled) VALUES(?,?,?,?,?)",
                    input.kind(), input.code().trim(), input.name().trim(), input.details(), input.enabled());
            if ("class".equals(input.kind())) db.update("INSERT INTO school_class(code,name,grade,enabled) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),enabled=VALUES(enabled)", input.code().trim(), input.name().trim(), "", input.enabled());
        } catch (DuplicateKeyException e) { throw conflict("该类别下的编码已存在"); }
        log(actor, "新增基础资料", input.kind() + ": " + input.code());
        return Map.of("message", "资料已新增");
    }

    @PutMapping("/base-data/{id}")
    @Transactional
    public Map<String, String> updateData(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                         @PathVariable long id, @RequestBody BaseInput input) {
        long actor = auth.requireAdmin(token);
        validateBase(input);
        try {
            if (db.update("UPDATE admin_base_data SET code=?,name=?,details=?,enabled=? WHERE id=? AND kind=?",
                    input.code().trim(), input.name().trim(), input.details(), input.enabled(), id, input.kind()) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在");
            if ("class".equals(input.kind())) db.update("INSERT INTO school_class(code,name,grade,enabled) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),enabled=VALUES(enabled)", input.code().trim(), input.name().trim(), "", input.enabled());
        } catch (DuplicateKeyException e) { throw conflict("该类别下的编码已存在"); }
        log(actor, "更新基础资料", input.kind() + ": " + input.code());
        return Map.of("message", "资料已更新");
    }

    @GetMapping("/operations")
    public Map<String, Object> operations(@RequestHeader(value="X-Auth-Token", required=false) String token) {
        auth.requireAdmin(token);
        long start = System.nanoTime();
        boolean database;
        try { database = Integer.valueOf(1).equals(db.queryForObject("SELECT 1", Integer.class)); }
        catch (DataAccessException e) { database = false; }
        Runtime runtime = Runtime.getRuntime();
        return Map.of("checkedAt", OffsetDateTime.now().toString(), "database", database,
                "demoMode", storage.isDemo(), "databaseMs", (System.nanoTime()-start)/1_000_000, "uptimeSeconds",
                ManagementFactory.getRuntimeMXBean().getUptime()/1000,
                "heapUsedMb", (runtime.totalMemory()-runtime.freeMemory())/1024/1024,
                "heapMaxMb", runtime.maxMemory()/1024/1024,
                "processors", runtime.availableProcessors(), "javaVersion", System.getProperty("java.version"));
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestHeader(value="X-Auth-Token", required=false) String token) {
        auth.requireAdmin(token);
        return db.queryForList("SELECT a.id,a.action,a.target,a.created_at,u.username FROM admin_audit_log a "
                + "LEFT JOIN sys_user u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 200");
    }

    private Map<String,Object> user(long id) {
        List<Map<String,Object>> rows = db.queryForList("SELECT id,username,role FROM sys_user WHERE id=? AND enabled>=0", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账号不存在");
        return rows.get(0);
    }
    private void ensureProfile(long id, String role) {
        db.update("INSERT IGNORE INTO " + role + "_profile(user_id) VALUES(?)", id);
    }
    private void log(long actor, String action, String target) {
        db.update("INSERT INTO admin_audit_log(actor_id,action,target) VALUES(?,?,?)", actor, action, target);
    }
    private void validateUser(UserInput input) {
        required(input.displayName(), 50, "姓名");
        if (input.role() == null || !ROLES.contains(input.role()) || input.enabled() == null) throw bad("身份或状态无效");
    }
    private void validateBase(BaseInput input) {
        if (input.kind() == null || !KINDS.contains(input.kind()) || input.enabled() == null) throw bad("类别或状态无效");
        required(input.code(), 40, "编码"); required(input.name(), 80, "名称");
        if (input.details() == null || input.details().length() > 255) throw bad("备注不能超过255字");
    }
    private String required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) throw bad(label + "不能为空且不能超过" + max + "字");
        return value.trim();
    }
    private void password(String value) {
        if (value == null || value.length()<6 || value.length()>128) throw bad("密码长度应为6至128位");
    }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }

    public record UserInput(String username, String displayName, String role, Boolean enabled, String password) {}
    public record PasswordInput(String password) {}
    public record BaseInput(String kind, String code, String name, String details, Boolean enabled) {}
    public record ClassAssignment(List<Long> classIds) {}
}
