package com.xinghe.zhixue.controller;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:./target/training-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@Transactional
class TrainingControllerTest {
    @Autowired JdbcTemplate db;
    @Autowired AuthController auth;
    @Autowired TrainingController training;
    long classId,otherClass;
    String student,teacher,outsider;
    @BeforeEach void setup() {
        classId=group();otherClass=group();
        student=account("student",classId);teacher=account("teacher",classId);outsider=account("teacher",otherClass);
    }
    long group() {
        String code="C"+UUID.randomUUID().toString().replace("-","");
        db.update("INSERT INTO school_class(code,name) VALUES(?,?)",code,"Test Class");
        return db.queryForObject("SELECT id FROM school_class WHERE code=?",Long.class,code);
    }
    String account(String role,long group) {
        String username="U"+UUID.randomUUID().toString().replace("-","");
        return auth.register(new AuthController.RegisterRequest(username,"Test123456",role,role,List.of(group))).get("token").toString();
    }
    long id(Map<String,Object> row) {return ((Number)row.get("id")).longValue();}
    @Test void plansStayConsistentAndTeacherEditsReachStudents() {
        var tasks=training.training(student);
        assertEquals(3,tasks.size());
        assertEquals(tasks,training.training(student));
        assertEquals(tasks.stream().map(this::id).toList(),training.classTraining(teacher,classId).stream().map(this::id).toList());
        long task=id(tasks.get(0));
        training.complete(student,task);
        assertEquals(100,((Number)training.training(student).get(0).get("progress")).intValue());
        training.update(teacher,classId,task,new TrainingController.TrainingInput("数学","Teacher Updated","Solve x+1=2",1,5));
        assertEquals("Teacher Updated",training.training(student).get(0).get("title"));
        assertEquals(0,((Number)training.training(student).get(0).get("progress")).intValue());
        training.create(teacher,classId,new TrainingController.TrainingInput("英语","New Task","Translate hello",1,5));
        assertEquals(4,training.training(student).size());
        for(var row:training.classTraining(teacher,classId))training.delete(teacher,classId,id(row));
        assertTrue(training.training(student).isEmpty(),"Deleted plans must not be regenerated on refresh");
    }
    @Test void otherClassesAndStudentsCannotManageThisPlan() {
        long task=id(training.training(student).get(0));
        var input=new TrainingController.TrainingInput("数学","Other","Other",1,5);
        assertThrows(ResponseStatusException.class,()->training.classTraining(outsider,classId));
        assertThrows(ResponseStatusException.class,()->training.create(outsider,classId,input));
        assertThrows(ResponseStatusException.class,()->training.update(outsider,classId,task,input));
        assertThrows(ResponseStatusException.class,()->training.delete(outsider,classId,task));
        assertThrows(ResponseStatusException.class,()->training.create(student,classId,input));
        String otherStudent=account("student",otherClass);
        assertThrows(ResponseStatusException.class,()->training.complete(otherStudent,task));
        assertThrows(ResponseStatusException.class,()->training.training(null));
    }
}
