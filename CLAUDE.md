# Mafia Online — Claude Code Guide

## Project Overview
A web-based multiplayer Mafia game. Backend is Spring Boot (Java 21), frontend is vanilla JavaScript. Players create/join rooms, get assigned roles (Mafia, Detective, Doctor, Citizen), and play through day/voting/night phases until a win condition is met.

## Stack
- **Backend**: Spring Boot 3, Java 21, Maven
- **Frontend**: Vanilla JS (`client/script.js`), HTML5, CSS3
- **Real-time**: WebSocket (`RoomWebSocketHandler`)
- **Testing**: JUnit 5, Spring Boot Test, Mockito

## Commands

### Run the server
```bash
cd server
./mvnw spring-boot:run
# App available at http://localhost:8080
```

### Run tests
```bash
cd server
./mvnw test
```

### Build JAR
```bash
cd server
./mvnw clean package
# Output: server/target/*.jar
```

## Architecture

```
client/
  script.js         # MafiaGameClient class — all UI and API calls

server/src/main/java/com/mafia/game/
  GameApplication.java          # Spring Boot entry point
  controller/
    GameController.java         # /api/game/** — start, vote, night actions, chat
    RoomController.java         # /api/rooms/** — create, join, leave
    HomeController.java         # GET / — serves index.html
  service/
    GameService.java            # Core game logic: phases, roles, win conditions
    RoomService.java            # Room CRUD, player auth, chat
    CaptchaService.java         # CAPTCHA verification on join
  room/
    Room.java                   # Room state: players, phase, messages
    RoomManager.java            # In-memory room registry (@Component)
  model/
    Player.java                 # Player state + Role enum (MAFIA/DETECTIVE/DOCTOR/CITIZEN)
    GamePhase.java              # Phase enum: LOBBY→DAY→VOTING→NIGHT_MAFIA→NIGHT_DOCTOR→NIGHT_DETECTIVE→ENDED
    ChatMessage.java            # Chat message + Channel enum
  websocket/
    RoomWebSocketHandler.java   # Broadcasts room state updates to connected clients
  config/
    WebSocketConfig.java        # Registers WebSocket endpoint
    WebConfig.java              # CORS config
```

## Game Phase Flow
`LOBBY` → `DAY` → `VOTING` → `NIGHT_MAFIA` → `NIGHT_DOCTOR` → `NIGHT_DETECTIVE` → back to `DAY` (or `ENDED`)

- Night phases auto-advance via `GameService.checkAutoAdvance()` once all required actions are submitted
- Win condition checked in `GameService.checkWinCondition()` after each resolution

## Auth
Players get a bearer token (`Player.secret`) on join. All game actions require this token, verified by `RoomService.verifyToken()`.

## Key God Nodes (most connected)
- `MafiaGameClient` — orchestrates all frontend UI and API calls (58 edges)
- `GameController` — bridges frontend to game/room services (36 edges)
- `GameService` — all game logic (26 edges)
- `RoomService` — room management, auth, chat (21 edges)

## Knowledge Graph
A graphify knowledge graph lives in `graphify-out/`.
- Before answering architecture questions, read `graphify-out/GRAPH_REPORT.md`
- After modifying code, run `python -m graphify update .` to keep graph current (AST-only, no API cost)

## graphify
This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `python -m graphify update .` to keep the graph current (AST-only, no API cost)
