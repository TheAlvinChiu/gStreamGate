# gStreamGate - Java ベースの gRPC プロキシゲートウェイ

<div align="center">

![gStreamGate Logo](/logos/logo.webp)

**エンタープライズグレード gRPC プロキシゲートウェイとインテリジェント管理**

[![ビルド状況](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/alvinchiu/gstream-gate) [![バージョン](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/alvinchiu/gstream-gate/releases) [![ライセンス](https://img.shields.io/badge/license-MIT-green)](LICENSE) [![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)](https://spring.io/projects/spring-boot) [![React](https://img.shields.io/badge/React-18.2.0-blue)](https://reactjs.org/) [![Docker](https://img.shields.io/badge/Docker-ready-blue)](https://www.docker.com/)

[クイックスタート](#クイックスタート) • [使用方法](#使用方法) • [API リファレンス](#api-リファレンス) • [コントリビューション](#コントリビューション)

**言語：** [English](README.md) | [繁體中文](README_zh-TW.md) | 日本語

</div>

## 💖 プロジェクトサポート

gStreamGate が役に立つと感じ、その開発をサポートしたい場合は、寄付をご検討ください：

<div align="center">

**USDT 寄付 (TRC20)**

[![USDT 寄付](https://img.shields.io/badge/寄付-USDT%20TRC20-green?style=for-the-badge&logo=tether)](https://tronscan.org/#/address/TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC)

```
TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC
```

*あなたのサポートがこのオープンソースプロジェクトの維持と改善に役立ちます！🙏*

</div>

---


## プロジェクト概要

gStreamGate は、インテリジェントなトラフィック管理、適応型パフォーマンス最適化、包括的な監視機能を提供する、精密でエンタープライズ対応の gRPC プロキシゲートウェイです。Spring Boot 3.5 と React 18 で構築され、サーキットブレーカー、適応型タイムアウト、スマートフロー制御などの高度な機能を備えた gRPC サービスプロキシを管理するための最新の Web インターフェースを提供します。

### 主要機能

- 🚀 **高性能 gRPC プロキシ** - Undertow Web サーバーによる効率的なリクエストルーティング
- 🧠 **適応型タイムアウト管理** - 呼び出しパターンに基づく自動タイムアウト調整
- 🔄 **スマートフロー制御** - ストリーミング RPC のインテリジェントなメッセージフロー最適化
- ⚡ **サーキットブレーカーパターン** - カスケード障害に対する保護
- 🔐 **JWT 認証** - ロールベースアクセス制御付きセキュア REST API
- 👥 **ユーザー管理システム** - 完全な CRUD 操作とロールベース権限管理
- 🎯 **リアルタイム監視** - Prometheus 統合による包括的メトリクス
- 🌐 **モダン Web インターフェース** - React ベースの管理ダッシュボードとユーザー管理
- 🐳 **Docker 対応** - マルチステージビルドによる完全なコンテナ化
- 📊 **パフォーマンス最適化** - メモリプール、コネクションプール、リソース管理

### 使用ケース

- **マイクロサービスゲートウェイ** - gRPC マイクロサービスの中央エントリーポイント
- **ロードバランシング** - バックエンドサービスへのインテリジェントなトラフィック配信
- **サービスメッシュ統合** - 可観測性とコントロールプレーン機能の強化
- **開発・テスト** - 開発環境でのローカルプロキシ
- **本番トラフィック管理** - 監視機能付きエンタープライズグレードプロキシ

## アーキテクチャ

```mermaid
graph TB
    Client[gRPC クライアント] --> LB[ロードバランサー]
    LB --> Proxy[gStreamGate プロキシ]
    
    subgraph "gStreamGate コンポーネント"
        Proxy --> ATM[適応型タイムアウトマネージャー]
        Proxy --> SFC[スマートフロー制御]
        Proxy --> CB[サーキットブレーカー]
        Proxy --> MM[メモリマネージャー]
        
        Web[Web インターフェース] --> API[REST API]
        API --> Auth[JWT 認証]
        API --> PM[プロキシマネージャー]
    end
    
    Proxy --> Backend1[gRPC サービス 1]
    Proxy --> Backend2[gRPC サービス 2]
    Proxy --> Backend3[gRPC サービス N]
    
    Metrics[メトリクス エクスポート] --> Prometheus[Prometheus]
    Prometheus --> Grafana[Grafana ダッシュボード]
```

### 技術スタック

**バックエンド：**

- Java 21 with Spring Boot 3.5.0
- Undertow Web サーバー（パフォーマンス最適化）
- gRPC 1.68.1 with Netty トランスポート
- H2 データベース（開発）/ PostgreSQL（本番）
- Spring Security による JWT 認証
- Micrometer メトリクスと Prometheus

**フロントエンド：**

- React 18.2.0 with TypeScript
- Tailwind CSS スタイリング
- Lucide React アイコン
- レスポンシブデザインとモダン UI/UX

**インフラストラクチャ：**

- Docker マルチステージビルド
- Prometheus & Grafana 監視
- ヘルスチェックと可観測性
- 本番対応設定

## クイックスタート

### 前提条件

- Docker 20.10+ と Docker Compose 2.0+
- 2GB+ 利用可能メモリ
- ポート 8080、9092 が利用可能

### Docker クイックスタート（推奨）

```bash
# リポジトリをクローン
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# Docker でビルドして実行
docker build -t gstreamgate:latest .
docker run -d --name gstream-gate -p 8080:8080 -p 9092:9092 gstreamgate:latest
```

**アクセスポイント：**

- Web インターフェース：http://localhost:8080
- gRPC プロキシ：localhost:9092
- ヘルスチェック：http://localhost:8080/actuator/health
- メトリクス：http://localhost:8080/actuator/prometheus

**デフォルト認証情報：**

- 管理者：`admin` / `password`
- ユーザー：`user` / `password`

### ローカル開発

```bash
# バックエンド（Java 21+ が必要）
./gradlew bootRun

# フロントエンド（Node.js 18+ が必要）
cd frontend
npm install
npm start
```

## インストールとデプロイ

### Docker デプロイ（本番環境）

```bash
# 外部データベースと本番デプロイ
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

### 手動インストール

```bash
# アプリケーションをビルド
./gradlew clean build

# JAR ファイルを実行
java -jar build/libs/gstream-gate-proxy-*.jar \
  --spring.profiles.active=production \
  --server.port=8080 \
  --grpc.proxy.server.port=9092
```

### 環境変数設定

主要な環境変数：

```env
# データベース設定
DB_USERNAME=gstreamgate
DB_PASSWORD=secure_password
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gstreamgate

# アプリケーション設定
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
GRPC_PROXY_SERVER_PORT=9092

# セキュリティ
JWT_SECRET=your_jwt_secret_key_here
JWT_EXPIRATION=86400000

# パフォーマンスチューニング
JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

## 設定

### プロキシマッピング設定

Web インターフェースまたは REST API を通してプロキシマッピングを設定：

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

### セキュリティモード

- **AUTO**：TLS サポートを自動検出
- **SECURE**：TLS 暗号化を強制
- **PLAINTEXT**：プレーン HTTP/2 を使用

### パフォーマンスチューニング

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

## 使用方法

### Web インターフェース

1. **ダッシュボードアクセス**：http://localhost:8080 に移動
2. **ログイン**：admin/password でフルアクセス
3. **プロキシ管理**：プロキシ設定の作成、編集、監視
4. **ユーザー管理**：管理者はユーザーアカウント、ロール、権限を管理可能
5. **メトリクス表示**：システムヘルスとパフォーマンスの監視

### REST API 例

```bash
# 認証
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# プロキシマッピング作成
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

# 全プロキシ一覧
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/proxy

# 新しいユーザー作成（管理者のみ）
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

# 全ユーザー一覧（ページネーション付き）
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users?page=0&size=10"

# キーワードでユーザー検索
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/admin/users/search?keyword=john&page=0&size=10"
```

### gRPC クライアント設定

プロキシ経由で接続するように gRPC クライアントを設定：

```java
// Java gRPC クライアント例
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9092)
    .usePlaintext() // または設定時に TLS を使用
    .build();

// サービススタブ
YourServiceGrpc.YourServiceBlockingStub stub = 
    YourServiceGrpc.newBlockingStub(channel);
```

## 開発

### 開発環境セットアップ

```bash
# クローンとセットアップ
git clone https://github.com/alvinchiu/gstream-gate.git
cd gstream-gate

# バックエンド開発
./gradlew bootRun  # ポート 8080 で開始

# フロントエンド開発（別ターミナル）
cd frontend
npm install
npm start  # ポート 3000 で開始
```

### ソースからビルド

```bash
# フロントエンド含む完全ビルド
./gradlew clean build

# バックエンドのみ
./gradlew clean bootJar

# テスト実行
./gradlew test

# テストカバレッジレポート生成
./gradlew jacocoTestReport
```

### コード品質

```bash
# セキュリティスキャン
./gradlew dependencyCheckAnalyze

# 依存関係検証
./gradlew verifyDependencies

# ビルド情報確認
./gradlew buildInfo
```

## 監視と運用

### ヘルスチェック

```bash
# アプリケーションヘルス
curl http://localhost:8080/actuator/health

# 詳細ヘルス（認証必要）
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/health
```

### メトリクス収集

アプリケーションは Prometheus 形式でメトリクスをエクスポート：

```bash
# Prometheus メトリクスエンドポイント
curl http://localhost:8080/actuator/prometheus
```

主要メトリクス：

- `grpc_proxy_requests_total` - プロキシリクエスト総数
- `grpc_proxy_request_duration` - リクエスト期間
- `grpc_proxy_connections_active` - アクティブコネクション数
- `jvm_memory_used_bytes` - メモリ使用量

### ログ記録

構造化ログには以下が含まれます：

- 一意の呼び出し ID によるリクエスト/レスポンストレース
- パフォーマンスメトリクス
- スタックトレース付きエラー詳細
- セキュリティイベント

```bash
# Docker ログ表示
docker logs gstream-gate

# ログをフォロー
docker logs -f gstream-gate
```

## API リファレンス

### 認証エンドポイント

| メソッド | エンドポイント | 説明 |
|---------|----------------|------|
| POST | `/api/auth/login` | ユーザー認証 |
| POST | `/api/auth/logout` | ユーザーログアウト |
| POST | `/api/auth/register` | ユーザー登録 |
| GET | `/api/auth/me` | 現在のユーザー情報 |

### プロキシ管理エンドポイント

| メソッド | エンドポイント | 説明 | 認証要件 |
|---------|----------------|------|----------|
| GET | `/api/proxy` | 全プロキシ一覧 | USER/ADMIN |
| GET | `/api/proxy/enabled` | 有効プロキシ一覧 | USER/ADMIN |
| POST | `/api/proxy` | プロキシ作成 | ADMIN |
| PUT | `/api/proxy/{id}` | プロキシ更新 | ADMIN |
| DELETE | `/api/proxy/{id}` | プロキシ削除 | ADMIN |
| PATCH | `/api/proxy/{id}/status` | プロキシステータス切替 | ADMIN |
| POST | `/api/proxy/refresh` | 全プロキシ更新 | ADMIN |

### ユーザー管理エンドポイント

| メソッド | エンドポイント | 説明 | 認証要件 |
|---------|----------------|------|----------|
| GET | `/api/admin/users` | 全ユーザー一覧 | ADMIN |
| GET | `/api/admin/users/{id}` | ID でユーザー取得 | ADMIN |
| POST | `/api/admin/users` | 新しいユーザー作成 | ADMIN |
| PUT | `/api/admin/users/{id}` | ユーザー更新 | ADMIN |
| DELETE | `/api/admin/users/{id}` | ユーザー削除 | ADMIN |
| PUT | `/api/admin/users/{id}/enable` | ユーザーアカウント有効化 | ADMIN |
| PUT | `/api/admin/users/{id}/disable` | ユーザーアカウント無効化 | ADMIN |
| PUT | `/api/admin/users/{id}/role` | ユーザーロール更新 | ADMIN |
| GET | `/api/admin/users/search` | キーワードでユーザー検索 | ADMIN |

### レスポンス形式

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

## セキュリティ

### 認証と認可

- **JWT ベース認証** - 設定可能な有効期限
- **ロールベースアクセス制御**（USER/ADMIN ロール）
- **BCrypt セキュアパスワードハッシュ**
- **CORS クロスオリジン保護**

### TLS 設定

```yaml
# プロキシサーバーの TLS 有効化
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

### セキュリティベストプラクティス

1. **本番環境でデフォルトパスワード変更**
2. **強固な JWT シークレット使用**（最低 256 ビット）
3. **全外部通信で TLS 有効化**
4. **依存関係スキャンによる定期セキュリティ更新**
5. **不審な活動のアクセスログ監視**

## パフォーマンスと最適化

### リソース要件

**最小要件：**

- CPU：2 コア
- RAM：2GB
- ストレージ：5GB

**推奨構成（本番環境）：**

- CPU：4+ コア
- RAM：4GB+
- ストレージ：20GB+

### パフォーマンスチューニング

```yaml
# 高性能設定
server:
  undertow:
    threads:
      io: 16        # 2x CPU コア数
      worker: 128   # 8x CPU コア数
    buffer-size: 32768
    direct-buffers: true

app:
  connectionPool:
    maxConnectionsPerTarget: 16
  memory:
    cacheMaxSize: 5000
```

### スケーリング考慮事項

- **水平スケーリング**：ロードバランサー後方への複数インスタンスデプロイ
- **データベーススケーリング**：コネクションプールとリードレプリカの使用
- **メモリ最適化**：負荷に基づく JVM ヒープサイズ調整
- **ネットワーク最適化**：適切なバッファサイズの使用

## トラブルシューティング

### 一般的な問題

**1. 接続拒否**

```bash
# プロキシ実行確認
curl http://localhost:8080/actuator/health

# gRPC ポート確認
netstat -tlnp | grep 9092
```

**2. 認証失敗**

```bash
# JWT トークン検証
curl -X POST http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

**3. 高メモリ使用量**

```bash
# メモリメトリクス確認
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# メモリ最適化有効化
export JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

### デバッグモード

デバッグログの有効化：

```yaml
logging:
  level:
    io.github.alvinchiu.gstreamgate: DEBUG
    org.springframework.security: DEBUG
```

### サポートリソース

- **GitHub Issues**：[バグ報告と機能リクエスト](https://github.com/alvinchiu/gstream-gate/issues)
- **ドキュメント**：`/docs` ディレクトリを確認
- **監視**：Prometheus/Grafana ダッシュボードを使用

## コントリビューション

コントリビューションを歓迎します！詳細については[コントリビューションガイドライン](CONTRIBUTING.md)をご覧ください。

### 開発ワークフロー

1. リポジトリをフォーク
2. 機能ブランチを作成（`git checkout -b feature/amazing-feature`）
3. 変更をコミット（`git commit -m 'Add amazing feature'`）
4. ブランチにプッシュ（`git push origin feature/amazing-feature`）
5. プルリクエストを開く

### コードスタイル

- **Java**：Google Java Style Guide に従う
- **React**：ESLint と Prettier 設定を使用
- **テスト**：>80% コードカバレッジを維持
- **ドキュメント**：README とコードコメントを更新

## ライセンスと謝辞

### ライセンス

このプロジェクトは MIT ライセンスの下でライセンスされています - 詳細は [LICENSE](LICENSE) ファイルをご覧ください。

### 謝辞

- **作者**：Alvin Chiu ([@thealvin](https://github.com/thealvin))
- **コントリビューター**：[CONTRIBUTORS.md](CONTRIBUTORS.md) を参照
- **技術提供**：Spring Boot、React、gRPC、素晴らしいオープンソースコミュニティ

### 感謝

- 優秀なフレームワークを提供した Spring Boot チーム
- 強力な RPC フレームワークを提供した gRPC チーム
- 素晴らしい UI ライブラリを提供した React チーム
- このプロジェクトの全てのコントリビューターとユーザー

## 💖 寄付

このプロジェクトがお役に立った場合は、継続的な開発をサポートすることをご検討ください：

**USDT (TRC20)：** `TCA9oxDKZXbSTH7McTfsEhET4QJ4qtT1AC`

あなたの寛大なサポートがこのプロジェクトを活気づけ、発展させ続けます！🙏

---

<div align="center">

**gRPC コミュニティのために ❤️ で作成**

[⬆ トップへ戻る](#gstreamgate)

</div>