# gStreamGate - A Java base gRPC Proxy Gateway

<div align="center">

![gStreamGate Logo](/logos/logo.webp)

**Enterprise-Grade gRPC Proxy Gateway with Intelligent Management**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/alvinchiu/gstream-gate) [![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/alvinchiu/gstream-gate/releases) [![License](https://img.shields.io/badge/license-MIT-green)](LICENSE) [![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)](https://spring.io/projects/spring-boot) [![React](https://img.shields.io/badge/React-18.2.0-blue)](https://reactjs.org/) [![Docker](https://img.shields.io/badge/Docker-ready-blue)](https://www.docker.com/)

[Quick Start](#quick-start) • [Documentation](#usage) • [API Reference](#api-reference) • [Contributing](#contributing)

**Language:** English | [繁體中文](README_zh-TW.md) | [日本語](README_ja.md)

</div>

## 💖 Support This Project

If you find gStreamGate useful and would like to support its development, consider making a donation:

<div align="center">

**USDT Donation (TRC20)**

[![Donate USDT](https://img.shields.io/badge/Donate-USDT%20TRC20-green?style=for-the-badge&logo=tether)](https://tronscan.org/#/address/TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC)

```
TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC
```

*Your support helps maintain and improve this open-source project! 🙏*

</div>

---


## Overview

gStreamGate is a sophisticated, enterprise-ready gRPC proxy gateway that provides intelligent traffic management, adaptive performance optimization, and comprehensive monitoring capabilities. Built with Spring Boot 3.5 and React 18, it offers a modern web interface for managing gRPC service proxies with advanced features like circuit breakers, adaptive timeouts, and smart flow control.

### Key Features

- 🚀 **High-Performance gRPC Proxy** - Efficient request routing with Undertow web server
- 🧠 **Adaptive Timeout Management** - Automatically adjusts timeouts based on call patterns
- 🔄 **Smart Flow Control** - Intelligent message flow optimization for streaming RPCs
- ⚡ **Circuit Breaker Pattern** - Protection against cascading failures
- 🔐 **JWT Authentication** - Secure REST API with role-based access control
- 👥 **User Management System** - Complete CRUD operations with role-based permissions
- 🎯 **Real-time Monitoring** - Comprehensive metrics with Prometheus integration
- 🌐 **Modern Web Interface** - React-based management dashboard with user management
- 🐳 **Docker Ready** - Complete containerization with multi-stage builds
- 📊 **Performance Optimization** - Memory pools, connection pooling, and resource management

### Use Cases

- **Microservices Gateway** - Central entry point for gRPC microservices
- **Load Balancing** - Intelligent traffic distribution across backend services
- **Service Mesh Integration** - Enhanced observability and control plane features
- **Development & Testing** - Local proxy for development environments
- **Production Traffic Management** - Enterprise-grade proxy with monitoring

## Architecture

```mermaid
graph TB
    Client[gRPC Clients] --> LB[Load Balancer]
    LB --> Proxy[gStreamGate Proxy]
    
    subgraph "gStreamGate Components"
        Proxy --> ATM[Adaptive Timeout Manager]
        Proxy --> SFC[Smart Flow Control]
        Proxy --> CB[Circuit Breaker]
        Proxy --> MM[Memory Manager]
        
        Web[Web Interface] --> API[REST API]
        API --> Auth[JWT Authentication]
        API --> PM[Proxy Manager]
    end
    
    Proxy --> Backend1[gRPC Service 1]
    Proxy --> Backend2[gRPC Service 2]
    Proxy --> Backend3[gRPC Service N]
    
    Metrics[Metrics Export] --> Prometheus[Prometheus]
    Prometheus --> Grafana[Grafana Dashboard]
```

### Technology Stack

**Backend:**

- Java 21 with Spring Boot 3.5.0
- Undertow Web Server (optimized for performance)
- gRPC 1.68.1 with Netty transport
- H2 Database (development) / PostgreSQL (production)
- JWT Authentication with Spring Security
- Micrometer Metrics with Prometheus

**Frontend:**

- React 18.2.0 with TypeScript
- Tailwind CSS for styling
- Lucide React icons
- Responsive design with modern UI/UX

**Infrastructure:**

- Docker multi-stage builds
- Prometheus & Grafana monitoring
- Health checks and observability
- Production-ready configuration

## Quick Start

### Prerequisites

- Docker 20.10+ and Docker Compose 2.0+
- 2GB+ available memory
- Ports 8080, 9092 available

### Docker Quick Start (Recommended)

```bash
# Clone the repository
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# Build and run with Docker
docker build -t gstreamgate:latest .
docker run -d --name gstream-gate -p 8080:8080 -p 9092:9092 gstreamgate:latest
```

**Access Points:**

- Web Interface: http://localhost:8080
- gRPC Proxy: localhost:9092
- Health Check: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

**Default Credentials:**

- Admin: `admin` / `password`
- User: `user` / `password`

### Local Development

```bash
# Backend (requires Java 21+)
./gradlew bootRun

# Frontend (requires Node.js 18+)
cd frontend
npm install
npm start
```

## Installation & Deployment

### Docker Deployment (Production)

```bash
# Production deployment with external database
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

### Manual Installation

```bash
# Build the application
./gradlew clean build

# Run the JAR file
java -jar build/libs/gstream-gate-proxy-*.jar \
  --spring.profiles.active=production \
  --server.port=8080 \
  --grpc.proxy.server.port=9092
```

### Environment Configuration

Key environment variables:

```env
# Database Configuration
DB_USERNAME=gstreamgate
DB_PASSWORD=secure_password
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gstreamgate

# Application Settings
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
GRPC_PROXY_SERVER_PORT=9092

# Security
JWT_SECRET=your_jwt_secret_key_here
JWT_EXPIRATION=86400000

# Performance Tuning
JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

## Configuration

### Proxy Mapping Configuration

Configure proxy mappings through the web interface or REST API:

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

### Security Modes

- **AUTO**: Automatically detect TLS support
- **SECURE**: Force TLS encryption
- **PLAINTEXT**: Use plain HTTP/2

### Performance Tuning

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

## Usage

### Web Interface

1. **Access Dashboard**: Navigate to http://localhost:8080
2. **Login**: Use admin/password for full access
3. **Manage Proxies**: Create, edit, and monitor proxy configurations
4. **User Management**: Admin users can manage user accounts, roles, and permissions
5. **View Metrics**: Monitor system health and performance

### REST API Examples

```bash
# Authenticate
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# Create proxy mapping
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

# List all proxies
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/proxy

# Create new user (Admin only)
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

# List all users with pagination
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users?page=0&size=10"

# Search users by keyword
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users/search?keyword=john&page=0&size=10"
```

### gRPC Client Configuration

Configure your gRPC clients to connect through the proxy:

```java
// Java gRPC client example
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9092)
    .usePlaintext() // or use TLS if configured
    .build();

// Your service stub
YourServiceGrpc.YourServiceBlockingStub stub = 
    YourServiceGrpc.newBlockingStub(channel);
```

## Development

### Development Environment Setup

```bash
# Clone and setup
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# Backend development
./gradlew bootRun  # Starts on port 8080

# Frontend development (separate terminal)
cd frontend
npm install
npm start  # Starts on port 3000
```

### Building from Source

```bash
# Full build with frontend
./gradlew clean build

# Backend only
./gradlew clean bootJar

# Run tests
./gradlew test

# Generate test coverage
./gradlew jacocoTestReport
```

### Code Quality

```bash
# Security scan
./gradlew dependencyCheckAnalyze

# Verify dependencies
./gradlew verifyDependencies

# Check build info
./gradlew buildInfo
```

## Monitoring & Operations

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health with authentication
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/health
```

### Metrics Collection

The application exports metrics in Prometheus format:

```bash
# Prometheus metrics endpoint
curl http://localhost:8080/actuator/prometheus
```

Key metrics include:

- `grpc_proxy_requests_total` - Total proxy requests
- `grpc_proxy_request_duration` - Request duration
- `grpc_proxy_connections_active` - Active connections
- `jvm_memory_used_bytes` - Memory usage

### Logging

Logs are structured and include:

- Request/response tracing with unique call IDs
- Performance metrics
- Error details with stack traces
- Security events

```bash
# View logs in Docker
docker logs gstream-gate

# Follow logs
docker logs -f gstream-gate
```

## API Reference

### Authentication Endpoints

| Method | Endpoint             | Description         |
| ------ | -------------------- | ------------------- |
| POST   | `/api/auth/login`    | User authentication |
| POST   | `/api/auth/logout`   | User logout         |
| POST   | `/api/auth/register` | User registration   |
| GET    | `/api/auth/me`       | Current user info   |

### Proxy Management Endpoints

| Method | Endpoint                 | Description          | Auth Required |
| ------ | ------------------------ | -------------------- | ------------- |
| GET    | `/api/proxy`             | List all proxies     | USER/ADMIN    |
| GET    | `/api/proxy/enabled`     | List enabled proxies | USER/ADMIN    |
| POST   | `/api/proxy`             | Create proxy         | ADMIN         |
| PUT    | `/api/proxy/{id}`        | Update proxy         | ADMIN         |
| DELETE | `/api/proxy/{id}`        | Delete proxy         | ADMIN         |
| PATCH  | `/api/proxy/{id}/status` | Toggle proxy status  | ADMIN         |
| POST   | `/api/proxy/refresh`     | Refresh all proxies  | ADMIN         |

### User Management Endpoints

| Method | Endpoint                    | Description              | Auth Required |
| ------ | --------------------------- | ------------------------ | ------------- |
| GET    | `/api/admin/users`          | List all users           | ADMIN         |
| GET    | `/api/admin/users/{id}`     | Get user by ID           | ADMIN         |
| POST   | `/api/admin/users`          | Create new user          | ADMIN         |
| PUT    | `/api/admin/users/{id}`     | Update user              | ADMIN         |
| DELETE | `/api/admin/users/{id}`     | Delete user              | ADMIN         |
| PUT    | `/api/admin/users/{id}/enable`  | Enable user account  | ADMIN         |
| PUT    | `/api/admin/users/{id}/disable` | Disable user account | ADMIN         |
| PUT    | `/api/admin/users/{id}/role`    | Update user role     | ADMIN         |
| GET    | `/api/admin/users/search`   | Search users by keyword  | ADMIN         |

### Response Format

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

## Security

### Authentication & Authorization

- **JWT-based authentication** with configurable expiration
- **Role-based access control** (USER/ADMIN roles)
- **Secure password hashing** with BCrypt
- **CORS protection** for cross-origin requests

### TLS Configuration

```yaml
# Enable TLS for proxy server
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

### Security Best Practices

1. **Change default passwords** in production
2. **Use strong JWT secrets** (minimum 256 bits)
3. **Enable TLS** for all external communications
4. **Regular security updates** via dependency scanning
5. **Monitor access logs** for suspicious activity

## Performance & Optimization

### Resource Requirements

**Minimum:**

- CPU: 2 cores
- RAM: 2GB
- Storage: 5GB

**Recommended (Production):**

- CPU: 4+ cores
- RAM: 4GB+
- Storage: 20GB+

### Performance Tuning

```yaml
# High-performance configuration
server:
  undertow:
    threads:
      io: 16        # 2x CPU cores
      worker: 128   # 8x CPU cores
    buffer-size: 32768
    direct-buffers: true

app:
  connectionPool:
    maxConnectionsPerTarget: 16
  memory:
    cacheMaxSize: 5000
```

### Scaling Considerations

- **Horizontal scaling**: Deploy multiple instances behind a load balancer
- **Database scaling**: Use connection pooling and read replicas
- **Memory optimization**: Tune JVM heap size based on load
- **Network optimization**: Use appropriate buffer sizes

## Troubleshooting

### Common Issues

**1. Connection Refused**

```bash
# Check if proxy is running
curl http://localhost:8080/actuator/health

# Verify gRPC port
netstat -tlnp | grep 9092
```

**2. Authentication Failed**

```bash
# Verify JWT token
curl -X POST http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

**3. High Memory Usage**

```bash
# Check memory metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Enable memory optimization
export JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    io.github.alvinchiu.gstreamgate: DEBUG
    org.springframework.security: DEBUG
```

### Support Resources

- **GitHub Issues**: [Report bugs and feature requests](https://github.com/alvinchiu/gstream-gate/issues)
- **Documentation**: Check the `/docs` directory
- **Monitoring**: Use Prometheus/Grafana dashboards

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- **Java**: Follow Google Java Style Guide
- **React**: Use ESLint and Prettier configurations
- **Tests**: Maintain >80% code coverage
- **Documentation**: Update README and code comments

## License & Credits

### License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

### Credits

- **Author**: Alvin Chiu ([@thealvin](https://github.com/thealvin))
- **Contributors**: See [CONTRIBUTORS.md](CONTRIBUTORS.md)
- **Powered by**: Spring Boot, React, gRPC, and the amazing open-source community

### Acknowledgments

- Spring Boot team for the excellent framework
- gRPC team for the powerful RPC framework
- React team for the fantastic UI library
- All contributors and users of this project

## 💖 Donations

If this project has been helpful to you, please consider supporting its continued development:

**USDT (TRC20):** `TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC`

Your generosity helps keep this project alive and thriving! 🙏

---

<div align="center">

**Made with ❤️ for the gRPC community**

[⬆ Back to Top](#gstreamgate)

</div>