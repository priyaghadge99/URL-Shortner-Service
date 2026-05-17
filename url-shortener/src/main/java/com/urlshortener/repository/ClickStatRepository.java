package com.urlshortener.repository;

import com.urlshortener.model.ClickStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ClickStatRepository extends JpaRepository<ClickStat, Long> {

    long countByShortCode(String shortCode);

    @Query("""
        SELECT DATE(c.timestamp) as day, COUNT(c) as clicks
        FROM ClickStat c
        WHERE c.shortCode = :code AND c.timestamp >= :since
        GROUP BY DATE(c.timestamp)
        ORDER BY DATE(c.timestamp)
        """)
    List<Object[]> clicksPerDay(@Param("code") String shortCode,
                                @Param("since") Instant since);

    @Query("""
        SELECT c.country, COUNT(c) as cnt
        FROM ClickStat c
        WHERE c.shortCode = :code
        GROUP BY c.country
        ORDER BY cnt DESC
        """)
    List<Object[]> topCountries(@Param("code") String shortCode);

    @Query("""
        SELECT c.deviceType, COUNT(c) as cnt
        FROM ClickStat c
        WHERE c.shortCode = :code
        GROUP BY c.deviceType
        """)
    List<Object[]> deviceBreakdown(@Param("code") String shortCode);

    @Query("""
        SELECT c.referer, COUNT(c) as cnt
        FROM ClickStat c
        WHERE c.shortCode = :code AND c.referer IS NOT NULL
        GROUP BY c.referer
        ORDER BY cnt DESC
        LIMIT 5
        """)
    List<Object[]> topReferrers(@Param("code") String shortCode);
}
