package com.zhimian.service.impl;

import com.zhimian.dto.ResumeAnalysis;
import com.zhimian.service.ResumeAnalyzer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于本地技术词典 + 规则的简历分析器（无需大模型）。
 * <p>
 * 思路：
 * 1. 用技术词典在简历原文里匹配技能关键词（大小写不敏感）；
 * 2. 按"项目/经历"等标志词切分出项目段落；
 * 3. 技能词 + 项目里出现的高频技术词 => 画像关键词。
 */
@Service
public class RuleBasedResumeAnalyzer implements ResumeAnalyzer {

    /** 计算机岗位常见技术词典（可持续扩充，后续可移到数据库/配置） */
    private static final List<String> TECH_DICT = Arrays.asList(
            // 语言
            "Java", "Python", "C++", "C#", "Go", "Golang", "JavaScript", "TypeScript", "Kotlin", "Rust", "PHP", "SQL",
            // 后端 / 框架
            "Spring", "Spring Boot", "SpringBoot", "Spring Cloud", "SpringCloud", "MyBatis", "Hibernate",
            "Netty", "Dubbo", "gRPC", "Maven", "Gradle",
            // 前端
            "Vue", "React", "Angular", "HTML", "CSS", "Sass", "Less", "Webpack", "Vite", "Element", "ECharts",
            "Node.js", "Nodejs", "jQuery", "ES6", "Axios", "Pinia", "Vuex", "Redux",
            // 数据库 / 中间件
            "MySQL", "Redis", "MongoDB", "PostgreSQL", "Oracle", "Elasticsearch", "Kafka", "RabbitMQ", "RocketMQ",
            "Zookeeper", "Nginx",
            // 概念 / 能力
            "并发", "多线程", "JVM", "分布式", "微服务", "高并发", "数据结构", "算法", "设计模式",
            "事务", "索引", "锁", "缓存", "消息队列", "负载均衡", "限流", "熔断",
            // 运维 / 工具
            "Docker", "Kubernetes", "K8s", "Linux", "Git", "Jenkins", "CI/CD", "Spring Security",
            // AI / 数据
            "机器学习", "深度学习", "TensorFlow", "PyTorch", "NLP", "大模型", "RAG"
    );

    /** 项目段落的起始标志词 */
    private static final Pattern PROJECT_SPLIT = Pattern.compile(
            "(?=项目名称|项目经历|项目经验|项目背景|项目描述|实习经历|实习经验|工作经历|【项目)");

    @Override
    public ResumeAnalysis analyze(String rawText) {
        ResumeAnalysis result = new ResumeAnalysis();
        if (rawText == null || rawText.isBlank()) {
            result.setSkills(Collections.emptyList());
            result.setKeywords(Collections.emptyList());
            result.setProjects(Collections.emptyList());
            return result;
        }

        // 1. 技能匹配（保持词典原始写法，去重，保留出现顺序）
        String lower = rawText.toLowerCase();
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (String tech : TECH_DICT) {
            if (lower.contains(tech.toLowerCase())) {
                skills.add(tech);
            }
        }

        // 2. 项目段落切分
        List<String> projects = new ArrayList<>();
        Matcher m = PROJECT_SPLIT.matcher(rawText);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            int begin = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : rawText.length();
            String seg = rawText.substring(begin, end).trim();
            if (seg.length() > 8) {
                // 段落过长时截断，避免画像页过载
                projects.add(seg.length() > 300 ? seg.substring(0, 300) + "…" : seg);
            }
        }

        // 3. 画像关键词 = 技能词（规则版下两者一致；LLM 版可扩展软技能等）
        result.setSkills(new ArrayList<>(skills));
        result.setKeywords(new ArrayList<>(skills));
        result.setProjects(projects);
        return result;
    }
}
