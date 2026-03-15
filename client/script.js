// Mafia Online Game Client

class MafiaGameClient {
    constructor() {
        this.baseUrl = 'http://localhost:8080';
        this.currentRoomId = null;
        this.currentPlayerId = null;
        this.roomPollingInterval = null;

        this.initializeEventListeners();
        this.showMessage('Welcome to Mafia Online!', 'info');
    }

    initializeEventListeners() {
        // Main menu buttons
        document.getElementById('create-room-btn').addEventListener('click', () => this.showCreateRoom());
        document.getElementById('join-room-btn').addEventListener('click', () => this.showJoinRoom());
        document.getElementById('list-rooms-btn').addEventListener('click', () => this.showRoomList());

        // Create room section
        document.getElementById('submit-create-room').addEventListener('click', () => this.createRoom());
        document.getElementById('back-from-create').addEventListener('click', () => this.showMainMenu());

        // Join room section
        document.getElementById('submit-join-room').addEventListener('click', () => this.joinRoom());
        document.getElementById('back-from-join').addEventListener('click', () => this.showMainMenu());

        // Room list section
        document.getElementById('refresh-rooms').addEventListener('click', () => this.listRooms());
        document.getElementById('back-from-list').addEventListener('click', () => this.showMainMenu());

        // Game room section
        document.getElementById('start-game-btn').addEventListener('click', () => this.startGame());
        document.getElementById('leave-room-btn').addEventListener('click', () => this.leaveRoom());
    }

    // Navigation methods
    showMainMenu() {
        this.hideAllSections();
        document.getElementById('main-menu').classList.remove('hidden');
    }

    showCreateRoom() {
        this.hideAllSections();
        document.getElementById('create-room-section').classList.remove('hidden');
    }

    showJoinRoom() {
        this.hideAllSections();
        document.getElementById('join-room-section').classList.remove('hidden');
    }

    showRoomList() {
        this.hideAllSections();
        document.getElementById('room-list-section').classList.remove('hidden');
        this.listRooms();
    }

    showGameRoom() {
        this.hideAllSections();
        document.getElementById('game-room-section').classList.remove('hidden');
        this.updateRoomDisplay();
        this.startRoomPolling();
    }

    hideAllSections() {
        const sections = document.querySelectorAll('.menu-section');
        sections.forEach(section => section.classList.add('hidden'));
        this.stopRoomPolling();
    }

    // API methods
    async createRoom() {
        const creatorName = document.getElementById('creator-name').value.trim();
        const joinCode = document.getElementById('join-code').value.trim();
        const minPlayers = parseInt(document.getElementById('min-players').value);
        const maxPlayers = parseInt(document.getElementById('max-players').value);

        if (!creatorName) {
            this.showMessage('Please enter your name', 'error');
            return;
        }

        if (maxPlayers < minPlayers) {
            this.showMessage('Max players must be greater than or equal to min players', 'error');
            return;
        }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

            const response = await fetch(`${this.baseUrl}/api/rooms`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    joinCode: joinCode || null,
                    playerName: creatorName,
                    minPlayers: minPlayers,
                    maxPlayers: maxPlayers
                }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (response.ok) {
                const createResponse = await response.json();
                console.log('Create room response:', createResponse);
                this.currentRoomId = createResponse.room.id;
                this.currentPlayerId = createResponse.creator.id;
                this.showMessage(`Room created! Code: ${createResponse.room.joinCode}`, 'success');
                this.showGameRoom();
                this.startRoomPolling();
            } else {
                const error = await response.text();
                this.showMessage(`Failed to create room: ${error}`, 'error');
            }
        } catch (error) {
            console.error('Network error details:', error);
            this.showMessage(`Network error while creating room: ${error.message}`, 'error');
        }
    }

    async joinRoom() {
        const roomCode = document.getElementById('room-code').value.trim().toUpperCase();
        const playerName = document.getElementById('player-name').value.trim();

        if (!roomCode) {
            this.showMessage('Please enter a room code', 'error');
            return;
        }

        if (!playerName) {
            this.showMessage('Please enter your name', 'error');
            return;
        }

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/join?code=${encodeURIComponent(roomCode)}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    playerName: playerName
                })
            });

            if (response.ok) {
                const player = await response.json();
                this.currentPlayerId = player.id;
                this.currentRoomId = player.roomId;
                this.showMessage(`Joined room as ${player.name}!`, 'success');
                this.showGameRoom();
                this.startRoomPolling();
            } else {
                const error = await response.text();
                this.showMessage(`Failed to join room: ${error}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error while joining room', 'error');
        }
    }

    async listRooms() {
        try {
            const response = await fetch(`${this.baseUrl}/api/rooms`);
            if (response.ok) {
                const rooms = await response.json();
                this.displayRooms(rooms);
            } else {
                this.showMessage('Failed to load rooms', 'error');
            }
        } catch (error) {
            this.showMessage('Network error while loading rooms', 'error');
        }
    }

    async startGame() {
        if (!this.currentRoomId) {
            this.showMessage('Not in a room', 'error');
            return;
        }

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/${this.currentRoomId}/start`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showMessage('Game started!', 'success');
                this.updateRoomDisplay();
            } else {
                const error = await response.text();
                this.showMessage(`Failed to start game: ${error}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error while starting game', 'error');
        }
    }

    async leaveRoom() {
        if (!this.currentRoomId || !this.currentPlayerId) {
            this.showMainMenu();
            return;
        }

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/${this.currentRoomId}/leave?playerId=${this.currentPlayerId}`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showMessage('Left the room', 'info');
            } else {
                const error = await response.text();
                this.showMessage(`Failed to leave room on server: ${error}, but left locally`, 'warning');
            }

            // Always return to main menu, even if the request fails
            this.currentRoomId = null;
            this.currentPlayerId = null;
            this.showMainMenu();
        } catch (error) {
            this.currentRoomId = null;
            this.currentPlayerId = null;
            this.showMainMenu();
            this.showMessage('Network error while leaving room, but left locally', 'warning');
        }
    }

    async updateRoomDisplay() {
        if (!this.currentRoomId) return;

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/${this.currentRoomId}`);
            if (response.ok) {
                const room = await response.json();
                document.getElementById('current-room-code').textContent = room.joinCode;
                document.getElementById('player-count').textContent = room.players.length;
                document.getElementById('max-players-display').textContent = room.maxPlayers;
                document.getElementById('game-phase').textContent = room.phase;

                // Show start game button if enough players and in lobby
                const startBtn = document.getElementById('start-game-btn');
                if (room.phase === 'LOBBY' && room.players.length >= room.minPlayers) {
                    startBtn.classList.remove('hidden');
                } else {
                    startBtn.classList.add('hidden');
                }

                this.displayPlayers(room.players);
            }
        } catch (error) {
            console.error('Failed to update room display:', error);
        }
    }

    displayRooms(rooms) {
        const container = document.getElementById('rooms-list');
        container.innerHTML = '';

        if (rooms.length === 0) {
            container.innerHTML = '<p>No rooms available</p>';
            return;
        }

        rooms.forEach(room => {
            const roomDiv = document.createElement('div');
            roomDiv.className = 'room-item';
            roomDiv.innerHTML = `
                <div>
                    <strong>Code:</strong> ${room.joinCode}<br>
                    <strong>Players:</strong> ${room.players.length}/${room.maxPlayers}
                </div>
                <button class="btn btn-primary" onclick="gameClient.quickJoin('${room.joinCode}')">Join</button>
            `;
            container.appendChild(roomDiv);
        });
    }

    displayPlayers(players) {
        const container = document.getElementById('players-list');
        container.innerHTML = '';

        if (players.length === 0) {
            container.innerHTML = '<p>No players in room</p>';
            return;
        }

        players.forEach(player => {
            const playerDiv = document.createElement('div');
            playerDiv.className = 'player-item';
            playerDiv.innerHTML = `
                <div>
                    <strong>${player.name}</strong>
                    ${player.alive ? '<span style="color: green;">(Alive)</span>' : '<span style="color: red;">(Dead)</span>'}
                </div>
            `;
            container.appendChild(playerDiv);
        });
    }

    quickJoin(roomCode) {
        document.getElementById('room-code').value = roomCode;
        this.showJoinRoom();
    }

    // Polling methods for real-time updates
    startRoomPolling() {
        this.stopRoomPolling(); // Clear any existing polling
        this.roomPollingInterval = setInterval(() => {
            this.updateRoomDisplay();
        }, 2000); // Poll every 2 seconds
    }

    stopRoomPolling() {
        if (this.roomPollingInterval) {
            clearInterval(this.roomPollingInterval);
            this.roomPollingInterval = null;
        }
    }

    showMessage(text, type = 'info') {
        const messagesDiv = document.getElementById('messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;
        messageDiv.textContent = text;

        messagesDiv.appendChild(messageDiv);

        // Auto-remove after 5 seconds
        setTimeout(() => {
            if (messageDiv.parentNode) {
                messageDiv.remove();
            }
        }, 5000);
    }
}

// Initialize the game client when the page loads
let gameClient;
document.addEventListener('DOMContentLoaded', () => {
    gameClient = new MafiaGameClient();
});
