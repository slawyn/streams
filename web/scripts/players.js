import { initHlsPlayer, initDashPlayer } from './video.js';
import { detectType } from './utils.js';

export function createPlayerManager() {
    const refHlsInstance = { current: null };
    const refDashInstance = { current: null };

    const playerInitializers = {
        hls: (video, url, name, status) => initHlsPlayer(video, url, name, status, refHlsInstance),
        dash: (video, url, name, status) => initDashPlayer(video, url, name, status, refDashInstance),
    };

    function destroyAll() {
        if (refHlsInstance.current) {
            try { refHlsInstance.current.destroy(); } catch (e) { /* ignore */ }
            refHlsInstance.current = null;
        }
        if (refDashInstance.current) {
            try { refDashInstance.current.reset(); } catch (e) { /* ignore */ }
            refDashInstance.current = null;
        }
    }

    function initForLink(video, link, name, statusDiv) {
        destroyAll();

        video.removeAttribute('src');
        video.load();

        const type = detectType(link);
        const playerFunc = playerInitializers[type];
        if (playerFunc) {
            playerFunc(video, link, name, statusDiv);
        } else {
            statusDiv.textContent = `Unsupported stream type: "${type}" `;
        }
    }

    return { initForLink, destroyAll, refHlsInstance, refDashInstance };
}
