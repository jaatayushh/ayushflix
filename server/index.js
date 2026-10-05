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
// MOVIEBOX (WITH FAMILY MODE ON) ENGINE
// -------------------------------------------------------------
const MOVIEBOX_BASE = 'https://api3.aoneroom.com';
const b1 = Buffer.from('NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==', 'base64').toString('utf8');
const MOVIEBOX_SECRET = Buffer.from(b1, 'base64');

const DEVICE_ID = crypto.randomBytes(16).toString('hex');

function md5(input) {
    return crypto.createHash('md5').update(input).digest('hex');
}

function generateXClientToken(timestamp = Date.now()) {
    const tsStr = String(timestamp);
    const reversed = tsStr.split('').reverse().join('');
    return `${tsStr},${md5(reversed)}`;
}

function getUserAgent() {
    return "com.community.mbox.in/50020126 (Linux; U; Android 14; en_IN; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)";
}

function getClientInfoJson() {
    return JSON.stringify({
        package_name: "com.community.mbox.in",
        version_name: "4.0.02.0831.03",
        version_code: 50020126,
        os: "android",
        os_version: "14",
        install_ch: "official",
        device_id: DEVICE_ID,
        install_store: "official",
        gaid: "1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d",
        brand: "Google",
        model: "Pixel 8",
        system_language: "en",
        net: "NETWORK_WIFI",
        region: "IN",
        timezone: "Asia/Calcutta",
        sp_code: "",
        "X-Play-Mode": "1",
        "X-Idle-Data": "1",
        "X-Family-Mode": "1",
        "X-Content-Mode": "1"
    });
}

function buildCanonicalString(method, accept, contentType, fullUrl, body, timestamp) {
    const parsed = new URL(fullUrl);
    const path = parsed.pathname;
    const params = Array.from(parsed.searchParams.keys()).sort();
    const query = params.map(k => `${k}=${parsed.searchParams.get(k)}`).join('&');
    const canonicalUrl = query ? `${path}?${query}` : path;

    const bodyBytes = body ? Buffer.from(body, 'utf8') : null;
    const bodyHash = bodyBytes ? md5(bodyBytes.subarray(0, 102400)) : '';
    const bodyLength = bodyBytes ? String(bodyBytes.length) : '';

    return `${method.toUpperCase()}\n${accept || ''}\n${contentType || ''}\n${bodyLength}\n${timestamp}\n${bodyHash}\n${canonicalUrl}`;
}

function generateXTrSignature(method, accept, contentType, fullUrl, body = null, timestamp = Date.now()) {
    const canonical = buildCanonicalString(method, accept, contentType, fullUrl, body, timestamp);
    const signature = crypto.createHmac('md5', MOVIEBOX_SECRET).update(Buffer.from(canonical, 'utf8')).digest('base64');
    return `${timestamp}|2|${signature}`;
}

// Adult Content / Family Mode Filter
const adultPattern = /(?:porn|porno|xxx|erotic|erotica|hentai|nsfw|nudity|onlyfans|softcore|hardcore|fetish|ullu|kooku|primeplay|hotshots|besharams|voovi|moodx|jav|playboy|lust\s*stories|rabbit\s*movies|hunters\s*app|chikooflix|redprime|sexy\s*scenes|adult|18\+|sex)/i;

function isAdultContent(title, genre, description) {
    if (title && adultPattern.test(title)) return true;
    if (genre && adultPattern.test(genre)) return true;
    if (description && adultPattern.test(description)) return true;
    return false;
}

let cachedGuestToken = null;
let lastTokenFetchMs = 0;

async function fetchAnonymousToken(forceRefresh = false) {
    const now = Date.now();
    if (!forceRefresh && cachedGuestToken && (now - lastTokenFetchMs < 3600000)) {
        return cachedGuestToken;
    }
    try {
        const rankingUrl = `${MOVIEBOX_BASE}/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1`;
        const sig = generateXTrSignature('GET', 'application/json', 'application/json', rankingUrl, null, now);
        const headers = {
            'user-agent': getUserAgent(),
            'accept': 'application/json',
            'content-type': 'application/json',
            'x-client-token': generateXClientToken(now),
            'x-tr-signature': sig,
            'x-client-info': getClientInfoJson(),
            'x-client-status': '0'
        };
        const res = await axios.get(rankingUrl, { headers, timeout: 8000 });
        if (res.headers['x-user']) {
            const xUser = JSON.parse(res.headers['x-user']);
            if (xUser.token) {
                cachedGuestToken = xUser.token;
                lastTokenFetchMs = now;
                return cachedGuestToken;
            }
        }
    } catch (e) {
        console.warn('Failed to fetch MovieBox token:', e.message);
    }
    return cachedGuestToken;
}

function extractPolicyResource(cookie) {
    try {
        if (!cookie) return null;
        const edgeMatch = cookie.match(/urlprefix=([^:]+)/);
        if (edgeMatch) {
            let rawB64 = edgeMatch[1];
            const rem = rawB64.length % 4;
            if (rem > 0) rawB64 += '='.repeat(4 - rem);
            return Buffer.from(rawB64, 'base64').toString('utf8');
        }
        const match = cookie.match(/CloudFront-Policy=([^;]+)/);
        if (match) {
            let rawB64 = match[1];
            const rem = rawB64.length % 4;
            if (rem > 0) rawB64 += '='.repeat(4 - rem);
            const normalized = rawB64.replace(/-/g, '+').replace(/_/g, '/');
            const json = Buffer.from(normalized, 'base64').toString('utf8');
            const root = JSON.parse(json);
            return root.Statement?.[0]?.Resource;
        }
    } catch (_) {}
    return null;
}

// -------------------------------------------------------------
// 1. HOME CATALOG (MovieBox Family Mode ON)
// -------------------------------------------------------------
app.get('/api/home', async (req, res) => {
    try {
        const rows = [];
        let hero = null;
        const token = await fetchAnonymousToken();

        const categories = [
            { id: '4516404531735022304', title: 'Trending in India' },
            { id: '414907768299210008', title: 'Bollywood & Hindi Hits' },
            { id: '3859721901924910512', title: 'South Indian (Hindi Dubbed)' },
            { id: '8019599703232971616', title: 'Hollywood Blockbusters' },
            { id: '4741626294545400336', title: 'Top Web Series' }
        ];

        for (const cat of categories) {
            try {
                const url = `${MOVIEBOX_BASE}/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${cat.id}&page=1&perPage=25`;
                const now = Date.now();
                const sig = generateXTrSignature('GET', 'application/json', 'application/json', url, null, now);
                const headers = {
                    'user-agent': getUserAgent(),
                    'accept': 'application/json',
                    'content-type': 'application/json',
                    'x-client-token': generateXClientToken(now),
                    'x-tr-signature': sig,
                    'x-client-info': getClientInfoJson(),
                    'x-client-status': '0'
                };
                if (token) headers['Authorization'] = `Bearer ${token}`;

                const rRes = await axios.get(url, { headers, timeout: 6000 });
                const rawItems = rRes.data?.data?.items || rRes.data?.data?.subjects || [];

                const validItems = rawItems
                    .map(item => {
                        const title = (item.title || '').replace(/\[.*?\]/g, '').trim();
                        if (!title || isAdultContent(title, item.genre, item.description)) return null;
                        const subjectId = item.subjectId;
                        if (!subjectId) return null;
                        return {
                            id: `mb_${subjectId}`,
                            sourceId: String(subjectId),
                            provider: 'moviebox',
                            title: title,
                            poster: item.cover?.url,
                            backdrop: item.cover?.url,
                            type: item.subjectType === 2 ? 'series' : 'movie',
                            score: item.imdbRatingValue || null,
                            year: (item.releaseDate || '').substring(0, 4),
                            overview: item.description || ''
                        };
                    })
                    .filter(Boolean);

                if (validItems.length > 0) {
                    rows.push({
                        title: cat.title,
                        provider: 'moviebox',
                        items: validItems
                    });
                    if (!hero) hero = validItems[0];
                }
            } catch (e) {
                console.warn(`MovieBox category ${cat.title} error:`, e.message);
            }
        }

        if (!hero && rows[0]?.items?.[0]) {
            hero = rows[0].items[0];
        }

        res.json({ success: true, hero, rows });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 2. SEARCH (MovieBox Family Mode ON)
// -------------------------------------------------------------
app.get('/api/search', async (req, res) => {
    const query = req.query.q || '';
    if (!query.trim()) return res.json({ success: true, results: [] });

    try {
        const results = [];
        const token = await fetchAnonymousToken();

        const searchUrl = `${MOVIEBOX_BASE}/wefeed-mobile-bff/subject-api/search/v2`;
        const body = JSON.stringify({ page: 1, perPage: 25, keyword: query.trim() });
        const now = Date.now();
        const cType = "application/json; charset=utf-8";
        const sig = generateXTrSignature('POST', 'application/json', cType, searchUrl, body, now);
        const headers = {
            'user-agent': getUserAgent(),
            'accept': 'application/json',
            'content-type': cType,
            'x-client-token': generateXClientToken(now),
            'x-tr-signature': sig,
            'x-client-info': getClientInfoJson(),
            'x-client-status': '0'
        };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const sRes = await axios.post(searchUrl, body, { headers, timeout: 8000 });
        const groups = sRes.data?.data?.items || [];

        groups.forEach(group => {
            const subjects = group.subjects || [];
            subjects.forEach(item => {
                const title = (item.title || '').replace(/\[.*?\]/g, '').trim();
                if (!title || isAdultContent(title, item.genre, item.description)) return;
                const subjectId = item.subjectId;
                if (!subjectId) return;

                results.push({
                    id: `mb_${subjectId}`,
                    sourceId: String(subjectId),
                    provider: 'MovieBox',
                    title: title,
                    poster: item.cover?.url,
                    backdrop: item.cover?.url,
                    type: item.subjectType === 2 ? 'series' : 'movie',
                    score: item.imdbRatingValue || null,
                    year: (item.releaseDate || '').substring(0, 4)
                });
            });
        });

        res.json({ success: true, count: results.length, results });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 3. DETAILS & EPISODES
// -------------------------------------------------------------
app.get('/api/details', async (req, res) => {
    const id = req.query.id; // e.g. "mb_8826677989518759008"
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    try {
        const subjectId = id.replace('mb_', '');
        const token = await fetchAnonymousToken();

        const detailUrl = `${MOVIEBOX_BASE}/wefeed-mobile-bff/subject-api/get?subjectId=${subjectId}`;
        const now = Date.now();
        const sig = generateXTrSignature('GET', 'application/json', 'application/json', detailUrl, null, now);
        const headers = {
            'user-agent': getUserAgent(),
            'accept': 'application/json',
            'content-type': 'application/json',
            'x-client-token': generateXClientToken(now),
            'x-tr-signature': sig,
            'x-client-info': getClientInfoJson(),
            'x-client-status': '0',
            'x-play-mode': '2'
        };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const dRes = await axios.get(detailUrl, { headers, timeout: 8000 });
        const data = dRes.data?.data;
        if (!data) return res.status(404).json({ success: false, error: 'Title not found' });

        const title = (data.title || '').replace(/\[.*?\]/g, '').trim();
        const isSeries = data.subjectType === 2;

        let episodes = [];
        if (isSeries) {
            // Check seasons/episodes
            const totalEps = data.epNum || data.seNum || 1;
            for (let i = 1; i <= Math.min(totalEps, 50); i++) {
                episodes.push({
                    id: String(i),
                    episodeNumber: i,
                    title: `Episode ${i}`,
                    cover: data.cover?.url
                });
            }
        } else {
            episodes.push({
                id: '0',
                episodeNumber: 1,
                title: 'Play Movie',
                cover: data.cover?.url
            });
        }

        res.json({
            success: true,
            id,
            sourceId: subjectId,
            provider: 'MovieBox',
            title,
            overview: data.description || `Watch ${title} in 1080p Full HD on Ayushflix.`,
            poster: data.cover?.url,
            backdrop: data.cover?.url,
            isSeries,
            score: data.imdbRatingValue || null,
            year: (data.releaseDate || '').substring(0, 4),
            episodes
        });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 4. STREAM RESOLVER (MovieBox 1080P Full HD Stream)
// -------------------------------------------------------------
app.get('/api/stream', async (req, res) => {
    const id = req.query.id; // e.g. "mb_8826677989518759008"
    if (!id) return res.status(400).json({ success: false, error: 'Missing id' });

    try {
        const subjectId = id.replace('mb_', '');
        const episode = req.query.episodeId || '0';
        const season = req.query.season || '0';
        const token = await fetchAnonymousToken();

        const playUrl = `${MOVIEBOX_BASE}/wefeed-mobile-bff/subject-api/play-info?subjectId=${subjectId}&se=${season}&ep=${episode}`;
        const now = Date.now();
        const sig = generateXTrSignature('GET', 'application/json', 'application/json', playUrl, null, now);
        const headers = {
            'user-agent': getUserAgent(),
            'accept': 'application/json',
            'content-type': 'application/json',
            'x-client-token': generateXClientToken(now),
            'x-tr-signature': sig,
            'x-client-info': getClientInfoJson(),
            'x-client-status': '0'
        };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const pRes = await axios.get(playUrl, { headers, timeout: 8000 });
        const playData = pRes.data?.data;
        const streams = playData?.streams || [];

        if (streams.length === 0) {
            return res.status(404).json({ success: false, error: 'No stream available for this title.' });
        }

        // Look for 1080p stream with signCookie
        let bestStream = streams.find(s => (s.resolutions || '').includes('1080')) || streams[0];
        const rawPrefix = extractPolicyResource(bestStream.signCookie);

        if (rawPrefix) {
            const prefix = rawPrefix.endsWith('/') ? rawPrefix : `${rawPrefix}/`;
            const mpdUrl = `${prefix}index.mpd`;
            const proxiedMpd = `/api/proxy/mpd?url=${encodeURIComponent(mpdUrl)}&cookie=${encodeURIComponent(bestStream.signCookie)}`;

            return res.json({
                success: true,
                streamUrl: proxiedMpd,
                rawStreamUrl: mpdUrl,
                type: 'dash',
                resolution: '1080p FHD',
                title: playData.title || '',
                tracks: [{ languageId: 1, name: 'Hindi Audio / Original', selected: true }]
            });
        }

        // Fallback to direct MP4
        if (bestStream.url) {
            return res.json({
                success: true,
                streamUrl: bestStream.url,
                type: 'video',
                resolution: '1080p FHD',
                tracks: [{ languageId: 1, name: 'Original Audio', selected: true }]
            });
        }

        res.status(404).json({ success: false, error: 'Stream could not be resolved.' });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

// -------------------------------------------------------------
// 5. DASH MPD PROXY (Proxies 1080P Manifest & Injects BaseURL)
// -------------------------------------------------------------
app.get('/api/proxy/mpd', async (req, res) => {
    const targetUrl = req.query.url;
    const cookie = req.query.cookie || '';
    if (!targetUrl) return res.status(400).send('Missing url');

    try {
        const response = await axios.get(targetUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                'Cookie': cookie
            },
            responseType: 'text',
            timeout: 8000
        });

        let mpdXml = response.data;
        // Calculate base URL directory
        const baseUrl = targetUrl.substring(0, targetUrl.lastIndexOf('/') + 1);

        // Inject <BaseURL> so all 1080p segments route through our CORS proxy (escape & to &amp; for valid XML)
        const proxyBase = `/api/proxy/dash?base=${encodeURIComponent(baseUrl)}&amp;cookie=${encodeURIComponent(cookie)}&amp;path=`;
        mpdXml = mpdXml.replace(/<MPD([^>]*)>/i, `<MPD$1>\n\t<BaseURL>${proxyBase}</BaseURL>`);

        res.setHeader('Content-Type', 'application/dash+xml');
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
        res.send(mpdXml);
    } catch (e) {
        res.status(500).send(e.message);
    }
});

// -------------------------------------------------------------
// 6. DASH VIDEO SEGMENT PROXY (Streams 1080P Chunks with CORS)
// -------------------------------------------------------------
app.get('/api/proxy/dash', async (req, res) => {
    const base = req.query.base || '';
    const cookie = req.query.cookie || '';
    const segmentPath = req.query.path || '';

    const targetUrl = base + segmentPath;
    if (!targetUrl) return res.status(400).send('Missing url');

    try {
        const segRes = await axios.get(targetUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                'Cookie': cookie
            },
            responseType: 'stream',
            timeout: 15000
        });

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
        res.setHeader('Content-Type', segRes.headers['content-type'] || 'video/mp4');
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
    console.log(`Ayushflix MovieBox Server running on port ${PORT}`);
});
