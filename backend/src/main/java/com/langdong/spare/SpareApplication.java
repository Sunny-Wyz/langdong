package com.langdong.spare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.langdong.spare.mapper")
public class SpareApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpareApplication.class, args);
    }
}
