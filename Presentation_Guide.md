# 🎓 Presentation Guide: Secure Chat Application

This document provides a comprehensive overview of the **Secure Chat Application** project, specifically organized for an Object-Oriented Programming (OOP) course presentation.

---

## 🌟 Project Overview
The **Secure Chat Application** is a real-time messaging platform that emphasizes security and modern software architecture. It features bidirectional communication via WebSockets, end-to-end message encryption, secure user authentication, and an integrated AI assistant.

---

## 🛠️ Technology Stack

### Backend (Java)
- **Language:** Java 17
- **Build Tool:** Maven
- **WebSockets:** `Java-WebSocket` for real-time communication.
- **Security:** `jBCrypt` for password hashing; Java Crypto API for AES-256-CBC encryption.
- **Data Handling:** `Gson` for JSON processing; `dotenv-java` for environment variables.

### Frontend (React)
- **Framework:** React 18
- **Build Tool:** Vite
- **Styling:** CSS3

---

## 🧬 Core OOP Concepts Used

### 1. Encapsulation
- **Implementation:** Private fields in model classes (`User`, `Message`, `ChatRoom`) with public getters and setters.
- **Example:** `ChatRoom.java` protects the internal members set, exposing only controlled methods like `addMember()` and `removeMember()` to maintain data integrity.

### 2. Abstraction
- **Implementation:** Complex logic is hidden behind simple interfaces.
- **Example:** `EncryptionService.java` abstracts the intricate details of AES encryption (IV generation, padding, Base64 encoding) into simple `encrypt()` and `decrypt()` methods.

### 3. Inheritance
- **Implementation:** Extending existing library classes to build custom functionality.
- **Example:** `ChatServer.java` extends `WebSocketServer`, inheriting robust networking logic while overriding specific methods (`onOpen`, `onMessage`) to handle chat-specific protocols.

### 4. Polymorphism
- **Implementation:** Dynamic method dispatch and factory methods.
- **Example:** `ChatService.java` uses polymorphic routing in `routeMessage()`, where the behavior changes based on the `MessageType` (Broadcast, Private, Group, etc.).

---

## 📐 Design Patterns

- **Singleton Pattern:** Implemented in `LoggerService.java` to provide a single, centralized logging point for the entire application.
- **Repository Pattern:** `UserRepository` and `MessageRepository` abstract the file-based JSON storage, separating data persistence from business logic.
- **Dependency Injection (DI):** Services are instantiated and "wired" together in the `ChatServer` constructor, promoting loose coupling and better testability.
- **Observer / Callback Pattern:** The `MessageDelivery` interface in `ChatService` allows the server to handle network delivery without the service needing to know the details of the WebSocket implementation.

---

## 🧮 Algorithms & Core Logic

- **AES-256-CBC Encryption:** Messages are encrypted before storage. A unique 16-byte Initialization Vector (IV) is generated for every message, ensuring that identical messages produce different ciphertexts.
- **BCrypt Hashing:** Passwords are never stored in plain text. They are hashed with a salt (work factor 12) using the BCrypt algorithm.
- **Thread Safety:** The server uses `ConcurrentHashMap` and `synchronized` blocks to ensure data consistency across multiple simultaneous user connections.
- **Asynchronous AI Processing:** AI responses are handled in a separate thread to prevent the main server loop from blocking while waiting for external API responses.

---

## 🚀 How to Run the Project

### Backend
1. **Navigate to folder:**
   ```powershell
   cd "OOP_Project/backend"
   ```
2. **Setup:** Ensure a `.env` file exists with `ENCRYPTION_KEY`, `SERVER_PORT`, and `OPENROUTER_API_KEY`.
3. **Run:**
   ```powershell
   .\mvnw.cmd clean compile exec:java
   ```

### Frontend
1. **Navigate to folder:**
   ```powershell
   cd "OOP_Project/frontend"
   ```
2. **Install dependencies:**
   ```bash
   npm install
   ```
3. **Run:**
   ```bash
   npm run dev
   ```
