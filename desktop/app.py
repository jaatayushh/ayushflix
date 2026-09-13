import sys
import os
import socket
import threading
import time
import webview

# Ensure current directory is in sys.path
BASE_DIR = getattr(sys, '_MEIPASS', os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from proxy import run_server

def find_free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]

def main():
    port = find_free_port()
    
    # Start backend HTTP streaming server in background
    server_thread = threading.Thread(target=run_server, args=(port,), daemon=True)
    server_thread.start()

    # Wait for server to bind
    time.sleep(0.3)

    icon_path = os.path.join(BASE_DIR, "icon.ico")
    if not os.path.exists(icon_path):
        icon_path = None

    url = f"http://127.0.0.1:{port}/"

    # Create pywebview window
    window = webview.create_window(
        title="Ayushflix",
        url=url,
        width=1280,
        height=820,
        min_size=(960, 600),
        background_color="#141414"
    )

    # Start application loop (Edge Chromium engine)
    webview.start(debug=False)

if __name__ == "__main__":
    main()
