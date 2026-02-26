CREATE DATABASE IF NOT EXISTS spare_db DEFAULT CHARACTER SET utf8mb4;
USE spare_db;

CREATE TABLE IF NOT EXISTS `user` (
    id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50)  NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `spare_part` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(100)   NOT NULL COMMENT '备件名称',
    model      VARCHAR(100)   DEFAULT NULL COMMENT '型号规格',
    quantity   INT            NOT NULL DEFAULT 0 COMMENT '库存数量',
    unit       VARCHAR(20)    DEFAULT '个' COMMENT '单位',
    price      DECIMAL(10, 2) DEFAULT NULL COMMENT '单价（元）',
    category   VARCHAR(50)    DEFAULT NULL COMMENT '类别',
    supplier   VARCHAR(100)   DEFAULT NULL COMMENT '供应商',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    location_id BIGINT        DEFAULT NULL COMMENT '所属货位ID',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件表';

CREATE TABLE IF NOT EXISTS `location` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)    NOT NULL COMMENT '货位编码',
    name       VARCHAR(100)   NOT NULL COMMENT '货位名称',
    zone       VARCHAR(50)    NOT NULL COMMENT '所属专区(1-12)',
    capacity   VARCHAR(50)    DEFAULT NULL COMMENT '容量',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位档案表';

-- 初始账号：admin / 123456
INSERT INTO `user` (username, password) VALUES (
    'admin',
    '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa'
);
