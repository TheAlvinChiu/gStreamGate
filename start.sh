#!/bin/bash

# ==========================================
# gStreamGate 啟動腳本
# ==========================================

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

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

log_title() {
    echo -e "${CYAN}===============================================${NC}"
    echo -e "${CYAN} $1${NC}"
    echo -e "${CYAN}===============================================${NC}"
}

# 檢查依賴
check_dependencies() {
    log_info "檢查系統依賴..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安裝，請先安裝 Docker"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker daemon 未運行，請啟動 Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安裝或不可用"
        exit 1
    fi
    
    log_success "系統依賴檢查通過"
}

# 檢查端口
check_ports() {
    log_info "檢查端口佔用情況..."
    
    local ports=("8080" "9092" "9091" "3000")
    local occupied_ports=()
    
    for port in "${ports[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            occupied_ports+=($port)
            log_warning "端口 $port 已被佔用"
        fi
    done
    
    if [ ${#occupied_ports[@]} -gt 0 ]; then
        log_warning "以下端口被佔用: ${occupied_ports[*]}"
        read -p "是否繼續啟動？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "用戶取消啟動"
            exit 0
        fi
    fi
}

# 創建必要目錄
create_directories() {
    log_info "創建必要目錄..."
    
    mkdir -p logs
    mkdir -p monitoring/grafana/dashboards
    mkdir -p monitoring/grafana/provisioning/datasources
    mkdir -p nginx/ssl
    
    log_success "目錄創建完成"
}

# 創建配置文件
create_configs() {
    log_info "創建配置文件..."
    
    # Prometheus 配置
    cat > monitoring/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'gstreamgate'
    static_configs:
      - targets: ['app:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
EOF

    # Grafana 數據源配置
    cat > monitoring/grafana/provisioning/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
EOF

    log_success "配置文件創建完成"
}

# 環境變數設置
setup_environment() {
    log_info "設置環境變數..."
    
    export BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
    export VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')
    export VERSION=${VERSION:-latest}
    export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-production}
    export GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-gStreamGate123!}
    export REDIS_PASSWORD=${REDIS_PASSWORD:-gStreamGate}
    
    log_success "環境變數設置完成"
}

# 啟動服務
start_services() {
    local profile=${1:-""}
    
    log_title "啟動 gStreamGate 服務"
    
    case "$profile" in
        "redis")
            log_info "啟動服務 (包含 Redis)..."
            docker-compose --profile redis up -d --build
            ;;
        "nginx")
            log_info "啟動服務 (包含 Nginx 反向代理)..."
            docker-compose --profile nginx up -d --build
            ;;
        "full")
            log_info "啟動完整服務 (包含所有組件)..."
            docker-compose --profile redis --profile nginx up -d --build
            ;;
        *)
            log_info "啟動基本服務..."
            docker-compose up -d --build
            ;;
    esac
    
    log_success "服務啟動完成"
}

# 等待服務啟動
wait_for_services() {
    log_info "等待服務啟動..."
    
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            log_success "gStreamGate 應用已啟動"
            break
        fi
        
        echo -n "."
        sleep 2
        ((attempt++))
    done
    
    if [ $attempt -eq $max_attempts ]; then
        log_error "服務啟動超時"
        return 1
    fi
    
    # 檢查其他服務
    sleep 5
    
    if curl -s -f http://localhost:9091 > /dev/null 2>&1; then
        log_success "Prometheus 已啟動"
    fi
    
    if curl -s -f http://localhost:3000 > /dev/null 2>&1; then
        log_success "Grafana 已啟動"
    fi
}

# 顯示服務信息
show_service_info() {
    log_title "服務訪問信息"
    
    echo ""
    echo "🌐 服務訪問地址："
    echo "   📊 gStreamGate Web: http://localhost:8080"
    echo "   🔗 gRPC 代理端口: localhost:9092"
    echo "   💓 健康檢查: http://localhost:8080/actuator/health"
    echo "   📈 Prometheus: http://localhost:9091"
    echo "   📋 Grafana: http://localhost:3000"
    echo ""
    echo "🔐 預設登入資訊："
    echo "   Grafana: admin / ${GRAFANA_ADMIN_PASSWORD:-gStreamGate123!}"
    echo ""
    echo "🛠 管理指令："
    echo "   查看狀態: docker-compose ps"
    echo "   查看日誌: docker-compose logs -f [service_name]"
    echo "   停止服務: docker-compose down"
    echo "   重啟服務: docker-compose restart"
    echo ""
    echo "📁 重要目錄："
    echo "   日誌目錄: ./logs"
    echo "   監控配置: ./monitoring"
    echo ""
}

# 顯示服務狀態
show_status() {
    log_info "檢查服務狀態..."
    docker-compose ps
    echo ""
    
    log_info "檢查容器健康狀態..."
    docker-compose exec app curl -s http://localhost:8080/actuator/health | jq . 2>/dev/null || echo "健康檢查失敗"
}

# 停止服務
stop_services() {
    log_info "停止 gStreamGate 服務..."
    docker-compose down
    log_success "服務已停止"
}

# 清理資源
clean_resources() {
    log_info "清理 gStreamGate 資源..."
    docker-compose down -v --rmi all
    docker system prune -f
    log_success "資源清理完成"
}

# 顯示使用說明
show_usage() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  start [profile]   啟動服務 (預設)"
    echo "  stop              停止服務"
    echo "  restart [profile] 重啟服務"
    echo "  status            查看服務狀態"
    echo "  logs [service]    查看服務日誌"
    echo "  clean             清理所有資源"
    echo "  help              顯示此說明"
    echo ""
    echo "Profiles:"
    echo "  (無)              基本服務 (app + prometheus + grafana)"
    echo "  redis             包含 Redis 快取"
    echo "  nginx             包含 Nginx 反向代理"
    echo "  full              包含所有組件"
    echo ""
    echo "Examples:"
    echo "  $0 start          # 啟動基本服務"
    echo "  $0 start redis    # 啟動服務並包含 Redis"
    echo "  $0 start full     # 啟動所有服務"
    echo "  $0 logs app       # 查看應用日誌"
    echo "  $0 stop           # 停止所有服務"
    echo ""
}

# 主函數
main() {
    local command="${1:-start}"
    local profile="${2:-}"
    
    case "$command" in
        "start")
            check_dependencies
            check_ports
            create_directories
            create_configs
            setup_environment
            start_services "$profile"
            if wait_for_services; then
                show_service_info
            else
                log_error "服務啟動失敗，請查看日誌：docker-compose logs"
                exit 1
            fi
            ;;
        "stop")
            stop_services
            ;;
        "restart")
            stop_services
            sleep 2
            main start "$profile"
            ;;
        "status")
            show_status
            ;;
        "logs")
            if [ -n "$profile" ]; then
                docker-compose logs -f "$profile"
            else
                docker-compose logs -f
            fi
            ;;
        "clean")
            clean_resources
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