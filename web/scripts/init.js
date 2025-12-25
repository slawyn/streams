import { initHlsPlayer, initDashPlayer } from './video.js';
import { isStreamAvailable, fetchSources } from './update.js';

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

    const refHlsInstance = { current: null };
    const refDashInstance = { current: null };

    let sources = [];
    const playerInitializers = {
        hls: (video, url, name, status) => initHlsPlayer(video, url, name, status, refHlsInstance),
        dash: (video, url, name, status) => initDashPlayer(video, url, name, status, refDashInstance),
    };

    tileContainer.addEventListener('click', (event) => {
        const clickedTile = event.target.closest('.tile');
        if (clickedTile) {
            document.querySelectorAll('.tile').forEach(t => t.classList.remove('active'));
            clickedTile.classList.add('active');
            const name = clickedTile.dataset.name;
            for (const source of sources) {
                if (source.name === name) {
                    loadSource(source);
                    break;
                }
            }
        }
    });

    select.addEventListener('change', () => {

        // Cleanup previous players
        if (refHlsInstance.current) {
            refHlsInstance.current.destroy();
            refHlsInstance.current = null;
        }
        if (refDashInstance.current) {
            refDashInstance.current.reset();
            refDashInstance.current = null;
        }

        video.removeAttribute('src');
        video.load();


        const selectedIndex = select.selectedIndex;
        const selectedOption = select.options[selectedIndex];
        const streamLink = selectedOption.value;
        const streamId = selectedOption.textContent;

        // Initialize correct player
        const type = detectType(streamLink);
        const playerFunc = playerInitializers[type];
        if (playerFunc) {
            playerFunc(video, streamLink, streamId, statusDiv);
        } else {
            statusDiv.textContent = `Unsupported stream type: "${type}" `;
        }
    });

    function detectType(link) {
        const l = (link || '').toLowerCase();
        if (l.includes('.m3u8') || l.includes('.m3u')) return 'hls';
        if (l.includes('.mpd')) return 'dash';
        if (l.includes('.mp3')) return 'radio';
        return 'unknown';
    }


    function loadSource(source) {
        select.innerHTML = '<option value="">Select stream…</option>';
        let index = 0;
        for (const stream of source.streams) {
            if (stream.available) {
                const opt = document.createElement('option');
                opt.value = stream.link;
                opt.textContent = `${stream.id} ${index + 1}`;
                select.appendChild(opt);
            }
            index++;
        }

        statusDiv.textContent = `Preparing to load ${source.name} stream...`;
        if (select.options.length > 1) {
            select.selectedIndex = 1;
            select.dispatchEvent(new Event("change"));
        }
    }



    function downloadJSON(data, filename = 'data.json') {
        const jsonStr = JSON.stringify(data, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.style.display = 'none';

        document.body.appendChild(a);
        a.click();

        // Cleanup
        URL.revokeObjectURL(url);
        document.body.removeChild(a);
    }

    async function renderTiles(mediaSources) {
        tileContainer.innerHTML = '';

        const groupContainers = {};
        const groupTiles = {};
        let sources = [];

        for (const [index, source] of mediaSources.entries()) {
            const groupName = source.group || 'Other';
            if (!groupContainers[groupName]) {
                const groupTile = document.createElement('div');
                groupTile.classList.add('group-tile');
                groupTile.style.cursor = 'pointer';
                groupTile.style.fontWeight = 'bold';
                groupTile.style.marginTop = '0.5em';
                groupTile.style.marginBottom = '0.2em';
                groupTile.textContent = `${groupName} (0)`;

                const groupContent = document.createElement('div');
                groupContent.classList.add('group-content');
                groupContent.style.display = 'none';
                groupContent.style.marginLeft = '1em';

                groupTile.addEventListener('click', () => {
                    const isOpen = groupContent.style.display === 'block';
                    groupContent.style.display = isOpen ? 'none' : 'block';
                    groupTile.classList.toggle('open', !isOpen);
                });

                tileContainer.appendChild(groupTile);
                tileContainer.appendChild(groupContent);

                groupContainers[groupName] = groupContent;
                groupTiles[groupName] = groupTile;
            }

            // Check availability of all streams
            let sourceAvailable = false;
            for (const stream of source.streams) {
                const available = await isStreamAvailable(stream.link, stream.available);
                stream.available = available;
                sourceAvailable ||= available;
            }

            if (sourceAvailable) {

                const tile = document.createElement('div');
                tile.classList.add('tile');
                tile.dataset.index = index;
                tile.dataset.name = source.name;

                if (source.logo) {
                    const img = document.createElement('img');
                    img.src = source.logo;
                    img.alt = source.name || '';
                    img.style.maxWidth = '48px';
                    img.style.maxHeight = '48px';
                    img.style.display = 'block';
                    img.style.margin = '0 auto 0.2em auto';
                    tile.appendChild(img);
                }

                const nameSpan = document.createElement('span');
                nameSpan.textContent = source.name || '';
                nameSpan.style.fontSize = '0.8em';
                nameSpan.style.display = 'block';
                nameSpan.style.textAlign = 'center';
                tile.appendChild(nameSpan);

                groupContainers[groupName].appendChild(tile);
                const count = groupContainers[groupName].children.length;
                groupTiles[groupName].textContent = `${groupName} (${count})`;

                sources.push(source);
            }
        }

        return sources;
    }

    function fullscreenToggle() {
        if (document.fullscreenElement) {
            document.exitFullscreen(); // Exit fullscreen
        } else {
            if (video.requestFullscreen) {
                video.requestFullscreen();
            } else if (video.webkitRequestFullscreen) {
                video.webkitRequestFullscreen(); // Safari
            } else if (video.mozRequestFullScreen) {
                video.mozRequestFullScreen(); // Firefox
            } else if (video.msRequestFullscreen) {
                video.msRequestFullscreen(); // IE/Edge
            }
        }
    }

    async function populateSources(resync) {
        let api
        let sources;
        if (resync) {
            api = 'api/resync';
            statusDiv.textContent = 'Resyncing streams...';
        } else {
            api = 'api/streams';
            statusDiv.textContent = 'Loading streams...';
        }

        try {
            statusDiv.textContent = `Fetching stream list from ${api}...`;
            sources = await fetchSources(api);
            if (sources.length === 0) {
                statusDiv.textContent = 'No supported streams found from API.';
            } else {
                statusDiv.textContent = 'Loaded Streams';
                sources = await renderTiles(sources);
            }
        } catch (error) {
            statusDiv.textContent = `Failed to load streams: ${error.message}. Check console.`;
        }
        return sources;
    }

    // Player controls
    playButton.addEventListener('click', () => { video.play() });
    pauseButton.addEventListener('click', () => { video.pause(); });
    muteButton.addEventListener('click', () => {
        video.muted = !video.muted;
        muteButton.textContent = video.muted ? 'Unmute' : 'Mute';
    });

    fullscreenButton.addEventListener('click', () => { fullscreenToggle(); });

    video.addEventListener('play', () => {
        const currentSource = 0
        statusDiv.textContent = `Playing... ${currentSource}`;
    });
    video.addEventListener('pause', () => statusDiv.textContent = 'Paused.');
    video.addEventListener('ended', () => statusDiv.textContent = 'Video ended.');
    video.addEventListener('volumechange', () => { muteButton.textContent = video.muted ? 'Unmute' : 'Mute'; });
    video.addEventListener('dblclick', () => { fullscreenToggle(); });

    filterInput.addEventListener('input', async function () {
        const query = filterInput.value.trim().toLowerCase();
        if (!query) {
            filterDropdown.style.display = 'none';
            filterDropdown.innerHTML = '';
            return;
        }

        // Find all available sources that match the query in their name
        const matches = [];
        for (const source of sources) {
            if (source.name && source.name.toLowerCase().includes(query)) {
                matches.push(source);
            }
        }

        if (matches.length === 0) {
            filterDropdown.style.display = 'none';
            filterDropdown.innerHTML = '';
            return;
        }

        // Group matches by group name
        const grouped = {};
        matches.forEach(src => {
            const group = src.group || 'Other';
            if (!grouped[group]) grouped[group] = [];
            grouped[group].push(src);
        });

        // Build dropdown HTML
        filterDropdown.innerHTML = '';
        Object.entries(grouped).forEach(([group, sources]) => {
            const groupDiv = document.createElement('div');
            groupDiv.style.fontWeight = 'bold';
            groupDiv.style.padding = '0.3em 0.5em 0.1em 0.5em';
            groupDiv.textContent = group;
            filterDropdown.appendChild(groupDiv);

            sources.forEach(source => {
                const item = document.createElement('div');
                item.style.cursor = 'pointer';
                item.style.padding = '0.2em 0.5em';
                item.style.display = 'flex';
                item.style.alignItems = 'center';
                item.style.gap = '0.5em';

                // Add logo if available
                if (source.logo) {
                    const img = document.createElement('img');
                    img.src = source.logo;
                    img.alt = source.name || '';
                    img.style.width = '24px';
                    img.style.height = '24px';
                    img.style.objectFit = 'contain';
                    item.appendChild(img);
                }

                // Add name
                const nameSpan = document.createElement('span');
                nameSpan.textContent = source.name || '';
                nameSpan.style.fontSize = '0.9em';
                item.appendChild(nameSpan);
                filterDropdown.appendChild(item);
            });
        });

        // Position and show dropdown
        const rect = filterInput.getBoundingClientRect();
        filterDropdown.style.display = 'block';
        filterDropdown.style.left = rect.left + window.scrollX + 'px';
        filterDropdown.style.top = rect.bottom + window.scrollY + 'px';
        filterDropdown.style.width = rect.width + 'px';
    });

    // Hide dropdown when input is cleared or loses focus
    filterInput.addEventListener('blur', () => {
        setTimeout(() => {
            filterDropdown.style.display = 'none';
        }, 150);
    });

    downloadButton.addEventListener('click', () => {
        downloadJSON(sources, 'config.json');
    });
    resyncButton.addEventListener('click', async () => {
        sources = await populateSources(true);
    });
    fetchButton.addEventListener('click', async () => {
        sources = await populateSources(false);
    });

    fetchButton.click();
});


