package com.xinghe.zhixue.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:./target/admin-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {
    @Autowired AdminController admin;
    @Autowired AuthController auth;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    String token;
    long actor;

    @BeforeEach void setup() {
        String username="qa_"+UUID.randomUUID().toString().replace("-","");
        db.update("INSERT INTO sys_user(username,password_hash,role,display_name) VALUES(?,?,?,?)",username,Passwords.encode("Test123456"),"admin","QA Admin");
        actor=db.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,username);
        token=auth.login(new AuthController.LoginRequest(username,"Test123456")).get("token").toString();
    }

    @Test void managementRequiresAdminAndDoesNotExposePasswords() throws Exception {
        for(String path:new String[]{"/users","/base-data","/operations","/audit"}) {
            http.perform(get("/api/admin"+path)).andExpect(status().isUnauthorized());
        }
        String student="qa_"+UUID.randomUUID().toString().replace("-","");
        Map<String,Object> registered=auth.register(new AuthController.RegisterRequest(student,"Test123456","admin","QA Student",java.util.List.of()));
        assertEquals("student",((Map<?,?>)registered.get("user")).get("role"));
        String studentToken=registered.get("token").toString();
        for(String path:new String[]{"/users","/base-data","/operations","/audit"}) {
            http.perform(get("/api/admin"+path).header("X-Auth-Token",studentToken)).andExpect(status().isForbidden());
        }
        assertTrue(admin.users(token).stream().noneMatch(row->row.containsKey("password_hash")));
    }

    @Test void accountChangesPersistWithProfilesAndAudit() {
        String username="qa_"+UUID.randomUUID().toString().replace("-","");
        long id=((Number)admin.createUser(token,new AdminController.UserInput(username,"QA Teacher","teacher",true,"Test123456")).get("id")).longValue();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM teacher_profile WHERE user_id=?",Integer.class,id));
        String session=auth.login(new AuthController.LoginRequest(username,"Test123456")).get("token").toString();
        admin.resetPassword(token,id,new AdminController.PasswordInput("New123456"));
        assertThrows(ResponseStatusException.class,()->auth.me(session));
        assertThrows(ResponseStatusException.class,()->auth.login(new AuthController.LoginRequest(username,"Test123456")));
        String newSession=auth.login(new AuthController.LoginRequest(username,"New123456")).get("token").toString();
        admin.updateUser(token,id,new AdminController.UserInput(username,"QA Student","student",false,null));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE user_id=?",Integer.class,id));
        assertThrows(ResponseStatusException.class,()->auth.me(newSession));
        assertThrows(ResponseStatusException.class,()->auth.login(new AuthController.LoginRequest(username,"New123456")));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id=?",Integer.class,actor));
    }

    @Test void ownPrivilegesAreProtected() {
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->admin.updateUser(token,actor,new AdminController.UserInput(null,"QA","student",false,null)));
        assertEquals(409,ex.getStatusCode().value());
    }

    @Test void baseDataCanBeCreatedAndUpdated() {
        String code="QA_"+UUID.randomUUID().toString().replace("-","");
        admin.createData(token,new AdminController.BaseInput("class",code,"QA Class","Test",true));
        long id=db.queryForObject("SELECT id FROM admin_base_data WHERE code=?",Long.class,code);
        admin.updateData(token,id,new AdminController.BaseInput("class",code,"Updated","",false));
        assertEquals("Updated",db.queryForObject("SELECT name FROM admin_base_data WHERE id=?",String.class,id));
        assertEquals(0,db.queryForObject("SELECT enabled FROM admin_base_data WHERE id=?",Integer.class,id));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id=?",Integer.class,actor));
    }

    @Test void operationsReportsActualDatabaseAndRuntime() {
        Map<String,Object> status=admin.operations(token);
        assertEquals(true,status.get("database"));
        assertTrue(((Number)status.get("heapMaxMb")).longValue()>0);
        assertNotNull(status.get("checkedAt"));
    }
}
