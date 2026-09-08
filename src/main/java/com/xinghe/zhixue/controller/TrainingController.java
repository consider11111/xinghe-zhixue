package com.xinghe.zhixue.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/education")
public class TrainingController {
    private final JdbcTemplate db;
    private final AuthController auth;
    public TrainingController(JdbcTemplate db, AuthController auth) { this.db=db; this.auth=auth; }

    @GetMapping("/teacher/classes")
    public List<Map<String,Object>> classes(@RequestHeader(value="X-Auth-Token",required=false) String token) {
        Map<String,Object> user = role(token,"teacher");
        return memberships(id(user));
    }

    @GetMapping("/training")
    @Transactional
    public List<Map<String,Object>> training(@RequestHeader(value="X-Auth-Token",required=false) String token) {
        Map<String,Object> user = role(token,"student");
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> group : memberships(id(user))) {
            long classId=id(group);
            generate(classId);
            result.addAll(db.queryForList("SELECT t.*,c.name AS class_name,COALESCE(p.completed,0)*100 AS progress "
                + "FROM class_training t JOIN school_class c ON c.id=t.class_id LEFT JOIN training_progress p "
                + "ON p.training_id=t.id AND p.user_id=? WHERE t.class_id=? AND t.plan_date=? ORDER BY t.id",
                id(user),classId,today()));
        }
        return result;
    }

    @GetMapping("/teacher/classes/{classId}/training")
    @Transactional
    public List<Map<String,Object>> classTraining(@RequestHeader(value="X-Auth-Token",required=false) String token,
                                                 @PathVariable long classId) {
        requireTeacher(token,classId);
        generate(classId);
        return db.queryForList("SELECT * FROM class_training WHERE class_id=? AND plan_date=? ORDER BY id",classId,today());
    }

    @PostMapping("/teacher/classes/{classId}/training")
    @Transactional
    public Map<String,String> create(@RequestHeader(value="X-Auth-Token",required=false) String token,
                                     @PathVariable long classId,@RequestBody TrainingInput input) {
        requireTeacher(token,classId); validate(input); generate(classId);
        insert(classId,input,"teacher");
        return Map.of("message","训练已添加");
    }

    @PutMapping("/teacher/classes/{classId}/training/{trainingId}")
    @Transactional
    public Map<String,String> update(@RequestHeader(value="X-Auth-Token",required=false) String token,
            @PathVariable long classId,@PathVariable long trainingId,@RequestBody TrainingInput input) {
        requireTeacher(token,classId); validate(input);
        if (db.update("UPDATE class_training SET subject=?,title=?,content=?,question_count=?,minutes=?,source='teacher' "
            + "WHERE id=? AND class_id=? AND plan_date=?",input.subject().trim(),input.title().trim(),input.content().trim(),
            input.count(),input.minutes(),trainingId,classId,today())==0) throw missing();
        db.update("DELETE FROM training_progress WHERE training_id=?",trainingId);
        return Map.of("message","训练已更新，学生需重新完成");
    }

    @DeleteMapping("/teacher/classes/{classId}/training/{trainingId}")
    @Transactional
    public Map<String,String> delete(@RequestHeader(value="X-Auth-Token",required=false) String token,
                                     @PathVariable long classId,@PathVariable long trainingId) {
        requireTeacher(token,classId);
        if(db.update("DELETE FROM class_training WHERE id=? AND class_id=? AND plan_date=?",trainingId,classId,today())==0)
            throw missing();
        return Map.of("message","训练已删除");
    }

    @PostMapping("/training/{trainingId}/complete")
    @Transactional
    public Map<String,String> complete(@RequestHeader(value="X-Auth-Token",required=false) String token,
                                       @PathVariable long trainingId) {
        long userId=id(role(token,"student"));
        List<Long> found=db.queryForList("SELECT t.id FROM class_training t JOIN user_class uc ON uc.class_id=t.class_id "
            + "JOIN school_class c ON c.id=t.class_id WHERE t.id=? AND uc.user_id=? AND c.enabled=1 AND t.plan_date=? FOR UPDATE",
            Long.class,trainingId,userId,today());
        if(found.isEmpty()) throw missing();
        db.update("INSERT INTO training_progress(training_id,user_id,completed) VALUES(?,?,1) ON DUPLICATE KEY UPDATE completed=1",trainingId,userId);
        return Map.of("message","已记录完成状态");
    }

    private List<Map<String,Object>> memberships(long userId) {
        return db.queryForList("SELECT c.id,c.code,c.name,c.grade FROM school_class c JOIN user_class uc ON c.id=uc.class_id "
            + "WHERE uc.user_id=? AND c.enabled=1 ORDER BY c.id",userId);
    }
    private Map<String,Object> role(String token,String role) {
        Map<String,Object> user=auth.requireUser(token);
        if(!role.equals(user.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份无权执行此操作");
        return user;
    }
    private void requireTeacher(String token,long classId) {
        long userId=id(role(token,"teacher"));
        if(memberships(userId).stream().noneMatch(c->id(c)==classId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能管理自己所属班级的训练");
    }
    private long id(Map<String,Object> row) { return ((Number)row.get("id")).longValue(); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"训练不存在或不属于当前班级的今日计划"); }
    private void validate(TrainingInput q) {
        if(q==null || !text(q.subject(),30) || !text(q.title(),100) || !text(q.content(),5000)
            || q.count()==null || q.count()<1 || q.count()>100 || q.minutes()==null || q.minutes()<1 || q.minutes()>180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写学科、标题和训练内容，题数为1至100，时长为1至180分钟");
    }
    private boolean text(String value,int max) { return value!=null && !value.isBlank() && value.length()<=max; }
    private void insert(long classId,TrainingInput q,String source) {
        db.update("INSERT INTO class_training(class_id,plan_date,subject,title,content,question_count,minutes,source) VALUES(?,?,?,?,?,?,?,?)",
            classId,today(),q.subject().trim(),q.title().trim(),q.content().trim(),q.count(),q.minutes(),source);
    }
    private void generate(long classId) {
        // Serialize first access per class; the day marker also preserves a teacher's empty plan after deletion.
        db.queryForObject("SELECT id FROM school_class WHERE id=? FOR UPDATE",Long.class,classId);
        if(db.queryForObject("SELECT COUNT(*) FROM training_day WHERE class_id=? AND plan_date=?",Integer.class,classId,today())>0) return;
        List<TrainingInput> choices=new ArrayList<>(List.of(
            new TrainingInput("数学","二次函数图像与最值","1. 求 y=x²-4x+3 的顶点。\n2. 求函数与 x 轴的交点。\n3. 求 x∈[0,3] 时的值域。",3,15),
            new TrainingInput("数学","等差数列巩固","1. 已知 a₁=3，d=2，求 a₁₀。\n2. 求前10项和。\n3. 若 aₙ=25，求 n。",3,12),
            new TrainingInput("数学","概率基础训练","1. 掷一枚骰子，出现偶数的概率是多少？\n2. 连掷两次，点数和为7的概率是多少？\n3. 说明两个事件独立的含义。",3,15),
            new TrainingInput("英语","长难句拆解","1. 找出 The book that you lent me is interesting. 的主句。\n2. 指出 that 引导从句的作用。\n3. 用定语从句写一句话。",3,12),
            new TrainingInput("英语","时态专项练习","1. 填空：She ___ (study) here since 2024.\n2. 填空：They ___ (play) football when it started to rain.\n3. 分别用一般过去时和现在完成时造句。",3,10),
            new TrainingInput("物理","匀变速运动","1. 物体初速度为0，加速度为2m/s²，求5秒后的速度。\n2. 求前5秒的位移。\n3. 画出对应的速度—时间图像。",3,15),
            new TrainingInput("物理","实验设计与变量控制","1. 设计实验研究加速度与合外力的关系。\n2. 写出需要保持不变的物理量。\n3. 列举两项可能的实验误差。",3,15),
            new TrainingInput("语文","古诗词赏析","1. 默写《登高》颔联。\n2. 分析其中的意象与情感。\n3. 用80字概括诗歌的情景交融特点。",3,15)
        ));
        Collections.shuffle(choices);
        for(TrainingInput q:choices.subList(0,3)) insert(classId,q,"random");
        db.update("INSERT INTO training_day(class_id,plan_date) VALUES(?,?)",classId,today());
    }
    public record TrainingInput(String subject,String title,String content,Integer count,Integer minutes) {}
}
