export async function fetchSources(apiPath) {
    const response = await fetch(apiPath);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} from ${apiPath}`);
    }
    const streamList = await response.json();
    if (!Array.isArray(streamList)) {
        throw new Error('API response is not an array.');
    }
    // Add "type" key based on link extension
    return streamList.map(entry => {
        let type = 'unknown';
        if (entry.link && entry.link.endsWith('.m3u8')) {
            type = 'hls';
        } else if (entry.link && entry.link.endsWith('.mpd')) {
            type = 'dash';
        } else if (entry.link && entry.link.endsWith('.mp3')) {
            type = 'radio';
        }
        return { ...entry, type };
    }).filter(source => source.type !== 'unknown');
}

function _fetchSilent(url) {
    return new Promise((resolve) => {
        const xhr = new XMLHttpRequest();
        xhr.open('HEAD', url);
        xhr.onload = () => resolve({ ok: xhr.status >= 200 && xhr.status < 400 });
        xhr.onerror = () => resolve({ ok: false });
        xhr.onabort = () => resolve({ ok: false });
        xhr.send();
    });
}

export async function isStreamAvailable(url, available) {
    if (available) {
        return available
    }

    try {
        // const response = await fetch(url, { method: 'HEAD' });
        const response = await _fetchSilent(url);
        return response.ok;
        // return true;
    } catch {
        // Error silently ignored
        return false;
    }
}