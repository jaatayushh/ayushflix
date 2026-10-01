const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const axios = require('axios');

const app = express();
app.use(cors());
app.use(express.json());

// -------------------------------------------------------------
// CONFIG & DOMAINS
// -------------------------------------------------------------
const NETMIRROR_BASE = 'https://net52.cc';
const CASTLE_BASE = 'https://api.hlowb.com';
const CASTLE_KEY_SUFFIX = Buffer.from('T!BgJB', 'utf8');

const DEFAULT_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*'
};

// -------------------------------------------------------------
// CASTLE TV DECRYPTION HELPER
// -------------------------------------------------------------
async function getCastleSecurityKey() {
    try {
        const res = await axios.get(`${CASTLE_BASE}/v0.1/system/getSecurityKey/1?channel=IndiaA&clientType=1&lang=en-US`, {
            headers: { 'User-Agent': 'okhttp/4.12.0', 'Accept': 'application/json' },
            timeout: 5000
        });
        if (res.data && res.data.data) {
            const rawKey = Buffer.from(res.data.data, 'base64');
            const combined = Buffer.concat([rawKey, CASTLE_KEY_SUFFIX]);
            return combined.subarray(0, 16); // 128-bit AES key
        }
    } catch (e) {
        console.warn('Failed to fetch Castle security key:', e.message);
    }
    // Fallback key
    return Buffer.concat([Buffer.from('ZkpBVG0qa2dmSg==', 'base64'), CASTLE_KEY_SUFFIX]).subarray(0, 16);
}

function decryptCastleData(cipherBase64, key) {
    try {
        const cipherBytes = Buffer.from(cipherBase64, 'base64');
        const decipher = crypto.createDecipheriv('aes-128-cbc', key, key);
        decipher.setAutoPadding(true);
        let decrypted = decipher.update(cipherBytes);
        decrypted = Buffer.concat([decrypted, decipher.final()]);
        return JSON.parse(decrypted.toString('utf8'));
    } catch (e) {
        return null;
    }
}

// -------------------------------------------------------------
// 1. HOME CATALOGS (Netflix, Prime Video, Hotstar, Castle TV)
// -------------------------------------------------------------
app.get('/api/home', async (req, res) => {
    try {
        const rows = [];

        // 1. Top Searches / Featured from Netflix Mirror
        try {
            const nfRes = await axios.get(`${NETMIRROR_BASE}/mobile/search.php?s=`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 6000
            });
            if (nfRes.data && nfRes.data.searchResult) {
                const items = nfRes.data.searchResult.map(item => ({
                    id: `nf_${item.id}`,
                    sourceId: item.id,
                    provider: 'netflix',
                    title: item.t,
                    year: item.y || '',
                    poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                    backdrop: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                    type: item.episodes ? 'series' : 'movie'
                }));
                rows.push({ title: 'Trending on Netflix', provider: 'netflix', items });
            }
        } catch (e) {
            console.warn('Error fetching Netflix row:', e.message);
        }

        // 2. Prime Video Popular Row
        try {
            const pvRes = await axios.get(`${NETMIRROR_BASE}/mobile/pv/search.php?s=a`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 6000
            });
            if (pvRes.data && pvRes.data.searchResult) {
                const items = pvRes.data.searchResult.slice(0, 20).map(item => ({
                    id: `pv_${item.id}`,
                    sourceId: item.id,
                    provider: 'prime',
                    title: item.t,
                    year: item.y || '',
                    duration: item.r || '',
                    poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                    backdrop: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                    type: 'movie'
                }));
                rows.push({ title: 'Top Amazon Prime Video', provider: 'prime', items });
            }
        } catch (e) {
            console.warn('Error fetching Prime row:', e.message);
        }

        // 3. Featured / Hero Banner Selection
        const hero = rows[0]?.items?.[0] || {
            id: 'nf_70155590',
            sourceId: '70155590',
            provider: 'netflix',
            title: 'The Mentalist',
            year: '2008',
            poster: 'https://imgcdn.kim/poster/v/70155590.jpg',
            backdrop: 'https://imgcdn.kim/poster/v/70155590.jpg',
            overview: 'A famous former psychic uses his extraordinary observing abilities to solve complex murder investigations with the California Bureau of Investigation.'
        };

        res.json({ success: true, hero, rows });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 2. UNIFIED SEARCH (Searches across Netflix & Prime Video)
// -------------------------------------------------------------
app.get('/api/search', async (req, res) => {
    const query = req.query.q || '';
    if (!query.trim()) return res.json({ success: true, results: [] });

    try {
        const results = [];

        // Search Netflix
        try {
            const nf = await axios.get(`${NETMIRROR_BASE}/mobile/search.php?s=${encodeURIComponent(query)}`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 5000
            });
            if (nf.data && nf.data.searchResult) {
                nf.data.searchResult.forEach(item => {
                    results.push({
                        id: `nf_${item.id}`,
                        sourceId: item.id,
                        provider: 'Netflix',
                        title: item.t,
                        year: item.y || '',
                        poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                        type: 'movie'
                    });
                });
            }
        } catch (_) {}

        // Search Prime Video
        try {
            const pv = await axios.get(`${NETMIRROR_BASE}/mobile/pv/search.php?s=${encodeURIComponent(query)}`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 5000
            });
            if (pv.data && pv.data.searchResult) {
                pv.data.searchResult.forEach(item => {
                    results.push({
                        id: `pv_${item.id}`,
                        sourceId: item.id,
                        provider: 'Prime Video',
                        title: item.t,
                        year: item.y || '',
                        duration: item.r || '',
                        poster: `https://imgcdn.kim/poster/v/${item.id}.jpg`,
                        type: 'movie'
                    });
                });
            }
        } catch (_) {}

        res.json({ success: true, count: results.length, results });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 3. MOVIE & SERIES DETAILS / EPISODES
// -------------------------------------------------------------
app.get('/api/details', async (req, res) => {
    const id = req.query.id; // e.g. "nf_70155590" or "pv_..."
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    const isPv = id.startsWith('pv_');
    const sourceId = id.replace(/^(nf|pv)_/, '');
    const endpointPrefix = isPv ? '/mobile/pv' : '/mobile';

    try {
        // Fetch seasons / episodes if it's a TV show
        let seasons = [];
        let isSeries = false;

        try {
            const epRes = await axios.get(`${NETMIRROR_BASE}${endpointPrefix}/episodes.php?s=${sourceId}`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 5000
            });
            if (epRes.data && epRes.data.nextPageSeason) {
                isSeries = true;
                // Generate default season 1 with episodes
                seasons.push({
                    seasonNumber: 1,
                    title: 'Season 1',
                    episodes: [
                        { episodeNumber: 1, title: 'Episode 1', id: sourceId },
                        { episodeNumber: 2, title: 'Episode 2', id: sourceId }
                    ]
                });
            }
        } catch (_) {}

        res.json({
            success: true,
            id,
            sourceId,
            provider: isPv ? 'Prime Video' : 'Netflix',
            isSeries,
            poster: `https://imgcdn.kim/poster/v/${sourceId}.jpg`,
            backdrop: `https://imgcdn.kim/poster/v/${sourceId}.jpg`,
            seasons
        });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 4. STREAM RESOLVER (Extracts direct M3U8 & multi-audio)
// -------------------------------------------------------------
app.get('/api/stream', async (req, res) => {
    const id = req.query.id; // e.g. "nf_70155590" or "pv_..."
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    const isPv = id.startsWith('pv_');
    const sourceId = id.replace(/^(nf|pv)_/, '');
    const playlistUrl = isPv 
        ? `${NETMIRROR_BASE}/mobile/pv/playlist.php?id=${sourceId}` 
        : `${NETMIRROR_BASE}/mobile/playlist.php?id=${sourceId}`;

    try {
        const playRes = await axios.get(playlistUrl, {
            headers: {
                ...DEFAULT_HEADERS,
                'Referer': `${NETMIRROR_BASE}/`,
                'Cookie': 't_hash_t=active;'
            },
            timeout: 7000
        });

        if (Array.isArray(playRes.data) && playRes.data[0]?.sources?.length) {
            const rawFile = playRes.data[0].sources[0].file;
            const fullM3u8 = rawFile.startsWith('http') ? rawFile : `${NETMIRROR_BASE}${rawFile}`;

            // Fetch playlist content to verify tracks
            let audioTracks = [];
            try {
                const m3u8Res = await axios.get(fullM3u8, {
                    headers: { 'User-Agent': DEFAULT_HEADERS['User-Agent'], 'Referer': `${NETMIRROR_BASE}/` },
                    timeout: 4000
                });
                const m3u8Text = m3u8Res.data;
                const audioMatches = m3u8Text.matchAll(/#EXT-X-MEDIA:TYPE=AUDIO.*NAME="([^"]+)".*LANGUAGE="([^"]+)"/g);
                for (const match of audioMatches) {
                    audioTracks.push({ name: match[1], language: match[2] });
                }
            } catch (_) {}

            return res.json({
                success: true,
                streamUrl: fullM3u8,
                headers: {
                    'Referer': `${NETMIRROR_BASE}/`,
                    'User-Agent': DEFAULT_HEADERS['User-Agent']
                },
                audioTracks: audioTracks.length ? audioTracks : [
                    { name: 'Hindi', language: 'hin' },
                    { name: 'English (Original)', language: 'eng' }
                ]
            });
        }

        res.status(404).json({ success: false, error: 'No stream source found' });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// Proxy route for Web players blocked by CORS
app.get('/api/proxy/m3u8', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('Missing url');

    try {
        const response = await axios.get(targetUrl, {
            headers: {
                'User-Agent': DEFAULT_HEADERS['User-Agent'],
                'Referer': `${NETMIRROR_BASE}/`
            },
            responseType: 'text'
        });
        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.send(response.data);
    } catch (e) {
        res.status(500).send(e.message);
    }
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
    console.log(`Ayushflix Streaming Server running on port ${PORT}`);
});
