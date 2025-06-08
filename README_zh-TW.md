# gStreamGate - 以 Java 為基礎的 gRPC 代理閘道

<div align="center">

![gStreamGate Logo](/logos/logo.webp)

**企業級 gRPC 代理閘道與智慧管理平台**

[![建置狀態](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/alvinchiu/gstream-gate) [![版本](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/alvinchiu/gstream-gate/releases) [![授權條款](https://img.shields.io/badge/license-MIT-green)](LICENSE) [![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)](https://spring.io/projects/spring-boot) [![React](https://img.shields.io/badge/React-18.2.0-blue)](https://reactjs.org/) [![Docker](https://img.shields.io/badge/Docker-ready-blue)](https://www.docker.com/)

[快速開始](#快速開始) • [使用說明](#使用說明) • [API 參考](#api-參考) • [參與貢獻](#參與貢獻)

**語言：** [English](README.md) | 繁體中文 | [日本語](README_ja.md)

</div>

## 💖 支持本專案

如果您覺得 gStreamGate 對您有幫助，並且想要支持其開發，歡迎您進行捐贈：

<div align="center">

**USDT 捐贈 (TRC20)**

[![捐贈 USDT](https://img.shields.io/badge/捐贈-USDT%20TRC20-green?style=for-the-badge&logo=tether)](https://tronscan.org/#/address/TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC)

```
TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC
```

*您的支持有助於維護和改進這個開源專案！🙏*

</div>

---


## 專案概述

gStreamGate 是一個精密且適合企業使用的 gRPC 代理閘道，提供智慧流量管理、自適應效能優化與全面監控功能。採用 Spring Boot 3.5 與 React 18 建構，具備現代化網頁介面，可管理 gRPC 服務代理，並包含熔斷器、自適應逾時與智慧流量控制等進階功能。

### 核心特色

- 🚀 **高效能 gRPC 代理** - 使用 Undertow 網頁伺服器進行高效請求路由
- 🧠 **自適應逾時管理** - 根據呼叫模式自動調整逾時設定
- 🔄 **智慧流量控制** - 針對串流 RPC 進行智慧訊息流最佳化
- ⚡ **熔斷器模式** - 防範級聯故障的保護機制
- 🔐 **JWT 認證** - 具角色權限控制的安全 REST API
- 👥 **使用者管理系統** - 完整的 CRUD 操作與角色權限管理
- 🎯 **即時監控** - 整合 Prometheus 的全面性指標監控
- 🌐 **現代化網頁介面** - 基於 React 的管理儀表板與使用者管理
- 🐳 **Docker 就緒** - 完整容器化與多階段建置
- 📊 **效能最佳化** - 記憶體池、連線池與資源管理

### 使用場景

- **微服務閘道** - gRPC 微服務的中央進入點
- **負載平衡** - 智慧流量分配至後端服務
- **服務網格整合** - 增強可觀察性與控制平面功能
- **開發與測試** - 開發環境的本地代理
- **正式環境流量管理** - 企業級代理與監控

## 系統架構

```mermaid
graph TB
    Client[gRPC 客戶端] --> LB[負載平衡器]
    LB --> Proxy[gStreamGate 代理]
    
    subgraph "gStreamGate 元件"
        Proxy --> ATM[自適應逾時管理器]
        Proxy --> SFC[智慧流量控制]
        Proxy --> CB[熔斷器]
        Proxy --> MM[記憶體管理器]
        
        Web[網頁介面] --> API[REST API]
        API --> Auth[JWT 認證]
        API --> PM[代理管理器]
    end
    
    Proxy --> Backend1[gRPC 服務 1]
    Proxy --> Backend2[gRPC 服務 2]
    Proxy --> Backend3[gRPC 服務 N]
    
    Metrics[指標匯出] --> Prometheus[Prometheus]
    Prometheus --> Grafana[Grafana 儀表板]
```

### 技術堆疊

**後端：**

- Java 21 搭配 Spring Boot 3.5.0
- Undertow 網頁伺服器（效能最佳化）
- gRPC 1.68.1 搭配 Netty 傳輸層
- H2 資料庫（開發）/ PostgreSQL（正式環境）
- Spring Security 的 JWT 認證
- Micrometer 指標搭配 Prometheus

**前端：**

- React 18.2.0 搭配 TypeScript
- Tailwind CSS 樣式設計
- Lucide React 圖示
- 響應式設計與現代 UI/UX

**基礎設施：**

- Docker 多階段建置
- Prometheus 與 Grafana 監控
- 健康檢查與可觀察性
- 正式環境就緒配置

## 快速開始

### 系統需求

- Docker 20.10+ 與 Docker Compose 2.0+
- 2GB+ 可用記憶體
- 埠號 8080、9092 可用

### Docker 快速啟動（建議）

```bash
# 複製專案倉庫
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# 使用 Docker 建置並執行
docker build -t gstreamgate:latest .
docker run -d --name gstream-gate -p 8080:8080 -p 9092:9092 gstreamgate:latest
```

**存取點：**

- 網頁介面：http://localhost:8080
- gRPC 代理：localhost:9092
- 健康檢查：http://localhost:8080/actuator/health
- 指標：http://localhost:8080/actuator/prometheus

**預設帳戶：**

- 管理員：`admin` / `password`
- 使用者：`user` / `password`

### 本地開發

```bash
# 後端（需要 Java 21+）
./gradlew bootRun

# 前端（需要 Node.js 18+）
cd frontend
npm install
npm start
```

## 安裝與部署

### Docker 部署（正式環境）

```bash
# 正式環境部署搭配外部資料庫
docker run -d \
  --name gstream-gate \
  -p 8080:8080 \
  -p 9092:9092 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e DB_USERNAME=gstreamgate \
  -e DB_PASSWORD=your_secure_password \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://your-db:5432/gstreamgate \
  gstreamgate:latest
```

### 手動安裝

```bash
# 建置應用程式
./gradlew clean build

# 執行 JAR 檔案
java -jar build/libs/gstream-gate-proxy-*.jar \
  --spring.profiles.active=production \
  --server.port=8080 \
  --grpc.proxy.server.port=9092
```

### 環境變數配置

主要環境變數：

```env
# 資料庫配置
DB_USERNAME=gstreamgate
DB_PASSWORD=secure_password
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gstreamgate

# 應用程式設定
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
GRPC_PROXY_SERVER_PORT=9092

# 安全性
JWT_SECRET=your_jwt_secret_key_here
JWT_EXPIRATION=86400000

# 效能調校
JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

## 設定配置

### 代理對應配置

透過網頁介面或 REST API 配置代理對應：

```json
{
  "serviceName": "user-service",
  "proxyHostName": "users.api.com",
  "targetHostName": "users-backend.internal",
  "targetPort": 9090,
  "secureMode": "AUTO",
  "connectTimeoutMs": 5000,
  "sendTimeoutMs": 10000,
  "readTimeoutMs": 30000,
  "enable": "Y"
}
```

### 安全模式

- **AUTO**：自動偵測 TLS 支援
- **SECURE**：強制 TLS 加密
- **PLAINTEXT**：使用明文 HTTP/2

### 效能調校

```yaml
# application.yml
server:
  undertow:
    threads:
      io: 16
      worker: 128
    buffer-size: 32768
    direct-buffers: true

app:
  connectionPool:
    maxConnectionsPerTarget: 16
  circuitBreaker:
    failureThreshold: 5
    waitDurationSeconds: 60
```

## 使用說明

### 網頁介面

1. **存取儀表板**：瀏覽至 http://localhost:8080
2. **登入**：使用 admin/password 取得完整存取權限
3. **管理代理**：建立、編輯與監控代理配置
4. **使用者管理**：管理員可管理使用者帳戶、角色與權限
5. **檢視指標**：監控系統健康狀態與效能

### REST API 範例

```bash
# 認證
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# 建立代理對應
curl -X POST http://localhost:8080/api/proxy \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "my-service",
    "proxyHostName": "api.example.com",
    "targetHostName": "backend.internal",
    "targetPort": 8080,
    "secureMode": "AUTO",
    "enable": "Y"
  }'

# 列出所有代理
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/proxy

# 建立新使用者（僅管理員）
curl -X POST http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "SecurePass123!",
    "email": "user@example.com",
    "role": "USER",
    "enabled": true
  }'

# 列出所有使用者（含分頁）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users?page=0&size=10"

# 依關鍵字搜尋使用者
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users/search?keyword=john&page=0&size=10"
```

### gRPC 客戶端配置

設定您的 gRPC 客戶端透過代理連線：

```java
// Java gRPC 客戶端範例
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9092)
    .usePlaintext() // 或在配置時使用 TLS
    .build();

// 您的服務存根
YourServiceGrpc.YourServiceBlockingStub stub = 
    YourServiceGrpc.newBlockingStub(channel);
```

## 開發

### 開發環境設定

```bash
# 複製與設定
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# 後端開發
./gradlew bootRun  # 啟動於埠號 8080

# 前端開發（另開終端）
cd frontend
npm install
npm start  # 啟動於埠號 3000
```

### 從原始碼建置

```bash
# 完整建置包含前端
./gradlew clean build

# 僅後端
./gradlew clean bootJar

# 執行測試
./gradlew test

# 產生測試覆蓋率報告
./gradlew jacocoTestReport
```

### 程式碼品質

```bash
# 安全性掃描
./gradlew dependencyCheckAnalyze

# 驗證相依性
./gradlew verifyDependencies

# 檢查建置資訊
./gradlew buildInfo
```

## 監控與維運

### 健康檢查

```bash
# 應用程式健康狀態
curl http://localhost:8080/actuator/health

# 詳細健康狀態（需認證）
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/health
```

### 指標收集

應用程式匯出 Prometheus 格式指標：

```bash
# Prometheus 指標端點
curl http://localhost:8080/actuator/prometheus
```

主要指標包括：

- `grpc_proxy_requests_total` - 代理請求總數
- `grpc_proxy_request_duration` - 請求持續時間
- `grpc_proxy_connections_active` - 活躍連線數
- `jvm_memory_used_bytes` - 記憶體使用量

### 日誌記錄

日誌結構化包含：

- 具唯一呼叫 ID 的請求/回應追蹤
- 效能指標
- 錯誤詳細資訊與堆疊追蹤
- 安全事件

```bash
# 檢視 Docker 日誌
docker logs gstream-gate

# 追蹤日誌
docker logs -f gstream-gate
```

## API 參考

### 認證端點

| 方法 | 端點 | 說明 |
|------|------|------|
| POST | `/api/auth/login` | 使用者認證 |
| POST | `/api/auth/logout` | 使用者登出 |
| POST | `/api/auth/register` | 使用者註冊 |
| GET | `/api/auth/me` | 目前使用者資訊 |

### 代理管理端點

| 方法 | 端點 | 說明 | 權限需求 |
|------|------|------|----------|
| GET | `/api/proxy` | 列出所有代理 | USER/ADMIN |
| GET | `/api/proxy/enabled` | 列出啟用的代理 | USER/ADMIN |
| POST | `/api/proxy` | 建立代理 | ADMIN |
| PUT | `/api/proxy/{id}` | 更新代理 | ADMIN |
| DELETE | `/api/proxy/{id}` | 刪除代理 | ADMIN |
| PATCH | `/api/proxy/{id}/status` | 切換代理狀態 | ADMIN |
| POST | `/api/proxy/refresh` | 重新整理所有代理 | ADMIN |

### 使用者管理端點

| 方法 | 端點 | 說明 | 權限需求 |
|------|------|------|----------|
| GET | `/api/admin/users` | 列出所有使用者 | ADMIN |
| GET | `/api/admin/users/{id}` | 依 ID 取得使用者 | ADMIN |
| POST | `/api/admin/users` | 建立新使用者 | ADMIN |
| PUT | `/api/admin/users/{id}` | 更新使用者 | ADMIN |
| DELETE | `/api/admin/users/{id}` | 刪除使用者 | ADMIN |
| PUT | `/api/admin/users/{id}/enable` | 啟用使用者帳戶 | ADMIN |
| PUT | `/api/admin/users/{id}/disable` | 停用使用者帳戶 | ADMIN |
| PUT | `/api/admin/users/{id}/role` | 更新使用者角色 | ADMIN |
| GET | `/api/admin/users/search` | 依關鍵字搜尋使用者 | ADMIN |

### 回應格式

```json
{
  "proxyMapId": 1,
  "serviceName": "user-service",
  "proxyHostName": "users.api.com",
  "targetHostName": "users-backend.internal",
  "targetPort": 9090,
  "connectTimeoutMs": 5000,
  "sendTimeoutMs": 10000,
  "readTimeoutMs": 30000,
  "secureMode": "AUTO",
  "enable": "Y",
  "createDateTime": "2025-06-06T10:30:00",
  "createUser": "admin"
}
```

## 安全性

### 認證與授權

- **基於 JWT 的認證**，可配置過期時間
- **角色權限控制**（USER/ADMIN 角色）
- **BCrypt 安全密碼雜湊**
- **CORS 跨來源保護**

### TLS 配置

```yaml
# 啟用代理伺服器 TLS
grpc:
  proxy:
    tls:
      enabled: true
      certContent: |
        -----BEGIN CERTIFICATE-----
        ...
        -----END CERTIFICATE-----
      keyContent: |
        -----BEGIN PRIVATE KEY-----
        ...
        -----END PRIVATE KEY-----
```

### 安全最佳實務

1. **更改預設密碼**於正式環境
2. **使用強化 JWT 密鑰**（最少 256 位元）
3. **啟用 TLS**於所有外部通訊
4. **定期安全更新**透過相依性掃描
5. **監控存取日誌**以發現可疑活動

## 效能與最佳化

### 資源需求

**最低需求：**

- CPU：2 核心
- RAM：2GB
- 儲存：5GB

**建議配置（正式環境）：**

- CPU：4+ 核心
- RAM：4GB+
- 儲存：20GB+

### 效能調校

```yaml
# 高效能配置
server:
  undertow:
    threads:
      io: 16        # 2x CPU 核心數
      worker: 128   # 8x CPU 核心數
    buffer-size: 32768
    direct-buffers: true

app:
  connectionPool:
    maxConnectionsPerTarget: 16
  memory:
    cacheMaxSize: 5000
```

### 擴展考量

- **水平擴展**：在負載平衡器後方部署多個實例
- **資料庫擴展**：使用連線池與讀取副本
- **記憶體最佳化**：根據負載調整 JVM 堆疊大小
- **網路最佳化**：使用適當的緩衝區大小

## 疑難排解

### 常見問題

**1. 連線被拒絕**

```bash
# 檢查代理是否執行
curl http://localhost:8080/actuator/health

# 驗證 gRPC 埠號
netstat -tlnp | grep 9092
```

**2. 認證失敗**

```bash
# 驗證 JWT 權杖
curl -X POST http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

**3. 記憶體使用量過高**

```bash
# 檢查記憶體指標
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 啟用記憶體最佳化
export JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

### 除錯模式

啟用除錯日誌：

```yaml
logging:
  level:
    io.github.alvinchiu.gstreamgate: DEBUG
    org.springframework.security: DEBUG
```

### 支援資源

- **GitHub Issues**：[回報錯誤與功能請求](https://github.com/alvinchiu/gstream-gate/issues)
- **文件**：檢查 `/docs` 目錄
- **監控**：使用 Prometheus/Grafana 儀表板

## 參與貢獻

我們歡迎貢獻！請參閱我們的[貢獻指南](CONTRIBUTING.md)以瞭解詳細資訊。

### 開發流程

1. Fork 專案倉庫
2. 建立功能分支（`git checkout -b feature/amazing-feature`）
3. 提交您的變更（`git commit -m 'Add amazing feature'`）
4. 推送至分支（`git push origin feature/amazing-feature`）
5. 開啟 Pull Request

### 程式碼風格

- **Java**：遵循 Google Java Style Guide
- **React**：使用 ESLint 與 Prettier 配置
- **測試**：維持 >80% 程式碼覆蓋率
- **文件**：更新 README 與程式碼註解

## 授權條款與致謝

### 授權條款

本專案採用 MIT 授權條款 - 詳見 [LICENSE](LICENSE) 檔案。

### 致謝

- **作者**：Alvin Chiu ([@thealvin](https://github.com/thealvin))
- **貢獻者**：參見 [CONTRIBUTORS.md](CONTRIBUTORS.md)
- **技術支援**：Spring Boot、React、gRPC 與優秀的開源社群

### 感謝

- Spring Boot 團隊提供優秀的框架
- gRPC 團隊提供強大的 RPC 框架
- React 團隊提供出色的 UI 函式庫
- 所有專案貢獻者與使用者

## 💖 捐贈支持

如果本專案對您有所幫助，請考慮支持其持續開發：

**USDT (TRC20)：** `TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC`

您的慷慨贊助有助於保持專案的活力與發展！🙏

---

<div align="center">

**用 ❤️ 為 gRPC 社群而生**

[⬆ 回到頂端](#gstreamgate)

</div>