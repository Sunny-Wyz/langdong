# 备件管理系统 (Spare Parts Management System)

## Project Overview

A spare parts management system built with a Spring Boot backend and Vue 2 frontend.

- **Backend**: Spring Boot 3.2 + MyBatis + Spring Security + JWT | Java 17 | MySQL
- **Frontend**: Vue 2 + Element UI + Vuex + Vue Router | Node/npm

## Repository Structure

```
backend/      # Spring Boot application (Maven)
frontend/     # Vue 2 SPA (Vue CLI)
sql/          # Database initialization scripts
docs/         # Documentation
```

## Development Setup

### Database
```bash
mysql -u root -p < sql/init.sql
```

### Backend
```bash
# Edit backend/src/main/resources/application.yml — set DB password
cd backend
mvn spring-boot:run
# Runs on http://localhost:8080
```

### Frontend
```bash
cd frontend
npm install
npm run serve
# Runs on http://localhost:3000
```

Default credentials: `admin` / `123456`

## Backend Architecture

**Package**: `com.langdong.spare`

| Layer | Location |
|---|---|
| Controllers | `controller/` — REST endpoints |
| Entities | `entity/` — JPA/MyBatis models |
| DTOs | `dto/` — Request/response objects |
| Mappers | `mapper/` — MyBatis interfaces |
| Config | `config/` — Spring Security config |
| Utils | `util/` — JWT utilities |

**Key configs** (`application.yml`):
- DB: `spare_db` on `localhost:3306`
- JWT expiration: 86400000ms (24h)
- MyBatis mappers: `src/main/resources/mapper/*.xml`

## Frontend Architecture

**Stack**: Vue 2, Element UI, Vuex, Vue Router, Axios

| Directory | Purpose |
|---|---|
| `src/views/` | Page components (Login, Home, SparePartList) |
| `src/router/` | Vue Router configuration |
| `src/store/` | Vuex state management |
| `src/utils/` | Shared utilities (e.g., Axios interceptors) |

## Common Commands

```bash
# Backend build
cd backend && mvn clean package

# Frontend build for production
cd frontend && npm run build

# Run backend tests
cd backend && mvn test
```

## Key Notes

- JWT tokens are stored client-side and sent as `Authorization: Bearer <token>` headers
- All API routes (except `/api/auth/**`) require authentication
- The frontend dev server proxies `/api` to `http://localhost:8080`
- Do not commit real DB passwords — `application.yml` contains a placeholder

## AI Assistant Guidelines (Memory)
- **Language Preference**: 以后的所有工作计划 (工作计划, implementation plans, tasks) 都必须用中文写。
- **记录问答与解决方案**: 遇到报错、Bug 排查及问题解决时，自动使用 `Q - 问题 - 解决方案` 的格式将记录写入到 `/Users/weiyaozhou/Documents/langdong/ QA.md` 中。
- **记录新增功能点**: 开发、修改或增强新的业务功能时，自动使用 `F - 功能描述 - 落实情况` 的格式将记录补充写入到 `/Users/weiyaozhou/Documents/langdong/function.md` 中。
