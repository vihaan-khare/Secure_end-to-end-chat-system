# 🔐 SecureChat — Real-Time Encrypted Chat Application

> A full-stack, real-time chat application built as an Object-Oriented Programming (OOP) course project.  
> Features end-to-end encrypted private messaging, invite-only group rooms, AI chat integration, and a modern React frontend — all over a raw WebSocket protocol.

---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture Overview](#-architecture-overview)
- [Project Structure](#-project-structure)
- [OOP Design Principles](#-oop-design-principles)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Backend Setup](#backend-setup)
  - [Frontend Setup](#frontend-setup)
- [Environment Variables](#-environment-variables)
- [WebSocket Protocol](#-websocket-protocol)
- [Security Model](#-security-model)
- [Known Limitations](#-known-limitations)

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔑 **Authentication** | Signup & login with BCrypt-hashed passwords and UUID session tokens |
| 💬 **Public Chat** | Broadcast messages visible to all connected users |
| 🔒 **Private Messaging** | Encrypted 1-on-1 direct messages; only the two parties can read them |
| 🚪 **Invite-Only Rooms** | Create group rooms with a unique Room Code — rooms are invisible until you enter the correct code |
| 🤖 **AI Integration** | Trigger an AI assistant in any chat with `@ai <question>` |
| 👥 **User Search** | Search all registered users (online and offline) by username |
| ⌨️ **Typing Indicators** | Real-time "is typing…" feedback in private and group chats |
| 📜 **Message History** | Persistent chat history loaded on login |
| 🌐 **Real-Time** | All communication runs over a persistent WebSocket connection |

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Core language |
| Java-WebSocket | 1.5.4 | WebSocket server (`org.java_websocket`) |
| Gson | 2.10.1 | JSON serialization / deserialization |
| jBCrypt | 0.4 | Password hashing |
| dotenv-java | 3.0.0 | `.env` file support |
| Maven | 3.x | Build & dependency management |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18.2 | UI framework |
| Vite | 5.0 | Dev server & bundler |
| Vanilla CSS | — | Styling (no frameworks) |
| Browser WebSocket API | — | Real-time communication |

---

## 🏗 Architecture Overview

```
┌─────────────────────────────────────┐
│           React Frontend            │
│  (Vite · React 18 · Vanilla CSS)    │
│                                     │
│  ChatContext (global state)         │
│       │                             │
│  wsClient (WebSocket utility)       │
└──────────────┬──────────────────────┘
               │  ws://localhost:8887
               │  JSON messages
┌──────────────▼──────────────────────┐
│         Java WebSocket Server       │
│              ChatServer             │
│                                     │
│  ┌──────────┐  ┌──────────────────┐ │
│  │AuthService│  │   ChatService    │ │
│  │ (BCrypt) │  │ (routing, rooms) │ │
│  └──────────┘  └──────────────────┘ │
│  ┌──────────┐  ┌──────────────────┐ │
│  │AIService │  │EncryptionService │ │
│  │(Gemini)  │  │  (AES-256-GCM)  │ │
│  └──────────┘  └──────────────────┘ │
│                                     │
│  JSON flat-file persistence         │
│  (users.json · messages.json)       │
└─────────────────────────────────────┘
```

The server is **stateful** — connected clients are tracked via a `ConcurrentHashMap<WebSocket, ClientHandler>`. All message routing, room membership, and session management happen in-process without a database.

---

## 📁 Project Structure

```
OOP_Project/
├── backend/
│   ├── src/main/java/com/chatapp/
│   │   ├── model/
│   │   │   ├── ChatRoom.java        # Group room entity (encapsulated members set)
│   │   │   ├── Message.java         # Message entity with factory methods
│   │   │   └── User.java            # User entity (stores hashed password)
│   │   ├── repository/
│   │   │   ├── UserRepository.java  # JSON-file user persistence
│   │   │   └── MessageRepository.java # JSON-file message persistence + encryption
│   │   ├── security/
│   │   │   └── EncryptionService.java # AES-256-GCM message encryption
│   │   ├── server/
│   │   │   ├── ChatServer.java      # Main WebSocket server & message dispatcher
│   │   │   └── ClientHandler.java   # Per-connection state (username, token, auth)
│   │   ├── service/
│   │   │   ├── AuthService.java     # Signup, login, BCrypt, session tokens
│   │   │   ├── ChatService.java     # Message routing, room management
│   │   │   ├── AIService.java       # Gemini AI integration
│   │   │   └── LoggerService.java   # Singleton structured logger
│   │   └── util/
│   │       └── LoggerService.java
│   ├── src/main/resources/data/
│   │   ├── users.json               # Registered users (hashed passwords)
│   │   └── messages.json            # Encrypted message history
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── Login.jsx            # Login / Signup form
│   │   │   ├── Sidebar.jsx          # User list, rooms, navigation
│   │   │   ├── ChatWindow.jsx       # Active chat view
│   │   │   ├── MessageList.jsx      # Message rendering
│   │   │   ├── MessageInput.jsx     # Send bar with typing indicators
│   │   │   └── RoomModal.jsx        # Create / Join room modal
│   │   ├── context/
│   │   │   └── ChatContext.jsx      # Global state (useReducer + Context API)
│   │   ├── utils/
│   │   │   └── websocket.js         # WebSocket client wrapper (event emitter)
│   │   ├── App.jsx
│   │   ├── App.css                  # Global styles & design system
│   │   └── main.jsx
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── .gitignore
├── Presentation_Guide.md
└── README.md
```

---

## 🎓 OOP Design Principles

This project was built to demonstrate core OOP concepts throughout the codebase.

### 1. Inheritance
`ChatServer` extends `WebSocketServer` (from the Java-WebSocket library), inheriting WebSocket lifecycle callbacks (`onOpen`, `onClose`, `onMessage`, `onError`).

### 2. Encapsulation
- `ChatRoom` keeps its `members` set `private` and only exposes it as an **unmodifiable view** via `getMembers()`. Mutation goes through controlled `addMember()` / `removeMember()` methods.
- `User` stores only a `passwordHash` — the plaintext password is never retained.

### 3. Abstraction
- `ChatService` exposes a clean interface for routing messages and managing rooms. Callers don't know how routing or persistence works internally.
- `MessageDelivery` is an inner functional interface (Strategy pattern) bridging `ChatService` and `ClientHandler` without creating a circular dependency.

### 4. Composition
`ChatServer` is composed of independently testable services:
```
ChatServer
  ├── AuthService   (authentication)
  ├── ChatService   (routing & rooms)
  ├── AIService     (AI responses)
  └── EncryptionService (message encryption)
```

### 5. Singleton Pattern
`LoggerService` uses the Singleton pattern — one shared logger instance across the entire application, obtained via `LoggerService.getInstance()`.

### 6. Observer Pattern
The WebSocket callback model (`onOpen`, `onMessage`, `onClose`) is an implementation of the Observer pattern — the server *reacts* to client events rather than polling.

### 7. Factory Methods
`Message` uses static factory methods for clarity:
```java
Message.broadcast(sender, content)
Message.privateMsg(sender, recipient, content)
Message.groupMsg(sender, roomId, content)
Message.system(content)
Message.aiResponse(content, requestedBy)
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** — [Download](https://adoptium.net/)
- **Maven 3.8+** — bundled via `mvnw` wrapper (no install needed)
- **Node.js 18+** — [Download](https://nodejs.org/)

---

### Backend Setup

```bash
# Navigate to the backend directory
cd backend

# (Optional) Create a .env file for configuration
# See the Environment Variables section below

# Build and run the server
./mvnw exec:java

# On Windows (PowerShell / CMD)
mvnw.cmd exec:java
```

The server starts on **`ws://localhost:8887`** by default.

> **First run note:** `users.json` and `messages.json` are auto-created in `src/main/resources/data/` if they don't exist.

---

### Frontend Setup

```bash
# Navigate to the frontend directory
cd frontend

# Install dependencies
npm install

# Start the development server
npm run dev
```

Open **`http://localhost:5173`** in your browser.

---

## 🔧 Environment Variables

Create a `.env` file inside the `backend/` directory:

```env
# WebSocket server port (default: 8887)
SERVER_PORT=8887

# Google Gemini API key (optional — AI features disabled if absent)
GEMINI_API_KEY=your_gemini_api_key_here

# AES encryption key for message storage (auto-generated if absent)
ENCRYPTION_KEY=your_32_char_secret_key_here
```

> The `.env` file is listed in `.gitignore` and will **never** be committed.

---

## 📡 WebSocket Protocol

All messages are JSON objects with a `"type"` field. The client and server communicate exclusively through this protocol.

### Client → Server

| Type | Payload | Description |
|---|---|---|
| `signup` | `{ username, password }` | Register a new account |
| `login` | `{ username, password }` | Authenticate |
| `message` | `{ content, messageType, recipient?, roomId? }` | Send a message |
| `createRoom` | `{ roomName }` | Create an invite-only group room |
| `joinRoom` | `{ roomId }` | Join a room using its secret code |
| `leaveRoom` | `{ roomId }` | Leave a room |
| `getHistory` | `{ target?, roomId? }` | Fetch message history |
| `getOnline` | — | Request currently online users |
| `getRooms` | — | Request **your** rooms (membership-filtered) |
| `getAllUsers` | — | Request all registered users |
| `typing` | `{ recipient?, roomId? }` | Send a typing indicator |

### Server → Client

| Type | Payload | Description |
|---|---|---|
| `auth` | `{ success, action, username, token, content }` | Auth result |
| `message` | `{ sender, content, messageType, timestamp, ... }` | Incoming message |
| `history` | `{ messages[] }` | Chat history batch |
| `onlineUsers` | `{ users[] }` | Updated online user list |
| `allUsers` | `{ users[{ username, online }] }` | All registered users |
| `roomList` | `{ rooms[] }` | **Only rooms you are a member of** |
| `roomCreated` | `{ roomId, roomName, createdBy }` | New room confirmation |
| `roomJoined` | `{ roomId, roomName }` | Join confirmation |
| `roomLeft` | `{ roomId }` | Leave confirmation |
| `typing` | `{ username }` | Typing indicator from another user |
| `system` | `{ content }` | Server system message |
| `error` | `{ content }` | Error message |

---

## 🔐 Security Model

| Layer | Implementation |
|---|---|
| **Password storage** | BCrypt with cost factor 12 — plaintext passwords are never stored or logged |
| **Session management** | UUID tokens stored server-side in a `ConcurrentHashMap`; invalidated on logout |
| **Message encryption** | AES-256-GCM encryption applied before writing messages to disk |
| **Private messages** | Delivered only to the sender and recipient; never broadcast |
| **Room privacy** | Rooms are completely invisible to non-members. The server returns a generic error for invalid room codes, making room existence non-discoverable |
| **Input validation** | Username (3–20 chars) and password (4+ chars) validated server-side on signup |

---

## ⚠️ Known Limitations

- **No TLS/WSS** — the WebSocket connection is unencrypted in transit. For production, put the server behind a reverse proxy (e.g., Nginx) with TLS.
- **Flat-file persistence** — data is stored in JSON files, not a database. Not suitable for high-volume use.
- **In-memory room state** — group rooms and their memberships are lost on server restart.
- **Single-server** — no horizontal scaling; all state is in-process.
- **AI rate limits** — the Gemini API has request quotas; `@ai` commands may fail if the quota is exceeded.

---

## 👨‍💻 Author

Built for an Object-Oriented Programming course project.  
Repository: [github.com/vihaan-khare/Secure_end-to-end-chat-system](https://github.com/vihaan-khare/Secure_end-to-end-chat-system)
