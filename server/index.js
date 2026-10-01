const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const axios = require('axios');
const path = require('path');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../tizen-wgt')));

// -------------------------------------------------------------
// CONFIG & DOMAINS
// -------------------------------------------------------------
const CASTLE_BASE = 'https://api.hlowb.com';
const CASTLE_KEY_SUFFIX = Buffer.from('T!BgJB', 'utf8');
const NETMIRROR_BASE = 'https://net52.cc';

const DEFAULT_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*'
};

const CASTLE_HEADERS = {
    'User-Agent': 'okhttp/4.12.0',
    'Accept': 'application/json'
};

// -------------------------------------------------------------
// CASTLE TV CRYPTO HELPERS
// -------------------------------------------------------------
let cachedCastleKey = null;
let lastKeyFetchTime = 0;

async function getCastleSecurityKey() {
    const now = Date.now();
    if (cachedCastleKey && (now - lastKeyFetchTime < 3600000)) {
        return cachedCastleKey;
    }
    try {
        const res = await axios.get(`${CASTLE_BASE}/v0.1/system/getSecurityKey/1?channel=IndiaA&clientType=1&lang=en-US`, {
            headers: CASTLE_HEADERS,
            timeout: 5000
        });
        let raw = res.data;
        if (typeof raw === 'object' && raw.data) raw = raw.data;
        const rawKey = Buffer.from(raw, 'base64');
        cachedCastleKey = Buffer.concat([rawKey, CASTLE_KEY_SUFFIX]).subarray(0, 16);
        lastKeyFetchTime = now;
        return cachedCastleKey;
    } catch (e) {
        console.warn('Failed to fetch dynamic Castle security key, using fallback:', e.message);
        return Buffer.concat([Buffer.from('ZkpBVG0qa2dmSg==', 'base64'), CASTLE_KEY_SUFFIX]).subarray(0, 16);
    }
}

function decryptCastleData(cipherBase64, key) {
    try {
        if (!cipherBase64) return null;
        if (typeof cipherBase64 === 'object') {
            if (cipherBase64.data && typeof cipherBase64.data === 'string') {
                cipherBase64 = cipherBase64.data;
            } else {
                return cipherBase64;
            }
        }
        const cipherBytes = Buffer.from(cipherBase64, 'base64');
        const decipher = crypto.createDecipheriv('aes-128-cbc', key, key);
        decipher.setAutoPadding(true);
        let decrypted = decipher.update(cipherBytes);
        decrypted = Buffer.concat([decrypted, decipher.final()]);
        let jsonStr = decrypted.toString('utf8');
        // Prevent IEEE-754 precision loss on 64-bit IDs by wrapping 15+ digit integers in quotes
        jsonStr = jsonStr.replace(/:\s*([0-9]{15,})/g, ': "$1"');
        return JSON.parse(jsonStr);
    } catch (e) {
        console.error('Castle decryption error:', e.message);
        return null;
    }
}

// -------------------------------------------------------------
// 1. HOME CATALOGS (Castle TV Popular Movies, Series, Marvel, etc.)
// -------------------------------------------------------------
app.get('/api/home', async (req, res) => {
    try {
        const rows = [];
        let hero = null;
        const key = await getCastleSecurityKey();

        // 1. Fetch Castle TV Home Catalog
        try {
            const homeUrl = `${CASTLE_BASE}/film-api/v0.1/category/home?channel=IndiaA&clientType=1&clientType=1&lang=en-US&locationId=1001&mode=1&packageName=com.external.castle&page=1`;
            const cRes = await axios.get(homeUrl, { headers: CASTLE_HEADERS, timeout: 7000 });
            const decrypted = decryptCastleData(cRes.data, key);

            if (decrypted && decrypted.data && decrypted.data.rows) {
                decrypted.data.rows.forEach(r => {
                    if (r.contents && r.contents.length > 0) {
                        const items = r.contents.map(item => ({
                            id: `ct_${item.redirectId}`,
                            sourceId: String(item.redirectId),
                            provider: 'castle',
                            title: item.title,
                            poster: item.coverImage,
                            backdrop: item.coverImage,
                            type: item.movieType === 1 ? 'series' : 'movie',
                            languages: item.languages || [],
                            score: item.score || null
                        }));

                        rows.push({
                            title: r.name,
                            provider: 'castle',
                            items: items
                        });
                    }
                });

                // Pick a hero from the first row (e.g. Trending / Popular)
                const firstRow = rows[0]?.items;
                if (firstRow && firstRow.length > 0) {
                    hero = firstRow[0];
                }
            }
        } catch (e) {
            console.warn('Error fetching Castle TV home:', e.message);
        }

        // 2. Fetch Netflix Mirror Trending
        try {
            const nfRes = await axios.get(`${NETMIRROR_BASE}/mobile/search.php?s=`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 5000
            });
            if (nfRes.data && nfRes.data.searchResult) {
                const items = nfRes.data.searchResult.slice(0, 25).map(item => ({
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
        } catch (_) {}

        // Fallback hero if needed
        if (!hero && rows[0]?.items?.[0]) {
            hero = rows[0].items[0];
        } else if (!hero) {
            hero = {
                id: 'ct_5704895401417728',
                sourceId: '5704895401417728',
                provider: 'castle',
                title: 'Avengers: Endgame',
                poster: 'https://img1.bhcxy.com/image/477e55a61c647644708c181882ac2b8d.jpg',
                backdrop: 'https://img1.bhcxy.com/image/477e55a61c647644708c181882ac2b8d.jpg',
                overview: 'The grave course of events set in motion by Thanos that wiped out half the universe and fractured the Avengers ranks compels the remaining Avengers to take one final stand.',
                type: 'movie',
                languages: ['Hindi', 'English', 'Tamil']
            };
        }

        res.json({ success: true, hero, rows });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 2. SEARCH (Castle TV + Netflix + Prime Video)
// -------------------------------------------------------------
app.get('/api/search', async (req, res) => {
    const query = req.query.q || '';
    if (!query.trim()) return res.json({ success: true, results: [] });

    try {
        const results = [];
        const key = await getCastleSecurityKey();

        // 1. Search Castle TV
        try {
            const searchUrl = `${CASTLE_BASE}/film-api/v1.1.0/movie/searchByKeyword?channel=IndiaA&clientType=1&clientType=1&keyword=${encodeURIComponent(query)}&lang=en-US&mode=1&packageName=com.external.castle&page=1&size=20`;
            const cRes = await axios.get(searchUrl, { headers: CASTLE_HEADERS, timeout: 6000 });
            const dec = decryptCastleData(cRes.data, key);
            if (dec && dec.data && dec.data.rows) {
                dec.data.rows.forEach(item => {
                    results.push({
                        id: `ct_${item.id}`,
                        sourceId: String(item.id),
                        provider: 'Castle TV',
                        title: item.title,
                        poster: item.coverVerticalImage || item.coverHorizontalImage,
                        backdrop: item.coverHorizontalImage,
                        type: item.movieType === 1 ? 'series' : 'movie',
                        languages: item.languages || [],
                        score: item.score || null
                    });
                });
            }
        } catch (e) {
            console.warn('Castle search failed:', e.message);
        }

        // 2. Search Netflix Mirror
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

        res.json({ success: true, count: results.length, results });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 3. MOVIE & SERIES DETAILS / EPISODES
// -------------------------------------------------------------
app.get('/api/details', async (req, res) => {
    const id = req.query.id; // e.g. "ct_5704895401417728" or "nf_..."
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    try {
        if (id.startsWith('ct_')) {
            const movieId = id.replace('ct_', '');
            const key = await getCastleSecurityKey();
            const detailUrl = `${CASTLE_BASE}/film-api/v1.9.9/movie?channel=IndiaA&clientType=1&clientType=1&lang=en-US&movieId=${movieId}`;
            const detailRes = await axios.get(detailUrl, { headers: CASTLE_HEADERS, timeout: 6000 });
            const decrypted = decryptCastleData(detailRes.data, key);

            if (!decrypted || !decrypted.data) {
                return res.status(404).json({ success: false, error: 'Title details not found' });
            }

            const data = decrypted.data;
            const isSeries = data.movieType === 1;

            const episodes = (data.episodes || []).map((ep, idx) => ({
                id: String(ep.id),
                episodeNumber: ep.number || idx + 1,
                title: ep.title ? `Episode ${ep.number || idx + 1}: ${ep.title}` : `Episode ${idx + 1}`,
                cover: ep.coverImage || data.coverHorizontalImage,
                tracks: (ep.tracks || []).map(t => ({
                    languageId: t.languageId,
                    name: t.languageName,
                    isDefault: t.isDefault
                }))
            }));

            return res.json({
                success: true,
                id,
                sourceId: movieId,
                provider: 'Castle TV',
                title: data.title,
                overview: data.briefIntroduction || `Watch ${data.title} in HD with multi-language audio.`,
                poster: data.coverVerticalImage || data.coverHorizontalImage,
                backdrop: data.coverHorizontalImage,
                isSeries,
                score: data.score,
                languages: data.audioTags || data.languages || [],
                episodes
            });
        }

        // Netmirror / Prime fallback
        const isPv = id.startsWith('pv_');
        const sourceId = id.replace(/^(nf|pv)_/, '');
        const endpointPrefix = isPv ? '/mobile/pv' : '/mobile';

        let episodes = [{ id: sourceId, episodeNumber: 1, title: 'Play Movie' }];
        let isSeries = false;

        try {
            const epRes = await axios.get(`${NETMIRROR_BASE}${endpointPrefix}/episodes.php?s=${sourceId}`, {
                headers: { ...DEFAULT_HEADERS, 'Referer': `${NETMIRROR_BASE}/` },
                timeout: 5000
            });
            if (epRes.data && epRes.data.nextPageSeason) {
                isSeries = true;
                episodes = [
                    { id: sourceId, episodeNumber: 1, title: 'Episode 1' },
                    { id: sourceId, episodeNumber: 2, title: 'Episode 2' },
                    { id: sourceId, episodeNumber: 3, title: 'Episode 3' },
                    { id: sourceId, episodeNumber: 4, title: 'Episode 4' }
                ];
            }
        } catch (_) {}

        res.json({
            success: true,
            id,
            sourceId,
            provider: isPv ? 'Prime Video' : 'Netflix',
            title: 'Stream',
            isSeries,
            poster: `https://imgcdn.kim/poster/v/${sourceId}.jpg`,
            backdrop: `https://imgcdn.kim/poster/v/${sourceId}.jpg`,
            episodes
        });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 4. STREAM RESOLVER (Multi-Audio, High-Speed HLS)
// -------------------------------------------------------------
app.get('/api/stream', async (req, res) => {
    const id = req.query.id; // e.g. "ct_5704895401417728"
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    try {
        if (id.startsWith('ct_')) {
            const movieId = id.replace('ct_', '');
            const key = await getCastleSecurityKey();

            // 1. Get Details to extract episodeId and tracks
            const detailUrl = `${CASTLE_BASE}/film-api/v1.9.9/movie?channel=IndiaA&clientType=1&clientType=1&lang=en-US&movieId=${movieId}`;
            const detailRes = await axios.get(detailUrl, { headers: CASTLE_HEADERS, timeout: 6000 });
            const detail = decryptCastleData(detailRes.data, key);

            if (!detail || !detail.data || !detail.data.episodes || detail.data.episodes.length === 0) {
                return res.status(404).json({ success: false, error: 'No episodes or streams available for this title.' });
            }

            const requestedEpId = req.query.episodeId;
            const episode = requestedEpId
                ? detail.data.episodes.find(e => String(e.id) === String(requestedEpId)) || detail.data.episodes[0]
                : detail.data.episodes[0];

            // Determine language track (Default to Hindi if available, else English or first track)
            const tracks = episode.tracks || [];
            let chosenTrack = null;
            if (req.query.languageId) {
                chosenTrack = tracks.find(t => String(t.languageId) === String(req.query.languageId));
            }
            if (!chosenTrack) {
                chosenTrack = tracks.find(t => (t.languageName || '').toLowerCase().includes('hindi'))
                    || tracks.find(t => (t.languageName || '').toLowerCase().includes('english'))
                    || tracks[0];
            }
            const languageId = chosenTrack ? chosenTrack.languageId : 1018;

            // 2. Call getVideo2
            const videoUrl = `${CASTLE_BASE}/film-api/v2.0.1/movie/getVideo2?clientType=1&packageName=com.external.castle&channel=IndiaA&lang=en-US`;
            const payload = {
                mode: "1",
                appMarket: "GuanWang",
                clientType: "1",
                woolUser: "false",
                apkSignKey: "ED0955EB04E67A1D9F3305B95454FED485261475",
                androidVersion: "13",
                movieId: String(movieId),
                episodeId: String(episode.id),
                languageId: String(languageId),
                packageName: "com.external.castle"
            };

            const videoRes = await axios.post(videoUrl, payload, {
                headers: { ...CASTLE_HEADERS, 'Content-Type': 'application/json' },
                timeout: 7000
            });
            const videoData = decryptCastleData(videoRes.data, key);

            if (videoData && videoData.data && videoData.data.videoUrl) {
                const rawUrl = videoData.data.videoUrl;
                const proxiedUrl = `/api/proxy/m3u8?url=${encodeURIComponent(rawUrl)}`;

                return res.json({
                    success: true,
                    streamUrl: proxiedUrl,
                    rawStreamUrl: rawUrl,
                    title: detail.data.title,
                    tracks: tracks.map(t => ({
                        languageId: t.languageId,
                        name: t.languageName,
                        selected: t.languageId === languageId
                    })),
                    subtitles: (videoData.data.subtitles || []).map(s => ({
                        lang: s.title || s.abbreviate,
                        url: s.url
                    }))
                });
            } else {
                return res.status(500).json({ success: false, error: 'Video source temporarily unavailable.' });
            }
        }

        // Netmirror stream resolution
        const isPv = id.startsWith('pv_');
        const sourceId = id.replace(/^(nf|pv)_/, '');
        const playlistUrl = isPv 
            ? `${NETMIRROR_BASE}/mobile/pv/playlist.php?id=${sourceId}` 
            : `${NETMIRROR_BASE}/mobile/playlist.php?id=${sourceId}`;

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
            const proxiedUrl = `/api/proxy/m3u8?url=${encodeURIComponent(fullM3u8)}`;

            return res.json({
                success: true,
                streamUrl: proxiedUrl,
                rawStreamUrl: fullM3u8,
                tracks: [
                    { languageId: 1, name: 'Hindi', selected: true },
                    { languageId: 2, name: 'English', selected: false }
                ]
            });
        }

        res.status(404).json({ success: false, error: 'No stream source found' });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 5. M3U8 STREAM REWRITING PROXY (Eliminates Browser CORS)
// -------------------------------------------------------------
app.get('/api/proxy/m3u8', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('Missing url');

    try {
        const response = await axios.get(targetUrl, {
            headers: {
                'User-Agent': DEFAULT_HEADERS['User-Agent'],
                'Referer': targetUrl.includes('klnwm.com') ? 'https://api.hlowb.com/' : `${NETMIRROR_BASE}/`
            },
            responseType: 'text',
            timeout: 8000
        });

        const lines = response.data.split('\n');
        const rewritten = lines.map(line => {
            const trimmed = line.trim();
            if (!trimmed) return line;

            // Rewrite URI in tags (e.g. #EXT-X-KEY or #EXT-X-MEDIA)
            if (trimmed.startsWith('#EXT-X-KEY') || trimmed.startsWith('#EXT-X-MEDIA')) {
                return trimmed.replace(/URI="([^"]+)"/, (match, uri) => {
                    try {
                        const absUri = new URL(uri, targetUrl).href;
                        const isPlaylist = absUri.includes('.m3u8');
                        const proxyPath = isPlaylist ? '/api/proxy/m3u8' : '/api/proxy/segment';
                        return `URI="${proxyPath}?url=${encodeURIComponent(absUri)}"`;
                    } catch (_) {
                        return match;
                    }
                });
            }

            // Keep comments & directives
            if (trimmed.startsWith('#')) return line;

            // Rewrite video segments and nested playlists
            try {
                const absUrl = new URL(trimmed, targetUrl).href;
                const isChildPlaylist = absUrl.includes('.m3u8');
                const proxyPath = isChildPlaylist ? '/api/proxy/m3u8' : '/api/proxy/segment';
                return `${proxyPath}?url=${encodeURIComponent(absUrl)}`;
            } catch (_) {
                return line;
            }
        });

        res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
        res.setHeader('Cache-Control', 'no-cache');
        res.send(rewritten.join('\n'));
    } catch (e) {
        console.error('Error proxying m3u8:', e.message);
        res.status(500).send(e.message);
    }
});

// -------------------------------------------------------------
// 6. VIDEO SEGMENT PROXY (Delivers TS video chunks with CORS)
// -------------------------------------------------------------
app.get('/api/proxy/segment', async (req, res) => {
    const targetUrl = req.query.url;
    if (!targetUrl) return res.status(400).send('Missing segment url');

    try {
        const segRes = await axios.get(targetUrl, {
            headers: {
                'User-Agent': DEFAULT_HEADERS['User-Agent'],
                'Referer': targetUrl.includes('klnwm.com') ? 'https://api.hlowb.com/' : `${NETMIRROR_BASE}/`
            },
            responseType: 'stream',
            timeout: 15000
        });

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
        res.setHeader('Content-Type', segRes.headers['content-type'] || 'video/mp2t');
        if (segRes.headers['content-length']) {
            res.setHeader('Content-Length', segRes.headers['content-length']);
        }

        segRes.data.pipe(res);
    } catch (e) {
        res.status(500).send(e.message);
    }
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
    console.log(`Ayushflix Streaming Server running on port ${PORT}`);
});
