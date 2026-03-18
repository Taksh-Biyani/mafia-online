// Mafia Online Game Client

class MafiaGameClient {
    constructor() {
        this.baseUrl = window.location.origin;
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

        this.nightTimerStart = null;
        this.nightTimerDuration = 0;
        this.votingTimerStart = null;

        this.currentScene = 'scene-menu';
        this.chatPanelOpen = false;
        this.chatLastCount = 0;
        this.gameOverShown = false;
        this._lastRequest = {};
        this._actionInFlight = false;

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

        // Night sub-phases — everyone sees the same icon for the active role
        if (phase === 'NIGHT_MAFIA') return 'scene-knife';
        if (phase === 'NIGHT_DOCTOR') return 'scene-medic';
        if (phase === 'NIGHT_DETECTIVE') return 'scene-magnifier';

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

        // Auto-update minPlayers when mafia count changes; clamp player inputs to [4,12]
        document.getElementById('mafia-count').addEventListener('change', () => {
            const mafiaCount = parseInt(document.getElementById('mafia-count').value) || 1;
            const minNeeded = mafiaCount >= 3 ? 8 : mafiaCount >= 2 ? 6 : 4;
            const minInput = document.getElementById('min-players');
            if (parseInt(minInput.value) < minNeeded) minInput.value = minNeeded;
        });
        ['min-players', 'max-players'].forEach(id => {
            document.getElementById(id).addEventListener('change', e => {
                const v = parseInt(e.target.value);
                if (isNaN(v) || v < 4) e.target.value = 4;
                else if (v > 12) e.target.value = 12;
            });
        });

        // Join room
        document.getElementById('submit-join-room').addEventListener('click', () => this.joinRoom());
        document.getElementById('back-from-join').addEventListener('click', () => this.showMainMenu());

        // Room list
        document.getElementById('refresh-rooms').addEventListener('click', () => this.listRooms());
        document.getElementById('back-from-list').addEventListener('click', () => this.showMainMenu());

        // Game room
        document.getElementById('start-game-btn').addEventListener('click', () => this.startGame());
        document.getElementById('leave-room-btn').addEventListener('click', () => this.leaveRoom());

        // Chat
        document.getElementById('chat-toggle-btn').addEventListener('click', () => this.toggleChatPanel());
        document.getElementById('chat-form').addEventListener('submit', (e) => {
            e.preventDefault();
            const input = document.getElementById('chat-input');
            const text = input.value.trim();
            if (text) {
                this.sendChatMessage(text);
                input.value = '';
            }
        });
    }

    // --- Animated Navigation ---

    navigateTo(sectionId, callback) {
        if (this.transitioning) return;

        const target = document.getElementById(sectionId);
        if (!target) return;

        const current = document.querySelector('.menu-section:not(.hidden)');

        // Stop polling and hide chat when leaving game room
        if (current && current.id === 'game-room-section' && sectionId !== 'game-room-section') {
            this.stopRoomPolling();
            document.getElementById('chat-toggle-btn').classList.remove('chat-btn-visible');
            if (this.chatPanelOpen) this.toggleChatPanel();
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
            // Open chat panel by default when entering a game room
            if (!this.chatPanelOpen) this.toggleChatPanel();
        });
    }

    // --- API calls ---

    async createRoom() {
        if (!this.isRequestAllowed('createRoom')) {
            this.showMessage('Please wait before trying again', 'warning'); return;
        }
        this.applyCooldownVisual('submit-create-room', 10);
        const creatorName = document.getElementById('creator-name').value.trim();
        const joinCode = document.getElementById('join-code').value.trim();
        const minPlayers = Math.min(12, Math.max(4, parseInt(document.getElementById('min-players').value) || 4));
        const maxPlayers = Math.min(12, Math.max(4, parseInt(document.getElementById('max-players').value) || 12));
        const dayDurationSeconds = parseInt(document.getElementById('day-duration').value) || 30;
        const mafiaCount = parseInt(document.getElementById('mafia-count').value) || 1;

        if (!creatorName || creatorName.length < 3 || creatorName.length > 12) {
            this.showMessage('Name must be between 3 and 12 characters', 'error'); return;
        }
        if (joinCode && (joinCode.length < 4 || joinCode.length > 12)) {
            this.showMessage('Room code must be between 4 and 12 characters', 'error'); return;
        }
        const minNeeded = mafiaCount >= 3 ? 8 : mafiaCount >= 2 ? 6 : 4;
        if (minPlayers < minNeeded) {
            this.showMessage(`${mafiaCount} mafia requires at least ${minNeeded} min players`, 'error'); return;
        }
        if (maxPlayers < minPlayers) { this.showMessage('Max players must be >= min players', 'error'); return; }

        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(`${this.baseUrl}/api/rooms`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ joinCode: joinCode || null, playerName: creatorName, minPlayers, maxPlayers, dayDurationSeconds, mafiaCount }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (response.ok) {
                const data = await response.json();
                this.currentRoomId = data.room.id;
                this.currentPlayerId = data.creator.id;
                this.currentPlayerName = creatorName;
                this.isHost = true;
                this.myRole = null;
                this.roleDisplayed = false;
                this.gameOverShown = false;
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
        if (!this.isRequestAllowed('joinRoom')) {
            this.showMessage('Please wait before trying again', 'warning'); return;
        }
        const roomCode = document.getElementById('room-code').value.trim().toUpperCase();
        const playerName = document.getElementById('player-name').value.trim();

        if (!roomCode || roomCode.length < 4 || roomCode.length > 12) {
            this._lastRequest['joinRoom'] = 0; // allow immediate retry on validation failure
            this.showMessage('Room code must be between 4 and 12 characters', 'error'); return;
        }
        if (!playerName || playerName.length < 3 || playerName.length > 12) {
            this._lastRequest['joinRoom'] = 0;
            this.showMessage('Name must be between 3 and 12 characters', 'error'); return;
        }

        try {
            const response = await fetch(`${this.baseUrl}/api/rooms/join?code=${encodeURIComponent(roomCode)}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ playerName })
            });

            if (response.status === 409) {
                this._lastRequest['joinRoom'] = 0; // username conflict — let them retry immediately
                this.showMessage('That username is already taken in this room. Please choose a different name.', 'error');
                return;
            }

            if (response.status === 429) {
                this.applyCooldownVisual('submit-join-room', 10);
                this.showMessage('Too many attempts. Please wait 10 seconds.', 'error');
                return;
            }

            this.applyCooldownVisual('submit-join-room', 10); // only apply cooldown for non-conflict responses

            if (response.ok) {
                const player = await response.json();
                this.currentPlayerId = player.id;
                this.currentRoomId = player.roomId;
                this.currentPlayerName = player.name;
                this.isHost = false;
                this.myRole = null;
                this.roleDisplayed = false;
                this.gameOverShown = false;
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
        if (!this.isRequestAllowed('listRooms')) return;
        this.applyCooldownVisual('list-rooms-btn', 10);
        this.applyCooldownVisual('refresh-rooms', 10);
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
        if (!this.isRequestAllowed('startGame', 5000)) return;
        this.applyCooldownVisual('start-game-btn', 5);

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

        document.getElementById('game-over-overlay').classList.add('hidden');
        this.gameOverShown = false;
        this._lastRequest = {};
        this._actionInFlight = false;
        this.currentRoomId = null;
        this.currentPlayerId = null;
        this.isHost = false;
        this.myRole = null;
        this.roleDisplayed = false;
        this.currentPhase = null;
        this.dayTimerStart = null;
        this.nightTimerStart = null;
        this.nightTimerDuration = 0;
        this.votingTimerStart = null;
        this.voteSubmitted = false;
        this.nightActionSubmitted = false;
        this.chatLastCount = 0;
        document.getElementById('chat-toggle-btn').classList.remove('chat-btn-visible');
        if (this.chatPanelOpen) this.toggleChatPanel();

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

            // Room was reset by everyone voting Play Again
            if (this.gameOverShown && room.phase === 'LOBBY') {
                document.getElementById('game-over-overlay').classList.add('hidden');
                this.gameOverShown = false;
                this.myRole = null;
                this.roleDisplayed = false;
                this.voteSubmitted = false;
                this.nightActionSubmitted = false;
                this.dayTimerStart = null;
                this.currentPhase = null;
            }

            // Keep end screen player cards in sync with vote choices
            if (this.gameOverShown && room.phase === 'ENDED') {
                this.updateGameOverPlayers(room);
                return;
            }

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
                if (room.phase === 'VOTING') {
                    this.votingTimerStart = Date.now();
                }
                if (room.phase === 'NIGHT_MAFIA') {
                    this.nightTimerStart = Date.now();
                    const mc = room.mafiaCount || 1;
                    this.nightTimerDuration = mc >= 3 ? 35 : mc === 2 ? 25 : 15;
                }
                if (room.phase === 'NIGHT_DOCTOR' || room.phase === 'NIGHT_DETECTIVE') {
                    this.nightTimerStart = Date.now();
                    this.nightTimerDuration = 15;
                }
                this.updateClock(room.phase);
                this.currentPhase = room.phase;
            }

            this.displayPlayers(room.players);
            this.renderPhaseUI(room);
            this.setScene(this.getSceneForState(room));
            this.updateChatButtonVisibility(room);

            // Fetch own role once when game moves out of lobby
            if (room.phase !== 'LOBBY' && !this.roleDisplayed) {
                await this.fetchMyRole();
            }

            await this.fetchAndRenderChat();
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
        const angles = { NIGHT_MAFIA: 180, NIGHT_DOCTOR: 210, NIGHT_DETECTIVE: 150, DAY: 0, VOTING: 90, ENDED: 0 };
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

    // --- Game Over Screen ---

    showGameOverScreen(room) {
        if (this.gameOverShown) return;
        this.gameOverShown = true;

        const isMafiaWin = room.winner === 'MAFIA';
        const iWon = this.myRole
            ? (isMafiaWin ? this.myRole === 'MAFIA' : this.myRole !== 'MAFIA')
            : false;

        const verdictEl = document.getElementById('game-over-verdict');
        verdictEl.textContent = iWon ? 'Victory' : 'Defeat';
        verdictEl.className = `game-over-verdict ${iWon ? 'victory' : 'defeat'}`;

        document.getElementById('game-over-subtitle').textContent =
            isMafiaWin ? 'The Mafia has taken over' : 'The Town has prevailed';

        this.updateGameOverPlayers(room);
        document.getElementById('game-over-overlay').classList.remove('hidden');
    }

    updateGameOverPlayers(room) {
        const container = document.getElementById('game-over-players');
        if (!container) return;

        const icons = {
            CITIZEN:   'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f464.png',
            DETECTIVE: 'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f50d.png',
            DOCTOR:    'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1fa79.png',
            MAFIA:     'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f52a.png',
        };
        const playAgainVotes = room.playAgainVotes || [];
        const leaveVotes = room.leaveVotes || [];

        container.innerHTML = room.players.map(p => {
            const icon = icons[p.role] || icons.CITIZEN;
            const isMafia = p.role === 'MAFIA';
            const isMe = p.id === this.currentPlayerId;
            const roleLabel = p.role.charAt(0) + p.role.slice(1).toLowerCase();
            const votedPlay  = playAgainVotes.includes(p.id);
            const votedLeave = leaveVotes.includes(p.id);
            const voteEl = votedPlay
                ? '<div class="go-vote go-vote-yes">✓</div>'
                : votedLeave
                ? '<div class="go-vote go-vote-no">✗</div>'
                : '<div class="go-vote"></div>';
            return `
                <div class="go-card${isMafia ? ' is-mafia' : ''}${isMe ? ' is-me' : ''}">
                    <img class="go-icon" src="${icon}" alt="${p.role}" loading="lazy">
                    <div class="go-name ${isMafia ? 'mafia' : 'town'}">${this.escapeHtml(p.name)}${isMe ? ' ★' : ''}</div>
                    <div class="go-role">${roleLabel}</div>
                    ${!p.alive ? '<div class="go-dead">eliminated</div>' : ''}
                    ${voteEl}
                </div>`;
        }).join('');
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

            case 'NIGHT_MAFIA': {
                const nightElapsed = this.nightTimerStart ? (Date.now() - this.nightTimerStart) / 1000 : 0;
                const nightRemaining = Math.max(0, this.nightTimerDuration - nightElapsed);
                if (nightRemaining <= 0 && this.isHost) { this.skipNightPhase(); break; }
                const nightTimerClass = nightRemaining <= 5 ? 'day-timer urgent' : 'day-timer';
                const nightTimerHtml = `<div class="${nightTimerClass}"><span>${Math.ceil(nightRemaining)}s</span> remaining</div>`;
                const hostSkipBtn = this.isHost ? `<button class="btn btn-warning" style="margin-top:10px" onclick="gameClient.skipNightPhase()">Skip Phase</button>` : '';
                if (!isAlive) {
                    container.innerHTML = `<div class="action-panel">${nightTimerHtml}<p class="phase-hint">You are eliminated. The mafia is choosing their target...</p>${hostSkipBtn}</div>`;
                } else if (this.myRole === 'MAFIA') {
                    const mafiaTargets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                    const aliveMafiaCount = room.players.filter(p => p.role === 'MAFIA' && p.alive).length;
                    const mafiaVoteCount = room.mafiaVoteCount || 0;
                    const mafiaAllies = room.players.filter(p => p.role === 'MAFIA' && p.alive && p.id !== this.currentPlayerId);
                    const alliesHtml = mafiaAllies.length > 0
                        ? `<p class="phase-hint mafia-allies">Your allies: ${mafiaAllies.map(p => `<strong>${p.name}</strong>`).join(', ')}</p>`
                        : `<p class="phase-hint mafia-allies">You are the only mafia member alive.</p>`;
                    const voteStatus = this.nightActionSubmitted
                        ? `<p class="phase-hint vote-progress">Your vote is in. <strong>${mafiaVoteCount}/${aliveMafiaCount}</strong> mafia acted.</p>`
                        : `<p class="phase-hint vote-progress"><strong>${mafiaVoteCount}/${aliveMafiaCount}</strong> mafia acted.</p>`;
                    const mafiaSkipBtn = !this.nightActionSubmitted
                        ? `<button class="btn btn-secondary" style="margin-top:8px" onclick="gameClient.skipMafiaKillVote()">Skip (no kill)</button>`
                        : '';
                    container.innerHTML = `
                        <div class="action-panel">
                            ${nightTimerHtml}
                            <h3>${this.nightActionSubmitted ? 'Change your target' : 'Choose your target to eliminate'}</h3>
                            ${alliesHtml}
                            ${voteStatus}
                            <div class="target-list">
                                ${mafiaTargets.map(p => `
                                    <button class="btn btn-danger target-btn"
                                        onclick="gameClient.submitNightAction('${p.id}')">
                                        ${p.name}
                                    </button>
                                `).join('')}
                            </div>
                            ${mafiaSkipBtn}
                            ${hostSkipBtn}
                        </div>`;
                } else {
                    container.innerHTML = `<div class="action-panel">${nightTimerHtml}<p class="phase-hint">Night falls... The mafia is choosing their target.</p>${hostSkipBtn}</div>`;
                }
                break;
            }

            case 'NIGHT_DOCTOR': {
                const ndElapsed = this.nightTimerStart ? (Date.now() - this.nightTimerStart) / 1000 : 0;
                const ndRemaining = Math.max(0, this.nightTimerDuration - ndElapsed);
                if (ndRemaining <= 0 && this.isHost) { this.skipNightPhase(); break; }
                const ndTimerClass = ndRemaining <= 5 ? 'day-timer urgent' : 'day-timer';
                const ndTimerHtml = `<div class="${ndTimerClass}"><span>${Math.ceil(ndRemaining)}s</span> remaining</div>`;
                const ndHostSkip = this.isHost ? `<button class="btn btn-warning" style="margin-top:10px" onclick="gameClient.skipNightPhase()">Skip Phase</button>` : '';
                if (!isAlive) {
                    container.innerHTML = `<div class="action-panel">${ndTimerHtml}<p class="phase-hint">You are eliminated. The doctor is choosing who to protect...</p>${ndHostSkip}</div>`;
                } else if (this.myRole === 'DOCTOR') {
                    if (this.nightActionSubmitted) {
                        container.innerHTML = `<div class="action-panel">${ndTimerHtml}<p class="phase-hint">Protection chosen. Waiting for the detective...</p>${ndHostSkip}</div>`;
                    } else {
                        const lastProtectedId = room.lastDoctorProtectedId;
                        const protectTargets = room.players.filter(p => p.alive);
                        container.innerHTML = `
                            <div class="action-panel">
                                ${ndTimerHtml}
                                <h3>Choose a player to protect tonight</h3>
                                ${lastProtectedId ? `<p class="phase-hint">Cannot protect the same player as last night.</p>` : ''}
                                <div class="target-list">
                                    ${protectTargets.map(p => {
                                        const blocked = p.id === lastProtectedId;
                                        return `<button class="btn btn-success target-btn${blocked ? ' btn-disabled' : ''}"
                                            ${blocked ? 'disabled title="Protected last night"' : `onclick="gameClient.submitDoctorProtect('${p.id}')"`}>
                                            ${p.name}${p.id === this.currentPlayerId ? ' (You)' : ''}${blocked ? ' ✗' : ''}
                                        </button>`;
                                    }).join('')}
                                </div>
                                ${ndHostSkip}
                            </div>`;
                    }
                } else {
                    container.innerHTML = `<div class="action-panel">${ndTimerHtml}<p class="phase-hint">The doctor is choosing who to protect...</p>${ndHostSkip}</div>`;
                }
                break;
            }

            case 'NIGHT_DETECTIVE': {
                const niElapsed = this.nightTimerStart ? (Date.now() - this.nightTimerStart) / 1000 : 0;
                const niRemaining = Math.max(0, this.nightTimerDuration - niElapsed);
                if (niRemaining <= 0 && this.isHost) { this.skipNightPhase(); break; }
                const niTimerClass = niRemaining <= 5 ? 'day-timer urgent' : 'day-timer';
                const niTimerHtml = `<div class="${niTimerClass}"><span>${Math.ceil(niRemaining)}s</span> remaining</div>`;
                const niHostSkip = this.isHost ? `<button class="btn btn-warning" style="margin-top:10px" onclick="gameClient.skipNightPhase()">Skip Phase</button>` : '';
                if (!isAlive) {
                    container.innerHTML = `<div class="action-panel">${niTimerHtml}<p class="phase-hint">You are eliminated. The detective is gathering information...</p>${niHostSkip}</div>`;
                } else if (this.myRole === 'DETECTIVE') {
                    if (this.nightActionSubmitted) {
                        container.innerHTML = `<div class="action-panel">${niTimerHtml}<p class="phase-hint">Investigation complete. Waiting for dawn...</p>${niHostSkip}</div>`;
                    } else {
                        const investigateTargets = room.players.filter(p => p.alive && p.id !== this.currentPlayerId);
                        container.innerHTML = `
                            <div class="action-panel">
                                ${niTimerHtml}
                                <h3>Investigate a player to learn their role</h3>
                                <div class="target-list">
                                    ${investigateTargets.map(p => `
                                        <button class="btn btn-primary target-btn"
                                            onclick="gameClient.submitDetectiveInvestigate('${p.id}')">
                                            ${p.name}
                                        </button>
                                    `).join('')}
                                </div>
                                ${niHostSkip}
                            </div>`;
                    }
                } else {
                    container.innerHTML = `<div class="action-panel">${niTimerHtml}<p class="phase-hint">The detective is gathering information...</p>${niHostSkip}</div>`;
                }
                break;
            }

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
                const vtElapsed = this.votingTimerStart ? (Date.now() - this.votingTimerStart) / 1000 : 0;
                const vtRemaining = Math.max(0, 30 - vtElapsed);
                if (vtRemaining <= 0 && this.isHost) { this.forceResolveVotes(); break; }
                const vtTimerClass = vtRemaining <= 10 ? 'day-timer urgent' : 'day-timer';
                const vtTimerHtml = `<div class="${vtTimerClass}"><span>${Math.ceil(vtRemaining)}s</span> until auto-resolve</div>`;

                const aliveCount = room.players.filter(p => p.alive).length;
                const votesIn = Object.keys(room.votes || {}).length + (room.voteSkips || []).length;

                // Build vote log including skips
                const voteEntries = Object.entries(room.votes || {}).map(([voterId, targetId]) => {
                    const voter = room.players.find(p => p.id === voterId);
                    const target = room.players.find(p => p.id === targetId);
                    return `<li><strong>${voter?.name || '?'}</strong> voted for <strong>${target?.name || '?'}</strong></li>`;
                }).join('');
                const skipEntries = (room.voteSkips || []).map(skipperId => {
                    const skipper = room.players.find(p => p.id === skipperId);
                    return `<li class="vote-skip-entry"><strong>${skipper?.name || '?'}</strong> skipped voting</li>`;
                }).join('');
                const voteLog = (voteEntries || skipEntries)
                    ? `<ul class="vote-log">${voteEntries}${skipEntries}</ul>`
                    : '';

                if (this.voteSubmitted) {
                    container.innerHTML = `
                        <div class="action-panel">
                            ${vtTimerHtml}
                            <p class="phase-hint">Vote submitted. Waiting for others... (${votesIn}/${aliveCount} acted)</p>
                            ${voteLog}
                            ${this.isHost ? `<button class="btn btn-warning" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                } else if (!isAlive) {
                    container.innerHTML = `
                        <div class="action-panel">
                            ${vtTimerHtml}
                            <p class="phase-hint">You are eliminated. Watching the vote... (${votesIn}/${aliveCount} acted)</p>
                            ${voteLog}
                        </div>`;
                } else {
                    const voteTargets = room.players.filter(p => p.alive);
                    container.innerHTML = `
                        <div class="action-panel">
                            ${vtTimerHtml}
                            <h3>Vote to eliminate a player (${votesIn}/${aliveCount} acted)</h3>
                            <div class="target-list">
                                ${voteTargets.map(p => `
                                    <button class="btn btn-warning target-btn"
                                        onclick="gameClient.submitVote('${p.id}')">
                                        ${p.name}${p.id === this.currentPlayerId ? ' (You)' : ''}
                                    </button>
                                `).join('')}
                            </div>
                            <button class="btn btn-secondary" style="margin-top:8px" onclick="gameClient.skipMyVote()">Skip Vote</button>
                            ${voteLog}
                            ${this.isHost ? `<button class="btn btn-danger" style="margin-top:10px" onclick="gameClient.forceResolveVotes()">Force Resolve</button>` : ''}
                        </div>`;
                }
                break;
            }

            case 'ENDED': {
                this.showGameOverScreen(room);
                container.innerHTML = '';
                break;
            }

            default:
                container.innerHTML = '';
        }
    }

    // --- Rematch Actions ---

    async playAgainVote() {
        if (!this.isRequestAllowed('rematch', 3000)) return;
        document.querySelectorAll('.game-over-btn').forEach(b => b.disabled = true);
        try {
            await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/rematch?playerId=${this.currentPlayerId}&choice=PLAY_AGAIN`,
                { method: 'POST' }
            );
        } catch (error) {
            document.querySelectorAll('.game-over-btn').forEach(b => b.disabled = false);
        }
    }

    async returnToLobby() {
        if (!this.isRequestAllowed('rematch', 3000)) return;
        document.querySelectorAll('.game-over-btn').forEach(b => b.disabled = true);
        try {
            await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/rematch?playerId=${this.currentPlayerId}&choice=LEAVE`,
                { method: 'POST' }
            );
        } catch (error) {
            // ignore network errors — still leave
        }
        setTimeout(() => this.leaveRoom(), 1500);
    }

    // --- Game Actions ---

    async endDay() {
        if (!this.isRequestAllowed('endDay', 4000)) return;
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

    async skipNightPhase() {
        if (!this.isRequestAllowed('skipNight', 3000)) return;
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/night/end?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (!response.ok) {
                this.showMessage('Could not skip night phase', 'error');
            }
        } catch (error) {
            this.showMessage('Network error skipping night phase', 'error');
        }
    }

    async forceResolveVotes() {
        if (!this.isRequestAllowed('forceResolveVotes', 5000)) return;
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

    async skipMyVote() {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/vote/skip?voterId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (response.ok) {
                this.voteSubmitted = true;
                this.showMessage('You skipped voting.', 'info');
                this.updateRoomDisplay();
            } else {
                this.showMessage('Could not skip vote', 'error');
            }
        } catch (error) {
            this.showMessage('Network error skipping vote', 'error');
        } finally {
            this._actionInFlight = false;
        }
    }

    async skipMafiaKillVote() {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/night/mafia-skip?playerId=${this.currentPlayerId}`,
                { method: 'POST' }
            );
            if (response.ok) {
                this.nightActionSubmitted = true;
                this.showMessage('You chose not to kill tonight.', 'info');
                this.updateRoomDisplay();
            } else {
                this.showMessage('Could not skip kill vote', 'error');
            }
        } catch (error) {
            this.showMessage('Network error skipping kill vote', 'error');
        } finally {
            this._actionInFlight = false;
        }
    }

    async submitNightAction(targetId) {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
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
                this.showMessage('Target chosen.', 'success');
                this.updateRoomDisplay();
            } else {
                this.showMessage(`Failed to submit action: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting night action', 'error');
        } finally {
            this._actionInFlight = false;
        }
    }

    async submitDetectiveInvestigate(targetId) {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
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
                this.updateRoomDisplay();
            } else {
                this.showMessage(`Failed to investigate: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error investigating', 'error');
        } finally {
            this._actionInFlight = false;
        }
    }

    async submitDoctorProtect(targetId) {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
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
                this.showMessage('Protection chosen.', 'success');
                this.updateRoomDisplay();
            } else {
                this.showMessage(`Failed to protect: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting protection', 'error');
        } finally {
            this._actionInFlight = false;
        }
    }

    async submitVote(targetId) {
        if (this._actionInFlight) return;
        this._actionInFlight = true;
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
                this.updateRoomDisplay();
            } else {
                this.showMessage(`Failed to vote: ${await response.text()}`, 'error');
            }
        } catch (error) {
            this.showMessage('Network error submitting vote', 'error');
        } finally {
            this._actionInFlight = false;
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
            const isMafiaAlly = !isMe && this.myRole === 'MAFIA' && player.role === 'MAFIA';
            const allyTag = isMafiaAlly ? ' <span class="ally-tag">[ALLY]</span>' : '';
            div.innerHTML = `
                <div>
                    <strong>${player.name}${isMe ? ' (You)' : ''}${allyTag}</strong>
                    ${player.alive
                        ? '<span class="status-alive"> Alive</span>'
                        : '<span class="status-dead"> Eliminated</span>'}
                </div>`;
            container.appendChild(div);
        });
    }

    quickJoin(roomCode) {
        document.getElementById('room-code').value = roomCode;
        this.transitioning = false;
        this.showJoinRoom();
    }

    // --- Polling ---

    startRoomPolling() {
        this.stopRoomPolling();
        this._scheduleNextPoll();
    }

    _scheduleNextPoll() {
        this.roomPollingInterval = setTimeout(async () => {
            await this.updateRoomDisplay();
            if (this.currentRoomId) this._scheduleNextPoll();
        }, 2000);
    }

    stopRoomPolling() {
        if (this.roomPollingInterval) {
            clearTimeout(this.roomPollingInterval);
            this.roomPollingInterval = null;
        }
    }

    // --- Chat ---

    toggleChatPanel() {
        const panel = document.getElementById('chat-panel');
        this.chatPanelOpen = !this.chatPanelOpen;
        panel.classList.toggle('chat-open', this.chatPanelOpen);
    }

    updateChatButtonVisibility(room) {
        const btn = document.getElementById('chat-toggle-btn');
        if (!btn) return;
        const myPlayer = room.players.find(p => p.id === this.currentPlayerId);
        const isAlive = myPlayer ? myPlayer.alive : false;
        const isNightPhase = room.phase === 'NIGHT_MAFIA' || room.phase === 'NIGHT_DOCTOR' || room.phase === 'NIGHT_DETECTIVE';
        if (isAlive && this.myRole !== 'MAFIA' && isNightPhase) {
            btn.classList.remove('chat-btn-visible');
            if (this.chatPanelOpen) this.toggleChatPanel();
        } else {
            btn.classList.add('chat-btn-visible');
        }

        const label = document.getElementById('chat-channel-label');
        if (!label) return;
        if (!isAlive) {
            label.textContent = 'Dead Chat';
        } else if (this.myRole === 'MAFIA' && room.phase === 'NIGHT_MAFIA') {
            label.textContent = 'Mafia Chat';
        } else {
            label.textContent = 'Game Chat';
        }
    }

    async fetchAndRenderChat() {
        if (!this.currentRoomId || !this.currentPlayerId) return;
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/chat?playerId=${this.currentPlayerId}`
            );
            if (!response.ok) return;
            const messages = await response.json();
            if (messages.length === this.chatLastCount) return;

            const log = document.getElementById('chat-log');
            if (!log) return;
            const wasAtBottom = log.scrollHeight - log.scrollTop <= log.clientHeight + 20;

            log.innerHTML = '';
            messages.forEach(msg => {
                const li = document.createElement('li');
                if (msg.channel === 'DEAD') li.classList.add('channel-dead');
                if (msg.channel === 'MAFIA_NIGHT') li.classList.add('channel-mafia');
                const isMe = msg.playerName === this.currentPlayerName;
                const skull = msg.channel === 'DEAD' ? '💀 ' : '';
                const nameLabel = isMe
                    ? `${this.escapeHtml(msg.playerName)} <span class="chat-you">(You)</span>`
                    : this.escapeHtml(msg.playerName);
                li.innerHTML = `<span class="chat-name">${skull}${nameLabel}:</span><span class="chat-text">${this.escapeHtml(msg.message)}</span>`;
                log.appendChild(li);
            });

            this.chatLastCount = messages.length;
            if (wasAtBottom || messages.length > this.chatLastCount) {
                log.scrollTop = log.scrollHeight;
            }
        } catch (error) {
            // silently ignore chat fetch errors
        }
    }

    async sendChatMessage(text) {
        if (!this.currentRoomId || !this.currentPlayerId) return;
        if (!this.isRequestAllowed('sendChat', 1500)) return;
        try {
            const response = await fetch(
                `${this.baseUrl}/api/rooms/${this.currentRoomId}/chat?playerId=${this.currentPlayerId}`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: text })
                }
            );
            if (response.status === 429) {
                this.showMessage('Slow down!', 'warning');
            } else if (response.status === 403) {
                this.showMessage("You can't chat right now", 'error');
            } else if (!response.ok) {
                this.showMessage('Failed to send message', 'error');
            } else {
                await this.fetchAndRenderChat();
            }
        } catch (error) {
            this.showMessage('Network error sending message', 'error');
        }
    }

    escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // --- Rate Limiting ---

    isRequestAllowed(key, cooldownMs = 10000) {
        const now = Date.now();
        if (now - (this._lastRequest[key] || 0) < cooldownMs) return false;
        this._lastRequest[key] = now;
        return true;
    }

    applyCooldownVisual(buttonId, seconds) {
        const btn = document.getElementById(buttonId);
        if (!btn) return;
        const orig = btn.textContent.trim();
        btn.disabled = true;
        btn.style.opacity = '0.4';
        btn.style.cursor = 'not-allowed';
        let r = seconds;
        btn.textContent = `${orig} (${r}s)`;
        const tick = setInterval(() => {
            r--;
            if (r <= 0 || !btn.isConnected) {
                clearInterval(tick);
                if (btn.isConnected) {
                    btn.disabled = false;
                    btn.style.opacity = '';
                    btn.style.cursor = '';
                    btn.textContent = orig;
                }
            } else {
                btn.textContent = `${orig} (${r}s)`;
            }
        }, 1000);
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
