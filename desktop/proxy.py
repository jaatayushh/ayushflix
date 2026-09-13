import http.server
import socketserver
import urllib.request
import urllib.parse
import urllib.error
import json
import os
import sys

from api import MovieBoxAPI

api_instance = MovieBoxAPI()

# Registry to store active streams with their signed cookies
# stream_id -> { "base_url": ..., "cookie": ..., "type": ... }
STREAM_REGISTRY = {}

class AyushflixHTTPHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        base_dir = getattr(sys, '_MEIPASS', os.path.dirname(os.path.abspath(__file__)))
        ui_path = os.path.join(base_dir, "ui")
        super().__init__(*args, directory=ui_path, **kwargs)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        qs = urllib.parse.parse_qs(parsed.query)

        # 1. API: /api/home
        if path == "/api/home":
            try:
                feed = api_instance.get_home_feed()
                self._send_json(200, feed)
            except Exception as e:
                self._send_json(500, {"error": str(e)})
            return

        # 2. API: /api/search?q=...
        elif path == "/api/search":
            q = qs.get("q", [""])[0]
            try:
                results = api_instance.search(q, per_page=20)
                self._send_json(200, {"results": results})
            except Exception as e:
                self._send_json(500, {"error": str(e)})
            return

        # 3. API: /api/details?id=...
        elif path == "/api/details":
            sid = qs.get("id", [""])[0]
            try:
                details = api_instance.get_details(sid)
                self._send_json(200, details)
            except Exception as e:
                self._send_json(500, {"error": str(e)})
            return

        # 4. API: /api/streams?id=...&se=0&ep=0&dub=...
        elif path == "/api/streams":
            sid = qs.get("id", [""])[0]
            se = int(qs.get("se", [0])[0])
            ep = int(qs.get("ep", [0])[0])
            dub = qs.get("dub", [None])[0]
            try:
                streams = api_instance.get_streams(sid, se=se, ep=ep, dub_id=dub)
                # Register each stream in the proxy registry
                registered_streams = []
                for s in streams:
                    raw_url = s["url"]
                    cookie = s.get("cookie", "")
                    stream_hash = hashlib_md5(raw_url)
                    base_url = raw_url.rsplit("/", 1)[0]
                    manifest_name = raw_url.rsplit("/", 1)[-1]

                    STREAM_REGISTRY[stream_hash] = {
                        "base_url": base_url,
                        "manifest_url": raw_url,
                        "cookie": cookie,
                        "type": s["type"]
                    }

                    proxy_stream_url = f"/proxy/stream/{stream_hash}/{manifest_name}"
                    registered_streams.append({
                        "url": proxy_stream_url,
                        "quality": s["quality"],
                        "type": s["type"],
                        "name": s["name"]
                    })
                self._send_json(200, {"streams": registered_streams})
            except Exception as e:
                self._send_json(500, {"error": str(e)})
            return

        # 5. Media Proxy: /proxy/stream/<stream_hash>/<filename>
        elif path.startswith("/proxy/stream/"):
            parts = path.replace("/proxy/stream/", "").split("/", 1)
            stream_hash = parts[0]
            filename = parts[1] if len(parts) > 1 else "index.mpd"

            stream_info = STREAM_REGISTRY.get(stream_hash)
            if not stream_info:
                self.send_error(404, "Stream not registered or expired")
                return

            upstream_url = f"{stream_info['base_url']}/{filename}"
            cookie = stream_info.get("cookie", "")

            # Forward Range request if present
            range_header = self.headers.get("Range")
            req_headers = {
                "User-Agent": "com.community.mbox.in/50020126 (Linux; U; Android 14; en_IN; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)",
                "Referer": "https://api3.aoneroom.com/"
            }
            if cookie:
                req_headers["Cookie"] = cookie
            if range_header:
                req_headers["Range"] = range_header

            req = urllib.request.Request(upstream_url, headers=req_headers)
            try:
                with urllib.request.urlopen(req, timeout=15) as upstream_resp:
                    code = upstream_resp.status
                    content_type = upstream_resp.headers.get("Content-Type", "application/octet-stream")
                    content_length = upstream_resp.headers.get("Content-Length")
                    content_range = upstream_resp.headers.get("Content-Range")
                    accept_ranges = upstream_resp.headers.get("Accept-Ranges", "bytes")

                    self.send_response(code)
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.send_header("Content-Type", content_type)
                    self.send_header("Accept-Ranges", accept_ranges)
                    if content_length:
                        self.send_header("Content-Length", content_length)
                    if content_range:
                        self.send_header("Content-Range", content_range)
                    self.end_headers()

                    # Stream data chunks
                    while True:
                        chunk = upstream_resp.read(65536)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
            except urllib.error.HTTPError as e:
                self.send_response(e.code)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(e.read())
            except Exception as e:
                self.send_error(500, f"Proxy error: {str(e)}")
            return

        # Fallback to serving static files (UI)
        super().do_GET()

    def _send_json(self, status: int, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

def hashlib_md5(s: str) -> str:
    import hashlib
    return hashlib.md5(s.encode("utf-8")).hexdigest()[:16]

def run_server(port: int = 38491):
    class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
        allow_reuse_address = True

    server = ThreadingTCPServer(("127.0.0.1", port), AyushflixHTTPHandler)
    print(f"Ayushflix server running on http://127.0.0.1:{port}")
    server.serve_forever()

if __name__ == "__main__":
    run_server()
