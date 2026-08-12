import { downloadJSON } from './utils.js';
import { fetchIsStreamAvailable, fetchSources, fetchProgram } from './api.js';

const DEFAULT_GROUP = 'Other';

async function renderTiles(sources, tileContainer, statusDiv) {
    if (!sources.length) {
        statusDiv.textContent = 'No supported streams found from API.';
        return [];
    }

    statusDiv.textContent = 'Loaded Streams';
    tileContainer.innerHTML = '';

    const groups = {};
    const availableSources = [];

    const getGroup = groupName => {
        if (groups[groupName]) return groups[groupName];

        const tile = document.createElement('div');
        tile.className = 'group-tile';
        tile.style.cssText = 'cursor:pointer;font-weight:bold;margin-top:.5em;margin-bottom:.2em';
        tile.textContent = `${groupName} (0)`;

        const content = document.createElement('div');
        content.className = 'group-content';
        content.style.cssText = 'display:none;margin-left:1em';

        tile.onclick = () => {
            const open = content.style.display === 'block';
            content.style.display = open ? 'none' : 'block';
            tile.classList.toggle('open', !open);
        };

        tileContainer.append(tile, content);
        return (groups[groupName] = { tile, content });
    };

    for (const [index, source] of sources.entries()) {
        const group = getGroup(source.group || DEFAULT_GROUP);
        let available = false;

        for (const stream of source.streams) {
            stream.available = await fetchIsStreamAvailable(stream.link, stream.available);
            available ||= stream.available;
        }

        if (!available) continue;

        const tile = document.createElement('div');
        tile.className = 'tile';
        tile.dataset.index = index;
        tile.dataset.name = source.name || '';

        if (source.logo) {
            const img = document.createElement('img');
            img.src = source.logo;
            img.alt = source.name || '';
            img.style.cssText = 'max-width:48px;max-height:48px;display:block;margin:0 auto .2em';
            tile.appendChild(img);
        }

        const name = document.createElement('span');
        name.textContent = source.name || '';
        name.style.cssText = 'font-size:.8em;display:block;text-align:center';
        tile.appendChild(name);

        group.content.appendChild(tile);
        group.tile.textContent = `${source.group || DEFAULT_GROUP} (${group.content.children.length})`;
        availableSources.push(source);
    }

    return availableSources;
}

async function loadProgram(source, container) {
    if (!source.program) return;

    try {
        const data = await fetchProgram(source.program);
        const list = typeof data === 'string' ? JSON.parse(data) : data;

        if (!Array.isArray(list) || !list.length) {
            container.innerHTML = '<div class="no-program">Keine Programmdaten verfügbar.</div>';
            return;
        }

        container.innerHTML = '';

        for (const item of list) {
            if (!item.time?.trim()) continue;

            const row = document.createElement('div');
            row.className = 'program-item';

            if (Array.isArray(item.attributes)) {
                row.classList.add(...item.attributes);

                if (item.attributes.includes('active')) {
                    row.classList.add('program-active');
                }
            }

            const time = document.createElement('span');
            time.className = 'program-time';
            time.textContent = item.time;

            const title = document.createElement('span');
            title.className = 'program-title';
            title.textContent = item.title || '';

            row.append(time, title);
            container.appendChild(row);
        }
    } catch (error) {
        console.error('Fehler beim Verarbeiten der Programmdaten:', error);
        container.innerHTML = '<div class="error">Fehler beim Laden des TV-Programms.</div>';
    }
}
function loadSource(source, select, statusDiv) {
    select.innerHTML = '<option value="">Select stream…</option>';

    for (const stream of source.streams) {
        if (!stream.available) continue;

        const option = document.createElement('option');
        option.value = stream.link;
        option.textContent = stream.id;
        select.appendChild(option);
    }

    statusDiv.textContent = `Preparing to load ${source.name} stream...`;

    if (select.options.length > 1) {
        select.selectedIndex = 1;
        select.dispatchEvent(new Event('change'));
    }
}

function toggleFullscreen(video) {
    if (document.fullscreenElement) return document.exitFullscreen();

    if (video.requestFullscreen) video.requestFullscreen();
    else if (video.webkitRequestFullscreen) video.webkitRequestFullscreen();
    else if (video.mozRequestFullScreen) video.mozRequestFullScreen();
    else if (video.msRequestFullscreen) video.msRequestFullscreen();
}

export function setupUI(elements, deps) {
    const {
        video, select, playButton, pauseButton, muteButton, fullscreenButton,
        statusDiv, downloadButton, resyncButton, fetchButton,
        tileContainer, filterInput, filterDropdown, program
    } = elements;

    const { playerManager } = deps;
    let sources = [];

    const selectSource = async source => {
        loadSource(source, select, statusDiv);
        await loadProgram(source, program);
    };

    tileContainer.addEventListener('click', async event => {
        const tile = event.target.closest('.tile');
        if (!tile) return;

        document.querySelectorAll('.tile').forEach(el => el.classList.remove('active'));
        tile.classList.add('active');

        const source = sources.find(item => item.name === tile.dataset.name);
        if (source) await selectSource(source);
    });

    select.addEventListener('change', () => {
        const option = select.options[select.selectedIndex];
        if (option?.value) {
            playerManager.initForLink(video, option.value, option.textContent, statusDiv);
        }
    });

    playButton.onclick = () => video.play();
    pauseButton.onclick = () => video.pause();
    muteButton.onclick = () => {
        video.muted = !video.muted;
        muteButton.textContent = video.muted ? 'Unmute' : 'Mute';
    };

    fullscreenButton.onclick = () => toggleFullscreen(video);
    video.ondblclick = () => toggleFullscreen(video);
    video.onplay = () => statusDiv.textContent = 'Playing...';
    video.onpause = () => statusDiv.textContent = 'Paused.';
    video.onended = () => statusDiv.textContent = 'Video ended.';
    video.onvolumechange = () => {
        muteButton.textContent = video.muted ? 'Unmute' : 'Mute';
    };

    filterInput.addEventListener('input', () => {
        const query = filterInput.value.trim().toLowerCase();

        if (!query) {
            filterDropdown.style.display = 'none';
            filterDropdown.innerHTML = '';
            return;
        }

        const matches = sources.filter(source =>
            source.name?.toLowerCase().includes(query)
        );

        if (!matches.length) {
            filterDropdown.style.display = 'none';
            filterDropdown.innerHTML = '';
            return;
        }

        const groups = {};
        matches.forEach(source => {
            const group = source.group || DEFAULT_GROUP;
            (groups[group] ||= []).push(source);
        });

        filterDropdown.innerHTML = '';

        for (const [group, items] of Object.entries(groups)) {
            const heading = document.createElement('div');
            heading.textContent = group;
            heading.style.cssText = 'font-weight:bold;padding:.3em .5em .1em';
            filterDropdown.appendChild(heading);

            items.forEach(source => {
                const item = document.createElement('div');
                item.style.cssText = 'cursor:pointer;padding:.2em .5em;display:flex;align-items:center;gap:.5em';

                if (source.logo) {
                    const img = document.createElement('img');
                    img.src = source.logo;
                    img.alt = source.name || '';
                    img.style.cssText = 'width:24px;height:24px;object-fit:contain';
                    item.appendChild(img);
                }

                const name = document.createElement('span');
                name.textContent = source.name || '';
                name.style.fontSize = '.9em';
                item.appendChild(name);
                filterDropdown.appendChild(item);
            });
        }

        const rect = filterInput.getBoundingClientRect();
        filterDropdown.style.cssText += `;display:block;left:${rect.left + scrollX}px;top:${rect.bottom + scrollY}px;width:${rect.width}px`;
    });

    filterInput.onblur = () => setTimeout(() => {
        filterDropdown.style.display = 'none';
    }, 150);

    downloadButton.onclick = () => downloadJSON(sources, 'config.json');

    const refresh = async force => {
        sources = await fetchSources(force);
        await renderTiles(sources, tileContainer, statusDiv);
    };

    resyncButton.onclick = () => refresh(true);
    fetchButton.onclick = () => refresh(false);

    return {
        init: () => refresh(false)
    };
}