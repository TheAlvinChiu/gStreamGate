#!/bin/bash

# ==========================================
# gStreamGate Docker 建置腳本
# ==========================================

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置變數
IMAGE_NAME="gstreamgate"
TAG="${1:-latest}"
DOCKERFILE="${2:-Dockerfile}"

# 日誌函數
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

# 檢查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安裝"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon 未運行"
        exit 1
    fi
    
    log_success "Docker 檢查通過"
}

# 準備建置環境
prepare_build() {
    log_info "準備建置環境..."

    # 檢查 Dockerfile
    if [[ ! -f "$DOCKERFILE" ]]; then
        log_error "Dockerfile 不存在: $DOCKERFILE"
        exit 1
    fi

    # 確保 gradlew 可執行
    if [[ -f "./gradlew" ]]; then
        chmod +x ./gradlew
    fi

    # 創建必要目錄
    mkdir -p logs

    log_success "建置環境準備完成"
}

# 建置 Docker 映像
build_image() {
    log_info "開始建置 Docker 映像: ${IMAGE_NAME}:${TAG}"

    # 建置參數
    local build_args=""
    build_args="--build-arg BUILDKIT_INLINE_CACHE=1"
    build_args="$build_args --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    build_args="$build_args --build-arg VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"

    # 建置映像
    DOCKER_BUILDKIT=1 docker build \
        $build_args \
        -f "$DOCKERFILE" \
        -t "${IMAGE_NAME}:${TAG}" \
        -t "${IMAGE_NAME}:latest" \
        .

    log_success "Docker 映像建置完成: ${IMAGE_NAME}:${TAG}"

    # 顯示映像信息
    docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
}

# 運行容器測試
test_container() {
    log_info "測試容器運行..."
    
    local test_container="${IMAGE_NAME}-test"
    
    # 清理現有測試容器
    docker stop "$test_container" 2>/dev/null || true
    docker rm "$test_container" 2>/dev/null || true
    
    # 運行測試容器
    docker run -d \
        --name "$test_container" \
        -p 18080:8080 \
        "${IMAGE_NAME}:${TAG}"
    
    # 等待啟動
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f http://localhost:18080/actuator/health > /dev/null 2>&1; then
            log_success "容器測試通過"
            break
        fi
        
        echo -n "."
        sleep 2
        ((attempt++))
    done
    
    # 清理測試容器
    docker stop "$test_container" 2>/dev/null || true
    docker rm "$test_container" 2>/dev/null || true
    
    if [ $attempt -eq $max_attempts ]; then
        log_error "容器測試失敗"
        return 1
    fi
}

# 推送映像
push_image() {
    if [[ -z "$DOCKER_REGISTRY" ]]; then
        log_warning "未設置 DOCKER_REGISTRY，跳過推送"
        return 0
    fi

    local full_image="${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}"

    log_info "推送映像到倉庫: $full_image"

    # 標記映像
    docker tag "${IMAGE_NAME}:${TAG}" "$full_image"

    # 推送映像
    docker push "$full_image"

    # 如果不是 latest 標籤，也推送 latest
    if [[ "$TAG" != "latest" ]]; then
        docker tag "${IMAGE_NAME}:${TAG}" "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
        docker push "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
    fi

    log_success "映像推送完成: $full_image"
}

# 清理建置緩存
cleanup() {
    log_info "清理建置緩存..."
    docker builder prune -f
    log_success "清理完成"
}

# 顯示使用說明
show_usage() {
    echo "Usage: $0 [TAG] [DOCKERFILE] [COMMAND]"
    echo ""
    echo "Parameters:"
    echo "  TAG          映像標籤 (預設: latest)"
    echo "  DOCKERFILE   Dockerfile 路徑 (預設: Dockerfile)"
    echo ""
    echo "Commands:"
    echo "  build        建置映像 (預設)"
    echo "  test         建置並測試映像"
    echo "  push         建置並推送映像"
    echo "  clean        清理建置緩存"
    echo ""
    echo "Environment Variables:"
    echo "  DOCKER_REGISTRY  Docker 倉庫地址 (用於推送)"
    echo ""
    echo "Examples:"
    echo "  $0                    # 建置 latest 版本"
    echo "  $0 v1.0.0            # 建置 v1.0.0 版本"
    echo "  $0 latest Dockerfile.fast  # 使用快速 Dockerfile"
    echo "  $0 latest Dockerfile test   # 建置並測試"
    echo ""
}

# 主函數
main() {
    local command="${3:-build}"
    
    # 如果第一個參數是命令，調整參數順序
    if [[ "$1" =~ ^(build|test|push|clean|help|-h|--help)$ ]]; then
        command="$1"
        TAG="${2:-latest}"
        DOCKERFILE="${3:-Dockerfile}"
    fi
    
    case "$command" in
        "build")
            check_docker
            prepare_build
            build_image
            ;;
        "test")
            check_docker
            prepare_build
            build_image
            test_container
            ;;
        "push")
            check_docker
            prepare_build
            build_image
            push_image
            ;;
        "clean")
            check_docker
            cleanup
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

# 執行主函數
main "$@"