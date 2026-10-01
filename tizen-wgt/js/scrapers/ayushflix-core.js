// Ayushflix Dynamic Scraper Core v1.0.0
// This file is auto-synced from GitHub to all Tizen Smart TVs and Web clients.
window.AyushflixCore = {
    version: '1.0.0',
    netmirrorBase: 'https://net52.cc',

    // Fetch Netflix Trending
    fetchNetflixTrending: async function() {
        const res = await fetch(`${this.netmirrorBase}/mobile/search.php?s=`);
        const data = await res.json();
        return (data.searchResult || []).map(item => ({
            id: item.id,
            provider: 'netflix',
            title: item.t,
            year: item.y || '',
            poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
        }));
    },

    // Fetch Prime Video Hits
    fetchPrimeHits: async function() {
        const res = await fetch(`${this.netmirrorBase}/mobile/pv/search.php?s=a`);
        const data = await res.json();
        return (data.searchResult || []).slice(0, 20).map(item => ({
            id: item.id,
            provider: 'prime',
            title: item.t,
            year: item.y || '',
            duration: item.r || '',
            poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
        }));
    },

    // Search across providers
    search: async function(query) {
        const results = [];
        try {
            const nfRes = await fetch(`${this.netmirrorBase}/mobile/search.php?s=${encodeURIComponent(query)}`);
            const nfData = await nfRes.json();
            if (nfData.searchResult) {
                nfData.searchResult.forEach(item => {
                    results.push({
                        id: item.id,
                        provider: 'netflix',
                        title: item.t,
                        year: item.y || '',
                        poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
                    });
                });
            }
        } catch (_) {}

        try {
            const pvRes = await fetch(`${this.netmirrorBase}/mobile/pv/search.php?s=${encodeURIComponent(query)}`);
            const pvData = await pvRes.json();
            if (pvData.searchResult) {
                pvData.searchResult.forEach(item => {
                    results.push({
                        id: item.id,
                        provider: 'prime',
                        title: item.t,
                        year: item.y || '',
                        duration: item.r || '',
                        poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`
                    });
                });
            }
        } catch (_) {}

        return results;
    },

    // Resolve M3U8 Stream
    resolveStream: async function(id, provider) {
        const playlistUrl = provider === 'prime'
            ? `${this.netmirrorBase}/mobile/pv/playlist.php?id=${id}`
            : `${this.netmirrorBase}/mobile/playlist.php?id=${id}`;

        const res = await fetch(playlistUrl);
        const data = await res.json();
        if (Array.isArray(data) && data[0]?.sources?.length) {
            const rawFile = data[0].sources[0].file;
            return rawFile.startsWith('http') ? rawFile : `${this.netmirrorBase}${rawFile}`;
        }
        return null;
    }
};

console.log(`Ayushflix Dynamic Scraper Core v${window.AyushflixCore.version} loaded.`);
