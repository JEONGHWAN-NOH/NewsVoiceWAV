#!/usr/bin/env python3
"""Publish a verified news script to NewsVoice server over HTTPS.
Usage: NV_TOKEN=... python enqueue_news.py https://host.example 20261121_am /path/news_script.txt
"""
import json, os, sys, urllib.request
from pathlib import Path
if len(sys.argv) != 4:
    raise SystemExit("Usage: enqueue_news.py https://server.example JOB_ID news.txt")
server, job_id, filename = sys.argv[1:]
if not server.startswith("https://"):
    raise SystemExit("HTTPS URL is required")
token = os.environ.get("NV_TOKEN", "")
if len(token) < 12:
    raise SystemExit("NV_TOKEN must have at least 12 characters")
script = Path(filename).read_text(encoding="utf-8")
payload = json.dumps({"id": job_id, "text": script}, ensure_ascii=False).encode("utf-8")
request = urllib.request.Request(server.rstrip("/") + "/api/jobs", data=payload, method="POST", headers={
    "Authorization": "Bearer " + token,
    "Content-Type": "application/json",
})
with urllib.request.urlopen(request, timeout=30) as response:
    print(response.read().decode("utf-8"))
