package io.github.alvinchiu.gstreamgate.controller;

import io.github.alvinchiu.gstreamgate.entity.GrpcCallLog;
import io.github.alvinchiu.gstreamgate.service.GrpcCallLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * gRPC 呼叫記錄 REST API 控制器
 */
@RestController
@RequestMapping("/api/grpc-logs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GrpcCallLogController {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcCallLogController.class);
    
    private final GrpcCallLogService grpcCallLogService;
    
    @Autowired
    public GrpcCallLogController(GrpcCallLogService grpcCallLogService) {
        this.grpcCallLogService = grpcCallLogService;
    }
    
    /**
     * 獲取所有 gRPC 呼叫記錄（分頁）
     */
    @GetMapping
    public ResponseEntity<Page<GrpcCallLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "callStartTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<GrpcCallLog> logs = grpcCallLogService.getAllLogs(pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving gRPC call logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 搜尋 gRPC 呼叫記錄
     */
    @GetMapping("/search")
    public ResponseEntity<Page<GrpcCallLog>> searchLogs(
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String targetLocation,
            @RequestParam(required = false) String methodName,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) String callType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "callStartTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            
            Pageable pageable = PageRequest.of(page, size, sort);
            
            // 處理空字符串為 null
            clientIp = (clientIp != null && clientIp.trim().isEmpty()) ? null : clientIp;
            targetLocation = (targetLocation != null && targetLocation.trim().isEmpty()) ? null : targetLocation;
            methodName = (methodName != null && methodName.trim().isEmpty()) ? null : methodName;
            statusCode = (statusCode != null && statusCode.trim().isEmpty()) ? null : statusCode;
            callType = (callType != null && callType.trim().isEmpty()) ? null : callType;
            
            Page<GrpcCallLog> logs = grpcCallLogService.searchLogs(
                clientIp, targetLocation, methodName, statusCode, callType, 
                startTime, endTime, pageable);
            
            logger.debug("Search query: clientIp={}, targetLocation={}, methodName={}, statusCode={}, callType={}, startTime={}, endTime={}", 
                        clientIp, targetLocation, methodName, statusCode, callType, startTime, endTime);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error searching gRPC call logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根據客戶端 IP 搜尋
     */
    @GetMapping("/by-client-ip")
    public ResponseEntity<Page<GrpcCallLog>> getLogsByClientIp(
            @RequestParam String clientIp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("callStartTime").descending());
            Page<GrpcCallLog> logs = grpcCallLogService.findByClientIp(clientIp, pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving logs by client IP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根據目標位置搜尋
     */
    @GetMapping("/by-target-location")
    public ResponseEntity<Page<GrpcCallLog>> getLogsByTargetLocation(
            @RequestParam String targetLocation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("callStartTime").descending());
            Page<GrpcCallLog> logs = grpcCallLogService.findByTargetLocation(targetLocation, pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving logs by target location: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根據方法名稱搜尋
     */
    @GetMapping("/by-method-name")
    public ResponseEntity<Page<GrpcCallLog>> getLogsByMethodName(
            @RequestParam String methodName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("callStartTime").descending());
            Page<GrpcCallLog> logs = grpcCallLogService.findByMethodName(methodName, pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving logs by method name: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根據狀態碼搜尋
     */
    @GetMapping("/by-status-code")
    public ResponseEntity<Page<GrpcCallLog>> getLogsByStatusCode(
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("callStartTime").descending());
            Page<GrpcCallLog> logs = grpcCallLogService.findByStatusCode(statusCode, pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving logs by status code: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根據追蹤 ID 搜尋
     */
    @GetMapping("/by-trace-id")
    public ResponseEntity<Page<GrpcCallLog>> getLogsByTraceId(
            @RequestParam String traceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("callStartTime").descending());
            Page<GrpcCallLog> logs = grpcCallLogService.findByTraceId(traceId, pageable);
            
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving logs by trace ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 獲取最近的呼叫記錄
     */
    @GetMapping("/recent")
    public ResponseEntity<List<GrpcCallLog>> getRecentLogs() {
        try {
            List<GrpcCallLog> logs = grpcCallLogService.getRecentLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving recent logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 獲取執行時間最長的呼叫記錄
     */
    @GetMapping("/slowest")
    public ResponseEntity<List<GrpcCallLog>> getSlowestLogs() {
        try {
            List<GrpcCallLog> logs = grpcCallLogService.getSlowestLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error retrieving slowest logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 獲取統計資訊
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> statistics = grpcCallLogService.getStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("Error retrieving statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 統計特定時間範圍內的呼叫次數
     */
    @GetMapping("/count-by-time-range")
    public ResponseEntity<Long> countCallsInTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        try {
            Long count = grpcCallLogService.countCallsInTimeRange(startTime, endTime);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            logger.error("Error counting calls in time range: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 統計特定狀態碼的呼叫次數
     */
    @GetMapping("/count-by-status-code")
    public ResponseEntity<Long> countCallsByStatusCode(@RequestParam String statusCode) {
        try {
            Long count = grpcCallLogService.countCallsByStatusCode(statusCode);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            logger.error("Error counting calls by status code: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 清理舊記錄
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<String> cleanupOldLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoffTime) {
        
        try {
            grpcCallLogService.cleanupOldLogs(cutoffTime);
            return ResponseEntity.ok("Old logs cleaned up successfully");
        } catch (Exception e) {
            logger.error("Error cleaning up old logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error cleaning up old logs: " + e.getMessage());
        }
    }
}