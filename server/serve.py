#!/usr/bin/env python3
"""
Minimal blocklist server for Sift. Standard library only.

Serves exactly one file (blocklist.txt) and nothing else — no directory listing, no other paths.
Optional bearer token: set SIFT_TOKEN in the environment and put the same token in the app.

    SIFT_TOKEN=changeme python3 serve.py --port 8088 --dir ./www
"""
import argparse
import gzip
import hmac
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ap = argparse.ArgumentParser()
ap.add_argument("--port", type=int, default=8088)
ap.add_argument("--bind", default="0.0.0.0")
ap.add_argument("--dir", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "www"))
args = ap.parse_args()
TOKEN = os.environ.get("SIFT_TOKEN", "")
PATH = os.path.join(args.dir, "blocklist.txt")


class Handler(BaseHTTPRequestHandler):
    server_version = "sift-blocklist"
    sys_version = ""

    def do_GET(self):
        if self.path.split("?")[0] not in ("/", "/blocklist.txt"):
            return self.send_error(404)
        if TOKEN:
            got = self.headers.get("Authorization", "")
            if not hmac.compare_digest(got.encode(), f"Bearer {TOKEN}".encode()):
                return self.send_error(401)
        try:
            with open(PATH, "rb") as f:
                body = f.read()
        except FileNotFoundError:
            return self.send_error(503, "blocklist not built yet — run build-blocklist.sh")
        gz = "gzip" in self.headers.get("Accept-Encoding", "")
        if gz:
            body = gzip.compress(body, 6)
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        if gz:
            self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *a):
        # Log the client and status only; there's nothing sensitive in the request anyway.
        print("%s - %s" % (self.address_string(), fmt % a), flush=True)


print(f"Serving {PATH} on http://{args.bind}:{args.port}/blocklist.txt" + (" (token required)" if TOKEN else ""))
ThreadingHTTPServer((args.bind, args.port), Handler).serve_forever()
