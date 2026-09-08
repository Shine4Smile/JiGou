package com.simple.jigou;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class JiGouApplication {

	public static void main(String[] args) {
		SpringApplication.run(JiGouApplication.class, args);
	}

}
