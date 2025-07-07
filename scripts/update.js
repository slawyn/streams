// update.js: Fetches stream list JSON from API and parses it

export async function fetchMediaSources(apiPath) {
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