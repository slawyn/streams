// video.js: Initializes video playback for a given source

export function initHlsPlayer(videoElement, url, streamName, statusDisplayElement, currentHlsInstanceRef) {
    if (currentHlsInstanceRef.current) {
        currentHlsInstanceRef.current.destroy();
        currentHlsInstanceRef.current = null;
    }
    if (window.Hls && Hls.isSupported()) {
        currentHlsInstanceRef.current = new Hls();
        currentHlsInstanceRef.current.attachMedia(videoElement);
        currentHlsInstanceRef.current.loadSource(url);
        currentHlsInstanceRef.current.on(Hls.Events.MANIFEST_PARSED, () => {
            statusDisplayElement.textContent = `Playing ${streamName}.`;
            videoElement.play().catch(e => console.error('HLS Autoplay prevented:', e));
        });
        currentHlsInstanceRef.current.on(Hls.Events.ERROR, (event, data) => {
            statusDisplayElement.textContent = `HLS Error for ${streamName}: ${data.details}.`;
        });
    } else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
        videoElement.src = url;
        videoElement.addEventListener('loadedmetadata', () => {
            statusDisplayElement.textContent = `Playing ${streamName} (Native HLS).`;
            videoElement.play().catch(e => console.error('Native HLS Autoplay prevented:', e));
        }, { once: true });
    } else {
        statusDisplayElement.textContent = `Browser does not support HLS for ${streamName}.`;
    }
}

export function initDashPlayer(videoElement, url, streamName, statusDisplayElement, currentDashInstanceRef) {
    if (currentDashInstanceRef.current) {
        currentDashInstanceRef.current.reset();
        currentDashInstanceRef.current = null;
    }
    if (window.dashjs && dashjs.MediaPlayer) {
        currentDashInstanceRef.current = dashjs.MediaPlayer().create();
        currentDashInstanceRef.current.initialize(videoElement, url, true);
        currentDashInstanceRef.current.on(dashjs.MediaPlayer.events.MANIFEST_LOADED, () => {
            statusDisplayElement.textContent = `Playing ${streamName}.`;
        });
        currentDashInstanceRef.current.on(dashjs.MediaPlayer.events.ERROR, (event) => {
            statusDisplayElement.textContent = `DASH Error for ${streamName}: ${event.error.message}.`;
        });
    } else {
        statusDisplayElement.textContent = `DASH.js is not loaded or not supported for ${streamName}.`;
    }
}