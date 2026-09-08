package com.xinghe.zhixue.controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/education")
public class EducationController {
 @GetMapping("/overview") public Map<String,Object> overview(){return Map.of("school","星河高级中学","term","2026 春季学期","studentCount",1286,"teacherCount",86,"activeToday",1042,"mastery",78);}
 @GetMapping("/students/{id}/diagnosis") public Map<String,Object> diagnosis(@PathVariable String id){return Map.of("studentId",id,"name","林同学","className","高一（3）班","overall",82,"trend","+6.4%","strengths",List.of("函数基础","阅读理解","实验设计"),"gaps",List.of(Map.of("subject","数学","topic","二次函数综合","mastery",56),Map.of("subject","英语","topic","长难句分析","mastery",63)),"suggestion","建议先完成二次函数错题巩固，再进行 15 分钟长难句精练。");}
 @GetMapping("/insights") public Map<String,Object> insights(){return Map.of("weeklyActive",List.of(72,78,75,83,88,91,89),"subjectMastery",List.of(Map.of("name","数学","value",81),Map.of("name","英语","value",76),Map.of("name","物理","value",73)),"alerts",List.of("高一（5）班数学作业完成率连续两周下降","12 名学生需要长难句专项辅导"));}
 @GetMapping("/knowledge-graph/{subject}") public Map<String,Object> graph(@PathVariable String subject){return Map.of("subject",subject,"nodes",List.of(Map.of("name","函数基础","mastery",91,"status","已掌握"),Map.of("name","二次函数综合","mastery",56,"status","待巩固"),Map.of("name","函数应用题","mastery",68,"status","学习中")));}
}
