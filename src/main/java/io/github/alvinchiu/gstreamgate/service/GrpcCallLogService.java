package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.GrpcCallLog;
import io.github.alvinchiu.gstreamgate.repository.GrpcCallLogRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * gRPC 呼叫記錄服務
 */
@Service
public class GrpcCallLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcCallLogService.class);
    
    private final GrpcCallLogRepository grpcCallLogRepository;
    private final ExecutorService asyncExecutor;
    
    @Autowired
    public GrpcCallLogService(GrpcCallLogRepository grpcCallLogRepository) {
        this.grpcCallLogRepository = grpcCallLogRepository;
        this.asyncExecutor = Executors.newFixedThreadPool(5);
    }
    
    /**
     * 異步記錄 gRPC 呼叫
     */
    public CompletableFuture<Void> logCallAsync(String clientIp, String targetLocation, 
                                               String methodName, Long executionTimeMs, 
                                               String statusCode, LocalDateTime callStartTime, 
                                               LocalDateTime callEndTime) {
        return CompletableFuture.runAsync(() -> {
            try {
                GrpcCallLog log = new GrpcCallLog(clientIp, targetLocation, methodName, 
                                                executionTimeMs, statusCode, callStartTime, callEndTime);
                
                // 嘗試從當前 span 獲取追蹤資訊
                try {
                    Span currentSpan = Span.current();
                    SpanContext spanContext = currentSpan.getSpanContext();
                    if (spanContext.isValid()) {
                        log.setTraceId(spanContext.getTraceId());
                        log.setSpanId(spanContext.getSpanId());
                    }
                } catch (Exception e) {
                    logger.debug("Could not extract trace information: {}", e.getMessage());
                }
                
                grpcCallLogRepository.save(log);
                logger.debug("Logged gRPC call: {} -> {} [{}] in {}ms", 
                           clientIp, targetLocation, methodName, executionTimeMs);
            } catch (Exception e) {
                logger.error("Failed to log gRPC call: {}", e.getMessage(), e);
            }
        }, asyncExecutor);
    }
    
    /**
     * 記錄 gRPC 呼叫（包含詳細資訊）
     */
    public CompletableFuture<Void> logCallWithDetailsAsync(String clientIp, String targetLocation, 
                                                          String methodName, Long executionTimeMs, 
                                                          String statusCode, LocalDateTime callStartTime, 
                                                          LocalDateTime callEndTime, String callType, 
                                                          Long requestSizeBytes, Long responseSizeBytes, 
                                                          String errorMessage, String traceId, String spanId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GrpcCallLog log = new GrpcCallLog(clientIp, targetLocation, methodName, 
                                                executionTimeMs, statusCode, callStartTime, callEndTime);
                
                log.setCallType(callType);
                log.setRequestSizeBytes(requestSizeBytes);
                log.setResponseSizeBytes(responseSizeBytes);
                log.setErrorMessage(errorMessage);
                log.setTraceId(traceId);
                log.setSpanId(spanId);
                
                grpcCallLogRepository.save(log);
                logger.debug("Logged detailed gRPC call: {} -> {} [{}] type={} in {}ms", 
                           clientIp, targetLocation, methodName, callType, executionTimeMs);
            } catch (Exception e) {
                logger.error("Failed to log detailed gRPC call: {}", e.getMessage(), e);
            }
        }, asyncExecutor);
    }
    
    /**
     * 搜尋呼叫記錄
     */
    public Page<GrpcCallLog> searchLogs(String clientIp, String targetLocation, String methodName, 
                                       String statusCode, String callType, LocalDateTime startTime, 
                                       LocalDateTime endTime, Pageable pageable) {
        return grpcCallLogRepository.findByMultipleFilters(
            clientIp, targetLocation, methodName, statusCode, callType, startTime, endTime, pageable);
    }
    
    /**
     * 獲取所有呼叫記錄
     */
    public Page<GrpcCallLog> getAllLogs(Pageable pageable) {
        return grpcCallLogRepository.findAll(pageable);
    }
    
    /**
     * 根據客戶端IP搜尋
     */
    public Page<GrpcCallLog> findByClientIp(String clientIp, Pageable pageable) {
        return grpcCallLogRepository.findByClientIpContainingIgnoreCase(clientIp, pageable);
    }
    
    /**
     * 根據目標位置搜尋
     */
    public Page<GrpcCallLog> findByTargetLocation(String targetLocation, Pageable pageable) {
        return grpcCallLogRepository.findByTargetLocationContainingIgnoreCase(targetLocation, pageable);
    }
    
    /**
     * 根據方法名稱搜尋
     */
    public Page<GrpcCallLog> findByMethodName(String methodName, Pageable pageable) {
        return grpcCallLogRepository.findByMethodNameContainingIgnoreCase(methodName, pageable);
    }
    
    /**
     * 根據狀態碼搜尋
     */
    public Page<GrpcCallLog> findByStatusCode(String statusCode, Pageable pageable) {
        return grpcCallLogRepository.findByStatusCodeContainingIgnoreCase(statusCode, pageable);
    }
    
    /**
     * 獲取最近的呼叫記錄
     */
    public List<GrpcCallLog> getRecentLogs() {
        return grpcCallLogRepository.findTop10ByOrderByCallStartTimeDesc();
    }
    
    /**
     * 獲取執行時間最長的呼叫記錄
     */
    public List<GrpcCallLog> getSlowestLogs() {
        return grpcCallLogRepository.findTop10ByOrderByExecutionTimeMsDesc();
    }
    
    /**
     * 獲取統計資訊
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "clientIpStats", grpcCallLogRepository.countCallsByClientIp(),
            "methodNameStats", grpcCallLogRepository.countCallsByMethodName(),
            "statusCodeStats", grpcCallLogRepository.countCallsByStatusCode(),
            "totalCalls", grpcCallLogRepository.count()
        );
    }
    
    /**
     * 清理舊記錄
     */
    public void cleanupOldLogs(LocalDateTime cutoffTime) {
        try {
            grpcCallLogRepository.deleteByCallStartTimeBefore(cutoffTime);
            logger.info("Cleaned up gRPC call logs older than {}", cutoffTime);
        } catch (Exception e) {
            logger.error("Failed to cleanup old gRPC call logs: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根據追蹤ID搜尋
     */
    public Page<GrpcCallLog> findByTraceId(String traceId, Pageable pageable) {
        return grpcCallLogRepository.findByTraceIdContainingIgnoreCase(traceId, pageable);
    }
    
    /**
     * 統計特定時間範圍內的呼叫次數
     */
    public Long countCallsInTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return grpcCallLogRepository.countCallsInTimeRange(startTime, endTime);
    }
    
    /**
     * 統計特定狀態碼的呼叫次數
     */
    public Long countCallsByStatusCode(String statusCode) {
        return grpcCallLogRepository.countCallsByStatusCode(statusCode);
    }
    
    /**
     * 關閉服務
     */
    public void shutdown() {
        asyncExecutor.shutdown();
    }
}