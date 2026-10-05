const axios = require('axios');
const crypto = require('crypto');
const CASTLE_BASE = 'https://api.hlowb.com';
const CASTLE_KEY_SUFFIX = Buffer.from('T!BgJB', 'utf8');

async function test() {
  const resKey = await axios.get(CASTLE_BASE + '/v0.1/system/getSecurityKey/1?channel=IndiaA&clientType=1&lang=en-US');
  let raw = resKey.data.data || resKey.data;
  const key = Buffer.concat([Buffer.from(raw, 'base64'), CASTLE_KEY_SUFFIX]).subarray(0, 16);

  function decrypt(cipher) {
    const b = Buffer.from(cipher.data || cipher, 'base64');
    const d = crypto.createDecipheriv('aes-128-cbc', key, key);
    d.setAutoPadding(true);
    let dec = Buffer.concat([d.update(b), d.final()]);
    let s = dec.toString('utf8').replace(/:\s*([0-9]{15,})/g, ': "$1"');
    return JSON.parse(s);
  }

  // Get Home items
  const homeRes = await axios.get(CASTLE_BASE + '/film-api/v0.1/category/home?channel=IndiaA&clientType=1&clientType=1&lang=en-US&locationId=1001&mode=1&packageName=com.external.castle&page=1', { headers: { 'User-Agent': 'okhttp/4.12.0' } });
  const home = decrypt(homeRes.data);
  const rows = home.data.rows;

  let tested = 0;
  for (const row of rows) {
    for (const item of (row.contents || []).slice(0, 2)) {
      if (!item.redirectId) continue;
      try {
        const dRes = await axios.get(CASTLE_BASE + '/film-api/v1.9.9/movie?channel=IndiaA&clientType=1&clientType=1&lang=en-US&movieId=' + item.redirectId, { headers: { 'User-Agent': 'okhttp/4.12.0' } });
        const detail = decrypt(dRes.data);
        const ep = detail.data?.episodes?.[0];
        const videoResList = ep?.videos?.map(v => v.resolutionDescription + ' (vip:' + v.premiumProPermission + ')') || [];
        console.log('Title:', detail.data?.title, 'Resolutions:', videoResList.join(', '));
        tested++;
        if (tested >= 8) return;
      } catch (_) {}
    }
  }
}
test();
