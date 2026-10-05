const axios = require('axios');
const crypto = require('crypto');

const UPSTREAM_API_URL = 'https://api3.aoneroom.com';
const b1 = Buffer.from('NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==', 'base64').toString('utf8');
const MOVIEBOX_SECRET = Buffer.from(b1, 'base64');

function md5(input) { return crypto.createHash('md5').update(input).digest('hex'); }
function generateXClientToken(ts = Date.now()) { return `${ts},${md5(String(ts).split('').reverse().join(''))}`; }
function getUserAgent() { return "com.community.mbox.in/50020126 (Linux; U; Android 14; en_IN; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)"; }
function getClientInfoJson() {
    return JSON.stringify({
        package_name: "com.community.mbox.in", version_name: "4.0.02.0831.03", version_code: 50020126,
        os: "android", os_version: "14", install_ch: "official", device_id: "test1234567890",
        install_store: "official", gaid: "1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d", brand: "Google",
        model: "Pixel 8", system_language: "en", net: "NETWORK_WIFI", region: "IN", timezone: "Asia/Calcutta",
        sp_code: "", "X-Play-Mode": "1", "X-Idle-Data": "1", "X-Family-Mode": "1", "X-Content-Mode": "1"
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

async function check() {
    try {
        const now = Date.now();
        const rankUrl = `${UPSTREAM_API_URL}/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=5`;
        const sig = generateXTrSignature('GET', 'application/json', 'application/json', rankUrl, null, now);
        const headers = {
            'user-agent': getUserAgent(), 'accept': 'application/json', 'content-type': 'application/json',
            'x-client-token': generateXClientToken(now), 'x-tr-signature': sig,
            'x-client-info': getClientInfoJson(), 'x-client-status': '0'
        };
        const r = await axios.get(rankUrl, { headers });
        const token = JSON.parse(r.headers['x-user']).token;
        const items = r.data.data.items || [];
        const item = items[0];
        console.log('Testing subject:', item.title, item.subjectId);

        const playUrl = `${UPSTREAM_API_URL}/wefeed-mobile-bff/subject-api/play-info?subjectId=${item.subjectId}&se=0&ep=0`;
        const pSig = generateXTrSignature('GET', 'application/json', 'application/json', playUrl, null, Date.now());
        const pHeaders = {
            'user-agent': getUserAgent(), 'accept': 'application/json', 'content-type': 'application/json',
            'x-client-token': generateXClientToken(), 'x-tr-signature': pSig,
            'x-client-info': getClientInfoJson(), 'x-client-status': '0', 'Authorization': `Bearer ${token}`
        };
        const pRes = await axios.get(playUrl, { headers: pHeaders });
        console.log('Play info keys:', Object.keys(pRes.data.data));
        console.log('Streams detail:', JSON.stringify(pRes.data.data.streams, null, 2));
    } catch (e) {
        console.error('Error:', e.message);
    }
}
check();
