#!/usr/bin/env python3

import os
import re
import json
import html
import shutil
import mimetypes
import posixpath
import urllib.parse
import http.server
from concurrent.futures import ThreadPoolExecutor

import requests
from bs4 import BeautifulSoup


HOST = ""
PORT = 80
ROOT = os.getcwd()
CONFIG = os.path.join(ROOT, "json", "config.json")
STREAMS = os.path.join(ROOT, "streams")


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(data)


def parse_program(content):
    soup = BeautifulSoup(content, "html.parser")
    channel = soup.find(id="channel_data")
    if not channel:
        return []

    result = []
    for prog in channel.find_all(
        "div",
        class_=lambda c: c and "prog" in c.split()
    ):
        result.append({
            "time": (
                prog.find("div", class_="time").get_text(strip=True)
                if prog.find("div", class_="time") else ""
            ),
            "title": (
                prog.find("div", class_="title").get_text(strip=True)
                if prog.find("div", class_="title") else ""
            ),
            "attributes": [
                x for x in prog.get("class", []) if x != "prog"
            ]
        })

    return result


def check_stream(url):
    try:
        r = requests.head(
            url,
            allow_redirects=True,
            timeout=(3, 5),
            headers={"User-Agent": "Mozilla/5.0"}
        )

        if r.ok:
            return True

        if r.status_code in (400, 403, 405, 501):
            r = requests.get(
                url,
                stream=True,
                timeout=(3, 5),
                headers={"User-Agent": "Mozilla/5.0"}
            )
            ok = r.ok
            r.close()
            return ok

    except requests.RequestException:
        pass

    return False


def validate(data):
    streams = []

    for entry in data:
        if isinstance(entry, dict) and isinstance(entry.get("streams"), list):
            streams.extend(entry["streams"])
        elif isinstance(entry, dict):
            streams.append(entry)

    with ThreadPoolExecutor(max_workers=10) as pool:
        for stream, result in zip(
            streams,
            pool.map(lambda s: check_stream(s.get("link", "")), streams)
        ):
            stream["available"] = result

    return data


def create_config(path):
    groups = {}

    for root, _, files in os.walk(path):
        for filename in files:
            if not filename.lower().endswith((".m3u", ".m3u8")):
                continue

            try:
                with open(
                    os.path.join(root, filename),
                    encoding="utf-8",
                    errors="ignore"
                ) as f:
                    lines = f.readlines()
            except OSError:
                continue

            for i, line in enumerate(lines):
                if not line.startswith("#EXTINF:"):
                    continue

                name_match = re.search(r",(.+)$", line)
                name = name_match.group(1).strip() if name_match else ""

                logo = re.search(r'tvg-logo="([^"]*)"', line)
                group = re.search(r'group-title="([^"]*)"', line)
                tvg_id = re.search(r'tvg-id="([^"]*)"', line)

                logo = logo.group(1) if logo else ""
                group = group.group(1) if group else ""
                tvg_id = tvg_id.group(1) if tvg_id else ""

                link = ""
                for next_line in lines[i + 1:i + 5]:
                    candidate = next_line.strip()
                    if candidate.startswith(("http://", "https://")):
                        link = candidate
                        break

                if not link:
                    continue

                key = (name, logo, group)

                if key not in groups:
                    groups[key] = {
                        "logo": logo,
                        "group": group,
                        "name": name,
                        "streams": []
                    }

                if not any(
                    s["link"] == link
                    for s in groups[key]["streams"]
                ):
                    groups[key]["streams"].append({
                        "link": link,
                        "available": False,
                        "id": tvg_id
                    })

    return list(groups.values())


class Handler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        self.handler()

    def do_HEAD(self):
        self.handler(True)

    def handler(self, head=False):
        parsed = urllib.parse.urlparse(self.path)

        try:
            if parsed.path == "/api/streams":
                return self.api_streams(head)

            if parsed.path == "/api/resync":
                return self.api_resync(head)

            if parsed.path == "/api/program":
                return self.api_program(
                    urllib.parse.parse_qs(parsed.query),
                    head
                )

            self.static(parsed.path, head)

        except BrokenPipeError:
            pass
        except Exception as e:
            print("[ERROR]", e)
            if not head:
                self.json({"error": str(e)}, status=500)

    def api_streams(self, head=False):
        data = read(CONFIG)

        if data:
            data = json.loads(data)
        else:
            data = create_config(STREAMS)

        self.json(data, head=head)

    def api_resync(self, head=False):
        data = validate(create_config(STREAMS))
        write(
            CONFIG,
            json.dumps(data, ensure_ascii=False, indent=2)
        )
        self.json(data, head=head)

    def api_program(self, query, head=False):
        urls = query.get("url")

        if not urls:
            return self.json({"error": "Missing url"}, status=400)

        if head:
            return self.json({}, head=True)

        try:
            r = requests.get(
                urllib.parse.unquote(urls[0]),
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=(3, 7)
            )
            r.raise_for_status()
            self.json(parse_program(r.text))
        except requests.RequestException as e:
            self.json({"error": str(e)}, 502)

    def json(self, data, *, status=200, head=False):
        body = json.dumps(data, ensure_ascii=False).encode()

        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()

        if not head:
            self.wfile.write(body)

    def static(self, url, head=False):
        path = self.translate(url)

        if os.path.isdir(path):
            index = os.path.join(path, "index.html")

            if os.path.exists(index):
                path = index
            else:
                return self.directory(path)

        if not os.path.isfile(path):
            return self.send_error(404, "File not found")

        size = os.path.getsize(path)

        self.send_response(200)
        self.send_header(
            "Content-Type",
            mimetypes.guess_type(path)[0]
            or "application/octet-stream"
        )
        self.send_header("Content-Length", str(size))
        self.end_headers()

        if not head:
            with open(path, "rb") as f:
                shutil.copyfileobj(f, self.wfile)

    def directory(self, path):
        items = []

        for name in sorted(os.listdir(path), key=str.lower):
            href = urllib.parse.quote(name)
            suffix = "/" if os.path.isdir(
                os.path.join(path, name)
            ) else ""

            items.append(
                f'<li><a href="{href}">'
                f'{html.escape(name)}{suffix}</a></li>'
            )

        body = f"""
<!doctype html>
<html>
<head><meta charset="utf-8"></head>
<body>
<h2>Directory</h2>
<form method="post" enctype="multipart/form-data">
<input name="file" type="file">
<input type="submit" value="Upload">
</form>
<ul>{"".join(items)}</ul>
</body>
</html>
"""

        body = body.encode()

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def translate(self, path):
        path = posixpath.normpath(
            urllib.parse.unquote(path).split("?", 1)[0]
        )

        result = ROOT

        for part in path.split("/"):
            if not part or part in (".", ".."):
                continue
            result = os.path.join(
                result,
                os.path.basename(part)
            )

        return result


class Server(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    print(f"Streams server: http://localhost:{PORT}")

    server = Server(
        (HOST, PORT),
        Handler
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()