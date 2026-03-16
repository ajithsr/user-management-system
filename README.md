
# User Management System — Kotlin Multiplatform

A high-fidelity **cross-platform User Management System** built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**, demonstrating how modern AI tooling enables developers to focus on **architecture, UX, and quality instead of boilerplate**.

This project was intentionally developed using **Claude Code** as an AI engineering partner to accelerate implementation while maintaining production-grade architectural standards.

---

## ✨ Project Goals

The objective was to build a production-ready application that:

* Shares **100% UI** across Android and iOS
* Implements **offline-first architecture**
* Demonstrates strong **Clean Architecture** principles
* Provides polished UX interactions
* Maintains high testability
* Uses AI responsibly as a development accelerator

---

## 🧠 AI-Assisted Development

This project was built using **Claude Code** as a collaborative coding assistant.

AI was **not used as a code generator alone**, but as an architectural partner.

### How AI Was Leveraged

Claude Code was guided using structured prompts to:

* Design system architecture before implementation
* Generate boilerplate safely within KMP constraints
* Enforce platform boundaries (`commonMain` purity)
* Implement offline-first repository patterns
* Create test scaffolding and edge-case handling
* Perform architectural audits and refactoring passes

Development followed an iterative workflow:

```
Plan → Generate → Review → Refactor → Test → Audit
```

Each generated component was manually reviewed and curated to ensure consistency and production quality.

---

## 🏗 Architecture Overview

The project follows **Clean Architecture + MVI**.

```
Compose Multiplatform UI
        ↓
Shared ViewModels (MVI)
        ↓
Use Cases (Domain)
        ↓
Repository (Offline-First)
   ↙                  ↘
Local Cache        Remote API
(SQLDelight)        (Ktor)
```

### Key Architectural Decisions

| Decision                    | Reason                                |
| --------------------------- | ------------------------------------- |
| Clean Architecture          | Ensures scalability & testability     |
| MVI State Model             | Predictable UI state & easier testing |
| Shared ViewModels           | Maximum code reuse across platforms   |
| Offline-First               | Better UX and resilience              |
| Flow-based state            | Reactive Compose integration          |
| Dependency Injection (Koin) | Lightweight KMP support               |

---

## 📱 Technology Stack

### Core

* Kotlin Multiplatform (Android + iOS)
* Compose Multiplatform (Shared UI)

### Data Layer

* Ktor — Networking
* SQLDelight — Local persistence
* Kotlinx Serialization

### Architecture

* Clean Architecture
* MVI (Model–View–Intent)
* Koin Dependency Injection

### Testing

* Kotlin Test
* Coroutine Test APIs
* Ktor MockEngine
* Compose UI Testing

---

## 🌐 API

The application integrates with:

**DummyJSON Users API**

https://dummyjson.com/docs/users

Key endpoints:

* `GET /users?limit=X&skip=Y`
* `POST /users/add`
* `DELETE /users/{id}`

Pagination is internally handled by the repository to fetch the **last page** of users as required.

---

## 📦 Project Structure

```
shared/
 ├── core/
 │   ├── network/
 │   ├── database/
 │   └── di/
 │
 ├── domain/
 │   ├── model/
 │   ├── repository/
 │   └── usecase/
 │
 ├── data/
 │   ├── remote/
 │   ├── local/
 │   ├── mapper/
 │   └── repository/
 │
 └── presentation/
     ├── userfeed/
     ├── adduser/
     └── components/

androidApp/
iosApp/
```

---

## 🔄 Offline-First Strategy

The local database acts as the **single source of truth**.

```
UI observes DB Flow
        ↓
Repository syncs network
        ↓
Database updates trigger UI refresh
```

Benefits:

* Works without internet
* Instant UI updates
* Predictable state flow

---

## ⭐ Core Features

### Smart User Feed

* Loads users from last API page
* Relative timestamps computed in shared logic
* Shimmer loading states
* Graceful offline handling

### Adaptive Add User Flow

* Real-time validation
* Optimistic updates
* Immediate UI insertion

### Delete with Undo

* Animated removal
* Snackbar undo window
* Delayed remote deletion

### Adaptive Layout

* Portrait: Single list
* Landscape/Tablet: Master-detail layout
* Shared Compose UI

---

## 🧩 Kotlin Multiplatform Strategy

* Business logic entirely in `commonMain`
* Minimal `expect/actual` usage
* Platform code limited to entry points
* Shared ViewModels across platforms

---

## 🧪 Testing Strategy

### Unit Tests

* ViewModel reducers
* Validation logic
* Relative time formatting
* Undo timing behavior

### Integration Tests

* Repository + API using mocked Ktor engine
* Offline fallback scenarios

### UI Tests

* Add user flow
* Delete + Undo interaction
* Adaptive layout behavior

---

## 🎨 UX Principles

The app aims to feel “premium”:

* Material 3 design
* Dark mode ready
* Smooth animations
* Shimmer placeholders
* Immediate feedback interactions

---

## 🚀 AI Orchestration Approach

Instead of replacing engineering decisions, AI was used to:

* accelerate boilerplate generation
* explore architectural alternatives
* validate edge cases
* improve code quality through iterative audits

All outputs were curated to ensure:

* architectural consistency
* maintainability
* platform correctness

---

## ✅ Production Readiness Considerations

* Clear separation of concerns
* Testable domain logic
* Offline resilience
* Reactive state handling
* Scalable module structure

---
