function normalizeEntry(entry) {
    if (Array.isArray(entry.streams)) {
        const streams = entry.streams
            .map(s => ({
                link: s.link,
                available: !!s.available,
                id: s.id || ''
            }))
            .filter(s => s.link);

        return {
            name: entry.name || '',
            group: entry.group || '',
            logo: entry.logo || '',
            program: entry.program || '',
            streams
        };
    }
    return null;
}
function fetchSilent(url) {
    return new Promise((resolve) => {
        const xhr = new XMLHttpRequest();
        xhr.open('HEAD', url);
        xhr.onload = () => resolve({ ok: xhr.status >= 200 && xhr.status < 400 });
        xhr.onerror = () => resolve({ ok: false });
        xhr.onabort = () => resolve({ ok: false });
        xhr.send();
    });
}
export async function fetchSources(resync) {
    let api;
    if (resync) {
        api = 'api/resync';
        console.log('Resyncing streams...');
    } else {
        api = 'api/streams';
        console.log('Loading streams...');
    }

    try {
        console.log(`Fetching stream list from ${api}...`);
        const response = await fetch(api);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status} from ${api}`);
        }
        const data = await response.json();
        if (!Array.isArray(data)) {
            throw new Error('API response is not an array.');
        }
        return data.map(normalizeEntry).filter(Boolean);
    } catch (error) {
        console.log(`Failed to load streams: ${error.message}. Check console.`);
        return [];
    }
}
export async function fetchProgram(url) {
    const response = await fetch(`api/program?url=${encodeURIComponent(url)}`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json'
        }
    });

    if (response.ok) {
        return await response.json();
    } else {
        console.error(`Fehler beim Laden: ${response.status}`);
        return null;
    }
}

export async function fetchIsStreamAvailable(url, available) {
    if (available) {
        return available
    }

    try {
        const response = await fetchSilent(url);
        return response.ok;
    } catch {
        return false;
    }
}