package com.example.urbanserve.repo;

import com.example.urbanserve.model.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);

    @Modifying
    @Query("update OtpChallenge c set c.consumed = true where c.phone = :phone and c.consumed = false")
    int consumeAllForPhone(@Param("phone") String phone);

    @Modifying
    @Query("delete from OtpChallenge c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
