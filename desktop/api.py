import urllib.request
import urllib.parse
import urllib.error
import json
import time
import hashlib
import hmac
import base64
from urllib.parse import urlparse, parse_qs

s1 = base64.b64decode("NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==").decode('utf-8')
SECRET_KEY = base64.b64decode(s1)

CLIENT_INFO = json.dumps({
    "package_name": "com.community.mbox.in",
    "version_name": "4.0.02.0831.03",
    "version_code": 50020126,
    "os": "android",
    "os_version": "14",
    "install_ch": "official",
    "device_id": "1234567890abcdef1234567890abcdef",
    "install_store": "official",
    "gaid": "1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d",
    "brand": "Google",
    "model": "Pixel 8",
    "system_language": "en",
    "net": "NETWORK_WIFI",
    "region": "IN",
    "timezone": "Asia/Calcutta",
    "sp_code": "",
    "X-Play-Mode": "1",
    "X-Idle-Data": "1",
    "X-Family-Mode": "0",
    "X-Content-Mode": "0"
})

BASE_URL = "https://api3.aoneroom.com"
RANKING_BASE_URL = "https://apig.inmoviebox.com"

class MovieBoxAPI:
    def __init__(self):
        self.cached_token = None
        self.token_fetch_time = 0

    def _md5(self, data: bytes) -> str:
        return hashlib.md5(data).hexdigest()

    def generate_x_client_token(self, timestamp: int) -> str:
        t_str = str(timestamp)
        return f"{t_str},{self._md5(t_str[::-1].encode('utf-8'))}"

    def generate_x_tr_signature(self, method: str, accept: str, content_type: str, url: str, body: str = None, timestamp: int = None) -> str:
        if timestamp is None:
            timestamp = int(time.time() * 1000)
        parsed = urlparse(url)
        path = parsed.path
        if parsed.query:
            q = parse_qs(parsed.query)
            canonical_query = "&".join(f"{k}={q[k][0]}" for k in sorted(q.keys()))
            canonical_url = f"{path}?{canonical_query}"
        else:
            canonical_url = path

        body_bytes = body.encode('utf-8') if body else None
        body_length = str(len(body_bytes)) if body_bytes else ""
        body_hash = self._md5(body_bytes[:102400] if len(body_bytes) > 102400 else body_bytes) if body_bytes else ""

        canonical = (
            f"{method.upper()}\n"
            f"{accept or ''}\n"
            f"{content_type or ''}\n"
            f"{body_length}\n"
            f"{timestamp}\n"
            f"{body_hash}\n"
            f"{canonical_url}"
        )
        mac = hmac.new(SECRET_KEY, canonical.encode('utf-8'), hashlib.md5).digest()
        sig_b64 = base64.b64encode(mac).decode('utf-8')
        return f"{timestamp}|2|{sig_b64}"

    def fetch_anonymous_token(self, force_refresh: bool = False) -> str:
        now = int(time.time() * 1000)
        if not force_refresh and self.cached_token and (now - self.token_fetch_time) < 3600000:
            return self.cached_token

        try:
            ranking_url = f"{RANKING_BASE_URL}/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1"
            headers = self.make_headers(ranking_url, method="GET", include_auth=False)
            req = urllib.request.Request(ranking_url, headers=headers)
            with urllib.request.urlopen(req, timeout=10) as resp:
                x_user = resp.headers.get("x-user")
                if x_user:
                    user_data = json.loads(x_user)
                    tok = user_data.get("token")
                    if tok:
                        self.cached_token = tok
                        self.token_fetch_time = now
                        return tok
        except Exception as e:
            print("Failed to fetch anonymous token:", e)
        return self.cached_token or ""

    def make_headers(self, url: str, method: str = "GET", body: str = None, include_auth: bool = True) -> dict:
        now = int(time.time() * 1000)
        content_type = "application/json; charset=utf-8" if body else "application/json"
        x_client_token = self.generate_x_client_token(now)
        x_tr_sig = self.generate_x_tr_signature(method, "application/json", content_type, url, body, now)

        headers = {
            "user-agent": "com.community.mbox.in/50020126 (Linux; U; Android 14; en_IN; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)",
            "accept": "application/json",
            "content-type": content_type,
            "connection": "keep-alive",
            "x-client-token": x_client_token,
            "x-tr-signature": x_tr_sig,
            "x-client-info": CLIENT_INFO,
            "x-client-status": "0"
        }
        if include_auth:
            token = self.fetch_anonymous_token()
            if token:
                headers["Authorization"] = f"Bearer {token}"
        return headers

    def _request(self, url: str, method: str = "GET", body: str = None) -> dict:
        headers = self.make_headers(url, method=method, body=body)
        data_bytes = body.encode('utf-8') if body else None
        req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            if e.code in (401, 441):
                # Token expired, refresh and retry once
                self.fetch_anonymous_token(force_refresh=True)
                headers = self.make_headers(url, method=method, body=body)
                req_retry = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
                with urllib.request.urlopen(req_retry, timeout=15) as resp_retry:
                    return json.loads(resp_retry.read().decode('utf-8'))
            raise

    def get_category(self, category_id: str, page: int = 1, per_page: int = 16) -> list:
        url = f"{BASE_URL}/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType={category_id}&page={page}&perPage={per_page}"
        data = self._request(url)
        items = data.get("data", {}).get("items") or data.get("data", {}).get("subjects") or []
        res = []
        for it in items:
            title = it.get("title", "").split("[")[0].strip()
            sid = it.get("subjectId")
            cover = (it.get("cover") or {}).get("url")
            stype = it.get("subjectType", 1)
            score = it.get("imdbRatingValue")
            if sid and title:
                res.append({
                    "id": sid,
                    "title": title,
                    "cover": cover,
                    "type": "series" if stype in (2, 7) else "movie",
                    "rating": score or "N/A"
                })
        return res

    def get_home_feed(self) -> dict:
        categories = [
            ("trending", "🌟 Trending Now", "4516404531735022304"),
            ("bollywood", "🎬 Bollywood Blockbusters", "414907768299210008"),
            ("south_indian", "🇮🇳 South Indian (Hindi Dubbed)", "3859721901924910512"),
            ("hollywood", "🌍 Hollywood Hits (Hindi Dubbed)", "8019599703232971616"),
            ("series", "📺 Trending Web Series", "4741626294545400336")
        ]
        result = []
        banner = None
        for key, name, cat_id in categories:
            try:
                items = self.get_category(cat_id, page=1, per_page=16)
                if items:
                    if not banner:
                        banner = items[0]
                    result.append({
                        "key": key,
                        "title": name,
                        "items": items
                    })
            except Exception as e:
                print(f"Error fetching category {name}:", e)
        return {
            "banner": banner,
            "rows": result
        }

    def search(self, query: str, page: int = 1, per_page: int = 18) -> list:
        url = f"{BASE_URL}/wefeed-mobile-bff/subject-api/search/v2"
        body = json.dumps({"page": page, "perPage": min(per_page, 20), "keyword": query})
        data = self._request(url, method="POST", body=body)
        results = data.get("data", {}).get("results", [])
        search_list = []
        for r in results:
            for it in r.get("subjects", []):
                title = it.get("title", "").split("[")[0].strip()
                sid = it.get("subjectId")
                cover = (it.get("cover") or {}).get("url")
                stype = it.get("subjectType", 1)
                score = it.get("imdbRatingValue")
                if sid and title:
                    search_list.append({
                        "id": sid,
                        "title": title,
                        "cover": cover,
                        "type": "series" if stype in (2, 7) else "movie",
                        "rating": score or "N/A"
                    })
        return search_list

    def get_details(self, subject_id: str) -> dict:
        url = f"{BASE_URL}/wefeed-mobile-bff/subject-api/get?subjectId={subject_id}"
        resp = self._request(url)
        data = resp.get("data", {})
        title = data.get("title", "").split("[")[0].strip()
        description = data.get("description", "")
        release_date = data.get("releaseDate", "")
        duration = data.get("duration", "")
        genre = data.get("genre", "")
        cover = (data.get("cover") or {}).get("url")
        rating = data.get("imdbRatingValue") or "N/A"
        stype = data.get("subjectType", 1)
        is_series = stype in (2, 7)

        dubs_raw = data.get("dubs", [])
        dubs = []
        for d in dubs_raw:
            dsid = d.get("subjectId")
            lan_name = d.get("lanName") or d.get("lan") or "Original"
            if dsid:
                dubs.append({"subjectId": dsid, "language": lan_name})

        # Sort so Hindi dubs are ALWAYS prioritized at index 0
        sorted_dubs = sorted(
            dubs,
            key=lambda x: 0 if ("hindi" in x["language"].lower() or x["language"].lower() == "hi") else 1
        )

        seasons_data = []
        if is_series:
            all_sids = [subject_id] + [d["subjectId"] for d in dubs if d["subjectId"] != subject_id]
            episode_map = {}
            for sid in all_sids[:3]:
                try:
                    s_url = f"{BASE_URL}/wefeed-mobile-bff/subject-api/season-info?subjectId={sid}"
                    s_data = self._request(s_url)
                    seasons = s_data.get("data", {}).get("seasons", [])
                    for s in seasons:
                        se_num = s.get("se", 1)
                        max_ep = s.get("maxEp", 1)
                        episode_map[se_num] = max(episode_map.get(se_num, 0), max_ep)
                except Exception:
                    pass

            for se_num in sorted(episode_map.keys()):
                seasons_data.append({
                    "season": se_num,
                    "episodes": list(range(1, episode_map[se_num] + 1))
                })

        return {
            "id": subject_id,
            "title": title,
            "description": description,
            "cover": cover,
            "release_date": release_date,
            "year": release_date[:4] if len(release_date) >= 4 else "",
            "duration": duration,
            "genre": genre,
            "rating": rating,
            "type": "series" if is_series else "movie",
            "dubs": sorted_dubs,
            "seasons": seasons_data
        }

    def extract_policy_resource(self, cookie: str) -> str:
        if not cookie or "CloudFront-Policy=" not in cookie:
            return None
        try:
            raw_b64 = cookie.split("CloudFront-Policy=")[1].split(";")[0]
            padded = raw_b64 + "=" * ((4 - len(raw_b64) % 4) % 4)
            normalized = padded.replace("-", "+").replace("_", "/")
            policy_json = base64.b64decode(normalized).decode("utf-8")
            policy_data = json.loads(policy_json)
            statement = policy_data.get("Statement", [{}])[0]
            res = statement.get("Resource")
            if res:
                return res.rstrip("/*").rstrip("/") + "/index.mpd"
        except Exception as e:
            print("Failed to decode CloudFront policy:", e)
        return None

    def get_streams(self, subject_id: str, se: int = 0, ep: int = 0, dub_id: str = None) -> list:
        target_sid = dub_id
        if not target_sid:
            try:
                details = self.get_details(subject_id)
                dubs = details.get("dubs", [])
                hindi_dubs = [d for d in dubs if "hindi" in d["language"].lower() or d["language"].lower() == "hi"]
                if hindi_dubs:
                    target_sid = hindi_dubs[0]["subjectId"]
                else:
                    target_sid = subject_id
            except Exception:
                target_sid = subject_id

        play_url = f"{BASE_URL}/wefeed-mobile-bff/subject-api/play-info?subjectId={target_sid}&se={se}&ep={ep}"
        data = self._request(play_url)
        streams_raw = data.get("data", {}).get("streams", [])

        results = []
        for s in streams_raw:
            raw_url = s.get("url", "")
            sign_cookie = s.get("signCookie", "")
            format_name = s.get("format", "")
            res = s.get("resolutions", "")
            name = s.get("name", "")

            # Extract real MPEG-DASH stream from signed CloudFront policy FIRST
            extracted_mpd = self.extract_policy_resource(sign_cookie)
            final_stream_url = extracted_mpd or raw_url

            if not final_stream_url:
                continue

            # Strict decoy video filtering
            if "b164fbfb4347792950bdfbfb563d39d9" in final_stream_url or "/other/2026/09/04/" in final_stream_url:
                continue

            quality_label = f"{res}p" if res and str(res).isdigit() else (res or "1080p")
            stream_type = "dash" if (".mpd" in final_stream_url or "dash" in format_name.lower()) else ("hls" if ".m3u8" in final_stream_url else "mp4")

            results.append({
                "url": final_stream_url,
                "cookie": sign_cookie,
                "quality": quality_label,
                "type": stream_type,
                "name": name or f"{quality_label} ({stream_type.upper()})"
            })

        if not results and target_sid != subject_id:
            return self.get_streams(subject_id, se=se, ep=ep, dub_id=subject_id)

        return results
