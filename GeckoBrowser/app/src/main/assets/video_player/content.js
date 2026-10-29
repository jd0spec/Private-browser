(function() {
    if (window.__gbVideoInstalled) return;
    window.__gbVideoInstalled = true;

    let currentPill = null;

    function createPill(video) {
        if (currentPill) {
            currentPill.remove();
        }

        let pill = document.createElement("div");
        pill.className = "__gbPill";
        pill.innerHTML = `
            <button class="__gbBtn" id="__gbDl" title="Download">⬇</button>
            <button class="__gbBtn" id="__gbPip" title="Popout">⛶</button>
            <button class="__gbBtn" id="__gbCopy" title="Copy Link">🔗</button>
        `;
        document.body.appendChild(pill);
        currentPill = pill;

        // Download Action
        pill.querySelector("#__gbDl").onclick = (e) => {
            e.stopPropagation();
            let url = video.currentSrc || video.src;
            if (url.startsWith("blob:")) {
                alert("This video is protected (DRM) and cannot be downloaded directly.");
            } else {
                browser.runtime.sendMessage("browser", { type: "DOWNLOAD_VIDEO", url: url });
            }
        };

        // Picture-in-Picture Action
        pill.querySelector("#__gbPip").onclick = (e) => {
            e.stopPropagation();
            if (document.pictureInPictureElement) {
                document.exitPictureInPicture();
            } else if (video.requestPictureInPicture) {
                video.requestPictureInPicture().catch(err => console.log(err));
            }
        };

        // Copy Link Action
        pill.querySelector("#__gbCopy").onclick = (e) => {
            e.stopPropagation();
            let url = video.currentSrc || video.src;
            browser.runtime.sendMessage("browser", { type: "COPY_LINK", url: url });
        };
    }

    function attachDetector(video) {
        if (video.__gbDetected) return;
        video.__gbDetected = true;

        video.addEventListener("play", () => {
            createPill(video);
        });

        video.addEventListener("pause", () => {
            // Keep the pill visible for a few seconds after pausing, then hide
            if (currentPill) {
                currentPill.style.opacity = "0.5";
                setTimeout(() => {
                    if (video.paused && currentPill) {
                        currentPill.remove();
                        currentPill = null;
                    }
                }, 3000);
            }
        });
        
        video.addEventListener("playing", () => {
            if (currentPill) currentPill.style.opacity = "1";
        });
    }

    document.querySelectorAll("video").forEach(attachDetector);
    new MutationObserver(() => document.querySelectorAll("video").forEach(attachDetector))
        .observe(document.body, {childList: true, subtree: true});
})();
