#!/bin/bash

# ==========================================
# gRPC Proxy Docker 構建和部署腳本
# ==========================================

set -e  # 錯誤時退出

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置變數
APP_NAME="gstream-gate-proxy"
IMAGE_NAME="gstream-gate-proxy"
TAG="${1:-latest}"
REGISTRY="${DOCKER_REGISTRY:-}"
BUILD_ARGS=""

# 函數定義
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_usage() {
    echo "Usage: $0 [TAG] [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build          構建 Docker 鏡像 (默認)"
    echo "  push           推送鏡像到倉庫"
    echo "  run            運行容器"
    echo "  stop           停止容器"
    echo "  clean          清理鏡像和容器"
    echo "  compose-up     使用 docker-compose 啟動服務"
    echo "  compose-down   使用 docker-compose 停止服務"
    echo "  logs           查看容器日志"
    echo "  health         檢查容器健康狀態"
    echo ""
    echo "Examples:"
    echo "  $0 v1.0.0 build"
    echo "  $0 latest push"
    echo "  $0 compose-up"
}

# 檢查 Docker 是否可用
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安裝或不可用"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon 未運行"
        exit 1
    fi
}

# 檢查 docker-compose 是否可用
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        log_warning "docker-compose 未安裝，嘗試使用 docker compose"
        if ! docker compose version &> /dev/null; then
            log_error "docker-compose 和 docker compose 都不可用"
            exit 1
        fi
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi
}

# 構建前準備
prepare_build() {
    log_info "準備構建環境..."

    # 創建必要的目錄
    mkdir -p logs
    mkdir -p monitoring/grafana/{dashboards,provisioning}
    mkdir -p nginx/ssl
    mkdir -p init-scripts

    # 檢查 Gradle 包裝器
    if [[ ! -f "./gradlew" ]]; then
        log_error "gradlew 文件不存在，請確保在項目根目錄運行此腳本"
        exit 1
    fi

    # 確保 gradlew 可執行
    chmod +x ./gradlew

    log_success "構建環境準備完成"
}

# 構建 Docker 鏡像
build_image() {
    log_info "開始構建 Docker 鏡像: ${IMAGE_NAME}:${TAG}"

    prepare_build

    # 構建參數
    BUILD_ARGS="--build-arg BUILDKIT_INLINE_CACHE=1"
    BUILD_ARGS="$BUILD_ARGS --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    BUILD_ARGS="$BUILD_ARGS --build-arg VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"

    # 使用 BuildKit 進行多階段構建
    DOCKER_BUILDKIT=1 docker build \
        $BUILD_ARGS \
        -t "${IMAGE_NAME}:${TAG}" \
        -t "${IMAGE_NAME}:latest" \
        .

    log_success "Docker 鏡像構建完成: ${IMAGE_NAME}:${TAG}"

    # 顯示鏡像信息
    docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
}

# 推送鏡像
push_image() {
    if [[ -z "$REGISTRY" ]]; then
        log_error "請設置 DOCKER_REGISTRY 環境變數"
        exit 1
    fi

    local full_image="${REGISTRY}/${IMAGE_NAME}:${TAG}"

    log_info "推送鏡像到倉庫: $full_image"

    # 標記鏡像
    docker tag "${IMAGE_NAME}:${TAG}" "$full_image"

    # 推送鏡像
    docker push "$full_image"

    # 如果是 latest 標籤，也推送 latest
    if [[ "$TAG" != "latest" ]]; then
        docker tag "${IMAGE_NAME}:${TAG}" "${REGISTRY}/${IMAGE_NAME}:latest"
        docker push "${REGISTRY}/${IMAGE_NAME}:latest"
    fi

    log_success "鏡像推送完成: $full_image"
}

# 運行容器
run_container() {
    log_info "啟動容器: ${APP_NAME}"

    # 停止現有容器
    docker stop "${APP_NAME}" 2>/dev/null || true
    docker rm "${APP_NAME}" 2>/dev/null || true

    # 運行新容器
    docker run -d \
        --name "${APP_NAME}" \
        --restart unless-stopped \
        -p 8080:8080 \
        -p 50051:50051 \
        -v "${PWD}/logs:/app/logs" \
        -e SPRING_PROFILES_ACTIVE=production \
        -e TZ=Asia/Taipei \
        --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
        --health-interval=30s \
        --health-timeout=10s \
        --health-retries=3 \
        --health-start-period=60s \
        "${IMAGE_NAME}:${TAG}"

    log_success "容器啟動完成: ${APP_NAME}"

    # 等待健康檢查
    log_info "等待應用程序啟動..."
    sleep 10

    # 顯示容器狀態
    docker ps -f "name=${APP_NAME}" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# 停止容器
stop_container() {
    log_info "停止容器: ${APP_NAME}"

    docker stop "${APP_NAME}" 2>/dev/null || log_warning "容器可能已經停止"
    docker rm "${APP_NAME}" 2>/dev/null || log_warning "容器可能已經刪除"

    log_success "容器已停止並刪除"
}

# 清理資源
clean_resources() {
    log_info "清理 Docker 資源..."

    # 停止並刪除容器
    stop_container

    # 刪除鏡像
    docker rmi "${IMAGE_NAME}:${TAG}" 2>/dev/null || log_warning "鏡像可能不存在"
    docker rmi "${IMAGE_NAME}:latest" 2>/dev/null || log_warning "latest 鏡像可能不存在"

    # 清理構建緩存
    docker builder prune -f

    log_success "資源清理完成"
}

# Docker Compose 啟動
compose_up() {
    check_docker_compose

    log_info "使用 Docker Compose 啟動服務..."

    # 創建監控配置文件
    create_monitoring_configs

    # 啟動服務
    $COMPOSE_CMD up -d --build

    log_success "服務啟動完成"

    # 顯示服務狀態
    $COMPOSE_CMD ps

    log_info "服務訪問地址:"
    echo "  - 應用程序: http://localhost:8080"
    echo "  - gRPC 端口: localhost:50051"
    echo "  - 健康檢查: http://localhost:8080/actuator/health"
    echo "  - Prometheus: http://localhost:9090"
    echo "  - Grafana: http://localhost:3000 (admin/AdminPassword123!)"
}

# Docker Compose 停止
compose_down() {
    check_docker_compose

    log_info "使用 Docker Compose 停止服務..."

    $COMPOSE_CMD down -v

    log_success "服務已停止"
}

# 查看日志
show_logs() {
    if docker ps -q -f "name=${APP_NAME}" > /dev/null; then
        log_info "顯示容器日志: ${APP_NAME}"
        docker logs -f "${APP_NAME}"
    else
        log_error "容器 ${APP_NAME} 未運行"
        exit 1
    fi
}

# 健康檢查
check_health() {
    if docker ps -q -f "name=${APP_NAME}" > /dev/null; then
        log_info "檢查容器健康狀態: ${APP_NAME}"

        # 檢查容器狀態
        local health_status=$(docker inspect --format='{{.State.Health.Status}}' "${APP_NAME}" 2>/dev/null || echo "no-healthcheck")

        echo "容器健康狀態: $health_status"

        # 檢查應用程序健康端點
        if curl -s -f http://localhost:8080/actuator/health > /dev/null; then
            log_success "應用程序健康檢查通過"
            curl -s http://localhost:8080/actuator/health | jq . 2>/dev/null || curl -s http://localhost:8080/actuator/health
        else
            log_error "應用程序健康檢查失敗"
        fi

        # 顯示容器資源使用情況
        docker stats "${APP_NAME}" --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}"
    else
        log_error "容器 ${APP_NAME} 未運行"
        exit 1
    fi
}

# 創建監控配置文件
create_monitoring_configs() {
    # Prometheus 配置
    mkdir -p monitoring
    cat > monitoring/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'grpc-proxy'
    static_configs:
      - targets: ['grpc-proxy:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
EOF

    # Grafana 數據源配置
    mkdir -p monitoring/grafana/provisioning/datasources
    cat > monitoring/grafana/provisioning/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
EOF

    log_success "監控配置文件創建完成"
}

# 主邏輯
main() {
    local command="${2:-build}"

    # 檢查 Docker
    check_docker

    case "$command" in
        "build")
            build_image
            ;;
        "push")
            push_image
            ;;
        "run")
            run_container
            ;;
        "stop")
            stop_container
            ;;
        "clean")
            clean_resources
            ;;
        "compose-up")
            compose_up
            ;;
        "compose-down")
            compose_down
            ;;
        "logs")
            show_logs
            ;;
        "health")
            check_health
            ;;
        "help"|"-h"|"--help")
            show_usage
            ;;
        *)
            log_error "未知命令: $command"
            show_usage
            exit 1
            ;;
    esac
}

# 檢查參數
if [[ $# -eq 0 ]]; then
    show_usage
    exit 0
fi

# 執行主函數
main "$@"