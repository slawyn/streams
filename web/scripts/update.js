async function fetchJson(apiPath) {
    const response = await fetch(apiPath);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} from ${apiPath}`);
    }
    const data = await response.json();
    if (!Array.isArray(data)) {
        throw new Error('API response is not an array.');
    }
    return data;
}

function normalizeStream(s) {
    return {
        link: s.link,
        available: !!s.available,
        id: s.id || ''
    };
}

function normalizeEntry(entry) {
    if (Array.isArray(entry.streams)) {
        const streams = entry.streams.map(normalizeStream).filter(s => s.link);
        return { name: entry.name || '', group: entry.group || '', logo: entry.logo || '', streams };
    }
    return null;
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
        const response = await _fetchSilent(url);
        return response.ok;
    } catch {
        return false;
    }
}

export async function fetchSources(apiPath) {
    const data = await fetchJson(apiPath);
    return data.map(normalizeEntry).filter(Boolean);
}