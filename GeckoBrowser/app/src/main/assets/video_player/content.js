(function() {
    if (window.__gbVideoInstalled) return;
    window.__gbVideoInstalled = true;

    let currentPill = null;

    function createPill(videoUrl) {
        if (currentPill) currentPill.remove();

        let pill = document.createElement("div");
        pill.className = "__gbPill";
        pill.innerHTML = `
            <button class="__gbBtn" id="__gbDl" title="Download">⬇</button>
            <button class="__gbBtn" id="__gbCopy" title="Copy Link">🔗</button>
        `;
        document.body.appendChild(pill);
        currentPill = pill;

        pill.querySelector("#__gbDl").onclick = (e) => {
            e.stopPropagation();
            browser.runtime.sendMessage("browser", { type: "DOWNLOAD_VIDEO", url: videoUrl });
        };

        pill.querySelector("#__gbCopy").onclick = (e) => {
            e.stopPropagation();
            browser.runtime.sendMessage("browser", { type: "COPY_LINK", url: videoUrl });
        };
    }

    // Listen for the network sniffer to send us a URL
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.type === "SHOW_SNIFFED_PILL") {
            createPill(message.url);
        }
    });
})();
