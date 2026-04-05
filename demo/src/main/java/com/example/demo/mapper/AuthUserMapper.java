package com.example.demo.mapper;

import com.example.demo.model.AuthUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthUserMapper {

    @Select("""
            SELECT id, user_id, username, password, nickname, phone, email, created_at, updated_at
            FROM auth_user
            WHERE username = #{username}
            LIMIT 1
            """)
    AuthUser findByUsername(String username);

    @Select("""
            SELECT id, user_id, username, password, nickname, phone, email, created_at, updated_at
            FROM auth_user
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    AuthUser findByUserId(String userId);

    @Select("""
            SELECT id, user_id, username, password, nickname, phone, email, created_at, updated_at
            FROM auth_user
            WHERE phone = #{phone}
            LIMIT 1
            """)
    AuthUser findByPhone(String phone);

    @Select("""
            SELECT id, user_id, username, password, nickname, phone, email, created_at, updated_at
            FROM auth_user
            WHERE email = #{email}
            LIMIT 1
            """)
    AuthUser findByEmail(String email);

    @Insert("""
            INSERT INTO auth_user (user_id, username, password, nickname, phone, email, created_at, updated_at)
            VALUES (#{userId}, #{username}, #{password}, #{nickname}, #{phone}, #{email}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuthUser user);
}
