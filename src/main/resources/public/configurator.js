document.addEventListener('DOMContentLoaded', () => {
    const matchGrid = document.getElementById('match-grid');
    const addMatchBtn = document.getElementById('add-match-btn');
    const runSimulationsBtn = document.getElementById('run-simulations-btn');
    const resetAllBtn = document.getElementById('reset-all-btn');

    let matchCount = 0;

    // Helper to generate unique match slot template
    function createMatchSlotHTML(slotIndex) {
        return `
<div id="match-slot-${slotIndex}" class="bg-surface rounded-xl border border-outline-variant/40 shadow-[0_4px_20px_rgba(46,50,48,0.03)] overflow-hidden flex flex-col w-full">
    <div class="px-5 py-3 border-b border-outline-variant/30 bg-surface-container-low flex justify-between items-center">
        <h3 class="font-headline font-medium text-lg text-on-background">Match Slot #${slotIndex}</h3>
        <div class="flex items-center gap-2">
            <span class="px-2.5 py-1 rounded-full bg-primary-container/20 text-on-primary-fixed-variant text-xs font-bold tracking-wide flex items-center">CONFIGURED</span>
            ${slotIndex > 1 ? `
            <button class="remove-slot-btn p-1 rounded-full hover:bg-red-100 text-error transition-colors flex items-center justify-center" data-slot-id="${slotIndex}">
                <span class="material-symbols-outlined text-[20px]">delete</span>
            </button>
            ` : ''}
        </div>
    </div>
    <div class="flex flex-col md:flex-row divide-y md:divide-y-0 md:divide-x divide-outline-variant/30 flex-1">
        <!-- Player 0 -->
        <div class="flex-1 p-5 space-y-4">
            <div class="mb-2 flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-tertiary shrink-0"></span>
                <input id="p0-name-${slotIndex}" class="w-full bg-transparent border-b border-outline-variant/30 focus:border-primary focus:ring-0 text-sm font-bold text-on-surface-variant uppercase tracking-wider px-0 py-1 transition-colors" placeholder="Player 0 Name" type="text" value="PLAYER 0">
            </div>
            <div>
                <label class="block text-xs font-medium text-on-surface-variant mb-1">Model ID</label>
                <input id="p0-model-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary" type="text" value="google/gemini-3-flash-preview">
            </div>
            <div class="bg-surface-container-low/50 p-3 rounded-lg border border-outline-variant/20 space-y-3">
                <div class="flex justify-between items-center">
                    <span class="text-xs font-medium text-on-surface-variant">Reasoning Configuration</span>
                </div>
                <div class="space-y-2">
                    <label class="flex items-center gap-2">
                        <input id="p0-reasoning-enabled-${slotIndex}" type="checkbox" checked class="rounded border-outline-variant/50 text-primary focus:ring-primary h-4 w-4">
                        <span class="text-xs font-medium text-on-surface-variant">Enable Reasoning</span>
                    </label>
                    <div>
                        <label class="block text-[10px] text-on-surface-variant uppercase font-bold mb-1">Reasoning Effort</label>
                        <select id="p0-reasoning-effort-${slotIndex}" class="w-full bg-surface border-outline-variant/50 text-on-background rounded text-xs py-1 px-2 focus:ring-primary focus:border-primary">
                            <option value="low">Low</option>
                            <option value="medium" selected>Medium</option>
                            <option value="high">High</option>
                        </select>
                    </div>
                </div>
            </div>
            <div>
                <label class="block text-xs font-medium text-on-surface-variant mb-1">Action Memory Window</label>
                <select id="p0-memory-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary">
                    <option value="1">1</option>
                    <option value="2">2</option>
                    <option value="3" selected>3</option>
                    <option value="5">5</option>
                    <option value="8">8</option>
                </select>
            </div>
        </div>
        <!-- Player 1 -->
        <div class="flex-1 p-5 space-y-4">
            <div class="mb-2 flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-primary shrink-0"></span>
                <input id="p1-name-${slotIndex}" class="w-full bg-transparent border-b border-outline-variant/30 focus:border-primary focus:ring-0 text-sm font-bold text-on-surface-variant uppercase tracking-wider px-0 py-1 transition-colors" placeholder="Player 1 Name" type="text" value="PLAYER 1">
            </div>
            <div>
                <label class="block text-xs font-medium text-on-surface-variant mb-1">Model ID</label>
                <input id="p1-model-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary" type="text" value="anthropic/claude-haiku-4.5">
            </div>
            <div class="bg-surface-container-low/50 p-3 rounded-lg border border-outline-variant/20 space-y-3">
                <div class="flex justify-between items-center">
                    <span class="text-xs font-medium text-on-surface-variant">Reasoning Configuration</span>
                </div>
                <div class="space-y-2">
                    <label class="flex items-center gap-2">
                        <input id="p1-reasoning-enabled-${slotIndex}" type="checkbox" class="rounded border-outline-variant/50 text-primary focus:ring-primary h-4 w-4">
                        <span class="text-xs font-medium text-on-surface-variant">Enable Reasoning</span>
                    </label>
                    <div>
                        <label class="block text-[10px] text-on-surface-variant uppercase font-bold mb-1">Reasoning Effort</label>
                        <select id="p1-reasoning-effort-${slotIndex}" class="w-full bg-surface border-outline-variant/50 text-on-background rounded text-xs py-1 px-2 focus:ring-primary focus:border-primary">
                            <option value="low" selected>Low</option>
                            <option value="medium">Medium</option>
                            <option value="high">High</option>
                        </select>
                    </div>
                </div>
            </div>
            <div>
                <label class="block text-xs font-medium text-on-surface-variant mb-1">Action Memory Window</label>
                <select id="p1-memory-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary">
                    <option value="1">1</option>
                    <option value="2">2</option>
                    <option value="3" selected>3</option>
                    <option value="5">5</option>
                    <option value="8">8</option>
                </select>
            </div>
        </div>
    </div>
    <div class="p-5 border-t border-outline-variant/30 bg-surface-container-low/30 space-y-4">
        <h4 class="text-sm font-bold text-on-surface-variant uppercase tracking-wider">Stage & Tournament Configuration</h4>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
            <!-- Left Column -->
            <div class="space-y-4">
                <div class="flex gap-4">
                    <div class="flex-1">
                        <label class="block text-xs font-medium text-on-surface-variant mb-1">Tournament Stage</label>
                        <select id="stage-name-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary">
                            <option value="">None</option>
                            <option value="Quarter-final">Quarter-final</option>
                            <option value="Semi-final">Semi-final</option>
                            <option value="Final">Final</option>
                        </select>
                    </div>
                    <div class="flex-1">
                        <label class="block text-xs font-medium text-on-surface-variant mb-1">Leg</label>
                        <select id="stage-leg-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary">
                            <option value="1" selected>1</option>
                            <option value="2">2</option>
                        </select>
                    </div>
                </div>
                <div class="pt-2">
                    <label class="flex items-center cursor-pointer group gap-2">
                        <input id="stage-swapped-starting-${slotIndex}" type="checkbox" class="rounded border-outline-variant/50 text-primary focus:ring-primary h-4 w-4">
                        <span class="text-xs font-medium text-on-surface-variant">Swapped Starting Player</span>
                    </label>
                </div>
            </div>
            <!-- Right Column -->
            <div class="space-y-4">
                <div>
                    <label class="block text-xs font-medium text-on-surface-variant mb-1">1st Leg Score Result (P0 : P1)</label>
                    <div class="flex gap-2 items-center">
                        <input id="stage-score-p0-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center" min="0" type="number" value="0">
                        <span class="text-on-surface-variant">:</span>
                        <input id="stage-score-p1-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center" min="0" type="number" value="0">
                    </div>
                </div>
                <div>
                    <label class="block text-xs font-medium text-on-surface-variant mb-1">1st Leg Cards Bought (P0 : P1)</label>
                    <div class="flex gap-2 items-center">
                        <input id="stage-cards-p0-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center" min="0" type="number" value="0">
                        <span class="text-on-surface-variant">:</span>
                        <input id="stage-cards-p1-${slotIndex}" class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center" min="0" type="number" value="0">
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
        `;
    }

    // Function to add a match slot card to UI
    function addMatchSlot() {
        if (matchCount >= 8) {
            alert('Maximum 8 matches can be configured simultaneously.');
            return;
        }
        matchCount++;

        const div = document.createElement('div');
        div.innerHTML = createMatchSlotHTML(matchCount);
        
        // Append slot
        matchGrid.appendChild(div.firstElementChild);

        // Hook up delete event if it's slot index > 1
        const removeBtn = document.querySelector(`.remove-slot-btn[data-slot-id="${matchCount}"]`);
        if (removeBtn) {
            removeBtn.addEventListener('click', (e) => {
                const id = e.currentTarget.getAttribute('data-slot-id');
                const slot = document.getElementById(`match-slot-${id}`);
                if (slot) {
                    slot.remove();
                    // We don't reset matchCount value so IDs stay unique, but total active matches count goes down
                }
            });
        }
    }

    // Add first slot by default
    addMatchSlot();

    addMatchBtn.addEventListener('click', addMatchSlot);

    // Global Reset
    resetAllBtn.addEventListener('click', () => {
        if (confirm('Are you sure you want to clear configurations and reset match slots?')) {
            matchGrid.innerHTML = '';
            matchCount = 0;
            addMatchSlot();
        }
    });

    // Run simulations
    runSimulationsBtn.addEventListener('click', () => {
        const slots = document.querySelectorAll('[id^="match-slot-"]');
        if (slots.length === 0) {
            alert('Please configure at least one match slot.');
            return;
        }

        const globalSemiAuto = document.getElementById('global-semi-auto').checked;
        const globalDebug = document.getElementById('global-debug').checked;
        const globalPromptCaching = document.getElementById('global-prompt-caching').value;

        slots.forEach(slot => {
            const slotId = slot.id.replace('match-slot-', '');

            // Construct payload
            const payload = {
                player0: {
                    name: document.getElementById(`p0-name-${slotId}`).value,
                    model: document.getElementById(`p0-model-${slotId}`).value,
                    memorySize: parseInt(document.getElementById(`p0-memory-${slotId}`).value),
                    reasoning: {
                        enabled: document.getElementById(`p0-reasoning-enabled-${slotId}`).checked,
                        effort: document.getElementById(`p0-reasoning-effort-${slotId}`).value
                    }
                },
                player1: {
                    name: document.getElementById(`p1-name-${slotId}`).value,
                    model: document.getElementById(`p1-model-${slotId}`).value,
                    memorySize: parseInt(document.getElementById(`p1-memory-${slotId}`).value),
                    reasoning: {
                        enabled: document.getElementById(`p1-reasoning-enabled-${slotId}`).checked,
                        effort: document.getElementById(`p1-reasoning-effort-${slotId}`).value
                    }
                },
                stage: {
                    stage: document.getElementById(`stage-name-${slotId}`).value,
                    leg: parseInt(document.getElementById(`stage-leg-${slotId}`).value),
                    swappedStartingPlayer: document.getElementById(`stage-swapped-starting-${slotId}`).checked,
                    firstLegResult: `${document.getElementById(`stage-score-p0-${slotId}`).value}:${document.getElementById(`stage-score-p1-${slotId}`).value}`,
                    firstLegCardsBought: `${document.getElementById(`stage-cards-p0-${slotId}`).value}:${document.getElementById(`stage-cards-p1-${slotId}`).value}`
                },
                environment: {
                    semiAuto: globalSemiAuto,
                    debugMode: globalDebug,
                    promptCaching: globalPromptCaching
                }
            };

            // Post to backend
            fetch('/api/matches/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Server returned error starting slot #' + slotId);
                }
                return res.json();
            })
            .then(data => {
                if (data.matchId) {
                    // Open a new live dashboard in a new tab!
                    window.open(`dashboard.html?matchId=${data.matchId}`, '_blank');
                }
            })
            .catch(err => {
                console.error(err);
                alert('Error starting match slot #' + slotId + ': ' + err.message);
            });
        });
    });
});
