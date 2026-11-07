-- Run manually in the intended empty database. The application never executes this file.
CREATE TABLE users (
    id BIGINT NOT NULL COMMENT '用户主键，由MyBatis-Plus雪花算法生成',
    email VARCHAR(254) COLLATE utf8mb4_0900_as_ci NOT NULL COMMENT '邮箱，大小写不敏感且唯一',
    password VARCHAR(255) NOT NULL COMMENT '前端提交的密码值，后端原样存储',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER：普通用户，ADMIN：管理员',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1：正常，0：禁用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';
