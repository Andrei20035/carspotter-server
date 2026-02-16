# 🚗 CarSpotter Server

The backend service powering **CarSpotter**, a full-stack social platform for car enthusiasts.

This server handles authentication, social interactions, media uploads, leaderboard logic, and real-time communication using a modular, testable, and scalable architecture.

Built with Kotlin + Ktor and structured using layered architecture principles.

---

## 🧠 Architecture Philosophy

The server follows a strict separation of concerns:

- **Routes** → HTTP layer
- **Services** → Business logic
- **Repositories** → Domain abstraction
- **DAOs** → Database interaction
- **Tables** → Database schema definitions
- **DTOs** → Safe API data transfer
- **Models** → Core domain entities

This layered design ensures:

- High testability
- Clear responsibility boundaries
- Maintainability at scale
- Safer refactoring
- Easier feature expansion

---

## 🏗 High-Level Architecture

Client (Android)
->
Ktor Routes
->
Service Layer
->
Repository Layer
->
DAO Layer (Exposed ORM)
->
PostgreSQL


Additional components:

- JWT Authentication middleware
- Google OAuth verification service
- AWS S3 storage service abstraction
- WebSocket support
- OpenAPI documentation
- Database migration scripts

---

## 🚀 Core Capabilities

### 🔐 Authentication & Security
- Email/password authentication
- Google OAuth integration
- JWT access & refresh tokens
- Route-level authentication middleware
- Password hashing
- Input validation & sanitization
- UUID-based entity identifiers

---

### 📷 Post & Media Management
- Create, edit, delete posts
- Upload media via pre-signed S3 URLs
- Store metadata in PostgreSQL
- Location-based fields (latitude/longitude)
- Feed pagination with cursor support

---

### 💬 Social System
- Comments
- Likes
- Friend system
- Friend requests
- Private messaging (WebSockets support)

---

### 🏆 Engagement Logic
- Weekly leaderboard mechanics
- Activity-based scoring system
- Feed staging logic (FeedStage, FeedCursor models)

---

## 🛠 Tech Stack

- **Kotlin**
- **Ktor (REST API)**
- **PostgreSQL**
- **Exposed ORM**
- **JWT Authentication**
- **Google OAuth**
- **AWS S3**
- **Docker**
- **Flyway-style SQL migrations**
- **OpenAPI documentation**

---

## 🧪 Testing Strategy

The project includes structured testing across all layers:

- DAO Tests
- Repository Tests
- Service Tests
- Route Tests
- Integration Tests
- Fake Google Token Verifier for isolation
- Dedicated TestDatabase setup
- Schema initialization utilities

This ensures:

- Business logic correctness
- Route validation
- Authentication safety
- Refactor confidence

---

## 📂 Project Structure

src/main/kotlin
 - routes/ → HTTP endpoints
 - service/ → Business logic
 - repository/ → Domain abstraction
 - data/dao/ → Database access layer
 - table/ → Exposed table definitions
 - dto/ → Request/response models
 - model/ → Core domain models
 - di/ → Dependency injection modules
 - serialization/ → Custom serializers
 - utils/ → Utilities (JWT helpers, UUID parsing)

## 📈 Design Decisions

- UUID primary keys for safer distributed scaling
- Layered architecture instead of direct DAO usage in routes
- Service abstraction for third-party integrations (S3, Google)
- DTO separation from internal models for API safety
- SQL migrations for schema evolution
- Cursor-based pagination for feed scalability

## 🔐 Security Considerations

- Stateless JWT authentication
- Refresh token support
- Secure password hashing
- Route-level authorization
- External service abstraction for easier mocking & testing

## 🌱 Future Improvements

- Rate limiting middleware
- Caching layer (Redis)
- Centralized logging aggregation
- CI/CD pipeline
- Horizontal scaling setup
- Monitoring dashboards
