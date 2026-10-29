(function() {
    if (window.__gbVideoInstalled) return;
    window.__gbVideoInstalled = true;

    let port = null;
    let activeVideo = null;
    let currentPill = null;

    // 1. Connect to Kotlin
    try {
        port = browser.runtime.connect({ name: "browser" });
        port.onMessage.addListener((msg) => {
            if (!activeVideo) return;
            if (msg.action === "play") activeVideo.play();
            if (msg.action === "pause") activeVideo.pause();
            if (msg.action === "seek") activeVideo.currentTime = msg.time;
        });
        port.postMessage({ type: "PING" }); // Ping Kotlin to test connection
    } catch (e) {
        console.log("GB Extension Port Failed: ", e);
    }

    function getVideoUrl(video) {
        let url = video.currentSrc || video.src;
        if (!url || url === "") {
            const source = video.querySelector('source');
            if (source) url = source.src;
        }
        return url;
    }

    function showPill(video) {
        if (currentPill) currentPill.remove();
        activeVideo = video;

        let pill = document.createElement("div");
        pill.className = "__gbPill";
        pill.innerHTML = `
            <button class="__gbBtn" id="__gbPlay" title="Play/Pause">⏸</button>
            <button class="__gbBtn" id="__gbPip" title="Popout">⛶</button>
            <button class="__gbBtn" id="__gbNative" title="Custom Player">▶</button>
            <button class="__gbBtn" id="__gbDl" title="Download">⬇</button>
        `;
        document.body.appendChild(pill);
        currentPill = pill;

        pill.querySelector("#__gbPlay").onclick = (e) => {
            e.stopPropagation();
            video.paused ? video.play() : video.pause();
            e.target.innerText = video.paused ? "⏸" : "▶";
        };

        pill.querySelector("#__gbPip").onclick = (e) => {
            e.stopPropagation();
            if (document.pictureInPictureElement) {
                document.exitPictureInPicture();
            } else if (video.requestPictureInPicture) {
                video.requestPictureInPicture();
            }
        };

        pill.querySelector("#__gbNative").onclick = (e) => {
            e.stopPropagation();
            if (port) {
                port.postMessage({ 
                    type: "OPEN_NATIVE_PLAYER", 
                    url: getVideoUrl(video),
                    duration: video.duration || 0,
                    currentTime: video.currentTime || 0,
                    width: video.videoWidth || 0,
                    height: video.videoHeight || 0
                });
            }
        };

        pill.querySelector("#__gbDl").onclick = (e) => {
            e.stopPropagation();
            let url = getVideoUrl(video);
            if (!url.startsWith("blob:")) {
                browser.runtime.sendMessage("browser", { type: "DOWNLOAD_VIDEO", url: url });
            }
        };
    }

    // Global Play Detector
    document.addEventListener('play', (e) => {
        if (e.target && e.target.tagName === 'VIDEO') {
            showPill(e.target);
        }
    }, true);

    // Hide pill when paused
    document.addEventListener('pause', (e) => {
        if (e.target && e.target.tagName === 'VIDEO') {
            if (currentPill) {
                currentPill.style.opacity = "0.3";
                setTimeout(() => {
                    if (e.target.paused && currentPill) {
                        currentPill.remove();
                        currentPill = null;
                    }
                }, 3000);
            }
        }
    }, true);
})();
