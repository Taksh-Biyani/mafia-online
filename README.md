# Mafia Online Game

A web-based Mafia game built with Spring Boot and vanilla JavaScript.

## Features

- **Create Rooms**: Set custom join codes and player limits
- **Join Rooms**: Enter room codes to join existing games
- **Room Management**: View available rooms and player lists
- **Game State**: Track game phases and player status
- **Responsive Design**: Works on desktop and mobile devices

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven

### Running the Application

1. **Start the Server**:
   ```bash
   cd server
   ./mvnw spring-boot:run
   ```

2. **Open the Game**:
   - Open your web browser
   - Navigate to `http://localhost:8080`
   - The game interface will load automatically

### How to Play

1. **Main Menu**:
   - **Create New Room**: Set up a new game room with custom settings
   - **Join Room**: Enter a room code to join an existing game
   - **List Available Rooms**: Browse and join open rooms

2. **Creating a Room**:
   - Enter an optional join code (auto-generated if left blank)
   - Set minimum and maximum player counts
   - Click "Create Room" to start

3. **Joining a Room**:
   - Enter the room code (case-insensitive)
   - Enter your player name
   - Click "Join Room"

4. **In-Game**:
   - View current room status and players
   - Start the game when enough players have joined
   - Leave the room at any time

## API Endpoints

The frontend communicates with these REST endpoints:

- `GET /` - Main game page
- `POST /api/rooms` - Create a new room
- `GET /api/rooms` - List available rooms
- `GET /api/rooms/{roomId}` - Get room details
- `POST /api/rooms/join` - Join room by code
- `POST /api/rooms/{roomId}/leave` - Leave a room
- `POST /api/rooms/{roomId}/start` - Start the game

## Project Structure

```
mafia-online/
├── client/                 # Frontend files
│   ├── index.html         # Main game interface
│   ├── styles.css         # Game styling
│   └── script.js          # Game logic and API calls
├── server/                # Spring Boot backend
│   ├── src/main/java/com/mafia/game/
│   │   ├── controller/    # REST controllers
│   │   ├── model/         # Data models
│   │   ├── room/          # Room management
│   │   ├── service/       # Business logic
│   │   └── GameApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── src/test/java/     # Unit tests
└── README.md
```

## Technologies Used

- **Backend**: Spring Boot, Java 21
- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **Build Tool**: Maven
- **Testing**: JUnit 5

## Development

### Running Tests
```bash
cd server
./mvnw test
```

### Building for Production
```bash
cd server
./mvnw clean package
```

The built JAR will be in `server/target/`.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

Thank you for playing Mafia Online! I hope you enjoy it as much as we did creating this game. Your feedback and contributions are what make this project great. Join our community, report issues, suggest features, or just share your experience playing the game. Let's make Mafia Online even better together!
