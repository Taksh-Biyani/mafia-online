# Graph Report - C:/Coding/Mafia  (2026-04-17)

## Corpus Check
- Corpus is ~15,366 words - fits in a single context window. You may not need a graph.

## Summary
- 414 nodes · 892 edges · 20 communities detected
- Extraction: 74% EXTRACTED · 26% INFERRED · 0% AMBIGUOUS · INFERRED: 233 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Game Logic Service|Game Logic Service]]
- [[_COMMUNITY_Room & Chat Service|Room & Chat Service]]
- [[_COMMUNITY_Game API Controllers|Game API Controllers]]
- [[_COMMUNITY_Frontend Client|Frontend Client]]
- [[_COMMUNITY_Player & Room Models|Player & Room Models]]
- [[_COMMUNITY_Game Controller Tests|Game Controller Tests]]
- [[_COMMUNITY_Room API & WebSocket Config|Room API & WebSocket Config]]
- [[_COMMUNITY_Game Phase Transitions|Game Phase Transitions]]
- [[_COMMUNITY_Room Manager & Auth|Room Manager & Auth]]
- [[_COMMUNITY_WebSocket Handler|WebSocket Handler]]
- [[_COMMUNITY_Spring Boot Entry Point|Spring Boot Entry Point]]
- [[_COMMUNITY_Home Controller|Home Controller]]
- [[_COMMUNITY_Application Context Tests|Application Context Tests]]
- [[_COMMUNITY_Web Config|Web Config]]
- [[_COMMUNITY_Vote Actions|Vote Actions]]
- [[_COMMUNITY_Chat Message Model|Chat Message Model]]
- [[_COMMUNITY_Game Phase Enum|Game Phase Enum]]
- [[_COMMUNITY_Captcha Service|Captcha Service]]
- [[_COMMUNITY_WebSocket Handler Instance|WebSocket Handler Instance]]
- [[_COMMUNITY_Context Load Test|Context Load Test]]

## God Nodes (most connected - your core abstractions)
1. `MafiaGameClient` - 58 edges
2. `GameServiceTest` - 48 edges
3. `GameController` - 36 edges
4. `GameControllerTest` - 34 edges
5. `GameService` - 26 edges
6. `RoomService` - 21 edges
7. `RoomServiceTest` - 20 edges
8. `RoomTest` - 19 edges
9. `RoomController` - 18 edges
10. `Room` - 18 edges

## Surprising Connections (you probably didn't know these)
- `REST API Endpoints` --conceptually_related_to--> `RoomService`  [INFERRED]
  README.md → C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\service\RoomService.java
- `CreateRoomRequest` --semantically_similar_to--> `JoinRoomRequest`  [INFERRED] [semantically similar]
  C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\CreateRoomRequest.java → C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\JoinRoomRequest.java
- `CreateRoomResponse` --semantically_similar_to--> `JoinRoomResponse`  [INFERRED] [semantically similar]
  C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\CreateRoomResponse.java → C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\JoinRoomResponse.java
- `NightActionRequest` --semantically_similar_to--> `VoteRequest`  [INFERRED] [semantically similar]
  C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\NightActionRequest.java → C:\Coding\Mafia\mafia-online\server\src\main\java\com\mafia\game\api\VoteRequest.java
- `MafiaGameClient` --calls--> `MafiaGameClient.createRoom`  [EXTRACTED]
  C:\Coding\Mafia\mafia-online\client\script.js → client/script.js

## Hyperedges (group relationships)
- **Night Action Request Flow** — script_submitnightaction, nightactionrequest_nightactionrequest, gamecontroller_gamecontroller [EXTRACTED 1.00]
- **Room Creation API Flow** — script_createroom, createroomrequest_createroomrequest, roomcontroller_roomcontroller, createroomresponse_createroomresponse [EXTRACTED 1.00]
- **WebSocket Real-time Room Update** — script_connectroomws, websocketconfig_websocketconfig, websocketconfig_roomwebsockethandler, script_updateroomdisplay [EXTRACTED 0.95]
- **Night Phase Sequential Sub-Phase Flow** — gamephase_night_mafia, gamephase_night_doctor, gamephase_night_detective, gameservice_checkautoadvance, gameservice_resolvenight [EXTRACTED 0.95]
- **Player Bearer Token Authentication Flow** — player_secret, roomservice_verifytoken, gamecontrollertest_test [INFERRED 0.85]
- **Full Game Phase Lifecycle** — gamephase_lobby, gamephase_day, gamephase_voting, gamephase_night_mafia, gamephase_ended [EXTRACTED 0.95]

## Communities

### Community 0 - "Game Logic Service"
Cohesion: 0.09
Nodes (2): GameService, GameServiceTest

### Community 1 - "Room & Chat Service"
Cohesion: 0.06
Nodes (6): REST API Endpoints, Mafia Online README, Project Structure (Spring Boot + Vanilla JS), RoomManagerTest, RoomService, RoomServiceTest

### Community 2 - "Game API Controllers"
Cohesion: 0.07
Nodes (23): ChatMessage.Channel, ChatMessage, ChatMessageRequest, GameController, NightActionRequest, MafiaGameClient._connectRoomWs, MafiaGameClient.endDay, MafiaGameClient.fetchAndRenderChat (+15 more)

### Community 3 - "Frontend Client"
Cohesion: 0.1
Nodes (1): MafiaGameClient

### Community 4 - "Player & Room Models"
Cohesion: 0.07
Nodes (9): GamePhase Enum, Player, Room.isFull, Room.canStartGame, Room.getAlivePlayers, Room, RoomTest, RoomTest (+1 more)

### Community 5 - "Game Controller Tests"
Cohesion: 0.14
Nodes (1): GameControllerTest

### Community 6 - "Room API & WebSocket Config"
Cohesion: 0.09
Nodes (11): CaptchaService, CreateRoomRequest, CreateRoomResponse, JoinRoomRequest, JoinRoomResponse, RoomController, MafiaGameClient.createRoom, MafiaGameClient.joinRoom (+3 more)

### Community 7 - "Game Phase Transitions"
Cohesion: 0.09
Nodes (28): GamePhase.DAY, GamePhase.ENDED, GamePhase.NIGHT_DETECTIVE, GamePhase.NIGHT_DOCTOR, GamePhase.NIGHT_MAFIA, GamePhase.VOTING, GameService.assignRoles, GameService.checkAutoAdvance (+20 more)

### Community 8 - "Room Manager & Auth"
Cohesion: 0.09
Nodes (18): GameControllerTest, GamePhase.LOBBY, Player.secret Bearer Token, Room.findPlayer, RoomManager.cleanup (PreDestroy), RoomManager.createRoom, RoomManager.getRoom, RoomManager.getRoomByJoinCode (+10 more)

### Community 9 - "WebSocket Handler"
Cohesion: 0.6
Nodes (1): RoomWebSocketHandler

### Community 10 - "Spring Boot Entry Point"
Cohesion: 0.67
Nodes (1): GameApplication

### Community 11 - "Home Controller"
Cohesion: 0.67
Nodes (1): HomeController

### Community 12 - "Application Context Tests"
Cohesion: 0.67
Nodes (1): GameApplicationTests

### Community 13 - "Web Config"
Cohesion: 1.0
Nodes (1): WebConfig

### Community 14 - "Vote Actions"
Cohesion: 1.0
Nodes (2): GameService.skipVote, GameService.submitVote

### Community 15 - "Chat Message Model"
Cohesion: 1.0
Nodes (0): 

### Community 16 - "Game Phase Enum"
Cohesion: 1.0
Nodes (0): 

### Community 17 - "Captcha Service"
Cohesion: 1.0
Nodes (1): CaptchaService.verify

### Community 18 - "WebSocket Handler Instance"
Cohesion: 1.0
Nodes (1): RoomWebSocketHandler

### Community 19 - "Context Load Test"
Cohesion: 1.0
Nodes (1): GameApplicationTests.contextLoads

## Knowledge Gaps
- **29 isolated node(s):** `WebConfig`, `MafiaGameClient.showGameOverScreen`, `ChatMessage.Channel`, `GamePhase Enum`, `GamePhase.VOTING` (+24 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Web Config`** (2 nodes): `WebConfig.java`, `WebConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Vote Actions`** (2 nodes): `GameService.skipVote`, `GameService.submitVote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Chat Message Model`** (1 nodes): `ChatMessage.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Game Phase Enum`** (1 nodes): `GamePhase.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Captcha Service`** (1 nodes): `CaptchaService.verify`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `WebSocket Handler Instance`** (1 nodes): `RoomWebSocketHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Context Load Test`** (1 nodes): `GameApplicationTests.contextLoads`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GameController` connect `Game API Controllers` to `Room & Chat Service`, `Room API & WebSocket Config`?**
  _High betweenness centrality (0.292) - this node is a cross-community bridge._
- **Why does `MafiaGameClient` connect `Frontend Client` to `Game API Controllers`, `Home Controller`, `Room API & WebSocket Config`?**
  _High betweenness centrality (0.233) - this node is a cross-community bridge._
- **Why does `RoomService` connect `Room & Chat Service` to `Game Logic Service`, `Room Manager & Auth`, `Game API Controllers`, `Game Phase Transitions`?**
  _High betweenness centrality (0.219) - this node is a cross-community bridge._
- **What connects `WebConfig`, `MafiaGameClient.showGameOverScreen`, `ChatMessage.Channel` to the rest of the system?**
  _29 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Game Logic Service` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Room & Chat Service` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `Game API Controllers` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._