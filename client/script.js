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
        this.transitioning = false;
        this.clockAngle = 0;

        this.currentScene = 'scene-menu';

        this.initializeEventListeners();
        this.showMessage('Welcome to Mafia Online!', 'info');
    }

    // --- Scene Management ---

    setScene(sceneId) {
        if (sceneId === this.currentScene) return;

        const oldEl = document.getElementById(this.currentScene);
        const newEl = document.getElementById(sceneId);
        if (!newEl) return;

        if (oldEl) {
            oldEl.classList.remove('scene-active');
            oldEl.classList.add('scene-exit');
            setTimeout(() => oldEl.classList.remove('scene-exit'), 600);
        }

        // Small delay so exit starts first
        setTimeout(() => {
            newEl.classList.add('scene-active');
        }, 150);

        this.currentScene = sceneId;
    }

    getSceneForState(room) {
        if (!room) return 'scene-menu';

        const phase = room.phase;
        const aliveCount = room.players ? room.players.filter(p => p.alive).length : 0;

        if (phase === 'LOBBY') return 'scene-lobby';

        if (phase === 'NIGHT') {
            if (this.myRole === 'DETECTIVE') return 'scene-magnifier';
            if (this.myRole === 'DOCTOR') return 'scene-medic';
            return 'scene-knife';
        }

        if (phase === 'DAY' || phase === 'VOTING') {
            if (aliveCount >= 6) return 'scene-group-6';
            if (aliveCount === 5) return 'scene-group-5';
            if (aliveCount === 4) return 'scene-group-4';
            return 'scene-group-3';
        }

        if (phase === 'ENDED') return 'scene-lobby'; // dead body for game over

        return 'scene-menu';
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

    // --- Animated Navigation ---

    navigateTo(sectionId, callback) {
        if (this.transitioning) return;

        const target = document.getElementById(sectionId);
        if (!target) return;

        const current = document.querySelector('.menu-section:not(.hidden)');

        // Stop polling when leaving game room
        if (current && current.id === 'game-room-section' && sectionId !== 'game-room-section') {
            this.stopRoomPolling();
        }

        const reveal = () => {
            target.classList.remove('hidden');
            void target.offsetWidth;
            target.classList.add('section-enter');
            target.addEventListener('animationend', () => {
                target.classList.remove('section-enter');
                this.transitioning = false;
            }, { once: true });
            if (callback) callback();
        };

        if (current && current !== target && !current.classList.contains('hidden')) {
            this.transitioning = true;
            current.classList.add('section-exit');
            current.addEventListener('animationend', () => {
                current.classList.add('hidden');
                current.classList.remove('section-exit');
                reveal();
            }, { once: true });
        } else {
            reveal();
        }
    }

    showMainMenu() {
        this.setScene('scene-menu');
        this.navigateTo('main-menu');
    }

    showCreateRoom() {
        this.setScene('scene-menu');
        this.navigateTo('create-room-section');
    }

    showJoinRoom() {
        this.setScene('scene-menu');
        this.navigateTo('join-room-section');
    }

    showRoomList() {
        this.setScene('scene-menu');
        this.navigateTo('room-list-section', () => this.listRooms());
    }

    showGameRoom() {
        this.setScene('scene-lobby');
        this.navigateTo('game-room-section', () => {
            this.updateRoomDisplay();
            this.startRoomPolling();
        });
    }

    // --- API calls ---

    async createRoom() {
        const creatorName = document.getElementById('creator-name').value.trim();
        const joinCode = document.getElementById('join-code').value.trim();
        const minPlayers = parseInt(document.getElementById('min-players').value);
        const maxPlayers = parseInt(document.getElementById('max-players').value);
        const dayDurationSeconds = parseInt(document.getElementById('day-duration').value) || 30;

        if (!creatorName || creatorName.length < 3 || creatorName.length > 12) {
            this.showMessage('Name must be between 3 and 12 characters', 'error'); return;
        }
        if (joinCode && (joinCode.length < 4 || joinCode.length > 12)) {
            this.showMessage('Room code must be between 4 and 12 characters', 'error'); return;
        }
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

        if (!roomCode || roomCode.length < 4 || roomCode.length > 12) {
            this.showMessage('Room code must be between 4 and 12 characters', 'error'); return;
        }
        if (!playerName || playerName.length < 3 || playerName.length > 12) {
            this.showMessage('Name must be between 3 and 12 characters', 'error'); return;
        }

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

        // Force-clear any in-progress transition
        this.transitioning = false;
        this.setScene('scene-menu');
        document.querySelectorAll('.menu-section').forEach(s => {
            s.classList.remove('section-enter', 'section-exit');
            s.classList.add('hidden');
        });
        this.stopRoomPolling();

        // Navigate to main menu with animation; clean up game room after transition
        this.navigateTo('main-menu', () => {
            document.getElementById('role-banner').classList.add('hidden');
            document.getElementById('phase-actions').innerHTML = '';
            const clock = document.getElementById('phase-clock');
            if (clock) clock.classList.add('clock-hidden');
        });
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
                this.updateClock(room.phase);
                this.currentPhase = room.phase;
            }

            this.displayPlayers(room.players);
            this.renderPhaseUI(room);
            this.setScene(this.getSceneForState(room));

            // Fetch own role once when game moves out of lobby
            if (room.phase !== 'LOBBY' && !this.roleDisplayed) {
                await this.fetchMyRole();
            }
        } catch (error) {
            console.error('Failed to update room display:', error);
        }
    }

    // --- Phase Clock ---

    updateClock(newPhase) {
        const hand = document.getElementById('clock-hand');
        const clock = document.getElementById('phase-clock');
        if (!hand || !clock) return;

        if (newPhase === 'LOBBY') {
            clock.classList.add('clock-hidden');
            return;
        }
        clock.classList.remove('clock-hidden');

        // Target angles: UP (0) = day, DOWN (180) = night, RIGHT (90) = voting
        const angles = { NIGHT: 180, DAY: 0, VOTING: 90, ENDED: 0 };
        const target = angles[newPhase] ?? 0;

        // Spin forward: 2 full rotations + delta to land on target
        const currentMod = ((this.clockAngle % 360) + 360) % 360;
        const delta = ((target - currentMod) % 360 + 360) % 360;
        this.clockAngle += 720 + delta;

        hand.style.transform = `rotate(${this.clockAngle}deg)`;
    }

    // --- Role Display ---

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

    // --- Phase UI ---

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
                    const mafiaTargets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Choose your target to eliminate</h3>
                            <div class="target-list">
                                ${mafiaTargets.map(p => `
                                    <button class="btn btn-danger target-btn"
                                        onclick="gameClient.submitNightAction('${p.id}')">
                                        ${p.name}
                                    </button>
                                `).join('')}
                            </div>
                        </div>`;
                } else if (this.myRole === 'DETECTIVE') {
                    const investigateTargets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Investigate a player to learn their role</h3>
                            <div class="target-list">
                                ${investigateTargets.map(p => `
                                    <button class="btn btn-primary target-btn"
                                        onclick="gameClient.submitDetectiveInvestigate('${p.id}')">
                                        ${p.name}
                                    </button>
                                `).join('')}
                            </div>
                        </div>`;
                } else if (this.myRole === 'DOCTOR') {
                    const protectTargets = room.players.filter(p => p.alive);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Choose a player to protect tonight</h3>
                            <div class="target-list">
                                ${protectTargets.map(p => `
                                    <button class="btn btn-success target-btn"
                                        onclick="gameClient.submitDoctorProtect('${p.id}')">
                                        ${p.name}${p.id === this.currentPlayerId ? ' (You)' : ''}
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

                // Build visible vote log — everyone sees how everyone voted
                const voteEntries = Object.entries(room.votes || {}).map(([voterId, targetId]) => {
                    const voter = room.players.find(p => p.id === voterId);
                    const target = room.players.find(p => p.id === targetId);
                    return `<li><strong>${voter?.name || '?'}</strong> voted for <strong>${target?.name || '?'}</strong></li>`;
                }).join('');
                const voteLog = voteEntries
                    ? `<ul class="vote-log">${voteEntries}</ul>`
                    : '';

                if (this.voteSubmitted) {
                    container.innerHTML = `
                        <div class="action-panel">
                            <p class="phase-hint">Vote submitted. Waiting for others... (${votesIn}/${aliveCount} voted)</p>
                            ${voteLog}
                            ${this.isHost ? `<button class="btn btn-warning" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                } else if (!isAlive) {
                    container.innerHTML = `
                        <div class="action-panel">
                            <p class="phase-hint">You are eliminated. Watching the vote... (${votesIn}/${aliveCount} voted)</p>
                            ${voteLog}
                        </div>`;
                } else {
                    const voteTargets = room.players.filter(p => p.alive);
                    container.innerHTML = `
                        <div class="action-panel">
                            <h3>Vote to eliminate a player (${votesIn}/${aliveCount} voted)</h3>
                            <div class="target-list">
                                ${voteTargets.map(p => `
                                    <button class="btn btn-warning target-btn"
                                        onclick="gameClient.submitVote('${p.id}')">
                                        ${p.name}${p.id === this.currentPlayerId ? ' (You)' : ''}
                                    </button>
                                `).join('')}
                            </div>
                            ${voteLog}
                            ${this.isHost ? `<button class="btn btn-danger" style="margin-top:10px" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                }
                break;
            }

            case 'ENDED': {
                const winnerText = room.winner === 'MAFIA' ? 'Mafia Wins!' : 'Town Wins!';
                const roleRows = room.players.map(p =>
                    `<li><strong>${p.name}</strong>: ${p.role}${p.alive ? '' : ' <span class="status-dead">(eliminated)</span>'}</li>`
                ).join('');
                container.innerHTML = `
                    <div class="game-over-banner">
                        <h2>${winnerText}</h2>
                        <h4>Final Roles</h4>
                        <ul style="list-style:none;padding:0;margin:0">${roleRows}</ul>
                    </div>`;
                break;
            }

            default:
                container.innerHTML = '';
        }
    }

    // --- Game Actions ---

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

    async submitDetectiveInvestigate(targetId) {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/night/investigate?playerId=${this.currentPlayerId}`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ targetPlayerId: targetId })
                }
            );

            if (response.ok) {
                const player = await response.json();
                this.nightActionSubmitted = true;
                this.showMessage(`Investigation: ${player.name} is ${player.role}`, 'info');
            } else {
                this.showMessage(`Failed to investigate: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error investigating', 'error');
        }
    }

    async submitDoctorProtect(targetId) {
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/night/protect?playerId=${this.currentPlayerId}`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ targetPlayerId: targetId })
                }
            );

            if (response.ok) {
                this.nightActionSubmitted = true;
                this.showMessage('Protection chosen. Waiting for day...', 'success');
            } else {
                this.showMessage(`Failed to protect: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting protection', 'error');
        }
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

    // --- Display ---

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

    // --- Polling ---

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

    // --- Messages ---

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
