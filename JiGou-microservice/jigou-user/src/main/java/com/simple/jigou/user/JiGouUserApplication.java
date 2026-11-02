package com.simple.jigou.user;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.simple.jigou.user.mapper")
@ComponentScan("com.simple")
@EnableDubbo
public class JiGouUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(JiGouUserApplication.class, args);
    }
}
