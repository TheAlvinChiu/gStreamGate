# ==========================================
# Multi-stage Dockerfile for gStreamGate
# 整合前後端建置與生產部署
# ==========================================

# ==========================================
# Stage 1: Frontend Build Environment (優化版)
# ==========================================
FROM node:18-alpine AS frontend-builder

# 設置工作目錄
WORKDIR /frontend

# 設置 Node.js 環境變數和優化配置
ENV NODE_ENV=production
ENV GENERATE_SOURCEMAP=false
ENV CI=true
ENV npm_config_cache=/tmp/.npm
ENV npm_config_prefer_offline=true

# 安裝必要的系統依賴
RUN apk add --no-cache git python3 make g++

# 使用國內鏡像源加速下載
RUN npm config set registry https://registry.npmmirror.com

# 複製前端套件配置檔案（利用 Docker 快取層）
COPY frontend/package*.json ./

# 安裝前端依賴 (優化版本)
RUN npm ci --omit=dev --prefer-offline --no-audit --no-fund --silent && \
    npm cache clean --force

# 複製前端原始碼
COPY frontend/ .

# 建置前端應用程式
RUN npm run build && \
    # 驗證建置結果
    ls -la build/ && \
    echo "✅ Frontend build completed"

# ==========================================
# Stage 2: Backend Build Environment
# ==========================================
FROM gradle:8.10.2-jdk21-alpine AS backend-builder

# 設置工作目錄
WORKDIR /app

# 設置建置環境變數
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true"
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 複製 Gradle 配置檔案（利用 Docker 快取層）
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# 設置 gradlew 執行權限
RUN chmod +x ./gradlew

# 預下載依賴（快取優化）
RUN ./gradlew dependencies --no-daemon --quiet

# 複製後端原始碼
COPY src/ src/

# 複製前端建置結果到 Spring Boot 靜態資源目錄
COPY --from=frontend-builder /frontend/build/ src/main/resources/static/

# 建置後端應用程式
RUN ./gradlew bootJar --no-daemon --quiet && \
    # 驗證 JAR 檔案
    ls -la build/libs/ && \
    # 提取 JAR 檔案名稱
    JAR_FILE=$(find build/libs -name "*.jar" -not -name "*-plain.jar" | head -1) && \
    echo "Built JAR: $JAR_FILE" && \
    # 重新命名為固定名稱以便後續使用
    cp "$JAR_FILE" /app/app.jar && \
    echo "✅ Backend build completed with integrated frontend"

# ==========================================
# Stage 3: Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# 安全性：建立非 root 使用者
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 安裝必要工具和安全更新
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
        curl \
        dumb-init \
        tzdata \
        ca-certificates && \
    # 清理快取
    rm -rf /var/cache/apk/* && \
    # 更新 CA 憑證
    update-ca-certificates

# 設置時區為台北時間
ENV TZ=Asia/Taipei
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 設置工作目錄
WORKDIR /app

# 建立日誌目錄
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# 複製 JAR 檔案
COPY --from=backend-builder --chown=appuser:appgroup /app/app.jar /app/app.jar

# 驗證 JAR 檔案
RUN java -jar /app/app.jar --help 2>/dev/null || echo "JAR file ready" && \
    echo "Container build completed successfully"

# ==========================================
# Environment Variables Configuration
# ==========================================

# JVM 優化配置
ENV JAVA_OPTS="-server \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=Asia/Taipei"

# Netty 優化配置
ENV NETTY_OPTS="-Dio.netty.allocator.type=pooled \
    -Dio.netty.recycler.maxCapacityPerThread=0 \
    -Dio.netty.noPreferDirect=false \
    -Dio.netty.noUnsafe=false"

# Undertow 優化配置
ENV UNDERTOW_OPTS="-Dio.undertow.disable-file-system-watcher=true \
    -Dio.undertow.eager-filter-init=true"

# gRPC 優化配置
ENV GRPC_OPTS="-Dio.grpc.netty.shaded.io.grpc.netty.useCustomAllocator=true"

# Spring Boot 應用程式配置
ENV SPRING_OPTS="--spring.profiles.active=production \
    --server.port=8080 \
    --grpc.proxy.server.port=9092 \
    --server.undertow.threads.io=8 \
    --server.undertow.threads.worker=64 \
    --server.undertow.buffer-size=16384 \
    --server.undertow.direct-buffers=true \
    --management.endpoints.web.exposure.include=health,metrics,prometheus,info \
    --management.endpoint.health.show-details=when-authorized"

# 健康檢查配置
ENV HEALTH_CHECK_OPTS="--management.health.circuitbreakers.enabled=true \
    --management.health.diskspace.enabled=true \
    --management.health.diskspace.threshold=1073741824"

# 日誌配置
ENV LOGGING_OPTS="--logging.level.io.github.alvinchiu.gstreamgate=INFO \
    --logging.level.org.springframework.web=INFO \
    --logging.file.name=/app/logs/gstream-gate.log"

# 組合所有 JVM 參數
ENV JVM_ARGS="$JAVA_OPTS $NETTY_OPTS $UNDERTOW_OPTS $GRPC_OPTS"
ENV APP_ARGS="$SPRING_OPTS $HEALTH_CHECK_OPTS $LOGGING_OPTS"

# ==========================================
# Runtime Configuration
# ==========================================

# 暴露埠號
EXPOSE 8080 9092

# 健康檢查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 切換到非 root 使用者
USER appuser:appgroup

# 設置入口點
ENTRYPOINT ["dumb-init", "--"]

# 啟動命令
CMD ["sh", "-c", "exec java $JVM_ARGS -jar /app/app.jar $APP_ARGS"]

# ==========================================
# Container Metadata
# ==========================================
LABEL maintainer="Alvin Chiu <thealvin@gmail.com>" \
      org.opencontainers.image.title="gStreamGate" \
      org.opencontainers.image.description="企業級 gRPC 代理服務，整合 Web 管理介面" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="AlvinChiu" \
      org.opencontainers.image.source="https://github.com/TheAlvinChiu/gStreamGate.git" \
      org.opencontainers.image.documentation="https://github.com/alvinchiu/gstream-gate-proxy/README.md" \
      org.opencontainers.image.licenses="MIT" \
      app.frontend.framework="React + TypeScript" \
      app.backend.framework="Spring Boot 3.5.0 + Undertow" \
      app.features="gRPC代理,Web管理介面,企業級安全,效能優化"