package com.example.demo.mapper;

import com.example.demo.model.AuthVerificationCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AuthVerificationCodeMapper {

    @Insert("""
            INSERT INTO auth_verification_code (target_value, code, code_type, used, expires_at, created_at, updated_at)
            VALUES (#{targetValue}, #{code}, #{codeType}, #{used}, #{expiresAt}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuthVerificationCode record);

    @Select("""
            SELECT id, target_value, code, code_type, used, expires_at, created_at, updated_at
            FROM auth_verification_code
            WHERE target_value = #{targetValue}
              AND code_type = #{codeType}
              AND used = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    AuthVerificationCode findLatestUnused(String targetValue, String codeType);

    @Update("""
            UPDATE auth_verification_code
            SET used = 1, updated_at = NOW()
            WHERE id = #{id}
            """)
    int markUsed(Long id);

    @Update("""
            DELETE FROM auth_verification_code
            WHERE expires_at < #{now}
               OR (used = 1 AND updated_at < #{usedBefore})
            """)
    int deleteExpiredOrUsed(@Param("now") LocalDateTime now, @Param("usedBefore") LocalDateTime usedBefore);
}
