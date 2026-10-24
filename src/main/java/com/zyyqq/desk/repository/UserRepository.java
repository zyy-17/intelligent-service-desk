package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {

    @Query("SELECT * FROM sys_user WHERE username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT * FROM sys_user ORDER BY role, id")
    List<User> findAllOrdered();

    @Query("SELECT * FROM sys_user WHERE role = :role ORDER BY id")
    List<User> findByRole(@Param("role") String role);

    @Query("SELECT COUNT(*) FROM sys_user")
    long countAll();

    @Query("SELECT COUNT(*) FROM sys_user WHERE role = :role")
    long countByRole(@Param("role") String role);
}
