// AI-Splendor Match Configurator Client Logic

document.addEventListener('DOMContentLoaded', () => {
    // App State
    let slots = [];
    let availableLogs = [];
    const maxSlots = 8;

    // DOM Elements
    const slotsGrid = document.getElementById('slots-grid');
    const addSlotBtn = document.getElementById('add-slot-btn');
    const runSimulationsBtn = document.getElementById('run-simulations-btn');
    const exportPropertiesBtn = document.getElementById('export-properties-btn');
    const resetAllBtn = document.getElementById('reset-all-btn');
    const apiKeyOverrideInput = document.getElementById('api-key-override');
    const globalDebugModeCheckbox = document.getElementById('global-debug-mode');
    const globalPromptCachingSelect = document.getElementById('global-prompt-caching');

    // Default Models
    const defaultModels = [
        "google/gemini-3-flash-preview",
        "anthropic/claude-haiku-4.5",
        "openai/gpt-4o-mini",
        "meta-llama/llama-3-70b-instruct"
    ];

    // Initialize
    async function init() {
        await fetchLogs();
        addNewSlot(); // Start with one configured slot
        renderSlots();
    }

    // Fetch historic logs from server
    async function fetchLogs() {
        try {
            const response = await fetch('/api/logs');
            if (response.ok) {
                availableLogs = await response.json();
            } else {
                console.error("Failed to fetch logs list");
            }
        } catch (e) {
            console.error("Error fetching logs list", e);
        }
    }

    // Create a new blank slot state (No defaults as per feedback)
    function addNewSlot() {
        if (slots.length >= maxSlots) {
            alert("Maximum of 8 parallel slots configured.");
            return;
        }

        const slotId = slots.length + 1;
        const newSlot = {
            id: slotId,
            isExpanded: true,
            player0: {
                name: "",
                model: "",
                inputTokenCost: 0.0,
                outputTokenCost: 0.0,
                reasoningEnabled: true,
                reasoningEffort: "medium",
                reasoningExclude: true,
                reasoningDynamic: false,
                memorySize: 3,
                rules: []
            },
            player1: {
                name: "",
                model: "",
                inputTokenCost: 0.0,
                outputTokenCost: 0.0,
                reasoningEnabled: true,
                reasoningEffort: "medium",
                reasoningExclude: true,
                reasoningDynamic: false,
                memorySize: 3,
                rules: []
            },
            resumeEnabled: false,
            resumeLogFile: availableLogs[0] || "",
            stageEnabled: false,
            stage: {
                stageName: "Semi-final",
                leg: 1,
                swappedStartingPlayer: false,
                firstLegResultP0: 0,
                firstLegResultP1: 0,
                firstLegCardsP0: 0,
                firstLegCardsP1: 0
            }
        };

        slots.push(newSlot);
    }

    // Render all slots to the DOM
    function renderSlots() {
        slotsGrid.innerHTML = '';

        slots.forEach((slot, index) => {
            const card = document.createElement('div');
            card.className = "bg-surface rounded-xl border border-outline-variant/40 shadow-[0_4px_20px_rgba(46,50,48,0.03)] overflow-hidden flex flex-col w-full";
            card.dataset.id = slot.id;

            // Header Section
            const header = document.createElement('div');
            header.className = "px-5 py-3 border-b border-outline-variant/30 bg-surface-container-low flex justify-between items-center cursor-pointer";
            
            const headerTitle = document.createElement('h3');
            headerTitle.className = "font-headline font-medium text-lg text-on-background";
            headerTitle.textContent = `Match Slot #${slot.id}`;

            const headerActions = document.createElement('div');
            headerActions.className = "flex items-center gap-3";
            
            const badge = document.createElement('span');
            badge.className = slot.resumeEnabled 
                ? "px-2.5 py-1 rounded-full bg-tertiary-container/30 text-on-tertiary-container text-xs font-bold tracking-wide flex items-center"
                : "px-2.5 py-1 rounded-full bg-primary-container/20 text-on-primary-fixed-variant text-xs font-bold tracking-wide flex items-center";
            badge.textContent = slot.resumeEnabled ? "RESUME MODE" : "CONFIGURED";

            const toggleBtn = document.createElement('button');
            toggleBtn.className = "p-1 rounded-full hover:bg-on-surface/5 text-on-surface-variant transition-colors flex items-center justify-center";
            toggleBtn.innerHTML = `<span class="material-symbols-outlined text-[20px]">${slot.isExpanded ? 'expand_less' : 'expand_more'}</span>`;
            toggleBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                slot.isExpanded = !slot.isExpanded;
                renderSlots();
            });

            headerActions.appendChild(badge);
            
            // Delete button for non-first slots
            if (slots.length > 1) {
                const deleteBtn = document.createElement('button');
                deleteBtn.className = "p-1 rounded-full hover:bg-error/10 text-error transition-colors flex items-center justify-center";
                deleteBtn.innerHTML = `<span class="material-symbols-outlined text-[20px]">delete</span>`;
                deleteBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    slots.splice(index, 1);
                    // Re-id slots
                    slots.forEach((s, idx) => s.id = idx + 1);
                    renderSlots();
                });
                headerActions.appendChild(deleteBtn);
            }

            headerActions.appendChild(toggleBtn);

            header.appendChild(headerTitle);
            header.appendChild(headerActions);
            header.addEventListener('click', () => {
                slot.isExpanded = !slot.isExpanded;
                renderSlots();
            });
            card.appendChild(header);

            // Expandable Content Body
            if (slot.isExpanded) {
                const body = document.createElement('div');
                body.className = "flex flex-col divide-y divide-outline-variant/30";

                // Resume Toggler
                const resumePanel = document.createElement('div');
                resumePanel.className = "p-5 bg-surface-container-low/20 flex flex-col sm:flex-row items-center justify-between gap-4";
                resumePanel.innerHTML = `
                    <div class="flex items-center gap-4">
                        <label class="flex items-center cursor-pointer group gap-3">
                            <span class="text-sm font-bold text-on-surface-variant uppercase tracking-wider">Resume Interrupted Game</span>
                            <div class="relative">
                                <input type="checkbox" class="sr-only peer resume-checkbox" ${slot.resumeEnabled ? 'checked' : ''}>
                                <div class="block bg-surface-variant w-10 h-6 rounded-full peer-checked:bg-primary/20 peer-checked:border peer-checked:border-primary/30 transition-colors"></div>
                                <div class="dot absolute left-1 top-1 bg-surface w-4 h-4 rounded-full transition-all peer-checked:translate-x-4 peer-checked:bg-primary"></div>
                            </div>
                        </label>
                    </div>
                `;

                // Handle Resume Checkbox change
                const resumeCheckbox = resumePanel.querySelector('.resume-checkbox');
                resumeCheckbox.addEventListener('change', (e) => {
                    slot.resumeEnabled = e.target.checked;
                    renderSlots();
                });

                if (slot.resumeEnabled) {
                    // Show ONLY Log File Selector when Resume Mode is enabled
                    const resumeSelector = document.createElement('div');
                    resumeSelector.className = "p-5 space-y-3";
                    
                    let optionsHtml = availableLogs.map(log => 
                        `<option value="${log}" ${slot.resumeLogFile === log ? 'selected' : ''}>${log}</option>`
                    ).join('');

                    if (availableLogs.length === 0) {
                        optionsHtml = `<option value="">No logs found in logs/ directory</option>`;
                    }

                    resumeSelector.innerHTML = `
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Select Game Log to Resume From</label>
                        <select class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary resume-log-select">
                            ${optionsHtml}
                        </select>
                        <p class="text-[11px] text-on-surface-variant">Backend will parse the log file, rebuild the last state, and continue simulation in the background.</p>
                    `;

                    const logSelect = resumeSelector.querySelector('.resume-log-select');
                    logSelect.addEventListener('change', (e) => {
                        slot.resumeLogFile = e.target.value;
                    });

                    body.appendChild(resumePanel);
                    body.appendChild(resumeSelector);
                } else {
                    // Standard Config Grid (Players)
                    const playersGrid = document.createElement('div');
                    playersGrid.className = "flex flex-col md:flex-row divide-y md:divide-y-0 md:divide-x divide-outline-variant/30 flex-1";

                    // Render Player 0 and Player 1 Column
                    [0, 1].forEach(playerIdx => {
                        const player = playerIdx === 0 ? slot.player0 : slot.player1;
                        const playerCol = document.createElement('div');
                        playerCol.className = "flex-1 p-5 space-y-4";

                        // Non-editable Static Header on Top
                        const nameHeader = document.createElement('div');
                        nameHeader.className = "mb-2 flex items-center gap-2";
                        nameHeader.innerHTML = `
                            <span class="w-2.5 h-2.5 rounded-full ${playerIdx === 0 ? 'bg-tertiary' : 'bg-primary'} shrink-0"></span>
                            <span class="text-sm font-bold text-on-surface-variant uppercase tracking-wider">PLAYER ${playerIdx}</span>
                        `;

                        // OpenRouter Model ID Input
                        const modelDiv = document.createElement('div');
                        modelDiv.innerHTML = `
                            <label class="block text-xs font-semibold text-on-surface-variant mb-1">OpenRouter Model ID</label>
                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary player-model-input" list="models-list-${slot.id}-${playerIdx}" type="text" placeholder="e.g. google/gemini-3.5-flash" value="${player.model}">
                            <datalist id="models-list-${slot.id}-${playerIdx}">
                                ${defaultModels.map(m => `<option value="${m}">`).join('')}
                            </datalist>
                        `;
                        const modelInput = modelDiv.querySelector('.player-model-input');
                        modelInput.addEventListener('input', (e) => {
                            player.model = e.target.value;
                        });

                        // Display Name Input (Editable custom name)
                        const displayNameDiv = document.createElement('div');
                        displayNameDiv.innerHTML = `
                            <label class="block text-xs font-semibold text-on-surface-variant mb-1">Display Name</label>
                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary player-display-name-input" type="text" placeholder="e.g. Gemini 3.5 Flash" value="${player.name}">
                        `;
                        const displayNameInput = displayNameDiv.querySelector('.player-display-name-input');
                        displayNameInput.addEventListener('input', (e) => {
                            player.name = e.target.value;
                        });

                        // Token pricing cost inputs (Input / Output cost per 1M tokens)
                        const pricingDiv = document.createElement('div');
                        pricingDiv.className = "grid grid-cols-2 gap-3";
                        pricingDiv.innerHTML = `
                            <div>
                                <label class="block text-xs font-semibold text-on-surface-variant mb-1">Input Cost (USD / 1M)</label>
                                <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary player-input-cost" type="number" step="0.001" min="0" placeholder="e.g. 1.50" value="${player.inputTokenCost !== undefined ? player.inputTokenCost : ''}">
                            </div>
                            <div>
                                <label class="block text-xs font-semibold text-on-surface-variant mb-1">Output Cost (USD / 1M)</label>
                                <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary player-output-cost" type="number" step="0.001" min="0" placeholder="e.g. 9.00" value="${player.outputTokenCost !== undefined ? player.outputTokenCost : ''}">
                            </div>
                        `;
                        pricingDiv.querySelector('.player-input-cost').addEventListener('input', (e) => {
                            player.inputTokenCost = parseFloat(e.target.value) || 0.0;
                        });
                        pricingDiv.querySelector('.player-output-cost').addEventListener('input', (e) => {
                            player.outputTokenCost = parseFloat(e.target.value) || 0.0;
                        });

                        // Reasoning Thresholds Section
                        const thresholdsDiv = document.createElement('div');
                        thresholdsDiv.className = "bg-surface-container-low/50 p-3 rounded-lg border border-outline-variant/20 space-y-3";
                        
                        const threshHeader = document.createElement('div');
                        threshHeader.className = "flex justify-between items-center";
                        threshHeader.innerHTML = `
                            <label class="flex items-center cursor-pointer group gap-2 select-none">
                                <input type="checkbox" class="rounded text-primary focus:ring-primary border-outline-variant/50 player-reasoning-toggle" ${player.reasoningEnabled ? 'checked' : ''}>
                                <span class="text-xs font-bold text-on-surface-variant uppercase tracking-wider group-hover:text-primary transition-colors">Reasoning Support</span>
                            </label>
                        `;
                        
                        const reasoningToggle = threshHeader.querySelector('.player-reasoning-toggle');
                        reasoningToggle.addEventListener('change', (e) => {
                            player.reasoningEnabled = e.target.checked;
                            renderSlots();
                        });

                        thresholdsDiv.appendChild(threshHeader);

                        if (player.reasoningEnabled) {
                            const dynamicToggleDiv = document.createElement('div');
                            dynamicToggleDiv.className = "flex items-center justify-between py-1 border-t border-outline-variant/20 pt-2";
                            dynamicToggleDiv.innerHTML = `
                                <span class="text-[11px] text-on-surface-variant font-medium">Dynamic Reasoning (Phases)</span>
                                <label class="relative inline-flex items-center cursor-pointer">
                                    <input type="checkbox" class="sr-only peer player-dynamic-toggle" ${player.reasoningDynamic ? 'checked' : ''}>
                                    <div class="w-7 h-4 bg-surface-variant rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-0.5 after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:bg-primary"></div>
                                </label>
                            `;
                            const dynamicToggle = dynamicToggleDiv.querySelector('.player-dynamic-toggle');
                            dynamicToggle.addEventListener('change', (e) => {
                                player.reasoningDynamic = e.target.checked;
                                renderSlots();
                            });
                            thresholdsDiv.appendChild(dynamicToggleDiv);

                            if (player.reasoningDynamic) {
                                // Rules table
                                const rulesContainer = document.createElement('div');
                                rulesContainer.className = "space-y-2 mt-2";
                                rulesContainer.innerHTML = `
                                    <div class="grid grid-cols-[1fr_80px_32px] gap-2 mb-1 px-1">
                                        <span class="text-[10px] text-on-surface-variant uppercase font-bold">Reasoning Effort</span>
                                        <span class="text-[10px] text-on-surface-variant uppercase font-bold text-center">Start Turn</span>
                                        <span></span>
                                    </div>
                                `;

                                player.rules.forEach((rule, rIdx) => {
                                    const ruleRow = document.createElement('div');
                                    ruleRow.className = "flex items-center gap-2 text-xs";
                                    ruleRow.innerHTML = `
                                        <select class="flex-1 bg-surface border-outline-variant/50 text-on-background rounded text-xs py-1 px-2 focus:ring-primary focus:border-primary rule-effort-select">
                                            <option value="off" ${rule.effort === 'off' ? 'selected' : ''}>Off</option>
                                            <option value="low" ${rule.effort === 'low' ? 'selected' : ''}>Low</option>
                                            <option value="medium" ${rule.effort === 'medium' ? 'selected' : ''}>Medium</option>
                                            <option value="high" ${rule.effort === 'high' ? 'selected' : ''}>High</option>
                                        </select>
                                        <input class="w-[80px] bg-surface border-outline-variant/50 text-on-background rounded text-xs py-1 px-2 focus:ring-primary focus:border-primary text-center rule-turn-input" min="1" type="number" value="${rule.startTurn}">
                                        <button class="text-outline hover:text-error transition-colors p-1 delete-rule-btn"><span class="material-symbols-outlined text-[16px]">close</span></button>
                                    `;

                                    ruleRow.querySelector('.rule-effort-select').addEventListener('change', (e) => {
                                        rule.effort = e.target.value;
                                    });

                                    ruleRow.querySelector('.rule-turn-input').addEventListener('input', (e) => {
                                        rule.startTurn = parseInt(e.target.value) || 1;
                                    });

                                    ruleRow.querySelector('.delete-rule-btn').addEventListener('click', () => {
                                        player.rules.splice(rIdx, 1);
                                        renderSlots();
                                    });

                                    rulesContainer.appendChild(ruleRow);
                                });

                                const addRuleBtn = document.createElement('button');
                                addRuleBtn.className = "w-full py-1 mt-1 border border-dashed border-outline-variant/50 rounded text-[10px] uppercase font-bold text-on-surface-variant hover:text-primary hover:border-primary/50 transition-colors flex items-center justify-center gap-1";
                                addRuleBtn.innerHTML = `<span class="material-symbols-outlined text-[14px]">add</span> Add Rule`;
                                addRuleBtn.addEventListener('click', () => {
                                    player.rules.push({ effort: "medium", startTurn: player.rules.length > 0 ? player.rules[player.rules.length - 1].startTurn + 5 : 1 });
                                    renderSlots();
                                });

                                rulesContainer.appendChild(addRuleBtn);
                                thresholdsDiv.appendChild(rulesContainer);
                            } else {
                                // Static reasoning effort select
                                const effortDiv = document.createElement('div');
                                effortDiv.className = "flex flex-col gap-1 mt-2";
                                effortDiv.innerHTML = `
                                    <span class="text-[11px] text-on-surface-variant font-medium">Reasoning Effort (Static)</span>
                                    <select class="w-full bg-surface border-outline-variant/50 text-on-background rounded-md text-xs py-1.5 px-2 focus:ring-primary focus:border-primary static-effort-select">
                                        <option value="low" ${player.reasoningEffort === 'low' ? 'selected' : ''}>Low</option>
                                        <option value="medium" ${player.reasoningEffort === 'medium' ? 'selected' : ''}>Medium</option>
                                        <option value="high" ${player.reasoningEffort === 'high' ? 'selected' : ''}>High</option>
                                    </select>
                                `;
                                effortDiv.querySelector('.static-effort-select').addEventListener('change', (e) => {
                                    player.reasoningEffort = e.target.value;
                                });
                                thresholdsDiv.appendChild(effortDiv);
                            }
                        }

                        playerCol.appendChild(nameHeader);
                        playerCol.appendChild(modelDiv);
                        playerCol.appendChild(displayNameDiv);
                        playerCol.appendChild(pricingDiv);
                        playerCol.appendChild(thresholdsDiv);

                        // Memory Window (Default is 3, with (recommended) text)
                        const memDiv = document.createElement('div');
                        memDiv.innerHTML = `
                            <label class="block text-xs font-semibold text-on-surface-variant mb-1">Action Memory Window</label>
                            <select class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary player-memory-select">
                                <option value="1" ${player.memorySize === 1 ? 'selected' : ''}>1</option>
                                <option value="2" ${player.memorySize === 2 ? 'selected' : ''}>2</option>
                                <option value="3" ${player.memorySize === 3 ? 'selected' : ''}>3 (recommended)</option>
                                <option value="4" ${player.memorySize === 4 ? 'selected' : ''}>4</option>
                                <option value="5" ${player.memorySize === 5 ? 'selected' : ''}>5</option>
                                <option value="6" ${player.memorySize === 6 ? 'selected' : ''}>6</option>
                                <option value="7" ${player.memorySize === 7 ? 'selected' : ''}>7</option>
                                <option value="8" ${player.memorySize === 8 ? 'selected' : ''}>8</option>
                                <option value="9" ${player.memorySize === 9 ? 'selected' : ''}>9</option>
                                <option value="10" ${player.memorySize === 10 ? 'selected' : ''}>10</option>
                            </select>
                        `;
                        const memSelect = memDiv.querySelector('.player-memory-select');
                        memSelect.addEventListener('change', (e) => {
                            player.memorySize = parseInt(e.target.value);
                        });
                        playerCol.appendChild(memDiv);

                        playersGrid.appendChild(playerCol);
                    });

                    body.appendChild(resumePanel);
                    body.appendChild(playersGrid);

                    // Stage Configuration Section (With Toggle as per feedback, empty defaults)
                    const stagePanel = document.createElement('div');
                    stagePanel.className = "p-5 border-t border-outline-variant/30 bg-surface-container-low/30 space-y-4";
                    
                    // Stage Header Toggle
                    const stageToggleHtml = `
                        <div class="flex justify-between items-center mb-3">
                            <label class="flex items-center cursor-pointer group gap-3 select-none">
                                <input type="checkbox" class="rounded text-primary focus:ring-primary border-outline-variant/50 stage-enabled-checkbox" ${slot.stageEnabled ? 'checked' : ''}>
                                <span class="text-sm font-bold text-on-surface-variant uppercase tracking-wider group-hover:text-primary transition-colors">Tournament Stage Config</span>
                            </label>
                        </div>
                    `;
                    
                    stagePanel.innerHTML = stageToggleHtml;

                    const stageCheckbox = stagePanel.querySelector('.stage-enabled-checkbox');
                    stageCheckbox.addEventListener('change', (e) => {
                        slot.stageEnabled = e.target.checked;
                        renderSlots();
                    });

                    if (slot.stageEnabled) {
                        const stageForm = document.createElement('div');
                        stageForm.className = "flex gap-4 flex-wrap";
                        stageForm.innerHTML = `
                            <div class="grid grid-cols-2 gap-8 w-full">
                                <!-- Left Column -->
                                <div class="space-y-4">
                                    <div class="flex gap-4">
                                        <div class="flex-1">
                                            <label class="block text-xs font-medium text-on-surface-variant mb-1">Stage</label>
                                            <select class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary stage-select">
                                                <option ${slot.stage.stageName === 'Quarter-final' ? 'selected' : ''}>Quarter-final</option>
                                                <option ${slot.stage.stageName === 'Semi-final' ? 'selected' : ''}>Semi-final</option>
                                                <option ${slot.stage.stageName === 'Final' ? 'selected' : ''}>Final</option>
                                            </select>
                                        </div>
                                        <div class="flex-1">
                                            <label class="block text-xs font-medium text-on-surface-variant mb-1">Leg</label>
                                            <select class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary leg-select">
                                                <option value="1" ${slot.stage.leg === 1 ? 'selected' : ''}>1</option>
                                                <option value="2" ${slot.stage.leg === 2 ? 'selected' : ''}>2</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="pt-2">
                                        <label class="flex items-center cursor-pointer group pb-2 gap-4">
                                            <span class="text-xs font-medium text-on-surface-variant">Swapped Starting Player</span>
                                            <div class="relative ml-3">
                                                <input type="checkbox" class="sr-only peer swap-start-checkbox" ${slot.stage.swappedStartingPlayer ? 'checked' : ''}>
                                                <div class="block bg-surface-variant w-10 h-6 rounded-full peer-checked:bg-primary/20 peer-checked:border peer-checked:border-primary/30 transition-colors"></div>
                                                <div class="dot absolute left-1 top-1 bg-surface w-4 h-4 rounded-full transition-all peer-checked:translate-x-4 peer-checked:bg-primary"></div>
                                            </div>
                                        </label>
                                    </div>
                                </div>
                                <!-- Right Column -->
                                <div class="space-y-4 leg2-only" style="${slot.stage.leg === 2 ? '' : 'display: none;'}">
                                    <div>
                                        <label class="block text-xs font-medium text-on-surface-variant mb-1">1st Leg Result (P0 : P1)</label>
                                        <div class="flex gap-2 items-center">
                                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center p0-score-input" type="number" min="0" placeholder="0" value="${slot.stage.firstLegResultP0 === 0 ? '' : slot.stage.firstLegResultP0}">
                                            <span class="text-on-surface-variant">:</span>
                                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center p1-score-input" type="number" min="0" placeholder="0" value="${slot.stage.firstLegResultP1 === 0 ? '' : slot.stage.firstLegResultP1}">
                                        </div>
                                    </div>
                                    <div>
                                        <label class="block text-xs font-medium text-on-surface-variant mb-1">1st Leg Cards Bought</label>
                                        <div class="flex gap-2 items-center">
                                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center p0-cards-input" type="number" min="0" placeholder="0" value="${slot.stage.firstLegCardsP0 === 0 ? '' : slot.stage.firstLegCardsP0}">
                                            <span class="text-on-surface-variant">:</span>
                                            <input class="w-full bg-surface-container-low border-outline-variant/50 text-on-background rounded-md text-sm focus:ring-primary focus:border-primary text-center p1-cards-input" type="number" min="0" placeholder="0" value="${slot.stage.firstLegCardsP1 === 0 ? '' : slot.stage.firstLegCardsP1}">
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        `;

                        stageForm.querySelector('.stage-select').addEventListener('change', (e) => {
                            slot.stage.stageName = e.target.value;
                        });
                        
                        const legSelect = stageForm.querySelector('.leg-select');
                        legSelect.addEventListener('change', (e) => {
                            slot.stage.leg = parseInt(e.target.value);
                            renderSlots(); // Re-render to show/hide 2nd leg details
                        });

                        stageForm.querySelector('.swap-start-checkbox').addEventListener('change', (e) => {
                            slot.stage.swappedStartingPlayer = e.target.checked;
                        });

                        const p0Score = stageForm.querySelector('.p0-score-input');
                        if (p0Score) {
                            p0Score.addEventListener('input', (e) => {
                                slot.stage.firstLegResultP0 = parseInt(e.target.value) || 0;
                            });
                        }

                        const p1Score = stageForm.querySelector('.p1-score-input');
                        if (p1Score) {
                            p1Score.addEventListener('input', (e) => {
                                slot.stage.firstLegResultP1 = parseInt(e.target.value) || 0;
                            });
                        }

                        const p0Cards = stageForm.querySelector('.p0-cards-input');
                        if (p0Cards) {
                            p0Cards.addEventListener('input', (e) => {
                                slot.stage.firstLegCardsP0 = parseInt(e.target.value) || 0;
                            });
                        }

                        const p1Cards = stageForm.querySelector('.p1-cards-input');
                        if (p1Cards) {
                            p1Cards.addEventListener('input', (e) => {
                                slot.stage.firstLegCardsP1 = parseInt(e.target.value) || 0;
                            });
                        }

                        stagePanel.appendChild(stageForm);
                    }

                    body.appendChild(stagePanel);
                }

                card.appendChild(body);
            }

            slotsGrid.appendChild(card);
        });

        // Add Empty Dotted Card if slots < 8
        if (slots.length < maxSlots) {
            const dottedCard = document.createElement('div');
            dottedCard.className = "bg-surface w-full rounded-xl border border-dashed border-outline-variant hover:border-primary/50 hover:bg-surface-container-low/50 transition-colors shadow-sm overflow-hidden flex flex-col justify-center items-center py-12 cursor-pointer group";
            dottedCard.innerHTML = `
                <span class="material-symbols-outlined text-4xl text-outline-variant mb-2 group-hover:text-primary transition-colors">add_circle</span>
                <h3 class="font-headline font-medium text-lg text-on-surface-variant group-hover:text-primary transition-colors">Configure Match Slot #${slots.length + 1}</h3>
                <p class="text-sm text-outline mt-1">Click to setup models and parameters</p>
            `;
            dottedCard.addEventListener('click', () => {
                addNewSlot();
                renderSlots();
            });
            slotsGrid.appendChild(dottedCard);
        }

        // Render Recent Logs Section for Replay Archive
        const logsSection = document.createElement('div');
        logsSection.className = "bg-surface rounded-xl border border-outline-variant/40 p-5 mt-8 shadow-sm w-full";
        
        const logsTitle = document.createElement('h3');
        logsTitle.className = "font-headline font-medium text-lg text-on-background mb-3";
        logsTitle.textContent = "Simulation Logs & Replay Archive";
        logsSection.appendChild(logsTitle);

        const logsList = document.createElement('div');
        logsList.className = "divide-y divide-outline-variant/20 max-h-60 overflow-y-auto pr-2";
        
        if (availableLogs.length === 0) {
            logsList.innerHTML = `<p class="text-xs text-outline italic py-3 text-center">No simulation logs found. Run a simulation to generate logs.</p>`;
        } else {
            availableLogs.forEach(log => {
                const logRow = document.createElement('div');
                logRow.className = "flex items-center justify-between py-2 text-xs";
                logRow.innerHTML = `
                    <div class="flex items-center gap-2">
                        <span class="material-symbols-outlined text-outline text-[16px]">description</span>
                        <span class="font-mono text-on-surface-variant font-bold">${log}</span>
                    </div>
                    <button class="px-3 py-1 bg-surface border border-outline-variant/50 text-primary rounded font-medium hover:bg-primary-container/20 transition-all flex items-center gap-1">
                        <span class="material-symbols-outlined text-[14px]">play_circle</span> Watch Replay
                    </button>
                `;
                
                logRow.querySelector('button').addEventListener('click', () => {
                    window.open(`/game_board.html?replayFile=${log}`, '_blank');
                });
                
                logsList.appendChild(logRow);
            });
        }
        logsSection.appendChild(logsList);
        slotsGrid.appendChild(logsSection);
    }

    // Helper: format dynamic reasoning phases string (e.g. 1-5:medium,6+:high)
    function buildPhasesString(rules) {
        if (!rules || rules.length === 0) return null;
        
        // Sort rules by startTurn
        const sorted = [...rules].sort((a, b) => a.startTurn - b.startTurn);
        
        let phases = [];
        for (let i = 0; i < sorted.length; i++) {
            const current = sorted[i];
            const next = sorted[i + 1];
            
            if (next) {
                const endTurn = next.startTurn - 1;
                if (current.startTurn === endTurn) {
                    phases.push(`${current.startTurn}:${current.effort}`);
                } else {
                    phases.push(`${current.startTurn}-${endTurn}:${current.effort}`);
                }
            } else {
                phases.push(`${current.startTurn}+:${current.effort}`);
            }
        }
        return phases.join(',');
    }

    // Export properties logic
    exportPropertiesBtn.addEventListener('click', async () => {
        if (slots.length === 0) return;
        const activeSlot = slots[0];

        const payload = {
            player0Model: activeSlot.player0.model,
            player1Model: activeSlot.player1.model,
            player0Name: activeSlot.player0.name,
            player1Name: activeSlot.player1.name,
            player0ReasoningEnabled: activeSlot.player0.reasoningEnabled,
            player0ReasoningEffort: activeSlot.player0.reasoningEffort,
            player0ReasoningExclude: activeSlot.player0.reasoningExclude,
            player0ReasoningDynamic: activeSlot.player0.reasoningDynamic,
            player0ReasoningPhases: buildPhasesString(activeSlot.player0.rules),
            player0MemorySize: activeSlot.player0.memorySize,
            player0InputTokenCost: activeSlot.player0.inputTokenCost || 0.0,
            player0OutputTokenCost: activeSlot.player0.outputTokenCost || 0.0,
            player1ReasoningEnabled: activeSlot.player1.reasoningEnabled,
            player1ReasoningEffort: activeSlot.player1.reasoningEffort,
            player1ReasoningExclude: activeSlot.player1.reasoningExclude,
            player1ReasoningDynamic: activeSlot.player1.reasoningDynamic,
            player1ReasoningPhases: buildPhasesString(activeSlot.player1.rules),
            player1MemorySize: activeSlot.player1.memorySize,
            player1InputTokenCost: activeSlot.player1.inputTokenCost || 0.0,
            player1OutputTokenCost: activeSlot.player1.outputTokenCost || 0.0,
            stageEnabled: activeSlot.stageEnabled,
            stageName: activeSlot.stage.stageName,
            stageLeg: activeSlot.stage.leg,
            stageSwappedStartingPlayer: activeSlot.stage.swappedStartingPlayer,
            stageFirstLegResultP0: activeSlot.stage.firstLegResultP0,
            stageFirstLegResultP1: activeSlot.stage.firstLegResultP1,
            stageFirstLegCardsP0: activeSlot.stage.firstLegCardsP0,
            stageFirstLegCardsP1: activeSlot.stage.firstLegCardsP1,
            debugMode: globalDebugModeCheckbox.checked,
            promptCachingSetting: globalPromptCachingSelect.value
        };

        try {
            const response = await fetch('/api/config/export', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (response.ok) {
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'game.properties';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            } else {
                alert("Failed to export properties file.");
            }
        } catch (e) {
            console.error("Error during properties export:", e);
        }
    });

    // Reset all slots
    resetAllBtn.addEventListener('click', () => {
        if (confirm("Are you sure you want to reset all slots to defaults?")) {
            slots = [];
            addNewSlot();
            renderSlots();
        }
    });

    // Run All Simulations logic
    runSimulationsBtn.addEventListener('click', async () => {
        const apiKey = apiKeyOverrideInput.value.trim();
        const globalDebug = globalDebugModeCheckbox.checked;
        const globalCaching = globalPromptCachingSelect.value;

        if (slots.length === 0) {
            alert("No configured slots found.");
            return;
        }

        // Loop over slots and invoke REST endpoint
        for (const slot of slots) {
            if (!slot.resumeEnabled && (!slot.player0.model || !slot.player1.model)) {
                alert(`Slot #${slot.id}: OpenRouter Model ID is required for both players to start a new simulation.`);
                continue;
            }

            if (slot.resumeEnabled) {
                if (!slot.resumeLogFile) {
                    alert(`Slot #${slot.id}: Resume mode enabled but no log file selected.`);
                    continue;
                }

                try {
                    const response = await fetch('/api/matches/resume', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            logFileName: slot.resumeLogFile,
                            apiKeyOverride: apiKey
                        })
                    });

                    if (response.ok) {
                        const data = await response.json();
                        window.open(`/game_board.html?gameId=${data.gameId}`, '_blank');
                    } else {
                        const err = await response.json();
                        alert(`Slot #${slot.id} resume failed: ${err.error || "Unknown server error"}`);
                    }
                } catch (e) {
                    console.error("Resume request failed", e);
                    alert(`Slot #${slot.id} connection failed: ${e.message}`);
                }
            } else {
                // New simulation
                const payload = {
                    player0Model: slot.player0.model,
                    player1Model: slot.player1.model,
                    player0Name: slot.player0.name,
                    player1Name: slot.player1.name,
                    player0ReasoningEnabled: slot.player0.reasoningEnabled,
                    player0ReasoningEffort: slot.player0.reasoningEffort,
                    player0ReasoningExclude: slot.player0.reasoningExclude,
                    player0ReasoningDynamic: slot.player0.reasoningDynamic,
                    player0ReasoningPhases: buildPhasesString(slot.player0.rules),
                    player0MemorySize: slot.player0.memorySize,
                    player0InputTokenCost: slot.player0.inputTokenCost || 0.0,
                    player0OutputTokenCost: slot.player0.outputTokenCost || 0.0,
                    player1ReasoningEnabled: slot.player1.reasoningEnabled,
                    player1ReasoningEffort: slot.player1.reasoningEffort,
                    player1ReasoningExclude: slot.player1.reasoningExclude,
                    player1ReasoningDynamic: slot.player1.reasoningDynamic,
                    player1ReasoningPhases: buildPhasesString(slot.player1.rules),
                    player1MemorySize: slot.player1.memorySize,
                    player1InputTokenCost: slot.player1.inputTokenCost || 0.0,
                    player1OutputTokenCost: slot.player1.outputTokenCost || 0.0,
                    stageEnabled: slot.stageEnabled,
                    stageName: slot.stage.stageName,
                    stageLeg: slot.stage.leg,
                    stageSwappedStartingPlayer: slot.stage.swappedStartingPlayer,
                    stageFirstLegResultP0: slot.stage.firstLegResultP0,
                    stageFirstLegResultP1: slot.stage.firstLegResultP1,
                    stageFirstLegCardsP0: slot.stage.firstLegCardsP0,
                    stageFirstLegCardsP1: slot.stage.firstLegCardsP1,
                    debugMode: globalDebug,
                    promptCachingSetting: globalCaching,
                    apiKeyOverride: apiKey
                };

                try {
                    const response = await fetch('/api/matches/start', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload)
                    });

                    if (response.ok) {
                        const data = await response.json();
                        window.open(`/game_board.html?gameId=${data.gameId}`, '_blank');
                    } else {
                        const err = await response.json();
                        alert(`Slot #${slot.id} failed to start: ${err.error || "Unknown server error"}`);
                    }
                } catch (e) {
                    console.error("Start request failed", e);
                    alert(`Slot #${slot.id} connection failed: ${e.message}`);
                }
            }
        }
    });

    // Run Init
    init();
});
