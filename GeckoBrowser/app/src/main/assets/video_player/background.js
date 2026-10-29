// background.js - The Network Sniffer
let detectedUrls = new Set();

browser.webRequest.onBeforeRequest.addListener(
    function(details) {
        let url = details.url;
        let isVideo = false;

        // Look for known video extensions
        if (url.includes(".mp4") || url.includes(".m3u8") || url.includes(".webm") || url.includes(".mpd") || url.includes(".ts")) {
            isVideo = true;
        }

        // If we find a new video link, tell the content script to show the pill
        if (isVideo && !detectedUrls.has(url)) {
            detectedUrls.add(url);
            // Clear old URLs occasionally to save memory
            if (detectedUrls.size > 50) detectedUrls.clear();

            browser.tabs.query({active: true, currentWindow: true}).then(tabs => {
                if (tabs[0] && tabs[0].id) {
                    browser.tabs.sendMessage(tabs[0].id, {
                        type: "SHOW_SNIFFED_PILL",
                        url: url
                    }).catch(e => {});
                }
            });
        }
    },
    { urls: ["<all_urls>"] }
);
