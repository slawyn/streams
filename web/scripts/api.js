import { isStreamAvailable, fetchSources } from './update.js';

export async function renderTiles(mediaSources, tileContainer) {
    tileContainer.innerHTML = '';

    const groupContainers = {};
    const groupTiles = {};
    const availableSources = [];

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

            availableSources.push(source);
        }
    }

    return availableSources;
}

export async function populateSources(resync, tileContainer, statusDiv) {
    let api;
    if (resync) {
        api = 'api/resync';
        statusDiv.textContent = 'Resyncing streams...';
    } else {
        api = 'api/streams';
        statusDiv.textContent = 'Loading streams...';
    }

    try {
        statusDiv.textContent = `Fetching stream list from ${api}...`;
        let sources = await fetchSources(api);
        if (sources.length === 0) {
            statusDiv.textContent = 'No supported streams found from API.';
        } else {
            statusDiv.textContent = 'Loaded Streams';
            sources = await renderTiles(sources, tileContainer);
        }
        return sources;
    } catch (error) {
        statusDiv.textContent = `Failed to load streams: ${error.message}. Check console.`;
        return [];
    }
}
