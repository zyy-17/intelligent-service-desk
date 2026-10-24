package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.Notification;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends CrudRepository<Notification, Long> {

    @Query("SELECT * FROM notification WHERE recipient_id = :uid ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<Notification> findByRecipient(@Param("uid") Long uid, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(*) FROM notification WHERE recipient_id = :uid AND is_read = 0")
    long countUnread(@Param("uid") Long uid);

    @Modifying
    @Query("UPDATE notification SET is_read = 1 WHERE id = :id AND recipient_id = :uid")
    void markRead(@Param("id") Long id, @Param("uid") Long uid);

    @Modifying
    @Query("UPDATE notification SET is_read = 1 WHERE recipient_id = :uid AND is_read = 0")
    void markAllRead(@Param("uid") Long uid);
}
