# ==========================================
# Multi-stage Dockerfile for gRPC Proxy
# Optimized for production with security best practices
# ==========================================

# ==========================================
# Stage 1: Build Environment
# ==========================================
FROM gradle:8.10.2-jdk21-alpine AS builder

# 設置工作目錄
WORKDIR /app

# 設置構建環境變量
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true"
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 複製 Gradle 配置文件（利用 Docker 緩存層）
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# 預下載依賴（緩存優化）
RUN ./gradlew dependencies --no-daemon --quiet

# 複製源代碼
COPY src/ src/

# 構建應用程序
RUN ./gradlew bootJar --no-daemon --quiet && \
    # 驗證 JAR 文件
    ls -la build/libs/ && \
    # 提取 JAR 文件名
    JAR_FILE=$(find build/libs -name "*.jar" -not -name "*-plain.jar" | head -1) && \
    echo "Built JAR: $JAR_FILE" && \
    # 重命名為固定名稱以便後續使用
    cp "$JAR_FILE" /app/app.jar

# ==========================================
# Stage 2: Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# 安全性：創建非 root 用戶
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 安裝必要的工具和安全更新
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
        curl \
        dumb-init \
        tzdata && \
    # 清理緩存
    rm -rf /var/cache/apk/*

# 設置時區
ENV TZ=Asia/Taipei
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 設置工作目錄
WORKDIR /app

# 創建日志目錄
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# 複製 JAR 文件
COPY --from=builder --chown=appuser:appgroup /app/app.jar /app/app.jar

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

# Spring Boot 配置
ENV SPRING_OPTS="--spring.profiles.active=production \
    --server.port=8080 \
    --grpc.proxy.server.port=9091 \
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

# 組合所有 JVM 參數
ENV JVM_ARGS="$JAVA_OPTS $NETTY_OPTS $UNDERTOW_OPTS $GRPC_OPTS"
ENV APP_ARGS="$SPRING_OPTS $HEALTH_CHECK_OPTS"

# 暴露端口
EXPOSE 8080 9091

# 健康檢查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 切換到非 root 用戶
USER appuser:appgroup

# 設置入口點
ENTRYPOINT ["dumb-init", "--"]

# 啟動命令
CMD ["sh", "-c", "exec java $JVM_ARGS -jar /app/app.jar $APP_ARGS"]

# ==========================================
# Metadata
# ==========================================
LABEL maintainer="Alvin Chiu <thealvinchiu@gmail.com>" \
      org.opencontainers.image.title="gRPC Proxy Gateway" \
      org.opencontainers.image.description="High-performance gRPC proxy with Undertow and Spring Boot 3.5.0" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="AlvinChiu" \
      org.opencontainers.image.source="https://github.com/alvinchiu/gstream-gate-proxy" \
      org.opencontainers.image.documentation="https://github.com/alvinchiu/gstream-gate-proxy/README.md"