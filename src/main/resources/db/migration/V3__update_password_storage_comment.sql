-- Change metadata only; existing credential values are intentionally preserved.
ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NOT NULL
    COMMENT '前端提交的密码值，后端原样存储';
