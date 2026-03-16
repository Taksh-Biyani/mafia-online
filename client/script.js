// Mafia Online Game Client

class MafiaGameClient {
    constructor() {
        this.baseUrl = 'http://localhost:8080';
        this.currentRoomId = null;
        this.currentPlayerId = null;
        this.isHost = false;
        this.myRole = null;
        this.roleDisplayed = false;
        this.roomPollingInterval = null;
        this.currentPhase = null;
        this.dayTimerStart = null;
        this.dayDurationSeconds = 30;
        this.voteSubmitted = false;
        this.nightActionSubmitted = false;

        this.initializeEventListeners();
        this.showMessage('Welcome to Mafia Online!', 'info');
    }

    initializeEventListeners() {
        // Main menu
        document.getElementById('create-room-btn').addEventListener('click', () => this.showCreateRoom());
        document.getElementById('join-room-btn').addEventListener('click', () => this.showJoinRoom());
        document.getElementById('list-rooms-btn').addEventListener('click', () => this.showRoomList());

        // Create room
        document.getElementById('submit-create-room').addEventListener('click', () => this.createRoom());
        document.getElementById('back-from-create').addEventListener('click', () => this.showMainMenu());

        // Join room
        document.getElementById('submit-join-room').addEventListener('click', () => this.joinRoom());
        document.getElementById('back-from-join').addEventListener('click', () => this.showMainMenu());

        // Room list
        document.getElementById('refresh-rooms').addEventListener('click', () => this.listRooms());
        document.getElementById('back-from-list').addEventListener('click', () => this.showMainMenu());

        // Game room
        document.getElementById('start-game-btn').addEventListener('click', () => this.startGame());
        document.getElementById('leave-room-btn').addEventListener('click', () => this.leaveRoom());
    }

    // --- Navigation ---

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
        document.querySelectorAll('.menu-section').forEach(s => s.classList.add('hidden'));
        this.stopRoomPolling();
    }

    // --- API calls ---

    async createRoom() {
        const creatorName = document.getElementById('creator-name').value.trim();
        const joinCode = document.getElementById('join-code').value.trim();
        const minPlayers = parseInt(document.getElementById('min-players').value);
        const maxPlayers = parseInt(document.getElementById('max-players').value);
        const dayDurationSeconds = parseInt(document.getElementById('day-duration').value) || 30;

        if (!creatorName) { this.showMessage('Please enter your name', 'error'); return; }
        if (maxPlayers < minPlayers) { this.showMessage('Max players must be >= min players', 'error'); return; }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`${this.baseUrl}/api/rooms`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ joinCode: joinCode || null, playerName: creatorName, minPlayers, maxPlayers, dayDurationSeconds }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (response.ok) {
                const data = await response.json();
                this.currentRoomId = data.room.id;
                this.currentPlayerId = data.creator.id;
                this.isHost = true;
                this.myRole = null;
                this.roleDisplayed = false;
                this.showMessage(`Room created! Code: ${data.room.joinCode}`, 'success');
                this.showGameRoom();
            } else {
                this.showMessage(`Failed to create room: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage(`Network error: ${error.message}`, 'error');
        }
    }

    async joinRoom() {
        const roomCode = document.getElementById('room-code').value.trim().toUpperCase();
        const playerName = document.getElementById('player-name').value.trim();

        if (!roomCode) { this.showMessage('Please enter a room code', 'error'); return; }
        if (!playerName) { this.showMessage('Please enter your name', 'error'); return; }

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/join?code=${encodeURIComponent(roomCode)}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ playerName })
            });

            if (response.ok) {
                const player = await response.json();
                this.currentPlayerId = player.id;
                this.currentRoomId = player.roomId;
                this.isHost = false;
                this.myRole = null;
                this.roleDisplayed = false;
                this.showMessage(`Joined room as ${player.name}!`, 'success');
                this.showGameRoom();
            } else {
                this.showMessage(`Failed to join room: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error while joining room', 'error');
        }
    }

    async listRooms() {
        try {
            const response = await fetch(`${this.baseUrl}/api/rooms`);
            if (response.ok) {
                this.displayRooms(await response.json());
            } else {
                this.showMessage('Failed to load rooms', 'error');
            }
        } catch (error) {
            this.showMessage('Network error while loading rooms', 'error');
        }
    }

    async startGame() {
        if (!this.currentRoomId) { this.showMessage('Not in a room', 'error'); return; }

        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/start?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );

            if (response.ok) {
                this.showMessage('Game started!', 'success');
                this.updateRoomDisplay();
            } else if (response.status === 403) {
                this.showMessage('Only the host can start the game', 'error');
            } else {
                this.showMessage(`Failed to start game: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error while starting game', 'error');
        }
    }

    async leaveRoom() {
        if (!this.currentRoomId || !this.currentPlayerId) { this.showMainMenu(); return; }

        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/leave?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (!response.ok) {
                this.showMessage('Failed to leave on server, but left locally', 'warning');
            }
        } catch (error) {
            this.showMessage('Network error while leaving, but left locally', 'warning');
        }

        this.currentRoomId = null;
        this.currentPlayerId = null;
        this.isHost = false;
        this.myRole = null;
        this.roleDisplayed = false;
        this.currentPhase = null;
        this.dayTimerStart = null;
        this.voteSubmitted = false;
        this.nightActionSubmitted = false;
        document.getElementById('role-banner').classList.add('hidden');
        document.getElementById('phase-actions').innerHTML = '';
        this.showMainMenu();
    }

    async updateRoomDisplay() {
        if (!this.currentRoomId) return;

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/${this.currentRoomId}`);
            if (!response.ok) return;

            const room = await response.json();

            document.getElementById('current-room-code').textContent = room.joinCode;
            document.getElementById('player-count').textContent = room.players.length;
            document.getElementById('max-players-display').textContent = room.maxPlayers;
            document.getElementById('game-phase').textContent = room.phase;

            // Start button: host only, lobby only, enough players
            const startBtn = document.getElementById('start-game-btn');
            if (this.isHost && room.phase === 'LOBBY' && room.players.length >= room.minPlayers) {
                startBtn.classList.remove('hidden');
            } else {
                startBtn.classList.add('hidden');
            }

            // Detect phase transitions — reset per-phase action state
            if (room.phase !== this.currentPhase) {
                this.voteSubmitted = false;
                this.nightActionSubmitted = false;
                if (room.phase === 'DAY') {
                    this.dayTimerStart = Date.now();
                    this.dayDurationSeconds = room.dayDurationSeconds || 30;
                }
                this.currentPhase = room.phase;
            }

            this.displayPlayers(room.players);
            this.renderPhaseUI(room);

            // Fetch own role once when game moves out of lobby
            if (room.phase !== 'LOBBY' && !this.roleDisplayed) {
                await this.fetchMyRole();
            }
        } catch (error) {
            console.error('Failed to update room display:', error);
        }
    }

    async fetchMyRole() {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/players/${this.currentPlayerId}`
            );
            if (response.ok) {
                const player = await response.json();
                this.myRole = player.role;
                this.roleDisplayed = true;
                this.showRoleBanner(player.role);
            }
        } catch (error) {
            console.error('Failed to fetch role:', error);
        }
    }

    showRoleBanner(role) {
        const banner = document.getElementById('role-banner');
        const config = {
            MAFIA:     { color: '#e74c3c', desc: 'Eliminate a citizen each night' },
            DETECTIVE: { color: '#3498db', desc: 'Investigate one player each night' },
            DOCTOR:    { color: '#27ae60', desc: 'Protect one player each night' },
            CITIZEN:   { color: '#7f8c8d', desc: 'Vote out the mafia during the day' },
        };
        const { color, desc } = config[role] || { color: '#7f8c8d', desc: '' };
        banner.style.backgroundColor = color;
        banner.innerHTML = `<strong>Your Role: ${role}</strong><br><small>${desc}</small>`;
        banner.classList.remove('hidden');
    }

    renderPhaseUI(room) {
        const container = document.getElementById('phase-actions');
        const myPlayer = room.players.find(p => p.id === this.currentPlayerId);
        const isAlive = myPlayer ? myPlayer.alive : false;

        switch (room.phase) {
            case 'LOBBY':
                container.innerHTML = '<p class="phase-hint">Waiting for players... Share the room code to invite others!</p>';
                break;

            case 'NIGHT':
                if (this.nightActionSubmitted) {
                    container.innerHTML = '<p class="phase-hint">Action submitted. Waiting for day phase...</p>';
                } else if (!isAlive) {
                    container.innerHTML = '<p class="phase-hint">You are eliminated. Watch the night unfold...</p>';
                } else if (this.myRole === 'MAFIA') {
                    const targets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Choose your target to eliminate</h3>
                            <div class="target-list">
                                ${targets.map(p => `
                                    <button class="btn btn-danger target-btn"
                                        onclick="gameClient.submitNightAction('${p.id}')">
                                        ${p.name}
                                    </button>
                                `).join('')}
                            </div>
                        </div>`;
                } else {
                    container.innerHTML = '<p class="phase-hint">Night falls... The mafia is choosing their target.</p>';
                }
                break;

            case 'DAY': {
                const elapsed = this.dayTimerStart ? (Date.now() - this.dayTimerStart) / 1000 : 0;
                const remaining = Math.max(0, this.dayDurationSeconds - elapsed);

                // Host's timer expired — trigger voting
                if (remaining <= 0 && this.isHost) {
                    this.endDay();
                    break;
                }

                const timerClass = remaining <= 10 ? 'day-timer urgent' : 'day-timer';
                container.innerHTML = `
                    <div class="action-panel">
                        <p class="phase-hint">Day breaks. Discuss and find the mafia!</p>
                        <div class="${timerClass}">
                            <span>${Math.ceil(remaining)}s</span> until voting begins
                        </div>
                        ${this.isHost ? `<button class="btn btn-primary" onclick="gameClient.endDay()">Start Voting Now</button>` : ''}
                    </div>`;
                break;
            }

            case 'VOTING': {
                const aliveCount = room.players.filter(p => p.alive).length;
                const votesIn = Object.keys(room.votes || {}).length;

                if (this.voteSubmitted) {
                    container.innerHTML = `
                        <div class="action-panel">
                            <p class="phase-hint">Vote submitted. Waiting for others... (${votesIn}/${aliveCount} voted)</p>
                            ${this.isHost ? `<button class="btn btn-warning" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                } else if (!isAlive) {
                    container.innerHTML = `<p class="phase-hint">You are eliminated. Watching the vote... (${votesIn}/${aliveCount} voted)</p>`;
                } else {
                    const voteTargets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Vote to eliminate a player (${votesIn}/${aliveCount} voted)</h3>
                            <div class="target-list">
                                ${voteTargets.map(p => `
                                    <button class="btn btn-warning target-btn"
                                        onclick="gameClient.submitVote('${p.id}')">
                                        ${p.name}
                                    </button>
                                `).join('')}
                            </div>
                            ${this.isHost ? `<button class="btn btn-danger" style="margin-top:10px" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                }
                break;
            }

            case 'ENDED':
                container.innerHTML = `
                    <div class="game-over-banner">
                        <h3>Game Over</h3>
                        <p>The game has ended!</p>
                    </div>`;
                break;

            default:
                container.innerHTML = '';
        }
    }

    async endDay() {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/day/end?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (!response.ok) {
                this.showMessage('Could not start voting', 'error');
            }
        } catch (error) {
            this.showMessage('Network error starting voting', 'error');
        }
    }

    async forceResolveVotes() {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/vote/resolve?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (!response.ok) {
                this.showMessage('Could not resolve votes', 'error');
            }
        } catch (error) {
            this.showMessage('Network error resolving votes', 'error');
        }
    }

    async submitNightAction(targetId) {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/night/action?playerId=${this.currentPlayerId}`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ targetPlayerId: targetId })
                }
            );

            if (response.ok) {
                this.nightActionSubmitted = true;
                this.showMessage('Target chosen. Waiting for day...', 'success');
            } else {
                this.showMessage(`Failed to submit action: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting night action', 'error');
        }
    }

    async startVoting() {
        // Transitions DAY -> VOTING by submitting a dummy first vote trigger.
        // The host clicks "Start Voting" which just calls the vote endpoint to flip the phase.
        // Since submitVote auto-transitions DAY->VOTING on first call, we use a small workaround:
        // we POST to /night/end equivalent — but for DAY there is no dedicated endpoint yet.
        // For now: just update the display and let the host's first vote trigger the transition.
        this.showMessage('Voting phase started! Cast your votes.', 'info');
        document.getElementById('phase-actions').innerHTML =
            '<p class="phase-hint">Waiting for players to vote...</p>';
    }

    async submitVote(targetId) {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/vote?voterId=${this.currentPlayerId}`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ targetPlayerId: targetId })
                }
            );

            if (response.ok) {
                this.voteSubmitted = true;
                this.showMessage('Vote submitted!', 'success');
            } else {
                this.showMessage(`Failed to vote: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting vote', 'error');
        }
    }

    displayRooms(rooms) {
        const container = document.getElementById('rooms-list');
        container.innerHTML = '';
        if (rooms.length === 0) { container.innerHTML = '<p>No rooms available</p>'; return; }

        rooms.forEach(room => {
            const div = document.createElement('div');
            div.className = 'room-item';
            div.innerHTML = `
                <div>
                    <strong>Code:</strong> ${room.joinCode}<br>
                    <strong>Players:</strong> ${room.players.length}/${room.maxPlayers}
                </div>
                <button class="btn btn-primary" onclick="gameClient.quickJoin('${room.joinCode}')">Join</button>`;
            container.appendChild(div);
        });
    }

    displayPlayers(players) {
        const container = document.getElementById('players-list');
        container.innerHTML = '';
        if (players.length === 0) { container.innerHTML = '<p>No players in room</p>'; return; }

        players.forEach(player => {
            const div = document.createElement('div');
            div.className = 'player-item';
            const isMe = player.id === this.currentPlayerId;
            div.innerHTML = `
                <div>
                    <strong>${player.name}${isMe ? ' (You)' : ''}</strong>
                    ${player.alive
                        ? '<span class="status-alive"> Alive</span>'
                        : '<span class="status-dead"> Eliminated</span>'}
                </div>`;
            container.appendChild(div);
        });
    }

    quickJoin(roomCode) {
        document.getElementById('room-code').value = roomCode;
        this.showJoinRoom();
    }

    startRoomPolling() {
        this.stopRoomPolling();
        this.roomPollingInterval = setInterval(() => this.updateRoomDisplay(), 2000);
    }

    stopRoomPolling() {
        if (this.roomPollingInterval) {
            clearInterval(this.roomPollingInterval);
            this.roomPollingInterval = null;
        }
    }

    showMessage(text, type = 'info') {
        const messagesDiv = document.getElementById('messages');
        const div = document.createElement('div');
        div.className = `message ${type}`;
        div.textContent = text;
        messagesDiv.appendChild(div);
        setTimeout(() => { if (div.parentNode) div.remove(); }, 5000);
    }
}

let gameClient;
document.addEventListener('DOMContentLoaded', () => {
    gameClient = new MafiaGameClient();
});
