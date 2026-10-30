package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 用户数据访问接口 */
public interface UserRepository extends CrudRepository<User, Long> {

    /** 按用户名查询用户 */
    @Query("SELECT * FROM sys_user WHERE username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    /** 按角色+id 排序查询全部用户 */
    @Query("SELECT * FROM sys_user ORDER BY role, id")
    List<User> findAllOrdered();

    /** 按角色查询用户 */
    @Query("SELECT * FROM sys_user WHERE role = :role ORDER BY id")
    List<User> findByRole(@Param("role") String role);

    /** 统计用户总数 */
    @Query("SELECT COUNT(*) FROM sys_user")
    long countAll();

    /** 按角色统计用户数 */
    @Query("SELECT COUNT(*) FROM sys_user WHERE role = :role")
    long countByRole(@Param("role") String role);
}
