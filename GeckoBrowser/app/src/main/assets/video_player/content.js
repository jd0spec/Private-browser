(function() {
    if (window.__gbVideoInstalled) return;
    window.__gbVideoInstalled = true;

    function attachSkin(video) {
        if (video.__gbSkin) return;
        video.__gbSkin = true;
        
        video.controls = false;

        let wrap = video.parentElement;
        if (!wrap.classList.contains("__gbWrap")) {
            let newWrap = document.createElement("div");
            newWrap.className = "__gbWrap";
            video.parentNode.insertBefore(newWrap, video);
            newWrap.appendChild(video);
            wrap = newWrap;
        }

        let bar = document.createElement("div");
        bar.className = "__gbBar";
        bar.innerHTML = `
            <button class="__gbBtn" id="__gbPlay">▶</button>
            <input type="range" class="__gbSeek" id="__gbSeek" min="0" max="1000" value="0"/>
            <span class="__gbTime" id="__gbTime">0:00</span>
            <button class="__gbDl" id="__gbDl">Download</button>
        `;
        wrap.appendChild(bar);

        let playBtn = bar.querySelector("#__gbPlay");
        let seek = bar.querySelector("#__gbSeek");
        let time = bar.querySelector("#__gbTime");
        let dlBtn = bar.querySelector("#__gbDl");

        playBtn.onclick = () => video.paused ? video.play() : video.pause();
        seek.oninput = () => { 
            if(video.duration) video.currentTime = (seek.value / 1000) * video.duration; 
        };
        
        dlBtn.onclick = () => { 
            let url = video.currentSrc || video.src;
            if(url.startsWith("blob:")) {
                alert("This video is protected (DRM) and cannot be downloaded directly.");
            } else {
                browser.runtime.sendMessage("browser", {
                    type: "DOWNLOAD_VIDEO",
                    url: url
                });
            }
        };

        video.addEventListener("timeupdate", () => {
            if(video.duration) {
                seek.value = Math.round((video.currentTime / video.duration) * 1000);
                let m = Math.floor(video.currentTime / 60);
                let s = Math.floor(video.currentTime % 60).toString().padStart(2, '0');
                time.innerText = m + ":" + s;
            }
        });
        video.addEventListener("play", () => playBtn.innerText = "⏸");
        video.addEventListener("pause", () => playBtn.innerText = "▶");
    }

    document.querySelectorAll("video").forEach(attachSkin);
    new MutationObserver(() => document.querySelectorAll("video").forEach(attachSkin))
        .observe(document.body, {childList: true, subtree: true});
})();
