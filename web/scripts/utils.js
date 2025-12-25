export function detectType(link) {
    const l = (link || '').toLowerCase();
    if (l.includes('.m3u8') || l.includes('.m3u')) return 'hls';
    if (l.includes('.mpd')) return 'dash';
    if (l.includes('.mp3')) return 'radio';
    return 'unknown';
}

export function downloadJSON(data, filename = 'data.json') {
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
