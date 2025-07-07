import { fetchMediaSources } from './update.js';
import { initHlsPlayer, initDashPlayer } from './video.js';

document.addEventListener('DOMContentLoaded', () => {
    const video = document.getElementById('videoPlayer');
    const playButton = document.getElementById('playButton');
    const pauseButton = document.getElementById('pauseButton');
    const muteButton = document.getElementById('muteButton');
    const statusDiv = document.getElementById('status');
    const tileContainer = document.getElementById('tileContainer');
    const mainTitle = document.querySelector('h1');

    let mediaSources = [];
    const currentHlsInstanceRef = { current: null };
    const currentDashInstanceRef = { current: null };

    const playerInitializers = {
        hls: (video, url, name, status) => initHlsPlayer(video, url, name, status, currentHlsInstanceRef),
        dash: (video, url, name, status) => initDashPlayer(video, url, name, status, currentDashInstanceRef),
    };

    function loadStream(sourceObject) {
        const { name, type, link } = sourceObject;
        statusDiv.textContent = `Preparing to load ${name} (${type} stream)...`;
        if (currentHlsInstanceRef.current) {
            currentHlsInstanceRef.current.destroy();
            currentHlsInstanceRef.current = null;
        }
        if (currentDashInstanceRef.current) {
            currentDashInstanceRef.current.reset();
            currentDashInstanceRef.current = null;
        }
        video.removeAttribute('src');
        video.load();
        const initPlayerFunc = playerInitializers[type];
        if (initPlayerFunc) {
            initPlayerFunc(video, link, name, statusDiv);
        } else {
            statusDiv.textContent = `Unsupported stream type: "${type}" for ${name}.`;
        }
    }

    function renderTiles() {
        tileContainer.innerHTML = '';

        // Group mediaSources by the "group" key
        const groups = {};
        mediaSources.forEach((source, index) => {
            const groupName = source.group || 'Other';
            if (!groups[groupName]) {
                groups[groupName] = [];
            }
            // Store index for tile selection
            groups[groupName].push({ ...source, _index: index });
        });

        Object.entries(groups).forEach(([groupName, sources]) => {
            // Create group tile
            const groupTile = document.createElement('div');
            groupTile.classList.add('group-tile');
            groupTile.textContent = `${groupName} (${sources.length})`; // Add count here
            groupTile.style.cursor = 'pointer';
            groupTile.style.fontWeight = 'bold';
            groupTile.style.marginTop = '0.5em';
            groupTile.style.marginBottom = '0.2em';

            // Create container for tiles in this group
            const groupContent = document.createElement('div');
            groupContent.classList.add('group-content');
            groupContent.style.display = 'none';
            groupContent.style.marginLeft = '1em';

            // Add tiles to group
            sources.forEach(source => {
                const tile = document.createElement('div');
                tile.classList.add('tile');
                tile.dataset.index = source._index;
                tile.textContent = source.name || '';
                groupContent.appendChild(tile);
            });

            // Toggle group open/close on click
            groupTile.addEventListener('click', () => {
                const isOpen = groupContent.style.display === 'block';
                groupContent.style.display = isOpen ? 'none' : 'block';
                groupTile.classList.toggle('open', !isOpen);
            });

            tileContainer.appendChild(groupTile);
            tileContainer.appendChild(groupContent);
        });
    }

    tileContainer.addEventListener('click', (event) => {
        const clickedTile = event.target.closest('.tile');
        if (clickedTile) {
            const index = parseInt(clickedTile.dataset.index, 10);
            const selectedSource = mediaSources[index];
            if (selectedSource) {
                document.querySelectorAll('.tile').forEach(t => t.classList.remove('active'));
                clickedTile.classList.add('active');
                mainTitle.textContent = selectedSource.name;
                loadStream(selectedSource);
            }
        }
    });

    async function populateMediaSources() {
        const apiPath = 'http://localhost:8080/api/streams';
        statusDiv.textContent = `Fetching stream list from ${apiPath}...`;
        mainTitle.textContent = 'Loading Streams...';
        try {
            mediaSources = await fetchMediaSources(apiPath);
            if (mediaSources.length === 0) {
                statusDiv.textContent = 'No supported streams found from API.';
                mainTitle.textContent = 'No Streams Available';
                return;
            }
            renderTiles();
            const firstTile = tileContainer.querySelector(`.tile[data-index="0"]`);
            if (firstTile) {
                firstTile.classList.add('active');
            }
            mainTitle.textContent = mediaSources[0].name;
            loadStream(mediaSources[0]);
        } catch (error) {
            statusDiv.textContent = `Failed to load streams: ${error.message}. Check console.`;
            mainTitle.textContent = 'Error Loading Streams';
        }
    }

    // Player controls
    playButton.addEventListener('click', () => {
        video.play()
            .then(() => statusDiv.textContent = 'Playing...')
            .catch(error => {
                statusDiv.textContent = 'Failed to play video. Check console.';
            });
    });

    pauseButton.addEventListener('click', () => {
        video.pause();
        statusDiv.textContent = 'Paused.';
    });

    muteButton.addEventListener('click', () => {
        video.muted = !video.muted;
        muteButton.textContent = video.muted ? 'Unmute' : 'Mute';
        statusDiv.textContent = video.muted ? 'Muted.' : 'Unmuted.';
    });

    video.addEventListener('play', () => statusDiv.textContent = 'Playing...');
    video.addEventListener('pause', () => statusDiv.textContent = 'Paused.');
    video.addEventListener('ended', () => statusDiv.textContent = 'Video ended.');
    video.addEventListener('volumechange', () => {
        muteButton.textContent = video.muted ? 'Unmute' : 'Mute';
    });

    // Initial fetch and render
    populateMediaSources();
});
