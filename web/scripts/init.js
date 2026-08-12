import { createPlayerManager } from './players.js';
import { setupUI } from './ui.js';

// Orchestrator: query DOM, wire modules together
document.addEventListener('DOMContentLoaded', () => {
    const video = document.getElementById('videoPlayer');
    const select = document.getElementById('streamSelect');
    const playButton = document.getElementById('playButton');
    const pauseButton = document.getElementById('pauseButton');
    const muteButton = document.getElementById('muteButton');
    const fullscreenButton = document.getElementById('fullscreenButton');
    const statusDiv = document.getElementById('status');
    const downloadButton = document.getElementById('downloadConfig');
    const resyncButton = document.getElementById('resyncConfig');
    const fetchButton = document.getElementById('fetchConfig');
    const tileContainer = document.getElementById('tileContainer');
    const filterInput = document.getElementById('filterInput');
    const filterDropdown = document.getElementById('filterDropdown');
    const program = document.getElementById('program');

    const elements = { video, select, playButton, pauseButton, muteButton, fullscreenButton, statusDiv, downloadButton, resyncButton, fetchButton, tileContainer, filterInput, filterDropdown, program };
    const playerManager = createPlayerManager();
    const ui = setupUI(elements, { playerManager });

    (async () => {
        await ui.init();
    })();
});


