package com.example.demo.mapper;

import com.example.demo.model.WpsFileRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WpsFileMapper {

    @Select("""
            SELECT id, file_id, bucket_name, object_key, file_name, version, size,
                   creator_id, modifier_id, create_time, modify_time
            FROM wps_file
            WHERE file_id = #{fileId}
            LIMIT 1
            """)
    WpsFileRecord findByFileId(String fileId);

    @Insert("""
            INSERT INTO wps_file (
                file_id, bucket_name, object_key, file_name, version, size,
                creator_id, modifier_id, create_time, modify_time
            ) VALUES (
                #{fileId}, #{bucketName}, #{objectKey}, #{fileName}, #{version}, #{size},
                #{creatorId}, #{modifierId}, #{createTime}, #{modifyTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WpsFileRecord record);

    @Update("""
            UPDATE wps_file
            SET bucket_name = #{bucketName},
                object_key = #{objectKey},
                file_name = #{fileName},
                version = #{version},
                size = #{size},
                creator_id = #{creatorId},
                modifier_id = #{modifierId},
                create_time = #{createTime},
                modify_time = #{modifyTime}
            WHERE file_id = #{fileId}
            """)
    int updateByFileId(WpsFileRecord record);
}
