package com.blogweb.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

/**
 * User persistence model. Password stores the frontend-submitted credential unchanged.
 * Role is USER or ADMIN; status is 1 (enabled) or 0 (disabled).
 */
@TableName("users")
@Data
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String email;

    @JsonIgnore
    @ToString.Exclude
    private String password;

    private String nickname;
    private String role;
    private Integer status;

}
