document.addEventListener('DOMContentLoaded', () => {
    const matchId = new URLSearchParams(window.location.search).get('matchId');
    if (!matchId) {
        alert('Missing matchId in URL!');
        return;
    }

    const statusLabel = document.getElementById('game-status-label');
    const turnDisplay = document.getElementById('turn-display');
    const activePlayerDisplay = document.getElementById('active-player-display');
    
    // Player DOM Cache
    const players = {
        0: {
            name: document.getElementById('p0-name'),
            score: document.getElementById('p0-score'),
            reasoning: document.getElementById('p0-reasoning'),
            reservedContainer: document.getElementById('p0-reserved-container')
        },
        1: {
            name: document.getElementById('p1-name'),
            score: document.getElementById('p1-score'),
            reasoning: document.getElementById('p1-reasoning'),
            reservedContainer: document.getElementById('p1-reserved-container')
        }
    };

    // Color definitions
    const colors = ['GREEN', 'BLUE', 'RED', 'BLACK', 'WHITE', 'GOLD'];
    const gemColors = ['GREEN', 'BLUE', 'RED', 'BLACK', 'WHITE']; // Nobles & card bonuses exclude gold

    // Helper to map color to Tailwind BG/Text classes
    function getColorStyle(color) {
        switch (color) {
            case 'GREEN': return { bg: 'bg-emerald-700', text: 'text-white', lightBg: 'bg-emerald-100', lightText: 'text-emerald-800', border: 'border-emerald-200' };
            case 'BLUE': return { bg: 'bg-blue-700', text: 'text-white', lightBg: 'bg-blue-100', lightText: 'text-blue-800', border: 'border-blue-200' };
            case 'RED': return { bg: 'bg-red-700', text: 'text-white', lightBg: 'bg-red-100', lightText: 'text-red-800', border: 'border-red-200' };
            case 'BLACK': return { bg: 'bg-stone-700', text: 'text-white', lightBg: 'bg-stone-200', lightText: 'text-stone-800', border: 'border-stone-300' };
            case 'WHITE': return { bg: 'bg-stone-100', text: 'text-stone-800', lightBg: 'bg-stone-50', lightText: 'text-stone-800', border: 'border-stone-200 shadow-sm' };
            case 'GOLD': return { bg: 'bg-amber-400', text: 'text-amber-900', lightBg: 'bg-amber-100', lightText: 'text-amber-900', border: 'border-amber-300' };
            default: return { bg: 'bg-stone-400', text: 'text-white', lightBg: 'bg-stone-100', lightText: 'text-stone-800', border: 'border-stone-200' };
        }
    }

    // Connect to WebSocket
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/match/${matchId}`;
    const socket = new WebSocket(wsUrl);

    statusLabel.classList.remove('hidden');
    statusLabel.innerText = 'CONNECTING';

    socket.onopen = () => {
        statusLabel.innerText = 'LIVE';
        statusLabel.className = 'bg-emerald-100 text-emerald-800 text-[11px] font-bold px-2 py-0.5 rounded border border-emerald-300';
    };

    socket.onclose = () => {
        statusLabel.innerText = 'DISCONNECTED';
        statusLabel.className = 'bg-red-100 text-red-800 text-[11px] font-bold px-2 py-0.5 rounded border border-red-300';
    };

    socket.onerror = () => {
        statusLabel.innerText = 'ERROR';
        statusLabel.className = 'bg-red-100 text-red-800 text-[11px] font-bold px-2 py-0.5 rounded border border-red-300';
    };

    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        handleGameEvent(data);
    };

    // Main Game Event Dispatcher
    function handleGameEvent(evt) {
        console.log("Received Game Event:", evt.type, evt);
        const state = evt.gameState;

        switch (evt.type) {
            case 'com.aisplendor.model.event.GameStartedEvent':
                players[0].name.innerText = `P0: ${evt.player0Model.split('/').pop()}`;
                players[1].name.innerText = `P1: ${evt.player1Model.split('/').pop()}`;
                logReasoning(0, `> Game Started with Match ID: ${matchId}`);
                logReasoning(0, `> P0 Model: ${evt.player0Model}`);
                logReasoning(1, `> P1 Model: ${evt.player1Model}`);
                updateBoard(state);
                break;

            case 'com.aisplendor.model.event.TurnStartedEvent':
                turnDisplay.innerText = `Turn ${state.turnNumber}`;
                activePlayerDisplay.innerText = `Active: Player ${state.currentPlayerIndex}`;
                // Highlight active player border
                highlightActivePlayer(state.currentPlayerIndex);
                updateBoard(state);
                break;

            case 'com.aisplendor.model.event.ReasoningEvent':
                logReasoning(evt.playerId, `> Evaluating board state...`);
                logReasoning(evt.playerId, evt.reasoning);
                break;

            case 'com.aisplendor.model.event.ActionEvent':
                const playerAction = evt.action;
                const actStr = formatAction(playerAction);
                logReasoning(evt.playerId, `<p class="text-primary font-bold">> Selected Action: ${actStr}</p>`);
                break;

            case 'com.aisplendor.model.event.RetryEvent':
                logReasoning(evt.playerId, `<p class="text-error font-bold">> Retry attempt ${evt.attemptNumber}. Error: ${evt.errorMessage}</p>`);
                break;

            case 'com.aisplendor.model.event.GameEndedEvent':
                statusLabel.innerText = 'FINISHED';
                statusLabel.className = 'bg-blue-100 text-blue-800 text-[11px] font-bold px-2 py-0.5 rounded border border-blue-300';
                activePlayerDisplay.innerText = 'Winner: ' + (evt.winnerPlayerIndex !== null ? `Player ${evt.winnerPlayerIndex}` : 'Tie');
                
                logReasoning(0, `<p class="text-primary font-bold">> Game Finished! Winner: ${evt.winnerReason}</p>`);
                logReasoning(1, `<p class="text-primary font-bold">> Game Finished! Winner: ${evt.winnerReason}</p>`);
                break;
        }
    }

    function logReasoning(playerId, text) {
        const reasoningDiv = players[playerId].reasoning;
        const line = document.createElement('p');
        line.innerHTML = text.replace(/\n/g, '<br/>');
        reasoningDiv.appendChild(line);
        reasoningDiv.scrollTop = reasoningDiv.scrollHeight;
    }

    function highlightActivePlayer(activeIndex) {
        const p0Col = players[0].name.closest('aside');
        const p1Col = players[1].name.closest('aside');

        if (activeIndex === 0) {
            p0Col.className = p0Col.className.replace('bg-surface-container-lowest', 'bg-emerald-50/20 border-emerald-500/20');
            p1Col.className = p1Col.className.replace('bg-emerald-50/20 border-emerald-500/20', 'bg-surface-container-lowest');
        } else {
            p1Col.className = p1Col.className.replace('bg-surface-container-lowest', 'bg-emerald-50/20 border-emerald-500/20');
            p0Col.className = p0Col.className.replace('bg-emerald-50/20 border-emerald-500/20', 'bg-surface-container-lowest');
        }
    }

    // Format game action for reasoning log
    function formatAction(action) {
        switch (action.type) {
            case 'TAKE_3_DIFF':
                return `TAKE_3_DIFF [${Object.keys(action.tokens).filter(k => action.tokens[k] > 0).join(', ')}]`;
            case 'TAKE_2_SAME':
                return `TAKE_2_SAME [${Object.keys(action.tokens).find(k => action.tokens[k] > 0)}]`;
            case 'RESERVE':
                if (action.cardId) return `RESERVE [Card: ${action.cardId}]`;
                return `RESERVE [Deck: ${action.reserveDeckLevel}]`;
            case 'PURCHASE':
                return `PURCHASE [Card: ${action.cardId}]`;
            default:
                return 'UNKNOWN';
        }
    }

    // Update entire board layout based on GameState JSON
    function updateBoard(state) {
        if (!state) return;

        // 1. Players Section
        state.players.forEach(p => {
            const pId = p.id;
            players[pId].score.innerText = p.score;

            // Gems in Hand
            colors.forEach(col => {
                const count = p.tokens.counts[col] || 0;
                const gemEl = document.getElementById(`p${pId}-gem-${col}`);
                if (gemEl) {
                    gemEl.innerText = count;
                    if (count === 0) {
                        gemEl.classList.add('opacity-40');
                    } else {
                        gemEl.classList.remove('opacity-40');
                    }
                }
            });

            // Card Bonuses (Tableau)
            gemColors.forEach(col => {
                const count = p.bonuses[col] || 0;
                const bonusEl = document.getElementById(`p${pId}-bonus-${col}`);
                if (bonusEl) {
                    bonusEl.innerText = count;
                }
            });

            // Reserved Cards
            renderReservedCards(pId, p.reservedCards);
        });

        // 2. Center Column: Table
        const board = state.board;

        // Bank Gems
        colors.forEach(col => {
            const count = board.availableTokens.counts[col] || 0;
            const bankEl = document.getElementById(`bank-gem-${col}`);
            if (bankEl) {
                bankEl.innerText = count;
            }
        });

        // Nobles
        renderNobles(board.availableNobles);

        // Face-up Development Cards
        ['LEVEL_3', 'LEVEL_2', 'LEVEL_1'].forEach(lvl => {
            const row = document.getElementById(`cards-row-${lvl}`);
            row.innerHTML = ''; // Clear row
            
            const cards = board.faceUpCards[lvl] || [];
            cards.forEach(card => {
                row.appendChild(createCardDOM(card));
            });
            
            // Refill with empty slots if row has less than 4 cards
            for (let i = cards.length; i < 4; i++) {
                row.appendChild(createEmptyCardSlotDOM(lvl));
            }
        });
    }

    // Render Nobles
    function renderNobles(nobles) {
        const noblesRow = document.getElementById('nobles-row');
        noblesRow.innerHTML = '';
        nobles.forEach(n => {
            const div = document.createElement('div');
            div.className = 'w-24 h-full bg-surface-container-lowest rounded-lg shadow-sm border border-surface-container-highest flex flex-col p-2';
            
            // Points (always 3)
            const pts = document.createElement('div');
            pts.className = 'text-lg font-bold text-on-background font-headline';
            pts.innerText = n.prestigePoints;
            div.appendChild(pts);

            // Requirements
            const reqs = document.createElement('div');
            reqs.className = 'flex flex-col gap-0.5 mt-auto';
            Object.keys(n.requirement).forEach(col => {
                const reqVal = n.requirement[col];
                if (reqVal > 0) {
                    const style = getColorStyle(col);
                    const item = document.createElement('div');
                    item.className = 'flex items-center gap-1 text-xs font-headline';
                    item.innerHTML = `<div class="w-3 h-3 rounded ${style.bg} ${col === 'WHITE' ? 'border border-stone-300' : ''}"></div>${reqVal}`;
                    reqs.appendChild(item);
                }
            });
            div.appendChild(reqs);
            noblesRow.appendChild(div);
        });
    }

    // Render Reserved Cards
    function renderReservedCards(pId, reserved) {
        const container = players[pId].reservedContainer;
        container.innerHTML = '';
        if (reserved.length === 0) {
            container.innerHTML = `<div class="text-xs text-outline italic">No reserved cards</div>`;
            return;
        }

        reserved.forEach(c => {
            const style = getColorStyle(c.bonusGem);
            
            const cardEl = document.createElement('div');
            cardEl.className = 'w-20 h-24 bg-surface-container-lowest rounded-md border border-outline-variant shadow-sm flex flex-col relative overflow-hidden shrink-0';
            
            // Header
            const header = document.createElement('div');
            header.className = 'flex justify-between items-start p-1.5 relative z-10';
            header.innerHTML = `
                <div class="text-base font-bold text-on-background leading-none font-headline z-10">${c.prestigePoints > 0 ? c.prestigePoints : ''}</div>
                <div class="absolute top-1 left-0 w-full text-center text-[0.55rem] font-mono text-outline font-semibold">${c.id}</div>
                <div class="w-3.5 h-3.5 rounded-full ${style.bg} shadow-sm border border-surface-container-lowest relative z-10"></div>
            `;
            cardEl.appendChild(header);

            // Cost footer
            const footer = document.createElement('div');
            footer.className = 'mt-auto p-1.5 flex gap-1 z-10 justify-center w-full flex-wrap';
            
            Object.keys(c.cost).forEach(col => {
                const val = c.cost[col];
                if (val > 0) {
                    const cStyle = getColorStyle(col);
                    const costBadge = document.createElement('div');
                    costBadge.className = `${cStyle.lightBg} ${cStyle.lightText} text-[0.6rem] px-1 rounded-[3px] border ${cStyle.border} flex items-center gap-0.5 font-bold font-headline shadow-sm leading-tight`;
                    costBadge.innerHTML = `<div class="w-1.5 h-1.5 rounded-full ${cStyle.bg}"></div>${val}`;
                    footer.appendChild(costBadge);
                }
            });
            cardEl.appendChild(footer);
            container.appendChild(cardEl);
        });
    }

    // Create DOM element for a development card
    function createCardDOM(c) {
        const style = getColorStyle(c.bonusGem);
        
        const card = document.createElement('div');
        // Custom background style for the card depending on its gem bonus
        const cardBgClass = c.bonusGem === 'WHITE' ? 'bg-stone-50 border-stone-200' :
                            c.bonusGem === 'GREEN' ? 'bg-emerald-50/50 border-emerald-200/50' :
                            c.bonusGem === 'BLUE' ? 'bg-blue-50/50 border-blue-200/50' :
                            c.bonusGem === 'RED' ? 'bg-red-50/50 border-red-200/50' :
                            c.bonusGem === 'BLACK' ? 'bg-stone-200/30 border-stone-300' : 'bg-surface';

        card.className = `flex-1 ${cardBgClass} rounded-md shadow-sm border flex flex-col overflow-hidden relative`;
        
        // Header Banner with gem type
        const banner = document.createElement('div');
        banner.className = `${style.bg} p-2 flex justify-between items-center ${style.text} h-8 relative z-10 ${c.bonusGem === 'WHITE' ? 'border-b border-stone-200' : ''}`;
        banner.innerHTML = `
            <div class="text-[1.4rem] font-bold leading-none ml-1 font-headline">${c.prestigePoints > 0 ? c.prestigePoints : '0'}</div>
            <div class="text-[10px] uppercase font-bold tracking-widest mr-1">${c.bonusGem}</div>
        `;
        card.appendChild(banner);

        // Watermark ID
        const watermark = document.createElement('div');
        watermark.className = 'absolute inset-0 top-8 flex items-center justify-center z-0 pointer-events-none mb-12';
        watermark.innerHTML = `<div class="font-headline font-bold text-[36px] opacity-10 text-on-background leading-none">${c.id}</div>`;
        card.appendChild(watermark);

        // Cost (Left aligned vertically)
        const costDiv = document.createElement('div');
        costDiv.className = 'p-2 mt-auto flex flex-col gap-0.5 w-1/2 relative z-10';
        
        Object.keys(c.cost).forEach(col => {
            const costVal = c.cost[col];
            if (costVal > 0) {
                const cStyle = getColorStyle(col);
                const item = document.createElement('div');
                item.className = `${cStyle.bg} ${cStyle.text} text-[0.65rem] px-1 rounded border ${col === 'WHITE' ? 'border-stone-300 text-stone-800' : 'border-black/10'} text-center font-bold font-headline`;
                item.innerText = costVal;
                costDiv.appendChild(item);
            }
        });
        card.appendChild(costDiv);

        return card;
    }

    // Create an empty card slot DOM placeholder
    function createEmptyCardSlotDOM(level) {
        const slot = document.createElement('div');
        slot.className = 'flex-1 bg-surface-container-low rounded-md border border-dashed border-outline-variant/30 flex items-center justify-center relative';
        slot.innerHTML = `<span class="text-xs text-outline italic">Empty</span>`;
        return slot;
    }
});
