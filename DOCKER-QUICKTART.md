# gStreamGate - Docker Quick Start Guide

## 🚀 Quick Start

### Method 1: Using Docker Compose (Recommended)

```bash
# 1. Clone the project and navigate to directory
git clone <your-repo-url>
cd gstream-gate-proxy

# 2. Start all services with one command
./docker-build.sh latest compose-up

# 3. Check service status
docker-compose ps
```

### Method 2: Build and Run Individually

```bash
# 1. Build Docker image
./docker-build.sh latest build

# 2. Run container
./docker-build.sh latest run

# 3. Check health status
./docker-build.sh latest health
```

## 📋 System Requirements

- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **Memory**: Minimum 2GB available memory
- **Ports**: Ensure the following ports are not occupied
   - `8080`: HTTP API service
   - `9191`: gRPC proxy port (external access)
   - `5432`: PostgreSQL database
   - `9090`: Prometheus monitoring
   - `3000`: Grafana dashboard

## 🔧 Configuration

### Environment Variables Configuration

Create a `.env` file:

```bash
# Database configuration
DB_USERNAME=gstreamgate
DB_PASSWORD=SecurePassword123!

# Application configuration
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
GRPC_PROXY_SERVER_PORT=50051

# JVM configuration
JAVA_OPTS=-server -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC

# Monitoring configuration
GRAFANA_ADMIN_PASSWORD=AdminPassword123!
```

### Custom Configuration Files

Place your configuration files in the following locations:

```
├── config/
│   ├── application-production.yml    # Production environment config
│   └── logback-spring.xml           # Logging configuration
├── monitoring/
│   ├── prometheus.yml               # Prometheus configuration
│   └── grafana/                     # Grafana configuration
└── nginx/
    └── nginx.conf                   # Nginx configuration (optional)
```

## 🌐 Service Access

After successful startup, you can access services through the following addresses:

| Service | URL | Description |
|---------|-----|-------------|
| **Application** | http://localhost:8080 | Main HTTP API |
| **Health Check** | http://localhost:8080/actuator/health | Application health status |
| **API Documentation** | http://localhost:8080/actuator | Spring Boot Actuator |
| **Metrics** | http://localhost:8080/actuator/prometheus | Prometheus metrics |
| **gRPC Port** | localhost:9191 | gRPC proxy service (external access) |
| **Database** | localhost:5432 | PostgreSQL |
| **Prometheus** | http://localhost:9090 | Monitoring system |
| **Grafana** | http://localhost:3000 | Visualization dashboard |

### Default Login Credentials

- **Grafana**: `admin` / `AdminPassword123!`
- **PostgreSQL**: `gstreamgate` / `SecurePassword123!`

## 📊 Monitoring and Logging

### View Application Logs

```bash
# View logs in real-time
./docker-build.sh latest logs

# Or use docker-compose
docker-compose logs -f grpc-proxy
```

### Monitoring Dashboards

1. **Prometheus Monitoring**: Visit http://localhost:9090
   - View system metrics and custom metrics
   - Set up alert rules

2. **Grafana Dashboard**: Visit http://localhost:3000
   - Login with `admin/AdminPassword123!`
   - View pre-configured gRPC Proxy dashboards

### Health Checks

```bash
# Check application health status
curl http://localhost:8080/actuator/health

# Check all Actuator endpoints
curl http://localhost:8080/actuator

# View Metrics
curl http://localhost:8080/actuator/metrics
```

## 🛠️ Development and Debugging

### Development Mode

```bash
# Run with development configuration
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Or use Gradle directly
./gradlew runDev
```

### View Container Details

```bash
# Check container status
docker ps

# Enter container
docker exec -it grpc-proxy-app bash

# View container resource usage
docker stats grpc-proxy-app
```

### Debug Network Connections

```bash
# Test gRPC connection (requires grpcurl)
grpcurl -plaintext localhost:50051 list

# Test HTTP API
curl -v http://localhost:8080/api/proxy/test

# Check port listening
netstat -tlnp | grep -E "(8080|50051)"
```

## 🔄 Common Operations

### Restart Services

```bash
# Restart specific service
docker-compose restart grpc-proxy

# Restart all services
docker-compose restart
```

### Update Application

```bash
# Rebuild and update
./docker-build.sh latest build
docker-compose up -d --no-deps grpc-proxy
```

### Data Backup

```bash
# Backup database
docker exec grpc-proxy-postgres pg_dump -U gstreamgate gstreamgate > backup.sql

# Restore database
docker exec -i grpc-proxy-postgres psql -U gstreamgate gstreamgate < backup.sql
```

### Cleanup Resources

```bash
# Stop and remove all services
./docker-build.sh latest compose-down

# Clean Docker resources
./docker-build.sh latest clean

# Complete cleanup (including volumes)
docker-compose down -v --remove-orphans
docker system prune -a
```

## 🚨 Troubleshooting

### Common Issues

1. **Port Conflicts**
   ```bash
   # Check port usage
   lsof -i :8080
   lsof -i :50051
   
   # Modify port mapping
   # Edit ports configuration in docker-compose.yml
   ```

2. **Insufficient Memory**
   ```bash
   # Reduce JVM memory usage
   export JAVA_OPTS="-XX:MaxRAMPercentage=50.0"
   
   # Or adjust resource limits in docker-compose.yml
   ```

3. **Database Connection Failed**
   ```bash
   # Check database container status
   docker-compose ps postgres
   
   # View database logs
   docker-compose logs postgres
   
   # Reset database
   docker-compose stop postgres
   docker volume rm gstream-gate-proxy_postgres-data
   docker-compose up -d postgres
   ```

4. **Application Startup Failed**
   ```bash
   # View detailed logs
   docker-compose logs grpc-proxy
   
   # Check configuration file
   docker exec grpc-proxy-app cat /app/application.yml
   
   # Check JVM parameters
   docker exec grpc-proxy-app ps aux | grep java
   ```

### Performance Tuning

1. **JVM Tuning**
   ```bash
   # Adjust garbage collector
   export JAVA_OPTS="-XX:+UseZGC -XX:+UseTransparentHugePages"
   
   # Adjust heap size
   export JAVA_OPTS="-Xms1g -Xmx2g"
   ```

2. **Undertow Tuning**
   ```bash
   # Adjust worker threads
   export UNDERTOW_OPTS="--server.undertow.threads.worker=128"
   
   # Adjust buffer size
   export UNDERTOW_OPTS="--server.undertow.buffer-size=32768"
   ```

3. **Database Tuning**
   ```sql
   -- Adjust PostgreSQL configuration
   ALTER SYSTEM SET shared_buffers = '256MB';
   ALTER SYSTEM SET effective_cache_size = '1GB';
   SELECT pg_reload_conf();
   ```

## 📞 Support

- **Documentation**: Check project Wiki
- **Issue Reports**: Submit GitHub Issues
- **Discussions**: Participate in GitHub Discussions

## 🔐 Security Recommendations

1. **Production Deployment**
   - Change all default passwords
   - Use HTTPS/TLS
   - Configure firewall rules
   - Regularly update images

2. **Monitoring and Alerting**
   - Set up resource usage alerts
   - Configure error rate monitoring
   - Enable security audit logging

3. **Backup Strategy**
   - Regular database backups
   - Backup configuration files
   - Test recovery procedures