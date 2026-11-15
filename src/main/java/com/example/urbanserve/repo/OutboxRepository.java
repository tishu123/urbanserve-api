package com.example.urbanserve.repo;

import com.example.urbanserve.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50BySentAtIsNullOrderByIdAsc();

    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt is not null and e.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
