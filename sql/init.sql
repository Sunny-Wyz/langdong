CREATE DATABASE IF NOT EXISTS spare_db DEFAULT CHARACTER SET utf8mb4;
USE spare_db;

CREATE TABLE IF NOT EXISTS `user` (
    id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50)  NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 初始账号：admin / 123456
INSERT INTO `user` (username, password) VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyU.VW7WS'
);
