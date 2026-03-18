# Mafia Online

## Auto-Approved Commands (no permission needed)

These commands can be run without asking the user first:

| Command | Scope |
|---|---|
| `ls` / `dir` | List directory contents |
| `cd` | Change working directory |
| `mkdir` | Create directories |
| `git status` | Show working tree status |
| `git log` | View commit history |
| `git diff` | View unstaged/staged changes (read-only) |
| `git branch` | List branches |
| `mvnw compile` | Compile server code |
| `mvnw test` | Run tests |
| `cat` / `head` / `tail` | Read file contents (prefer Read tool) |
| `grep` / `find` | Search files (prefer Grep/Glob tools) |

Everything else (git push, git reset, file deletion, server restart, package installs, etc.) requires explicit user approval.

Web-based multiplayer Mafia party game. Spring Boot 4.0.2 / Java 21 backend + Vanilla JS frontend.

## Running the project

- **Start server:** run `server/run-server.bat` (clean → package → launches jar)
- **Compile only:** `cd server && ./mvnw compile`
- **Run tests:** `cd server && ./mvnw test`
- **Access game:** http://localhost:8080

## Project structure

```
mafia-online/
├── client/                        # Frontend (served as static files by Spring)
│   ├── index.html                 # SPA — 5 sections: Main Menu, Create Room, Join Room, Room List, Game Room
│   ├── script.js                  # MafiaGameClient class — all frontend logic
│   └── styles.css                 # Responsive styles, purple gradient theme
│
└── server/
    ├── pom.xml
    └── src/main/java/com/mafia/game/
        ├── GameApplication.java
        ├── config/
        │   ├── WebConfig.java         # Empty MVC config
        │   └── WebSocketConfig.java   # STUB — not implemented
        ├── model/
        │   ├── Player.java            # id, name, role, alive, roomId
        │   ├── GamePhase.java         # Enum: LOBBY, NIGHT, DAY, VOTING, ENDED
        │   └── ChatMessage.java       # record: playerName, message, timestamp, channel (GENERAL/MAFIA_NIGHT/DEAD)
        ├── room/
        │   ├── Room.java              # id, joinCode, phase, minPlayers, maxPlayers, hostId, dayDurationSeconds, players, votes, chatMessages (@JsonIgnore)
        │   └── RoomManager.java       # In-memory store (two ConcurrentHashMaps: by UUID + by join code)
        ├── service/
        │   ├── RoomService.java       # create/join/leave/list rooms; postMessage (rate-limited) + getMessages (per-player filtered)
        │   └── GameService.java       # startGame, assignRoles, submitNightAction, submitDoctorProtect, submitDetectiveInvestigate, endDay, submitVote, resolveVoting, checkWinCondition
        ├── controller/
        │   ├── HomeController.java    # Serves index.html at /
        │   ├── RoomController.java    # REST: room CRUD
        │   └── GameController.java    # REST: game actions + chat
        └── api/
            ├── CreateRoomRequest.java  # joinCode, playerName, minPlayers, maxPlayers, dayDurationSeconds
            ├── CreateRoomResponse.java # room + creator player
            ├── JoinRoomRequest.java
            ├── NightActionRequest.java
            ├── VoteRequest.java
            └── ChatMessageRequest.java # message: String
```

## REST API

### Rooms
```
POST   /api/rooms                             Create room (auto-joins creator, sets hostId)
GET    /api/rooms                             List LOBBY-phase rooms
GET    /api/rooms/{roomId}                    Get room details
POST   /api/rooms/{roomId}/join               Join by room ID
POST   /api/rooms/join?code=...               Join by join code
POST   /api/rooms/{roomId}/leave?playerId=... Leave room
DELETE /api/rooms/{roomId}                    Delete room
```

### Game
```
POST   /api/rooms/{roomId}/start?playerId=...             Start game (host only — 403 if not)
GET    /api/rooms/{roomId}/state                          Get current state
POST   /api/rooms/{roomId}/night/action?playerId=...      Mafia eliminates target (NIGHT only)
POST   /api/rooms/{roomId}/night/end                      Manually end night phase
POST   /api/rooms/{roomId}/day/end?playerId=...           End discussion, start voting (host only)
POST   /api/rooms/{roomId}/vote?voterId=...               Submit vote (VOTING only; auto-resolves when all alive voted)
POST   /api/rooms/{roomId}/vote/resolve?playerId=...      Force resolve votes early (host only)
GET    /api/rooms/{roomId}/players/{playerId}             Get player details (used to reveal own role)
POST   /api/rooms/{roomId}/night/protect?playerId=...     Doctor protects a target (NIGHT only)
POST   /api/rooms/{roomId}/night/investigate?playerId=... Detective investigates a target — returns Player with role (NIGHT only)
POST   /api/rooms/{roomId}/chat?playerId=...              Post chat message (rate-limited: 1500ms / 200 chars)
GET    /api/rooms/{roomId}/chat?playerId=...              Get chat messages (filtered by player role/phase)
```

## Game logic

**Phase flow:** LOBBY → NIGHT → DAY → VOTING → ENDED (or back to NIGHT)

**Role assignment:** ~25% MAFIA (min 1), DETECTIVE if ≥5 players, DOCTOR if ≥6 players, rest CITIZEN

**Win conditions:**
- Citizens win: all MAFIA eliminated
- Mafia wins: mafia count ≥ non-mafia alive count

**Day timer:** `dayDurationSeconds` stored in Room (default 30). Frontend tracks start time on DAY entry; host's client auto-calls `day/end` at 0. Timer pulses red in last 10s.

**Voting:** Votes stored as `Map<UUID, UUID>` (voterId → targetId) in Room. Auto-resolves when all alive players vote. Most-voted player eliminated.

## What's not implemented yet

- **Detective UI** — endpoint exists (`/night/investigate`), no frontend for it
- **Doctor UI** — endpoint exists (`/night/protect`), no frontend for it
- **Game end screen** — shows generic "Game Over"; no winner announcement or role reveal
- **Night timer** — no auto-advance; mafia must act or host calls `/night/end`
- **WebSocket** — `WebSocketConfig.java` is an empty stub; using 2s polling instead
- **Persistence** — all in-memory, wiped on server restart
- **Security** — no auth; role and vote map exposed in room JSON
- **Tests** — GameService and GameController have zero test coverage

## Chat system

Channels: `GENERAL` (lobby/day/voting), `MAFIA_NIGHT` (mafia during night), `DEAD` (dead players only).
Spam rules: 1500ms cooldown, 200-char max. Messages capped at 200 per room (oldest dropped).
Frontend: slide-out panel (top-right button), auto-opens on room entry, polled every 2s.
Dead players see GENERAL + DEAD. Alive non-MAFIA during NIGHT have no chat access.

## Conventions

- Don't add or modify comments/docstrings on code that wasn't changed
- Don't mock dependencies in tests — use `@SpringBootTest` integration tests as the existing tests do
- Frontend base URL is hardcoded to `http://localhost:8080` — don't change it without a plan for deployment config
