package com.simple.jigou;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
@MapperScan("com.simple.jigou.mapper")
public class JiGouApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiGouApplication.class, args);
    }

}
