# Mafia Online — AI Agent Guide

## Project
Web-based multiplayer Mafia game. Spring Boot (Java 21) backend + vanilla JavaScript frontend.

## Build & Test
```bash
cd server && ./mvnw test          # run all tests
cd server && ./mvnw spring-boot:run  # start server on :8080
cd server && ./mvnw clean package    # build JAR → server/target/
```

All tests must pass before committing. Do not skip tests.

## Structure
```
client/script.js                  — frontend (MafiaGameClient class)
server/src/main/java/com/mafia/game/
  controller/GameController.java  — game actions API
  controller/RoomController.java  — room management API
  service/GameService.java        — game logic (phases, roles, win conditions)
  service/RoomService.java        — room CRUD, player auth, chat
  room/Room.java                  — room state
  room/RoomManager.java           — in-memory room registry
  model/Player.java               — player + Role enum
  model/GamePhase.java            — phase enum
  websocket/RoomWebSocketHandler.java — real-time broadcasts
```

## Key Invariants
- Players authenticate with a bearer token (`Player.secret`) set at join time
- All game actions must call `RoomService.verifyToken()` before proceeding
- `GameService.checkAutoAdvance()` triggers phase transitions automatically when all actions are submitted
- `GameService.checkWinCondition()` is called after every night resolution and vote resolution
- Night phases run in order: NIGHT_MAFIA → NIGHT_DOCTOR → NIGHT_DETECTIVE → back to DAY

## API Endpoints
```
POST /api/rooms                        — create room
GET  /api/rooms                        — list rooms
POST /api/rooms/join                   — join by code
POST /api/rooms/{id}/leave             — leave room
POST /api/rooms/{id}/start             — start game
POST /api/game/{id}/night-action       — mafia kill vote
POST /api/game/{id}/doctor-protect     — doctor protection
POST /api/game/{id}/detective-investigate — detective query
POST /api/game/{id}/vote               — day vote
POST /api/game/{id}/skip-vote          — skip vote
POST /api/game/{id}/chat               — send chat message
GET  /api/game/{id}/chat               — get chat messages
GET  /api/game/{id}/role               — get own role
```

## Knowledge Graph
`graphify-out/graph.json` contains the full knowledge graph. `graphify-out/GRAPH_REPORT.md` has a plain-language summary of architecture, god nodes, and surprising connections. Read it before making structural changes.

Update after code changes: `python -m graphify update .`
