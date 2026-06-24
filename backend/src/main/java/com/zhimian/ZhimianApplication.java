package com.zhimian;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智面幻境 · 智能模拟面试系统 — 后端启动类
 */
@SpringBootApplication
@MapperScan("com.zhimian.mapper")
public class ZhimianApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhimianApplication.class, args);
    }
}
