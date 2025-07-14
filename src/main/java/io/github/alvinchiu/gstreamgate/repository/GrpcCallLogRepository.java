package io.github.alvinchiu.gstreamgate.repository;

import io.github.alvinchiu.gstreamgate.entity.GrpcCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * gRPC 呼叫記錄存儲庫介面
 */
@Repository
public interface GrpcCallLogRepository extends JpaRepository<GrpcCallLog, Long> {
    
    /**
     * 根據客戶端IP搜尋記錄
     */
    Page<GrpcCallLog> findByClientIpContainingIgnoreCase(String clientIp, Pageable pageable);
    
    /**
     * 根據目標位置搜尋記錄
     */
    Page<GrpcCallLog> findByTargetLocationContainingIgnoreCase(String targetLocation, Pageable pageable);
    
    /**
     * 根據方法名稱搜尋記錄
     */
    Page<GrpcCallLog> findByMethodNameContainingIgnoreCase(String methodName, Pageable pageable);
    
    /**
     * 根據狀態碼搜尋記錄
     */
    Page<GrpcCallLog> findByStatusCodeContainingIgnoreCase(String statusCode, Pageable pageable);
    
    /**
     * 根據呼叫類型搜尋記錄
     */
    Page<GrpcCallLog> findByCallType(String callType, Pageable pageable);
    
    /**
     * 根據時間範圍搜尋記錄
     */
    Page<GrpcCallLog> findByCallStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * 根據追蹤ID搜尋記錄
     */
    Page<GrpcCallLog> findByTraceIdContainingIgnoreCase(String traceId, Pageable pageable);
    
    /**
     * 複合搜尋 - 支援多個條件同時搜尋
     */
    @Query("SELECT g FROM GrpcCallLog g WHERE " +
           "(:clientIp IS NULL OR LOWER(g.clientIp) LIKE LOWER(CONCAT('%', :clientIp, '%'))) AND " +
           "(:targetLocation IS NULL OR LOWER(g.targetLocation) LIKE LOWER(CONCAT('%', :targetLocation, '%'))) AND " +
           "(:methodName IS NULL OR LOWER(g.methodName) LIKE LOWER(CONCAT('%', :methodName, '%'))) AND " +
           "(:statusCode IS NULL OR LOWER(g.statusCode) LIKE LOWER(CONCAT('%', :statusCode, '%'))) AND " +
           "(:callType IS NULL OR g.callType = :callType) AND " +
           "(:startTime IS NULL OR g.callStartTime >= :startTime) AND " +
           "(:endTime IS NULL OR g.callEndTime <= :endTime)")
    Page<GrpcCallLog> findByMultipleFilters(@Param("clientIp") String clientIp,
                                           @Param("targetLocation") String targetLocation,
                                           @Param("methodName") String methodName,
                                           @Param("statusCode") String statusCode,
                                           @Param("callType") String callType,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           Pageable pageable);
    
    /**
     * 統計特定時間範圍內的呼叫次數
     */
    @Query("SELECT COUNT(g) FROM GrpcCallLog g WHERE g.callStartTime >= :startTime AND g.callEndTime <= :endTime")
    Long countCallsInTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * 統計特定狀態碼的呼叫次數
     */
    @Query("SELECT COUNT(g) FROM GrpcCallLog g WHERE g.statusCode = :statusCode")
    Long countCallsByStatusCode(@Param("statusCode") String statusCode);
    
    /**
     * 獲取最近的呼叫記錄
     */
    List<GrpcCallLog> findTop10ByOrderByCallStartTimeDesc();
    
    /**
     * 獲取執行時間最長的呼叫記錄
     */
    List<GrpcCallLog> findTop10ByOrderByExecutionTimeMsDesc();
    
    /**
     * 根據客戶端IP統計呼叫次數
     */
    @Query("SELECT g.clientIp, COUNT(g) FROM GrpcCallLog g GROUP BY g.clientIp ORDER BY COUNT(g) DESC")
    List<Object[]> countCallsByClientIp();
    
    /**
     * 根據方法名稱統計呼叫次數
     */
    @Query("SELECT g.methodName, COUNT(g) FROM GrpcCallLog g GROUP BY g.methodName ORDER BY COUNT(g) DESC")
    List<Object[]> countCallsByMethodName();
    
    /**
     * 根據狀態碼統計呼叫次數
     */
    @Query("SELECT g.statusCode, COUNT(g) FROM GrpcCallLog g GROUP BY g.statusCode ORDER BY COUNT(g) DESC")
    List<Object[]> countCallsByStatusCode();
    
    /**
     * 刪除舊記錄（清理功能）
     */
    void deleteByCallStartTimeBefore(LocalDateTime cutoffTime);
}