# YouTube Hub Service (Core Backend)

![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![YouTube API](https://img.shields.io/badge/API-YouTube%20Data%20v3-red)
![Architecture](https://img.shields.io/badge/Architecture-Microservice-blueviolet)
![Maven](https://img.shields.io/badge/Build-Maven-blue)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED)
![License](https://img.shields.io/badge/License-MIT-green)
![MinIO](https://img.shields.io/badge/Storage-MinIO%20%7C%20S3-C7202C)

The **YouTube Hub Service** is the central nervous system ("The Brain") of the **YouTube Data Hub** platform.

It serves as the primary backend API for the **YouTube-Hub-UI**, manages the core business logic (Channel/Video metadata), orchestrates download tasks, and ensures data consistency by periodically polling the YouTube Data API.

## 🏗️ Architecture & Role

*   **The "Brain"**: Centralizes state management. It knows which channels are tracked, which videos are downloaded, and their current status.
*   **Polling Engine**: Runs scheduled tasks to fetch new videos from the YouTube Data API v3, ensuring the local database mirrors the actual channel content.
*   **Orchestrator**: Dispatches download jobs to the **Downloader Service** and processes status callbacks (Fire-and-Forget pattern).
*   **Facade**: Provides a unified REST API for the **YouTube-Hub-UI** (Next.js) to interact with the system.

### Design Philosophy
*   **Source of Truth**: While the Downloader handles the files, the Hub handles the *metadata*. The Hub's database is the single source of truth for the system's state.
*   **Resilience**: Designed to handle YouTube API quota limits and network interruptions gracefully.
*   **Pluggable Storage**: Employs the Strategy Pattern to seamlessly switch between local file storage and S3-compatible object storage.
*   **Zero-Landing Streaming**: Direct `InputStream` piping from the YouTube API to S3/MinIO, preventing JVM memory bloat and unnecessary local disk I/O.

## 🚀 Key Features

*   **Channel Tracking**: Subscribe to YouTube channels and automatically detect new uploads.
*   **Metadata Management**: Stores rich metadata (Titles, Thumbnails, Descriptions, Publish Dates) in Database.
*   **Download Orchestration**: Sends download requests to the Downloader and tracks progress.
*   **Polling**: Configurable polling intervals to optimize API quota usage.
*   **Search & Filter**: Provides APIs for the UI to search and filter the local video library.
*   **Dynamic Storage**: Effortlessly switch thumbnail storage between local disk and AWS S3/MinIO via simple environment variables.

## 🛠️ Tech Stack

*   **Language**: Java (25+)
*   **Framework**: Spring Boot 3.5
*   **Database**: MySQL (Core Data)
*   **External API**: YouTube Data API v3
*   **Build Tool**: Maven
*   **Storage SDK**: AWS SDK for Java v2 (S3)

## 📦 Prerequisites & Dependencies

Since this project was split from a mono-repo, it depends on the shared **Platform** library.

1.  **Platform Library**: You must build and install the `Platform` project locally first.
    ```bash
    cd ../Platform
    mvn clean install
    ```
2.  **Database**: A MySQL instance.
3.  **YouTube API Key**: A valid Google Cloud API Key with YouTube Data API v3 enabled.
4.  **RSA Public Key**: To validate JWTs from the Auth Service.

## ⚙️ Configuration

Create a `.env` file in the root directory. You can copy `.env.example`.

```properties
# Server Configuration
SERVER_PORT=8080

# Database
DB_URL=jdbc:mysql://localhost:3306/youtube_hub
DB_USERNAME=root
DB_PASSWORD=secret

# YouTube Data API
YOUTUBE_HUB_DEFAULT_YOUTUBE_API_KEY=your_google_api_key

# Integration URLs
AUTH_SERVICE_URL=http://localhost:8081
DOWNLOADER_SERVICE_URL=http://localhost:8084

# Security (RSA Key Path for Validation)
RSA_PUBLIC_KEY_PATH=/path/to/public_key.pem

# Security (Client Credentials for Outbound Calls)
YOUTUBE_HUB_DEFAULT_CLIENT_ID=hub-service
YOUTUBE_HUB_DEFAULT_CLIENT_SECRET=hub-client-secret

# Storage Configuration (S3 / MinIO)
YOUTUBE_HUB_STORAGE_TYPE=s3
YOUTUBE_HUB_STORAGE_S3_ENDPOINT=http://localhost:9000
YOUTUBE_HUB_STORAGE_S3_ACCESS_KEY=admin
YOUTUBE_HUB_STORAGE_S3_SECRET_KEY=SuperSecretPassword123!
YOUTUBE_HUB_STORAGE_S3_REGION=us-east-1
YOUTUBE_HUB_STORAGE_S3_BUCKET_NAME=youtube-thumbnails
```

> **Note**: The `Setup-Scripts/Init-secrets.sh` script can automatically inject the API Key and Client Credentials for you.

## 🏃‍♂️ Build & Run

### Local Development

```bash
# 1. Build the project
cd youtube-hub-backend
mvn clean package

# 2. Run the JAR
java -jar ../bin/youtube-hub-service.jar
```

### Docker

```bash
# 1. Navigate to parent directory
cd ..

# 2. Build image
docker build -f Youtube-Hub/youtube-hub-backend/Dockerfile -t youtube-data-hub/hub-service .
```

```bash
# 3. Run container
cd Youtube-Hub
docker run -d \
  -p 8080:8080 \
  --env-file .env \
  -v $(pwd)/keys:/app/keys \
  youtube-data-hub/hub-service
```

## 🔐 Authentication Strategy

The Hub Service has a dual role in the security architecture:

1.  **Resource Server (Inbound)**:
    *   Validates **User JWTs** from the UI (YouTube-Hub-UI) for actions (e.g., "Subscribe to Channel").
    *   Validates **Service JWTs** from the Downloader for status callbacks (e.g., "Download Completed").
    *   It uses the Auth Service's **Public Key** to verify signatures.

2.  **Machine Client (Outbound)**:
    *   When the Hub needs to call the **Downloader Service** (to trigger a download), it authenticates itself using the **Client Credentials Flow**.
    *   It uses `YOUTUBE_HUB_DEFAULT_CLIENT_ID` and `SECRET` to obtain a token from the Auth Service.

## 🔌 API Endpoints (Overview)

*   `GET /api/v1/channels`: List tracked channels.
*   `POST /api/v1/channels`: Subscribe to a new channel.
*   `GET /api/v1/videos`: List/Search videos.
*   `POST /api/v1/downloads/{videoId}`: Trigger a download.
*   `POST /api/v1/callbacks/status`: Webhook for Downloader status updates.

## 📜 License

MIT
