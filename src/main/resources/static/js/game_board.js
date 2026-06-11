// AI-Splendor Game Dashboard Client Logic

window.addEventListener('error', (event) => {
    const indicator = document.getElementById('active-player-indicator');
    if (indicator) {
        indicator.textContent = `JS Error: ${event.message} (Line ${event.lineno})`;
        indicator.style.color = '#b83230';
    }
    console.error("Global JS Error Captured:", event.error);
});

document.addEventListener('DOMContentLoaded', () => {
    // App State
    let gameId = null;
    let replayFile = null;
    let isReplayMode = false;
    let events = [];
    let currentEventIndex = -1;
    let replayInterval = null;
    let isPlaying = false;

    // Player metadata (display names)
    let playerNames = ["Player 0", "Player 1"];
    let cardIdToColor = {};
    let cardIdToCardObj = {};
    let processedEventTimestamps = new Set();

    // DOM Elements
    const livePulsar = document.getElementById('live-pulsar');
    const btnAbort = document.getElementById('btn-abort');
    const replayBadge = document.getElementById('replay-badge');
    const replayControlPanel = document.getElementById('replay-control-panel');
    const turnIndicator = document.getElementById('turn-indicator');
    const activePlayerIndicator = document.getElementById('active-player-indicator');
    
    // Replay controls
    const btnPrev = document.getElementById('btn-prev');
    const btnPlayPause = document.getElementById('btn-play-pause');
    const playPauseIcon = document.getElementById('play-pause-icon');
    const btnNext = document.getElementById('btn-next');
    const replayTimeline = document.getElementById('replay-timeline');
    const replayStepIndicator = document.getElementById('replay-step-indicator');

    // Player 0 Elements
    const p0Name = document.getElementById('p0-name');
    const p0Aside = document.getElementById('p0-aside');
    const p0TopSection = document.getElementById('p0-top-section');
    const p0Score = document.getElementById('p0-score');
    const p0Gems = {
        emerald: document.getElementById('p0-emerald-token'),
        sapphire: document.getElementById('p0-sapphire-token'),
        ruby: document.getElementById('p0-ruby-token'),
        onyx: document.getElementById('p0-onyx-token'),
        diamond: document.getElementById('p0-diamond-token'),
        gold: document.getElementById('p0-gold-token')
    };
    const p0Bonuses = {
        emerald: document.getElementById('p0-emerald-bonus'),
        sapphire: document.getElementById('p0-sapphire-bonus'),
        ruby: document.getElementById('p0-ruby-bonus'),
        onyx: document.getElementById('p0-onyx-bonus'),
        diamond: document.getElementById('p0-diamond-bonus')
    };
    const p0ReservedContainer = document.getElementById('p0-reserved-container');
    const p0ReasoningConsole = document.getElementById('p0-reasoning-console');

    // Player 1 Elements
    const p1Name = document.getElementById('p1-name');
    const p1Aside = document.getElementById('p1-aside');
    const p1TopSection = document.getElementById('p1-top-section');
    const p1Score = document.getElementById('p1-score');
    const p1Gems = {
        emerald: document.getElementById('p1-emerald-token'),
        sapphire: document.getElementById('p1-sapphire-token'),
        ruby: document.getElementById('p1-ruby-token'),
        onyx: document.getElementById('p1-onyx-token'),
        diamond: document.getElementById('p1-diamond-token'),
        gold: document.getElementById('p1-gold-token')
    };
    const p1Bonuses = {
        emerald: document.getElementById('p1-emerald-bonus'),
        sapphire: document.getElementById('p1-sapphire-bonus'),
        ruby: document.getElementById('p1-ruby-bonus'),
        onyx: document.getElementById('p1-onyx-bonus'),
        diamond: document.getElementById('p1-diamond-bonus')
    };
    const p1ReservedContainer = document.getElementById('p1-reserved-container');
    const p1ReasoningConsole = document.getElementById('p1-reasoning-console');

    // Board Elements
    const noblesContainer = document.getElementById('nobles-container');
    const row3 = document.getElementById('row-3');
    const row2 = document.getElementById('row-2');
    const row1 = document.getElementById('row-1');
    const deckSize3 = document.getElementById('deck-size-3');
    const deckSize2 = document.getElementById('deck-size-2');
    const deckSize1 = document.getElementById('deck-size-1');

    const bankGems = {
        emerald: document.getElementById('bank-emerald'),
        sapphire: document.getElementById('bank-sapphire'),
        ruby: document.getElementById('bank-ruby'),
        onyx: document.getElementById('bank-onyx'),
        diamond: document.getElementById('bank-diamond'),
        gold: document.getElementById('bank-gold')
    };

    // Modal Elements
    const endGameModal = document.getElementById('end-game-modal');
    const winnerReason = document.getElementById('winner-reason');
    const modalP0Name = document.getElementById('modal-p0-name');
    const modalP0Score = document.getElementById('modal-p0-score');
    const modalP1Name = document.getElementById('modal-p1-name');
    const modalP1Score = document.getElementById('modal-p1-score');
    const modalExecutionTime = document.getElementById('modal-execution-time');
    const modalCloseBtn = document.getElementById('modal-close-btn');
    const modalP0Tokens = document.getElementById('modal-p0-tokens');
    const modalP0Cost = document.getElementById('modal-p0-cost');
    const modalP1Tokens = document.getElementById('modal-p1-tokens');
    const modalP1Cost = document.getElementById('modal-p1-cost');
    const cardPreviewTooltip = document.getElementById('card-preview-tooltip');

    // Parse URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    gameId = urlParams.get('gameId');
    replayFile = urlParams.get('replayFile');

    if (replayFile) {
        isReplayMode = true;
    }

    // Initialize Dashboard
    async function init() {
        if (isReplayMode) {
            replayBadge.classList.remove('hidden');
            replayControlPanel.classList.remove('hidden');
            await loadReplayData();
        } else if (gameId) {
            livePulsar.classList.remove('hidden');
            if (btnAbort) btnAbort.classList.remove('hidden');
            await loadLiveHistory();
            connectWebSocket();
        } else {
            // Static view or no active game
            activePlayerIndicator.textContent = "No active match or log selected.";
        }
    }

    // --- WEBSOCKET MODE (LIVE) ---
    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/game/${gameId}`;
        console.log(`Connecting to WebSocket: ${wsUrl}`);
        
        const socket = new WebSocket(wsUrl);

        socket.onopen = () => {
            console.log("WebSocket connected.");
            activePlayerIndicator.textContent = "Connected. Awaiting first turn...";
        };

        socket.onmessage = (event) => {
            try {
                const gameEvent = JSON.parse(event.data);
                console.log("Received live game event:", gameEvent.eventType);
                
                const ts = gameEvent.timestamp;
                const type = detectEventType(gameEvent);
                const key = (ts && type) ? `${ts}_${type}` : ts;
                if (key && processedEventTimestamps.has(key)) {
                    console.log("Ignoring duplicate live event from WebSocket:", key);
                    return;
                }
                if (key) {
                    processedEventTimestamps.add(key);
                }
                
                events.push(gameEvent);
                currentEventIndex = events.length - 1;
                processEventLive(gameEvent);
            } catch (e) {
                console.error("Failed to parse WebSocket message", e);
            }
        };

        socket.onclose = () => {
            console.log("WebSocket disconnected.");
            activePlayerIndicator.textContent = "Live stream disconnected.";
        };

        socket.onerror = (err) => {
            console.error("WebSocket error", err);
        };
    }
    async function loadLiveHistory() {
        try {
            const response = await fetch(`/api/logs/${gameId}.json`);
            if (response.ok) {
                const rawText = await response.text();
                const historicalEvents = rawText.trim().split('\n')
                    .filter(line => line.trim() !== '')
                    .map(line => JSON.parse(line));
                
                console.log(`Preloaded ${historicalEvents.length} historical events for live game.`);
                
                // Clear console messages to start fresh
                p0ReasoningConsole.innerHTML = '';
                p1ReasoningConsole.innerHTML = '';

                // Create document fragments to batch appends and avoid layout thrashing
                const p0Fragment = document.createDocumentFragment();
                const p1Fragment = document.createDocumentFragment();

                const appendToFragment = (playerIdx, htmlContent, customClass) => {
                    const p = document.createElement('p');
                    p.className = customClass || '';
                    p.innerHTML = htmlContent;
                    if (playerIdx === 0) {
                        p0Fragment.appendChild(p);
                    } else {
                        p1Fragment.appendChild(p);
                    }
                };

                let lastSeenState = null;
                let activePlayerIdx = 0;
                let turnNum = 1;
                let gameEndedEvent = null;

                historicalEvents.forEach(ev => {
                    const ts = ev.timestamp;
                    const type = detectEventType(ev);
                    const key = (ts && type) ? `${ts}_${type}` : ts;
                    if (key) {
                        processedEventTimestamps.add(key);
                    }
                    events.push(ev);
                    currentEventIndex = events.length - 1;

                    switch (type) {
                        case "GAME_STARTED":
                            playerNames = [
                                ev.player0Name || ev.player0Model || "Player 0",
                                ev.player1Name || ev.player1Model || "Player 1"
                            ];
                            p0Name.textContent = playerNames[0];
                            p1Name.textContent = playerNames[1];
                            lastSeenState = ev.initialState;
                            
                            // Initialize consoles
                            const p0Started = document.createElement('p');
                            p0Started.className = 'text-primary font-bold';
                            p0Started.textContent = '> Game started';
                            p0Fragment.appendChild(p0Started);

                            const p1Started = document.createElement('p');
                            p1Started.className = 'text-primary font-bold';
                            p1Started.textContent = '> Game started';
                            p1Fragment.appendChild(p1Started);

                            registerCardsFromState(ev.initialState);
                            break;

                        case "TURN_STARTED":
                            const turnNumVal = ev.turnNumber !== undefined ? ev.turnNumber : ev.turn;
                            const currentIdxVal = ev.currentPlayerIndex !== undefined ? ev.currentPlayerIndex : ev.playerIndex;
                            turnNum = turnNumVal;
                            activePlayerIdx = currentIdxVal;
                            lastSeenState = ev.gameState || ev.state;
                            
                            appendToFragment(currentIdxVal, formatTokensAndCards(`\n=== Turn ${turnNumVal} ===`));
                            registerCardsFromState(ev.gameState || ev.state);
                            break;

                        case "REASONING":
                            appendToFragment(ev.playerIndex, formatTokensAndCards(`> ${ev.reasoning}`));
                            break;

                        case "ACTION":
                            const actStr = summarizeAction(ev.action);
                            const dur = ev.durationMs ? `(${formatDurationShort(ev.durationMs)})` : '';
                            const customClass = ev.playerIndex === 0 ? 'text-tertiary font-bold mt-1 mb-2' : 'text-primary font-bold mt-1 mb-2';
                            appendToFragment(ev.playerIndex, formatTokensAndCards(`Selected Action: ${actStr} ${dur}`), customClass);
                            break;

                        case "RETRY":
                            appendToFragment(ev.playerIndex, formatTokensAndCards(`[RETRY #${ev.attemptNumber}] Error: ${ev.errorMessage}`), 'text-error font-bold');
                            break;

                        case "GAME_ENDED":
                            gameEndedEvent = ev;
                            break;
                    }
                });

                // Batch append reasoning fragments
                p0ReasoningConsole.appendChild(p0Fragment);
                p1ReasoningConsole.appendChild(p1Fragment);

                // Scroll to bottom once
                p0ReasoningConsole.scrollTop = p0ReasoningConsole.scrollHeight;
                p1ReasoningConsole.scrollTop = p1ReasoningConsole.scrollHeight;

                // Render latest board state exactly once
                if (lastSeenState) {
                    renderGameState(lastSeenState);
                }

                // Update turn and active indicators
                turnIndicator.textContent = `Turn ${turnNum}`;
                const activePlayer = activePlayerIdx === 0 ? playerNames[0] : playerNames[1];
                activePlayerIndicator.textContent = `Active: ${activePlayer}`;
                highlightActivePlayer(activePlayerIdx);

                // Handle game end state
                if (gameEndedEvent) {
                    activePlayerIndicator.textContent = `Finished. Winner: ${gameEndedEvent.winnerReason || "Tie"}`;
                    if (btnAbort) btnAbort.classList.add('hidden');
                    showEndGameModal(gameEndedEvent);
                }
            }
        } catch (e) {
            console.warn("No historical log file found or failed to load history, starting clean live view.", e);
        }
    }
    function detectEventType(ev) {
        if (!ev) return null;
        if (ev.eventType) return ev.eventType;
        if (ev.initialState !== undefined) return "GAME_STARTED";
        if (ev.gameState !== undefined || ev.state !== undefined) return "TURN_STARTED";
        if (ev.reasoning !== undefined) return "REASONING";
        if (ev.action !== undefined) return "ACTION";
        if (ev.attemptNumber !== undefined || ev.errorMessage !== undefined) return "RETRY";
        if (ev.winnerIndex !== undefined || ev.winnerReason !== undefined) return "GAME_ENDED";
        return null;
    }

    function processEventLive(event) {
        // In Live mode, we simply apply events sequentially
        const type = detectEventType(event);
        switch (type) {
            case "GAME_STARTED":
                playerNames = [
                    event.player0Name || event.player0Model || "Player 0",
                    event.player1Name || event.player1Model || "Player 1"
                ];
                p0Name.textContent = playerNames[0];
                p1Name.textContent = playerNames[1];
                p0ReasoningConsole.innerHTML = '<p class="text-primary font-bold">&gt; Game started</p>';
                p1ReasoningConsole.innerHTML = '<p class="text-primary font-bold">&gt; Game started</p>';
                registerCardsFromState(event.initialState);
                renderGameState(event.initialState);
                break;

            case "TURN_STARTED":
                const turnNumVal = event.turnNumber !== undefined ? event.turnNumber : event.turn;
                const currentIdxVal = event.currentPlayerIndex !== undefined ? event.currentPlayerIndex : event.playerIndex;
                turnIndicator.textContent = `Turn ${turnNumVal}`;
                const activePlayer = currentIdxVal === 0 ? playerNames[0] : playerNames[1];
                activePlayerIndicator.textContent = `Active: ${activePlayer}`;
                
                // Highlight active player card
                highlightActivePlayer(currentIdxVal);
                registerCardsFromState(event.gameState || event.state);
                renderGameState(event.gameState || event.state);
                break;

            case "REASONING":
                appendReasoning(event.playerIndex, event.reasoning);
                break;

            case "ACTION":
                appendAction(event.playerIndex, event.action, event.durationMs);
                break;

            case "RETRY":
                appendRetry(event.playerIndex, event.attemptNumber, event.errorMessage);
                break;

            case "GAME_ENDED":
                activePlayerIndicator.textContent = `Finished. Winner: ${event.winnerReason || "Tie"}`;
                if (btnAbort) btnAbort.classList.add('hidden');
                showEndGameModal(event);
                break;
        }
    }

    // --- REPLAY MODE (HISTORICAL) ---
    async function loadReplayData() {
        activePlayerIndicator.textContent = "Loading replay data...";
        try {
            const response = await fetch(`/api/logs/${replayFile}`);
            if (!response.ok) {
                throw new Error(`Failed to load log file: ${response.statusText}`);
            }
            
            const rawText = await response.text();
            // Parse NDJSON lines
            events = rawText.trim().split('\n')
                .filter(line => line.trim() !== '')
                .map(line => JSON.parse(line));

            if (events.length === 0) {
                throw new Error("No events found in the log file.");
            }

            console.log(`Successfully loaded ${events.length} replay events.`);
            
            // Pre-scan all cards from events to populate lookup dictionary
            events.forEach(ev => {
                const type = detectEventType(ev);
                if (type === "GAME_STARTED" && ev.initialState) {
                    registerCardsFromState(ev.initialState);
                } else if (type === "TURN_STARTED" && (ev.gameState || ev.state)) {
                    registerCardsFromState(ev.gameState || ev.state);
                }
            });

            // Setup timeline properties
            replayTimeline.max = events.length - 1;
            replayTimeline.value = 0;
            currentEventIndex = 0;

            setupReplayControls();
            renderEventAt(0);
        } catch (e) {
            console.error("Replay load error", e);
            activePlayerIndicator.textContent = `Replay Error: ${e.message}`;
            alert(`Failed to load replay: ${e.message}`);
        }
    }

    function setupReplayControls() {
        btnPlayPause.addEventListener('click', togglePlayPause);
        btnNext.addEventListener('click', stepForward);
        btnPrev.addEventListener('click', stepBackward);
        
        replayTimeline.addEventListener('input', (e) => {
            pauseReplay();
            currentEventIndex = parseInt(e.target.value);
            renderEventAt(currentEventIndex);
        });

        // Keyboard navigation
        document.addEventListener('keydown', (e) => {
            if (isReplayMode) {
                if (e.key === 'ArrowRight') {
                    pauseReplay();
                    stepForward();
                } else if (e.key === 'ArrowLeft') {
                    pauseReplay();
                    stepBackward();
                } else if (e.key === ' ') {
                    e.preventDefault();
                    togglePlayPause();
                }
            }
        });
    }

    function togglePlayPause() {
        if (isPlaying) {
            pauseReplay();
        } else {
            playReplay();
        }
    }

    function playReplay() {
        if (currentEventIndex >= events.length - 1) {
            currentEventIndex = 0; // Loop to start
        }
        isPlaying = true;
        playPauseIcon.textContent = 'pause';
        replayInterval = setInterval(() => {
            if (currentEventIndex < events.length - 1) {
                stepForward();
            } else {
                pauseReplay();
            }
        }, 1200); // 1.2 seconds per event step
    }

    function pauseReplay() {
        isPlaying = false;
        playPauseIcon.textContent = 'play_arrow';
        if (replayInterval) {
            clearInterval(replayInterval);
            replayInterval = null;
        }
    }

    function stepForward() {
        if (currentEventIndex < events.length - 1) {
            currentEventIndex++;
            replayTimeline.value = currentEventIndex;
            renderEventAt(currentEventIndex);
        }
    }

    function stepBackward() {
        if (currentEventIndex > 0) {
            currentEventIndex--;
            replayTimeline.value = currentEventIndex;
            renderEventAt(currentEventIndex);
        }
    }

    // Render replay state at specified event index
    function renderEventAt(index) {
        if (index < 0 || index >= events.length) return;
        
        replayStepIndicator.textContent = `Step ${index + 1}/${events.length}`;

        // Reset display names and reasoning panels
        p0ReasoningConsole.innerHTML = '';
        p1ReasoningConsole.innerHTML = '';

        // DocumentFragment to batch DOM updates
        const p0Fragment = document.createDocumentFragment();
        const p1Fragment = document.createDocumentFragment();

        const appendToFragment = (playerIdx, htmlContent, customClass) => {
            const p = document.createElement('p');
            p.className = customClass || '';
            p.innerHTML = htmlContent;
            if (playerIdx === 0) {
                p0Fragment.appendChild(p);
            } else {
                p1Fragment.appendChild(p);
            }
        };

        let lastSeenState = null;
        let activePlayerIdx = 0;
        let turnNum = 1;

        // Traverse history from start up to index to build console lists and get most recent state
        for (let i = 0; i <= index; i++) {
            const ev = events[i];
            const type = detectEventType(ev);
            
            if (type === "GAME_STARTED") {
                playerNames = [
                    ev.player0Name || ev.player0Model || "Player 0",
                    ev.player1Name || ev.player1Model || "Player 1"
                ];
                p0Name.textContent = playerNames[0];
                p1Name.textContent = playerNames[1];
                lastSeenState = ev.initialState;
                appendToFragment(0, formatTokensAndCards(`> Game started with P0: ${playerNames[0]}`));
                appendToFragment(1, formatTokensAndCards(`> Game started with P1: ${playerNames[1]}`));
            }
            else if (type === "TURN_STARTED") {
                const turnNumVal = ev.turnNumber !== undefined ? ev.turnNumber : ev.turn;
                const currentIdxVal = ev.currentPlayerIndex !== undefined ? ev.currentPlayerIndex : ev.playerIndex;
                turnNum = turnNumVal;
                activePlayerIdx = currentIdxVal;
                lastSeenState = ev.gameState || ev.state;
                appendToFragment(currentIdxVal, formatTokensAndCards(`\n=== Turn ${turnNumVal} ===`));
            }
            else if (type === "REASONING") {
                appendToFragment(ev.playerIndex, formatTokensAndCards(`> ${ev.reasoning}`));
            }
            else if (type === "ACTION") {
                const actStr = summarizeAction(ev.action);
                const dur = ev.durationMs ? `(${formatDurationShort(ev.durationMs)})` : '';
                const customClass = ev.playerIndex === 0 ? 'text-tertiary font-bold mt-1 mb-2' : 'text-primary font-bold mt-1 mb-2';
                appendToFragment(ev.playerIndex, formatTokensAndCards(`Selected Action: ${actStr} ${dur}`), customClass);
            }
            else if (type === "RETRY") {
                appendToFragment(ev.playerIndex, formatTokensAndCards(`[RETRY #${ev.attemptNumber}] Error: ${ev.errorMessage}`), 'text-error font-bold');
            }
            else if (type === "GAME_ENDED") {
                appendToFragment(0, formatTokensAndCards(`\n=== Game Over ===`));
                appendToFragment(1, formatTokensAndCards(`\n=== Game Over ===`));
                appendToFragment(ev.winnerIndex === 0 ? 0 : 1, formatTokensAndCards(`🏆 WinnerDeclared! Reason: ${ev.winnerReason}`), 'text-primary font-bold');
            }
        }

        // Batch append reasoning fragments
        p0ReasoningConsole.appendChild(p0Fragment);
        p1ReasoningConsole.appendChild(p1Fragment);

        // Scroll to bottom once
        p0ReasoningConsole.scrollTop = p0ReasoningConsole.scrollHeight;
        p1ReasoningConsole.scrollTop = p1ReasoningConsole.scrollHeight;

        // Render current metadata
        turnIndicator.textContent = `Turn ${turnNum}`;
        const activePlayer = activePlayerIdx === 0 ? playerNames[0] : playerNames[1];
        activePlayerIndicator.textContent = `Active: ${activePlayer}`;
        highlightActivePlayer(activePlayerIdx);

        // Render board
        if (lastSeenState) {
            renderGameState(lastSeenState);
        }

        // Modal triggers on the exact final step
        const finalEvent = events[index];
        const finalType = detectEventType(finalEvent);
        if (finalType === "GAME_ENDED") {
            showEndGameModal(finalEvent);
        } else {
            endGameModal.classList.add('hidden');
        }
    }

    // --- UI RENDERING & BUILDERS ---

    function highlightActivePlayer(index) {
        if (index === 0) {
            p0Name.parentElement.className = "flex justify-between items-center mb-2 pb-2 border-b-2 border-tertiary transition-all duration-350";
            p1Name.parentElement.className = "flex justify-between items-center mb-2 pb-2 border-b border-surface-container transition-all duration-350 opacity-70";
            p0Aside.classList.add('active-player-pulse-0');
            p0Aside.classList.remove('opacity-60');
            p1Aside.classList.remove('active-player-pulse-1');
            p1Aside.classList.remove('opacity-60');
            if (p0TopSection) p0TopSection.classList.remove('opacity-60');
            if (p1TopSection) p1TopSection.classList.add('opacity-60');
        } else {
            p0Name.parentElement.className = "flex justify-between items-center mb-2 pb-2 border-b border-surface-container transition-all duration-350 opacity-70";
            p1Name.parentElement.className = "flex justify-between items-center mb-2 pb-2 border-b-2 border-primary transition-all duration-350";
            p0Aside.classList.remove('active-player-pulse-0');
            p0Aside.classList.remove('opacity-60');
            p1Aside.classList.add('active-player-pulse-1');
            p1Aside.classList.remove('opacity-60');
            if (p0TopSection) p0TopSection.classList.add('opacity-60');
            if (p1TopSection) p1TopSection.classList.remove('opacity-60');
        }
    }

    function registerCardsFromState(state) {
        if (!state) return;
        
        const registerList = (list) => {
            if (!list || !Array.isArray(list)) return;
            list.forEach(card => {
                if (card && card.id) {
                    const color = card.bonusGem || card.bonusColor;
                    if (color) {
                        cardIdToColor[card.id] = color.toUpperCase();
                        cardIdToCardObj[card.id] = card;
                    }
                }
            });
        };
        
        // 1. Face up cards
        if (state.board && state.board.faceUpCards) {
            Object.values(state.board.faceUpCards).forEach(registerList);
        }
        
        // 2. Decks
        if (state.board && state.board.decks) {
            Object.values(state.board.decks).forEach(registerList);
        }
        
        // 3. Players reserved and purchased cards
        if (state.players && Array.isArray(state.players)) {
            state.players.forEach(player => {
                registerList(player.reservedCards);
                registerList(player.purchasedCards);
            });
        }
    }

    function renderGameState(state) {
        if (!state) return;

        registerCardsFromState(state);

        // 1. Players scores & gems
        const players = state.players;
        if (players && players.length >= 2) {
            const p0 = players[0];
            const p1 = players[1];

            // P0 Scores
            p0Score.textContent = p0.score;
            p0Score.className = p0.score >= 15 
                ? "bg-tertiary text-on-tertiary px-3 py-1 rounded-full font-bold text-xl font-headline transition-all animate-pulse"
                : "bg-tertiary-container/30 text-on-tertiary-container px-3 py-1 rounded-full font-bold text-xl font-headline transition-all";

            // P1 Scores
            p1Score.textContent = p1.score;
            p1Score.className = p1.score >= 15 
                ? "bg-primary text-on-primary px-3 py-1 rounded-full font-bold text-xl font-headline transition-all animate-pulse"
                : "bg-surface-container text-on-surface px-3 py-1 rounded-full font-bold text-xl font-headline transition-all";

            // Player Gems Tokens & Bonuses
            updatePlayerGems(0, p0);
            updatePlayerGems(1, p1);

            // Player Reserved Cards
            renderReservedCards(0, p0.reservedCards);
            renderReservedCards(1, p1.reservedCards);
        }

        // 2. Token Bank
        const bank = state.board.bank || state.board.availableTokens || {};
        const tokens = bank.tokens || bank.counts || {};
        updateBankToken('emerald', tokens.GREEN || 0);
        updateBankToken('sapphire', tokens.BLUE || 0);
        updateBankToken('ruby', tokens.RED || 0);
        updateBankToken('onyx', tokens.BLACK || 0);
        updateBankToken('diamond', tokens.WHITE || 0);
        updateBankToken('gold', tokens.GOLD || 0);

        // 3. Nobles
        const nobles = state.board.availableNobles || [];
        renderNobles(nobles);

        // 4. Face-up Cards
        const faceUp = state.board.faceUpCards || {};
        renderRow(row3, faceUp.LEVEL_3 || [], 3);
        renderRow(row2, faceUp.LEVEL_2 || [], 2);
        renderRow(row1, faceUp.LEVEL_1 || [], 1);

        // 5. Deck Sizes
        const decks = state.board.decks || {};
        if (deckSize3) deckSize3.textContent = decks.LEVEL_3 ? decks.LEVEL_3.length : 0;
        if (deckSize2) deckSize2.textContent = decks.LEVEL_2 ? decks.LEVEL_2.length : 0;
        if (deckSize1) deckSize1.textContent = decks.LEVEL_1 ? decks.LEVEL_1.length : 0;
    }

    function updatePlayerGems(playerIdx, playerState) {
        const tokensObj = playerState.tokens || {};
        const gems = tokensObj.tokens || tokensObj.counts || {};
        const bonuses = playerState.bonuses || {};
        const prefix = playerIdx === 0 ? p0Gems : p1Gems;
        const bPrefix = playerIdx === 0 ? p0Bonuses : p1Bonuses;

        // Gems Tokens
        updateTokenStyle(prefix.emerald, gems.GREEN || 0);
        updateTokenStyle(prefix.sapphire, gems.BLUE || 0);
        updateTokenStyle(prefix.ruby, gems.RED || 0);
        updateTokenStyle(prefix.onyx, gems.BLACK || 0);
        updateTokenStyle(prefix.diamond, gems.WHITE || 0);
        updateTokenStyle(prefix.gold, gems.GOLD || 0);

        // Bonuses
        bPrefix.emerald.textContent = bonuses.GREEN || 0;
        bPrefix.sapphire.textContent = bonuses.BLUE || 0;
        bPrefix.ruby.textContent = bonuses.RED || 0;
        bPrefix.onyx.textContent = bonuses.BLACK || 0;
        bPrefix.diamond.textContent = bonuses.WHITE || 0;
    }

    // --- Premium Vector SVG Gem Icons (Terra Palette) ---
    function getGreenGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-green" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#2bb168" />
                    <stop offset="100%" stop-color="#14532d" />
                </linearGradient>
            </defs>
            <polygon points="30,10 70,10 90,30 90,70 70,90 30,90 10,70 10,30" fill="url(#grad-green)" stroke="#166534" stroke-width="3" />
            <polygon points="30,10 70,10 50,50" fill="#ffffff" fill-opacity="0.18" />
            <polygon points="70,10 90,30 50,50" fill="#000000" fill-opacity="0.1" />
            <polygon points="90,30 90,70 50,50" fill="#ffffff" fill-opacity="0.05" />
            <polygon points="90,70 70,90 50,50" fill="#000000" fill-opacity="0.15" />
            <polygon points="70,90 30,90 50,50" fill="#000000" fill-opacity="0.25" />
            <polygon points="30,90 10,70 50,50" fill="#000000" fill-opacity="0.15" />
            <polygon points="10,70 10,30 50,50" fill="#ffffff" fill-opacity="0.1" />
            <polygon points="10,30 30,10 50,50" fill="#ffffff" fill-opacity="0.25" />
            <polygon points="38,22 62,22 74,38 74,62 62,74 38,74 26,62 26,38" fill="url(#grad-green)" fill-opacity="0.9" stroke="#15803d" stroke-width="1.5" />
        </svg>`;
    }

    function getBlueGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-blue" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#3b82f6" />
                    <stop offset="100%" stop-color="#1e3a8a" />
                </linearGradient>
            </defs>
            <path d="M50,5 C50,5 90,45 90,68 C90,85 72,95 50,95 C28,95 10,85 10,68 C10,45 50,5 50,5 Z" fill="url(#grad-blue)" stroke="#1e40af" stroke-width="3" />
            <path d="M50,5 L50,50 L90,68" stroke="#ffffff" stroke-opacity="0.25" stroke-width="1.5" />
            <path d="M50,5 L50,50 L10,68" stroke="#ffffff" stroke-opacity="0.25" stroke-width="1.5" />
            <path d="M50,50 L50,95" stroke="#000000" stroke-opacity="0.2" stroke-width="1.5" />
            <polygon points="50,5 72,46 50,30" fill="#ffffff" fill-opacity="0.15" />
            <polygon points="50,5 28,46 50,30" fill="#ffffff" fill-opacity="0.2" />
            <polygon points="50,50 90,68 70,82" fill="#000000" fill-opacity="0.1" />
            <polygon points="50,50 10,68 30,82" fill="#ffffff" fill-opacity="0.1" />
            <polygon points="50,50 50,95 70,82" fill="#000000" fill-opacity="0.15" />
            <polygon points="50,50 50,95 30,82" fill="#000000" fill-opacity="0.15" />
        </svg>`;
    }

    function getRedGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-red" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#f87171" />
                    <stop offset="100%" stop-color="#7f1d1d" />
                </linearGradient>
            </defs>
            <path d="M25,8 C40,5 60,5 75,8 C88,12 95,25 92,40 C95,60 95,65 92,75 C88,88 75,95 50,95 C25,95 12,88 8,75 C5,65 5,60 8,40 C12,25 25,12 25,8 Z" fill="url(#grad-red)" stroke="#991b1b" stroke-width="3" />
            <polygon points="25,8 50,5 50,50" fill="#ffffff" fill-opacity="0.2" />
            <polygon points="50,5 75,8 50,50" fill="#ffffff" fill-opacity="0.1" />
            <polygon points="75,8 92,40 50,50" fill="#000000" fill-opacity="0.05" />
            <polygon points="92,40 92,75 50,50" fill="#000000" fill-opacity="0.15" />
            <polygon points="92,75 50,95 50,50" fill="#000000" fill-opacity="0.25" />
            <polygon points="50,95 8,75 50,50" fill="#000000" fill-opacity="0.15" />
            <polygon points="8,75 8,40 50,50" fill="#ffffff" fill-opacity="0.05" />
            <polygon points="8,40 25,8 50,50" fill="#ffffff" fill-opacity="0.25" />
            <path d="M35,22 C45,20 55,20 65,22 C72,25 78,32 75,45 C78,55 78,60 75,65 C72,72 65,78 50,78 C35,78 28,72 25,65 C22,60 22,55 25,45 C28,32 35,25 35,22 Z" fill="url(#grad-red)" fill-opacity="0.9" stroke="#b91c1c" stroke-width="1.5" />
        </svg>`;
    }

    function getBlackGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-black" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#6b7280" />
                    <stop offset="100%" stop-color="#111827" />
                </linearGradient>
            </defs>
            <polygon points="25,5 75,5 95,25 95,75 75,95 25,95 5,75 5,25" fill="url(#grad-black)" stroke="#1f2937" stroke-width="3" />
            <polygon points="25,5 75,5 70,15 30,15" fill="#ffffff" fill-opacity="0.15" />
            <polygon points="75,5 95,25 85,30 70,15" fill="#000000" fill-opacity="0.1" />
            <polygon points="95,25 95,75 85,70 85,30" fill="#ffffff" fill-opacity="0.05" />
            <polygon points="95,75 75,95 70,85 85,70" fill="#000000" fill-opacity="0.2" />
            <polygon points="75,95 25,95 30,85 70,85" fill="#000000" fill-opacity="0.3" />
            <polygon points="25,95 5,75 15,70 30,85" fill="#000000" fill-opacity="0.15" />
            <polygon points="5,75 5,25 15,30 15,70" fill="#ffffff" fill-opacity="0.08" />
            <polygon points="5,25 25,5 30,15 15,30" fill="#ffffff" fill-opacity="0.2" />
            <polygon points="30,15 70,15 85,30 85,70 70,85 30,85 15,70 15,30" fill="url(#grad-black)" fill-opacity="0.9" stroke="#374151" stroke-width="1.5" />
        </svg>`;
    }

    function getWhiteGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-white" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#ffffff" />
                    <stop offset="100%" stop-color="#e2e8f0" />
                </linearGradient>
            </defs>
            <polygon points="50,2 92,25 80,85 20,85 8,25" fill="url(#grad-white)" stroke="#cbd5e1" stroke-width="3" />
            <polygon points="50,2 92,25 50,45" fill="#f1f5f9" fill-opacity="0.7" />
            <polygon points="92,25 80,85 50,45" fill="#94a3b8" fill-opacity="0.2" />
            <polygon points="80,85 50,98 50,45" fill="#475569" fill-opacity="0.15" />
            <polygon points="50,98 20,85 50,45" fill="#475569" fill-opacity="0.1" />
            <polygon points="20,85 8,25 50,45" fill="#94a3b8" fill-opacity="0.3" />
            <polygon points="8,25 50,2 50,45" fill="#ffffff" fill-opacity="0.95" />
            <polygon points="50,15 75,28 65,65 35,65 25,28" fill="#ffffff" stroke="#e2e8f0" stroke-width="1.5" />
        </svg>`;
    }

    function getGoldGem(sizeClass) {
        return `<svg class="${sizeClass}" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id="grad-gold" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#fbbf24" />
                    <stop offset="50%" stop-color="#d97706" />
                    <stop offset="100%" stop-color="#78350f" />
                </linearGradient>
                <linearGradient id="gold-shine" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#fffbeb" />
                    <stop offset="100%" stop-color="#fbbf24" />
                </linearGradient>
            </defs>
            <circle cx="50" cy="50" r="45" fill="url(#grad-gold)" stroke="#b45309" stroke-width="3" />
            <circle cx="50" cy="50" r="37" fill="url(#gold-shine)" stroke="#d97706" stroke-width="1.5" fill-opacity="0.9" />
            <polygon points="50,22 56,38 74,38 60,48 65,66 50,56 35,66 40,48 26,38 44,38" fill="url(#grad-gold)" stroke="#78350f" stroke-width="1" />
            <circle cx="50" cy="50" r="5" fill="#fffbeb" />
        </svg>`;
    }

    function getGemSvg(color, sizeClass = 'w-4 h-4') {
        const norm = String(color).toUpperCase().trim();
        if (norm.includes('GREEN') || norm.includes('EMERALD')) return getGreenGem(sizeClass);
        if (norm.includes('BLUE') || norm.includes('SAPPHIRE')) return getBlueGem(sizeClass);
        if (norm.includes('RED') || norm.includes('RUBY')) return getRedGem(sizeClass);
        if (norm.includes('BLACK') || norm.includes('ONYX')) return getBlackGem(sizeClass);
        if (norm.includes('WHITE') || norm.includes('DIAMOND')) return getWhiteGem(sizeClass);
        if (norm.includes('GOLD')) return getGoldGem(sizeClass);
        return getGoldGem(sizeClass);
    }

    function getNormalizedColorName(element) {
        const id = element.id.toLowerCase();
        if (id.includes('emerald') || id.includes('green')) return 'GREEN';
        if (id.includes('sapphire') || id.includes('blue')) return 'BLUE';
        if (id.includes('ruby') || id.includes('red')) return 'RED';
        if (id.includes('onyx') || id.includes('black')) return 'BLACK';
        if (id.includes('diamond') || id.includes('white')) return 'WHITE';
        if (id.includes('gold') || id.includes('yellow')) return 'GOLD';
        return 'GOLD';
    }

    function updateTokenStyle(element, value) {
        const oldValue = element.dataset.value;
        element.dataset.value = value;
        
        const colorName = getNormalizedColorName(element);
        
        element.style.background = 'transparent';
        element.style.borderColor = 'transparent';
        element.style.boxShadow = 'none';
        
        element.innerHTML = `
            <div class="relative w-full h-full flex items-center justify-center">
                ${getGemSvg(colorName, 'w-full h-full')}
                <span class="absolute text-white font-bold font-headline drop-shadow-[0_1.5px_1.5px_rgba(0,0,0,0.8)] text-base select-none">${value}</span>
            </div>
        `;

        if (value === 0) {
            element.classList.add('opacity-40');
        } else {
            element.classList.remove('opacity-40');
        }
        
        if (oldValue !== undefined && oldValue !== String(value)) {
            element.classList.add('token-change-pop');
            setTimeout(() => {
                element.classList.remove('token-change-pop');
            }, 400);
        }
    }

    function updateBankToken(color, value) {
        const element = bankGems[color];
        const oldValue = element.dataset.value;
        element.dataset.value = value;

        element.style.background = 'transparent';
        element.style.borderColor = 'transparent';
        element.style.boxShadow = 'none';

        element.innerHTML = `
            <div class="relative w-full h-full flex items-center justify-center">
                ${getGemSvg(color, 'w-full h-full')}
                <span class="absolute text-white font-bold font-headline drop-shadow-[0_1.5px_1.5px_rgba(0,0,0,0.8)] text-lg select-none">${value}</span>
            </div>
        `;

        if (value === 0) {
            element.classList.add('opacity-30', 'scale-90');
        } else {
            element.classList.remove('opacity-30', 'scale-90');
        }

        if (oldValue !== undefined && oldValue !== String(value)) {
            element.classList.add('token-change-pop');
            setTimeout(() => {
                element.classList.remove('token-change-pop');
            }, 400);
        }
    }

    function renderNobles(nobles) {
        noblesContainer.innerHTML = '';
        if (nobles.length === 0) {
            noblesContainer.innerHTML = '<span class="text-xs text-outline italic m-auto">All nobles visited</span>';
            return;
        }

        nobles.forEach(noble => {
            const nDiv = document.createElement('div');
            nDiv.className = "w-24 h-full bg-surface-container-lowest rounded-lg shadow-sm border border-surface-container-highest flex flex-col p-2";
            
            const points = document.createElement('div');
            points.className = "text-lg font-bold text-on-background font-headline leading-none";
            const pts = (noble.prestigePoints !== undefined) ? noble.prestigePoints : (noble.victoryPoints || 3);
            points.textContent = pts;

            const reqs = document.createElement('div');
            reqs.className = "flex flex-col gap-0.5 mt-auto";

            const costs = noble.requirements || noble.requirement || {};
            Object.entries(costs).forEach(([color, cost]) => {
                if (cost > 0) {
                    const row = document.createElement('div');
                    row.className = "flex items-center gap-1 text-[10px] font-headline font-bold text-on-background";
                    row.innerHTML = `<div class="w-3.5 h-3.5 flex items-center justify-center">${getGemSvg(color, 'w-full h-full')}</div> ${cost}`;
                    reqs.appendChild(row);
                }
            });

            nDiv.appendChild(points);
            nDiv.appendChild(reqs);
            noblesContainer.appendChild(nDiv);
        });
    }

    function renderRow(rowElement, cards, level) {
        const oldCardIds = Array.from(rowElement.children).map(child => child.dataset ? child.dataset.cardId : null);
        
        rowElement.innerHTML = '';
        
        // Pad row up to 4 card placeholders if necessary
        for (let i = 0; i < 4; i++) {
            const card = cards[i];
            const cardWrapper = document.createElement('div');
            cardWrapper.className = "flex-1 rounded-md border flex flex-col overflow-hidden relative shadow-sm h-full transition-all";
            
            if (card) {
                const color = card.bonusGem || card.bonusColor;
                const points = (card.prestigePoints !== undefined) ? card.prestigePoints : (card.victoryPoints || 0);
                
                cardWrapper.dataset.cardId = card.id;
                
                const isNewCard = oldCardIds[i] !== card.id;
                const flipClass = isNewCard ? " card-flip-entrance" : "";
                
                cardWrapper.className += ` ${getCardBgClass(color)} border-outline-variant/30 hover:shadow-md hover:scale-[1.02]${flipClass}`;
                
                // Card Header
                const cardHeader = document.createElement('div');
                const headerTextClass = color === 'WHITE' ? 'text-on-background' : 'text-white';
                cardHeader.className = `${getCardHeaderBgClass(color)} p-1 px-2 flex justify-between items-center ${headerTextClass} h-7 relative z-10`;
                if (color === 'WHITE') cardHeader.className += ' border-b border-outline-variant/20';

                cardHeader.innerHTML = `
                    <div class="text-base font-bold leading-none font-headline">${points > 0 ? points : ''}</div>
                    <div class="w-3.5 h-3.5 flex items-center justify-center">${getGemSvg(color, 'w-full h-full')}</div>
                `;

                // Card Code / Watermark
                const watermark = document.createElement('div');
                watermark.className = "absolute inset-0 top-7 flex items-center justify-center z-0 pointer-events-none mb-6";
                watermark.innerHTML = `<div class="font-headline font-bold text-[28px] ${getWatermarkTextClass(color)} leading-none select-none" style="opacity: 0.55">${card.id}</div>`;

                // Cost section
                const costSection = document.createElement('div');
                costSection.className = "p-1.5 mt-auto flex gap-1 z-10 w-full flex-wrap justify-start items-center bg-white/20";
                
                const costs = card.costs || card.cost || {};
                Object.entries(costs).forEach(([cColor, cCost]) => {
                    if (cCost > 0) {
                        const badge = document.createElement('div');
                        badge.className = `text-[11px] sm:text-[13px] px-1.5 py-0.5 rounded-[4px] border flex items-center gap-1 font-bold font-headline shadow-sm leading-none ${getCostBadgeClass(cColor)}`;
                        badge.innerHTML = `<div class="w-3 h-3 sm:w-3.5 sm:h-3.5 flex items-center justify-center">${getGemSvg(cColor, 'w-full h-full')}</div>${cCost}`;
                        costSection.appendChild(badge);
                    }
                });

                cardWrapper.appendChild(cardHeader);
                cardWrapper.appendChild(watermark);
                cardWrapper.appendChild(costSection);
            } else {
                // Empty slot placeholder
                cardWrapper.className += " bg-surface-container-low/20 border-dashed border-outline-variant/30 flex items-center justify-center";
                cardWrapper.innerHTML = `<span class="text-outline text-xs italic select-none">Empty</span>`;
            }
            
            rowElement.appendChild(cardWrapper);
        }
    }

    function renderReservedCards(playerIdx, cards) {
        const container = playerIdx === 0 ? p0ReservedContainer : p1ReservedContainer;
        container.innerHTML = '';

        const countEl = document.getElementById(`p${playerIdx}-reserved-count`);
        if (countEl) {
            countEl.textContent = `(${cards ? cards.length : 0})`;
        }

        if (!cards || cards.length === 0) {
            container.innerHTML = '<span class="text-[11px] text-outline italic m-auto text-center">No cards reserved</span>';
            return;
        }

        cards.forEach(card => {
            const cardWrapper = document.createElement('div');
            cardWrapper.dataset.cardId = card.id;
            const color = card.bonusGem || card.bonusColor;
            cardWrapper.className = `w-16 h-20 shrink-0 rounded-md border border-outline-variant/30 flex flex-col relative overflow-hidden shadow-sm ${getCardBgClass(color)}`;
            
            // Header
            const header = document.createElement('div');
            const headerTextClass = color === 'WHITE' ? 'text-on-background' : 'text-white';
            header.className = `${getCardHeaderBgClass(color)} p-1 px-1.5 flex justify-between items-center ${headerTextClass} h-6 relative z-10`;
            if (color === 'WHITE') header.className += ' border-b border-outline-variant/10';
            
            const points = (card.prestigePoints !== undefined) ? card.prestigePoints : (card.victoryPoints || 0);
            header.innerHTML = `
                <div class="text-xs font-bold leading-none font-headline">${points || ''}</div>
                <div class="w-3 h-3 flex items-center justify-center">${getGemSvg(color, 'w-full h-full')}</div>
            `;

            // Watermark
            const watermark = document.createElement('div');
            watermark.className = "absolute inset-0 top-5 flex items-center justify-center z-0 pointer-events-none mb-4";
            watermark.innerHTML = `<div class="font-headline font-bold text-[14px] ${getWatermarkTextClass(color)} select-none" style="opacity: 0.60">${card.id}</div>`;

            // Costs
            const costSection = document.createElement('div');
            costSection.className = "mt-auto p-1 flex gap-0.5 z-10 justify-center w-full flex-wrap bg-white/10";
            
            const costs = card.costs || card.cost || {};
            Object.entries(costs).forEach(([cColor, cCost]) => {
                if (cCost > 0) {
                    const badge = document.createElement('div');
                    badge.className = `text-[8px] sm:text-[10px] px-1 py-0.5 rounded-[3px] border flex items-center gap-0.5 font-bold font-headline shadow-sm leading-none ${getCostBadgeClass(cColor)}`;
                    badge.innerHTML = `<div class="w-2.5 h-2.5 sm:w-3 sm:h-3 flex items-center justify-center">${getGemSvg(cColor, 'w-full h-full')}</div>${cCost}`;
                    costSection.appendChild(badge);
                }
            });

            cardWrapper.appendChild(header);
            cardWrapper.appendChild(watermark);
            cardWrapper.appendChild(costSection);
            container.appendChild(cardWrapper);
        });
    }

    // --- REASONING CONSOLE HELPERS ---
    
    function appendReasoning(playerIdx, text) {
        const consoleEl = playerIdx === 0 ? p0ReasoningConsole : p1ReasoningConsole;
        // Clean initial text if present
        if (consoleEl.innerHTML.includes("Awaiting game start...")) {
            consoleEl.innerHTML = '';
        }
        
        appendReasoningText(playerIdx, `> ${text}`);
    }

    function appendAction(playerIdx, action, durationMs) {
        const actStr = summarizeAction(action);
        const dur = durationMs ? `(${formatDurationShort(durationMs)})` : '';
        
        // Wypisz akcję na konsoli (bez typewriter, dopisanie natychmiastowe z kolorem akcentowym)
        appendActionText(playerIdx, `Selected Action: ${actStr} ${dur}`);
    }

    function appendRetry(playerIdx, attempt, error) {
        appendReasoningText(playerIdx, `[RETRY #${attempt}] Error: ${error}`, 'text-error font-bold');
    }

    function formatTokensAndCards(text) {
        if (!text) return '';
        // 1. Escape HTML
        let html = text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");

        // 2. Replace Token/Gem codes with premium inline SVGs first
        const tokenPatterns = [
            { regex: /\b(WHT|WHI|WHITE|DIAMOND|DIAM)\b/gi, color: 'WHITE' },
            { regex: /\b(BLU|BLUE|SAPPHIRE|SAPPH)\b/gi, color: 'BLUE' },
            { regex: /\b(GRN|GRE|GREEN|EMERALD|EMER)\b/gi, color: 'GREEN' },
            { regex: /\b(RED|RUBY)\b/gi, color: 'RED' },
            { regex: /\b(BLK|BLA|BLACK|ONYX)\b/gi, color: 'BLACK' },
            { regex: /\b(GLD|GOL|GOLD)\b/gi, color: 'GOLD' }
        ];

        tokenPatterns.forEach(pattern => {
            html = html.replace(pattern.regex, () => {
                return getGemSvg(pattern.color, 'w-3.5 h-3.5 inline-block align-text-bottom');
            });
        });

        // 3. Replace Card IDs like L1_23 or L2_56 or L3_87 with hoverable elements
        html = html.replace(/\b(L[1-3]_\d+)\b/g, (match) => {
            const color = cardIdToColor[match];
            const colorClass = color ? ` reasoning-card-${color.toLowerCase()}` : '';
            return `<span class="reasoning-card-link${colorClass}" data-card-id="${match}">${match}</span>`;
        });

        return html;
    }

    function appendReasoningText(playerIdx, text, customClass = '') {
        const consoleEl = playerIdx === 0 ? p0ReasoningConsole : p1ReasoningConsole;
        const p = document.createElement('p');
        p.className = customClass;
        p.innerHTML = formatTokensAndCards(text);
        consoleEl.appendChild(p);
        
        // Automatic scroll to bottom
        consoleEl.scrollTop = consoleEl.scrollHeight;
    }

    function appendActionText(playerIdx, text) {
        const consoleEl = playerIdx === 0 ? p0ReasoningConsole : p1ReasoningConsole;
        const p = document.createElement('p');
        // Wyodrębniamy decyzje o akcjach kolorem akcentowym bota (Player 0 -> Amber/Tertiary, Player 1 -> Green/Primary)
        p.className = playerIdx === 0 ? 'text-tertiary font-bold mt-1 mb-2' : 'text-primary font-bold mt-1 mb-2';
        p.innerHTML = formatTokensAndCards(`> ${text}`);
        consoleEl.appendChild(p);
        
        consoleEl.scrollTop = consoleEl.scrollHeight;
    }

    // Modal End Game Trigger
    function showEndGameModal(endedEvent) {
        pauseReplay();
        modalP0Name.textContent = playerNames[0];
        modalP1Name.textContent = playerNames[1];
        
        const scores = endedEvent.finalScores || {};
        modalP0Score.textContent = `${scores[0] || 0} pts`;
        modalP1Score.textContent = `${scores[1] || 0} pts`;
        
        winnerReason.textContent = `Result: ${endedEvent.winnerReason}`;
        
        // Gather execution time and token usage if available
        let totalTimeMs = 0;
        let p0Prompt = 0, p0Completion = 0, p0Cost = 0.0;
        let p1Prompt = 0, p1Completion = 0, p1Cost = 0.0;

        events.forEach((ev, i) => {
            if (isReplayMode && i > currentEventIndex) return;

            const type = detectEventType(ev);
            if (type === "ACTION" && ev.durationMs) {
                totalTimeMs += ev.durationMs;
            } else if (type === "REASONING" && ev.tokenUsage) {
                if (ev.playerIndex === 0) {
                    p0Prompt += ev.tokenUsage.promptTokens || 0;
                    p0Completion += ev.tokenUsage.completionTokens || 0;
                    p0Cost += ev.tokenUsage.cost || 0.0;
                } else if (ev.playerIndex === 1) {
                    p1Prompt += ev.tokenUsage.promptTokens || 0;
                    p1Completion += ev.tokenUsage.completionTokens || 0;
                    p1Cost += ev.tokenUsage.cost || 0.0;
                }
            }
        });

        // Fallback to endedEvent totals if we have them but didn't accumulate them
        if (endedEvent.playerUsages) {
            const u0 = endedEvent.playerUsages[0] || {};
            const u1 = endedEvent.playerUsages[1] || {};
            if (p0Prompt === 0) p0Prompt = u0.promptTokens || 0;
            if (p0Completion === 0) p0Completion = u0.completionTokens || 0;
            if (p0Cost === 0.0) p0Cost = u0.cost || 0.0;
            if (p1Prompt === 0) p1Prompt = u1.promptTokens || 0;
            if (p1Completion === 0) p1Completion = u1.completionTokens || 0;
            if (p1Cost === 0.0) p1Cost = u1.cost || 0.0;
        }

        modalP0Tokens.textContent = `${formatTokenCount(p0Prompt)} / ${formatTokenCount(p0Completion)}`;
        modalP0Cost.textContent = `$${p0Cost.toFixed(6)}`;
        modalP1Tokens.textContent = `${formatTokenCount(p1Prompt)} / ${formatTokenCount(p1Completion)}`;
        modalP1Cost.textContent = `$${p1Cost.toFixed(6)}`;
        
        modalExecutionTime.textContent = formatDuration(totalTimeMs);
        endGameModal.classList.remove('hidden');

        modalCloseBtn.addEventListener('click', () => {
            endGameModal.classList.add('hidden');
            window.close(); // Back to configurator or close tab
        });
    }

    function formatTokenCount(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(2) + 'M';
        }
        if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'k';
        }
        return num;
    }

    // --- GENERAL TRANSLATORS & HELPERS ---

    function summarizeAction(action) {
        if (!action) return "Passed turn";
        const type = action.type;
        
        if (type === "TAKE_TOKENS") {
            const tokens = action.tokens || {};
            const taken = Object.entries(tokens)
                .filter(([_, val]) => val > 0)
                .map(([color, val]) => `${val}x${color.substring(0, 3)}`)
                .join(', ');
            return `TAKE_TOKENS [${taken}]`;
        }
        else if (type === "PURCHASE_CARD") {
            return `PURCHASE_CARD [${action.cardId}]`;
        }
        else if (type === "RESERVE_CARD") {
            if (action.cardId) {
                return `RESERVE_CARD [${action.cardId}]`;
            } else if (action.deckLevel) {
                return `RESERVE_CARD [Blind from ${action.deckLevel}]`;
            }
            return `RESERVE_CARD`;
        }
        return "Unknown Action";
    }

    function getColorClass(color) {
        return {
            'GREEN': 'bg-emerald-700 text-white',
            'BLUE': 'bg-blue-700 text-white',
            'RED': 'bg-red-700 text-white',
            'BLACK': 'bg-stone-700 text-white',
            'WHITE': 'bg-stone-100 text-stone-800 border border-stone-300',
            'GOLD': 'bg-amber-400 text-amber-900'
        }[color] || 'bg-stone-500';
    }

    function getCardBgClass(color) {
        return {
            'GREEN': 'bg-emerald-50 text-on-background',
            'BLUE': 'bg-blue-50 text-on-background',
            'RED': 'bg-red-50 text-on-background',
            'BLACK': 'bg-stone-200 text-on-background',
            'WHITE': 'bg-stone-50 text-on-background'
        }[color] || 'bg-surface';
    }

    function getCardHeaderBgClass(color) {
        return {
            'GREEN': 'bg-emerald-700',
            'BLUE': 'bg-blue-700',
            'RED': 'bg-red-700',
            'BLACK': 'bg-stone-800',
            'WHITE': 'bg-stone-100 text-stone-800'
        }[color] || 'bg-stone-500';
    }

    function getWatermarkTextClass(color) {
        return {
            'GREEN': 'text-emerald-950/20',
            'BLUE': 'text-blue-950/20',
            'RED': 'text-red-950/20',
            'BLACK': 'text-stone-900/20',
            'WHITE': 'text-stone-950/15'
        }[color] || 'text-stone-500/20';
    }

    function getCostBadgeClass(color) {
        return {
            'GREEN': 'bg-emerald-100 text-emerald-800 border-emerald-300',
            'BLUE': 'bg-blue-100 text-blue-800 border-blue-300',
            'RED': 'bg-red-100 text-red-800 border-red-300',
            'BLACK': 'bg-stone-300 text-stone-900 border-stone-400',
            'WHITE': 'bg-stone-100 text-stone-800 border-stone-300',
            'GOLD': 'bg-amber-100 text-amber-900 border-amber-300'
        }[color] || 'bg-stone-100 border-stone-200';
    }

    function formatDurationShort(durationMs) {
        return `${(durationMs / 1000).toFixed(1)}s`;
    }

    function setupReservedCollapsibles() {
        [0, 1].forEach(playerIdx => {
            const toggle = document.querySelector(`.p${playerIdx}-reserved-toggle`);
            const wrapper = document.getElementById(`p${playerIdx}-reserved-wrapper`);
            const arrow = document.getElementById(`p${playerIdx}-reserved-arrow`);
            if (toggle && wrapper && arrow) {
                toggle.addEventListener('click', () => {
                    const isCollapsed = wrapper.classList.contains('max-h-0');
                    if (isCollapsed) {
                        wrapper.classList.remove('max-h-0');
                        wrapper.classList.add('max-h-[120px]');
                        arrow.classList.add('rotate-180');
                    } else {
                        wrapper.classList.remove('max-h-[120px]');
                        wrapper.classList.add('max-h-0');
                        arrow.classList.remove('rotate-180');
                    }
                });
            }
        });
    }

    // --- CARD PREVIEW TOOLTIP HELPERS ---

    function showCardTooltip(e, card) {
        if (!cardPreviewTooltip || !card) return;
        
        const color = card.bonusGem || card.bonusColor;
        const points = (card.prestigePoints !== undefined) ? card.prestigePoints : (card.victoryPoints || 0);
        
        let costHtml = '';
        const costs = card.costs || card.cost || {};
        Object.entries(costs).forEach(([cColor, cCost]) => {
            if (cCost > 0) {
                costHtml += `
                    <div class="text-[8px] sm:text-[10px] px-1 py-0.5 rounded-[3px] border flex items-center gap-0.5 font-bold font-headline shadow-sm leading-none ${getCostBadgeClass(cColor)}">
                        <div class="w-2.5 h-2.5 sm:w-3 sm:h-3 flex items-center justify-center">${getGemSvg(cColor, 'w-full h-full')}</div>${cCost}
                    </div>
                `;
            }
        });
        
        const headerTextClass = color === 'WHITE' ? 'text-on-background' : 'text-white';
        const whiteBorderClass = color === 'WHITE' ? ' border-b border-outline-variant/10' : '';
        
        cardPreviewTooltip.className = `absolute pointer-events-none z-50 rounded-md border border-outline-variant/30 flex flex-col overflow-hidden shadow-lg w-16 h-20 ${getCardBgClass(color)}`;
        cardPreviewTooltip.innerHTML = `
            <div class="${getCardHeaderBgClass(color)} p-1 px-1.5 flex justify-between items-center ${headerTextClass} h-6 relative z-10 ${whiteBorderClass}">
                <div class="text-xs font-bold leading-none font-headline">${points || ''}</div>
                <div class="w-3 h-3 flex items-center justify-center">${getGemSvg(color, 'w-full h-full')}</div>
            </div>
            <div class="absolute inset-0 top-5 flex items-center justify-center z-0 pointer-events-none mb-4">
                <div class="font-headline font-bold text-[14px] ${getWatermarkTextClass(color)} select-none" style="opacity: 0.60">${card.id}</div>
            </div>
            <div class="mt-auto p-1 flex gap-0.5 z-10 justify-center w-full flex-wrap bg-white/10">
                ${costHtml}
            </div>
        `;
        
        updateTooltipPosition(e);
        cardPreviewTooltip.classList.remove('hidden');
    }
    
    function updateTooltipPosition(e) {
        if (!cardPreviewTooltip) return;
        const x = e.pageX;
        const y = e.pageY;
        cardPreviewTooltip.style.left = `${x + 12}px`;
        cardPreviewTooltip.style.top = `${y - 92}px`;
    }
    
    function hideCardTooltip() {
        if (cardPreviewTooltip) {
            cardPreviewTooltip.classList.add('hidden');
        }
    }

    function setupReasoningConsoleHover(consoleEl) {
        if (!consoleEl) return;
        
        consoleEl.addEventListener('mouseover', (e) => {
            const link = e.target.closest('.reasoning-card-link');
            if (link) {
                const cardId = link.dataset.cardId;
                if (cardId) {
                    const color = cardIdToColor[cardId];
                    const highlightClass = color ? `card-highlight-active-${color.toLowerCase()}` : 'card-highlight-active';
                    
                    const cards = document.querySelectorAll(`[data-card-id="${cardId}"]`);
                    cards.forEach(card => {
                        card.classList.add(highlightClass);
                        card.dataset.appliedHighlight = highlightClass;
                    });
                    
                    // Show card preview tooltip
                    const cardObj = cardIdToCardObj[cardId];
                    if (cardObj) {
                        showCardTooltip(e, cardObj);
                    }
                }
            }
        });

        consoleEl.addEventListener('mousemove', (e) => {
            const link = e.target.closest('.reasoning-card-link');
            if (link) {
                updateTooltipPosition(e);
            }
        });

        consoleEl.addEventListener('mouseout', (e) => {
            const link = e.target.closest('.reasoning-card-link');
            if (link) {
                const cardId = link.dataset.cardId;
                if (cardId) {
                    const cards = document.querySelectorAll(`[data-card-id="${cardId}"]`);
                    cards.forEach(card => {
                        const highlightClass = card.dataset.appliedHighlight || 'card-highlight-active';
                        card.classList.remove(highlightClass);
                        // Clean up all possible highlight classes
                        card.classList.remove('card-highlight-active-green', 'card-highlight-active-blue', 'card-highlight-active-red', 'card-highlight-active-black', 'card-highlight-active-white', 'card-highlight-active');
                        delete card.dataset.appliedHighlight;
                    });
                }
                // Hide card preview tooltip
                hideCardTooltip();
            }
        });
    }

    function formatDuration(durationMs) {
        const totalSeconds = Math.floor(durationMs / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        if (minutes > 0) {
            return `${minutes} min ${seconds} s`;
        }
        return `${seconds} s`;
    }

    // Hook up Abort button click
    if (btnAbort) {
        btnAbort.addEventListener('click', async () => {
            if (confirm("Are you sure you want to abort this match?")) {
                btnAbort.disabled = true;
                btnAbort.innerHTML = `<span class="material-symbols-outlined text-[14px]">sync</span> Aborting...`;
                try {
                    const response = await fetch(`/api/matches/${gameId}/abort`, {
                        method: 'POST'
                    });
                    if (response.ok) {
                        console.log("Abort request sent successfully.");
                    } else {
                        const err = await response.json();
                        alert(`Failed to abort match: ${err.error || "Unknown error"}`);
                        btnAbort.disabled = false;
                        btnAbort.innerHTML = `<span class="material-symbols-outlined text-[14px]">cancel</span> Abort Match`;
                    }
                } catch (e) {
                    console.error("Error sending abort request:", e);
                    alert(`Connection error: ${e.message}`);
                    btnAbort.disabled = false;
                    btnAbort.innerHTML = `<span class="material-symbols-outlined text-[14px]">cancel</span> Abort Match`;
                }
            }
        });
    }

    // Run Init
    setupReservedCollapsibles();
    setupReasoningConsoleHover(p0ReasoningConsole);
    setupReasoningConsoleHover(p1ReasoningConsole);
    init();
});
