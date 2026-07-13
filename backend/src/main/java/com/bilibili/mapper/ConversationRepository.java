package com.bilibili.mapper;

import com.bilibili.pojo.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Conversation c WHERE c.userId = :userId AND c.id = :conversationId")
    void deleteByUserIdAndId(@Param("userId") Long userId, @Param("conversationId") Long conversationId);
}
