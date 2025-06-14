# OpenTelemetry 整合指南

gStreamGate 已整合 OpenTelemetry 分散式追蹤功能，提供完整的請求追蹤和可觀測性支援。

## 🚀 功能特性

### 1. 分散式追蹤
- **gRPC 請求追蹤**: 自動追蹤所有 gRPC 代理請求
- **HTTP 請求追蹤**: 追蹤 Web 管理介面的 HTTP 請求
- **跨服務關聯**: 支援 W3C Trace Context 標準
- **自訂 Span**: 業務邏輯和組件級別的追蹤

### 2. 多種導出器支援
- **Jaeger**: 企業級分散式追蹤系統
- **OTLP**: OpenTelemetry 標準協定
- **日誌**: 開發環境除錯用途

### 3. 智慧採樣
- **開發環境**: 100% 採樣，完整追蹤
- **生產環境**: 10% 採樣，平衡效能與可觀測性

## 📋 快速開始

### 1. 使用 Docker Compose 啟動

```bash
# 啟動包含 Jaeger 的完整環境
docker-compose -f docker-compose-jaeger.yml up -d

# 檢查服務狀態
docker-compose -f docker-compose-jaeger.yml ps
```

### 2. 訪問 Jaeger UI

打開瀏覽器訪問: http://localhost:16686

在 Jaeger UI 中可以：
- 搜尋追蹤記錄
- 查看請求延遲分佈
- 分析錯誤和異常
- 監控服務依賴關係

### 3. 測試 gRPC 追蹤

```bash
# 使用 grpcurl 測試代理請求
grpcurl -plaintext localhost:9092 list

# 查看生成的追蹤資料
# 在 Jaeger UI 中搜尋 "gstream-gate-proxy" 服務
```

## ⚙️ 配置說明

### 1. 基本配置 (application.yml)

```yaml
opentelemetry:
  service:
    name: gstream-gate-proxy
    version: 1.0.0
  
  traces:
    enabled: true
  
  sampler:
    probability: 1.0  # 開發環境 100% 採樣
  
  exporter:
    type: logging  # logging, jaeger, otlp
    jaeger:
      endpoint: http://localhost:14250
    otlp:
      endpoint: http://localhost:4317
```

### 2. 環境變數配置

```bash
# 導出器類型
export OPENTELEMETRY_EXPORTER_TYPE=jaeger

# Jaeger 端點
export JAEGER_ENDPOINT=http://jaeger:14250

# OTLP 端點
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

# 採樣率 (0.0-1.0)
export OPENTELEMETRY_SAMPLER_PROBABILITY=0.1
```

## 🔧 進階配置

### 1. OpenTelemetry Collector

使用 OpenTelemetry Collector 作為中間層：

```yaml
# monitoring/otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [jaeger]
```

### 2. 自訂 Span 標籤

```java
// 使用 TracingUtils 添加自訂標籤
@Autowired
private TracingUtils tracingUtils;

public void someBusinessLogic() {
    Span span = tracingUtils.createConnectionPoolSpan("get_connection", "target-1");
    
    tracingUtils.executeWithSpan(span, "get_connection", () -> {
        // 業務邏輯
        tracingUtils.addSpanAttributes("pool.size", 10);
        tracingUtils.addSpanAttributes("target.healthy", true);
        
        return connectionPool.getConnection();
    });
}
```

### 3. 錯誤追蹤

```java
try {
    // 可能拋出異常的程式碼
    riskyOperation();
} catch (Exception e) {
    // 自動添加錯誤資訊到當前 Span
    tracingUtils.addSpanError(e);
    throw e;
}
```

## 📊 追蹤資料分析

### 1. 關鍵指標

在 Jaeger UI 中關注以下指標：

- **請求延遲**: P50, P95, P99 延遲分佈
- **錯誤率**: 失敗請求比例
- **吞吐量**: 每秒處理的請求數
- **服務依賴**: 上下游服務調用關係

### 2. 常見追蹤模式

#### gRPC 代理請求
```
gRPC Client → gstream-gate-proxy → Upstream gRPC Service
    ↓              ↓                    ↓
  Span A        Span B              Span C
```

#### Web 管理請求
```
Browser → gstream-gate-proxy → Database
   ↓           ↓                  ↓
 Span A      Span B            Span C
```

### 3. Span 屬性說明

| 屬性名稱 | 描述 | 範例值 |
|---------|------|-------|
| `rpc.service` | gRPC 服務名稱 | `grpc.health.v1.Health` |
| `rpc.method` | gRPC 方法名稱 | `Check` |
| `grpc.status_code` | gRPC 狀態碼 | `0` (OK) |
| `target.key` | 代理目標標識 | `backend-service-1` |
| `connection_pool.size` | 連線池大小 | `8` |
| `circuit_breaker.state` | 熔斷器狀態 | `CLOSED` |

## 🛠️ 故障排除

### 1. 追蹤資料缺失

檢查配置：
```bash
# 檢查服務是否啟用追蹤
curl http://localhost:8080/actuator/configprops | grep opentelemetry

# 檢查 Jaeger 連線
curl http://localhost:14250/api/traces
```

### 2. 效能影響

調整採樣率：
```yaml
opentelemetry:
  sampler:
    probability: 0.1  # 降低至 10%
```

### 3. 記憶體使用

優化批次處理：
```yaml
# monitoring/otel-collector-config.yaml
processors:
  batch:
    timeout: 1s
    send_batch_size: 512
    send_batch_max_size: 1024
```

## 🔗 相關連結

- [OpenTelemetry 官方文件](https://opentelemetry.io/docs/)
- [Jaeger 官方文件](https://www.jaegertracing.io/docs/)
- [gRPC Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#grpc)

## 📝 最佳實踐

### 1. 採樣策略
- **開發環境**: 100% 採樣，便於除錯
- **測試環境**: 50% 採樣，平衡效能與覆蓋率
- **生產環境**: 10% 採樣，減少效能影響

### 2. Span 命名
- 使用描述性名稱: `grpc.client/Health.Check`
- 包含操作類型: `connection_pool.get_connection`
- 避免高基數標籤: 不要包含 UUID 或時間戳

### 3. 錯誤處理
- 所有異常都應標記 Span 狀態為 ERROR
- 包含錯誤訊息和類型
- 記錄關鍵的上下文資訊

### 4. 效能考量
- 使用適當的採樣率
- 避免在熱路徑創建過多 Span
- 定期清理過期的追蹤資料