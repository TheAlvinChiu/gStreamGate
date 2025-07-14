package io.github.alvinchiu.gstreamgate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * gRPC 呼叫記錄實體類別
 */
@Entity
@Table(name = "grpc_call_logs")
public class GrpcCallLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;
    
    @Column(name = "target_location", nullable = false)
    private String targetLocation;
    
    @Column(name = "method_name", nullable = false)
    private String methodName;
    
    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs;
    
    @Column(name = "status_code", nullable = false, length = 20)
    private String statusCode;
    
    @Column(name = "request_size_bytes")
    private Long requestSizeBytes = 0L;
    
    @Column(name = "response_size_bytes")
    private Long responseSizeBytes = 0L;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "trace_id", length = 64)
    private String traceId;
    
    @Column(name = "span_id", length = 32)
    private String spanId;
    
    @Column(name = "call_type", length = 20)
    private String callType = "UNARY";
    
    @Column(name = "call_start_time", nullable = false)
    private LocalDateTime callStartTime;
    
    @Column(name = "call_end_time", nullable = false)
    private LocalDateTime callEndTime;
    
    @Column(name = "create_date_time")
    private LocalDateTime createDateTime = LocalDateTime.now();
    
    @Column(name = "version")
    private Integer version = 1;
    
    // 默認構造函數
    public GrpcCallLog() {}
    
    // 構造函數
    public GrpcCallLog(String clientIp, String targetLocation, String methodName, 
                      Long executionTimeMs, String statusCode, LocalDateTime callStartTime, 
                      LocalDateTime callEndTime) {
        this.clientIp = clientIp;
        this.targetLocation = targetLocation;
        this.methodName = methodName;
        this.executionTimeMs = executionTimeMs;
        this.statusCode = statusCode;
        this.callStartTime = callStartTime;
        this.callEndTime = callEndTime;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getTargetLocation() {
        return targetLocation;
    }
    
    public void setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
    
    public String getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    
    public Long getRequestSizeBytes() {
        return requestSizeBytes;
    }
    
    public void setRequestSizeBytes(Long requestSizeBytes) {
        this.requestSizeBytes = requestSizeBytes;
    }
    
    public Long getResponseSizeBytes() {
        return responseSizeBytes;
    }
    
    public void setResponseSizeBytes(Long responseSizeBytes) {
        this.responseSizeBytes = responseSizeBytes;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public String getSpanId() {
        return spanId;
    }
    
    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }
    
    public String getCallType() {
        return callType;
    }
    
    public void setCallType(String callType) {
        this.callType = callType;
    }
    
    public LocalDateTime getCallStartTime() {
        return callStartTime;
    }
    
    public void setCallStartTime(LocalDateTime callStartTime) {
        this.callStartTime = callStartTime;
    }
    
    public LocalDateTime getCallEndTime() {
        return callEndTime;
    }
    
    public void setCallEndTime(LocalDateTime callEndTime) {
        this.callEndTime = callEndTime;
    }
    
    public LocalDateTime getCreateDateTime() {
        return createDateTime;
    }
    
    public void setCreateDateTime(LocalDateTime createDateTime) {
        this.createDateTime = createDateTime;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    @Override
    public String toString() {
        return "GrpcCallLog{" +
                "id=" + id +
                ", clientIp='" + clientIp + '\'' +
                ", targetLocation='" + targetLocation + '\'' +
                ", methodName='" + methodName + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", statusCode='" + statusCode + '\'' +
                ", callType='" + callType + '\'' +
                ", callStartTime=" + callStartTime +
                ", callEndTime=" + callEndTime +
                '}';
    }
}