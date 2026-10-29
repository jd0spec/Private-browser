(function() {
    if (window.__gbVideoInstalled) return;
    window.__gbVideoInstalled = true;
    console.log("GB Video Scanner: Activated");

    let currentPill = null;

    // 1. Deep recursive search for videos (Shadow DOMs & Iframes)
    function getAllVideos(root = document) {
        let videos = [];
        try {
            // Find standard video tags
            videos.push(...Array.from(root.querySelectorAll('video')));
            
            // Find videos inside same-origin iframes
            const iframes = root.querySelectorAll('iframe');
            for (let iframe of iframes) {
                try {
                    if (iframe.contentDocument) {
                        videos.push(...getAllVideos(iframe.contentDocument));
                    }
                } catch(e) {} // Ignore cross-origin iframe errors
            }

            // Dig into Shadow DOMs
            const allElements = root.querySelectorAll('*');
            for (let el of allElements) {
                if (el.shadowRoot) {
                    videos.push(...getAllVideos(el.shadowRoot));
                }
            }
        } catch(e) {}
        return videos;
    }

    // 2. Extract the real video URL
    function getVideoUrl(video) {
        let url = video.currentSrc || video.src;
        if (!url || url === "") {
            const source = video.querySelector('source');
            if (source) url = source.src;
        }
        return url;
    }

    // 3. Create the floating Material 3 pill
    function showPill(video) {
        if (currentPill) currentPill.remove();

        let pill = document.createElement("div");
        pill.className = "__gbPill";
        pill.innerHTML = `
            <button class="__gbBtn" id="__gbDl" title="Download">⬇</button>
            <button class="__gbBtn" id="__gbPip" title="Popout">⛶</button>
            <button class="__gbBtn" id="__gbCopy" title="Copy Link">🔗</button>
        `;
        document.body.appendChild(pill);
        currentPill = pill;

        pill.querySelector("#__gbDl").onclick = (e) => {
            e.stopPropagation();
            let url = getVideoUrl(video);
            if (!url) {
                alert("Could not find the video link.");
            } else if (url.startsWith("blob:")) {
                alert("This video is using a stream (Blob URL). The browser cannot download it directly.");
            } else {
                browser.runtime.sendMessage("browser", { type: "DOWNLOAD_VIDEO", url: url });
            }
        };

        pill.querySelector("#__gbPip").onclick = (e) => {
            e.stopPropagation();
            if (document.pictureInPictureElement) {
                document.exitPictureInPicture();
            } else if (video.requestPictureInPicture) {
                video.requestPictureInPicture().catch(err => console.log("PiP failed"));
            }
        };

        pill.querySelector("#__gbCopy").onclick = (e) => {
            e.stopPropagation();
            let url = getVideoUrl(video);
            if (url) {
                browser.runtime.sendMessage("browser", { type: "COPY_LINK", url: url });
            }
        };
    }

    // 4. Attach listeners to every video found
    function attachDetector(video) {
        if (video.__gbDetected) return;
        video.__gbDetected = true;

        // Show pill when video plays
        video.addEventListener("play", () => {
            console.log("GB Video: Play event detected");
            showPill(video);
        });

        // Fade out when paused
        video.addEventListener("pause", () => {
            if (currentPill) {
                currentPill.style.opacity = "0.3";
                setTimeout(() => {
                    if (video.paused && currentPill) {
                        currentPill.remove();
                        currentPill = null;
                    }
                }, 2000);
            }
        });
        
        video.addEventListener("playing", () => {
            if (currentPill) currentPill.style.opacity = "1";
        });
    }

    // 5. The Ultimate Scanner
    function scan() {
        let vids = getAllVideos();
        vids.forEach(attachDetector);
    }

    // Run immediately, then keep scanning every 2 seconds for late-loading players
    scan();
    setInterval(scan, 2000);
    
    // Also scan whenever the page changes (infinite scroll, SPA loading)
    new MutationObserver(scan).observe(document.documentElement, {childList: true, subtree: true});
    
    // Global event listener: Catch ANY play event that bubbles up
    document.addEventListener('play', (e) => {
        if (e.target && e.target.tagName === 'VIDEO') {
            showPill(e.target);
        }
    }, true);

})();
