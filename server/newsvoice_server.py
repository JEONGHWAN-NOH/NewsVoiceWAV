#!/usr/bin/env python3
"""NewsVoice WAV minimal queue server. Bind only to localhost; put behind HTTPS proxy.
Environment: NV_TOKEN (>=12 chars), NV_QUEUE_DIR, NV_PORT, NV_HOST (127.0.0.1 default).
Client GET /api/jobs/next -> 200 {id,text}, or 204 no content.
Client POST /api/jobs/{id}/audio Content-Type audio/wav -> 201.
Admin POST /api/jobs Content-Type application/json {id,text} -> 201.
ALL endpoints require Authorization: Bearer <NV_TOKEN>.
"""
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

LOCK = threading.Lock()
ID_PATTERN = re.compile(r"[a-zA-Z0-9_-]{1,80}\Z")
MAX_SCRIPT_BYTES = 240_000
MAX_WAV_BYTES = 80_000_000
LEASE_SECONDS = 20 * 60


def env_config():
    token = os.environ.get("NV_TOKEN", "")
    if len(token) < 12:
        raise ValueError("Set NV_TOKEN environment variable to a random token of >= 12 characters")
    queue_dir = Path(os.environ.get("NV_QUEUE_DIR", "./newsvoice_jobs")).resolve()
    queue_dir.mkdir(parents=True, exist_ok=True)
    return token, queue_dir


def handler_factory(token, queue_dir):
    class Handler(BaseHTTPRequestHandler):
        def send_json(self, status, obj):
            content = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(content)

        def authorized(self):
            import hmac
            provided = self.headers.get("Authorization", "")
            if not hmac.compare_digest(provided, "Bearer " + token):
                self.send_json(401, {"error": "Unauthorized"})
                return False
            return True

        def do_GET(self):
            # Public, non-sensitive health endpoint used by the hosting provider.
            if self.path == "/healthz":
                return self.send_json(200, {"status": "ok"})
            if not self.authorized():
                return
            if self.path != "/api/jobs/next":
                return self.send_json(404, {"error": "Not found"})
            with LOCK:
                for f in sorted(queue_dir.glob("*.json")):
                    try:
                        job = json.loads(f.read_text(encoding="utf-8"))
                    except (ValueError, OSError):
                        continue
                    if job.get("state") == "done":
                        continue
                    if job.get("state") == "leased" and time.time() - job.get("leased_at", 0) < LEASE_SECONDS:
                        continue
                    job["state"] = "leased"
                    job["leased_at"] = time.time()
                    f.write_text(json.dumps(job, ensure_ascii=False, indent=2), encoding="utf-8")
                    return self.send_json(200, {"id": job["id"], "text": job["text"]})
            self.send_response(204)
            self.send_header("Cache-Control", "no-store")
            self.end_headers()

        def do_POST(self):
            if not self.authorized():
                return
            try:
                size = int(self.headers.get("Content-Length", "-1"))
            except ValueError:
                return self.send_json(400, {"error": "Invalid content length"})
            if self.path == "/api/jobs":
                if not (0 < size <= MAX_SCRIPT_BYTES):
                    return self.send_json(413, {"error": "Script too large"})
                if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                    return self.send_json(415, {"error": "JSON required"})
                try:
                    obj = json.loads(self.rfile.read(size))
                    job_id = obj["id"]
                    text = obj["text"]
                    if not isinstance(job_id, str) or not ID_PATTERN.fullmatch(job_id):
                        raise ValueError("Invalid job ID")
                    if not isinstance(text, str) or not text.strip() or len(text) > 60000:
                        raise ValueError("Invalid text")
                except (KeyError, ValueError, UnicodeDecodeError, TypeError) as exc:
                    return self.send_json(400, {"error": str(exc)})
                target = queue_dir / f"{job_id}.json"
                with LOCK:
                    if target.exists():
                        return self.send_json(409, {"error": "Job ID already exists"})
                    target.write_text(json.dumps({"id": job_id, "text": text, "state": "pending", "created_at": time.time()}, ensure_ascii=False, indent=2), encoding="utf-8")
                return self.send_json(201, {"id": job_id, "status": "pending"})
            match = re.fullmatch(r"/api/jobs/([a-zA-Z0-9_-]{1,80})/audio", self.path)
            if not match:
                return self.send_json(404, {"error": "Not found"})
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "audio/wav":
                return self.send_json(415, {"error": "WAV required"})
            if not (44 <= size <= MAX_WAV_BYTES):
                return self.send_json(413, {"error": "Invalid WAV size"})
            job_id = match.group(1)
            metadata_file = queue_dir / (job_id + ".json")
            with LOCK:
                if not metadata_file.exists():
                    return self.send_json(404, {"error": "Unknown job"})
            temp = queue_dir / (job_id + ".part")
            target = queue_dir / (job_id + ".wav")
            try:
                remaining = size
                with temp.open("wb") as out:
                    while remaining:
                        b = self.rfile.read(min(32768, remaining))
                        if not b:
                            raise ValueError("Truncated audio upload")
                        out.write(b)
                        remaining -= len(b)
                with temp.open("rb") as inp:
                    head = inp.read(12)
                if head[:4] != b"RIFF" or head[8:12] != b"WAVE":
                    raise ValueError("Invalid WAV header")
                with LOCK:
                    temp.replace(target)
                    job = json.loads(metadata_file.read_text(encoding="utf-8"))
                    job["state"] = "done"
                    job["completed_at"] = time.time()
                    metadata_file.write_text(json.dumps(job, ensure_ascii=False, indent=2), encoding="utf-8")
                return self.send_json(201, {"id": job_id, "status": "done", "file": target.name})
            except (OSError, ValueError) as exc:
                temp.unlink(missing_ok=True)
                return self.send_json(400, {"error": str(exc)})

        def log_message(self, fmt, *args):
            # Avoid logging Authorization headers or script/audio contents.
            print("[%s] %s" % (self.log_date_time_string(), fmt % args))
    return Handler


def main():
    token, queue_dir = env_config()
    host = os.environ.get("NV_HOST", "127.0.0.1")
    # Respect PORT supplied by Render or another platform, but retain local NV_PORT.
    port = int(os.environ.get("NV_PORT") or os.environ.get("PORT", "8787"))
    print(f"Queue dir: {queue_dir}; listening on {host}:{port}")
    print("Security: use HTTPS reverse proxy for network exposure. Do not expose this HTTP port publicly.")
    ThreadingHTTPServer((host, port), handler_factory(token, queue_dir)).serve_forever()


if __name__ == "__main__":
    main()
