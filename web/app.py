#!/usr/bin/env python



import os
import posixpath
import http.server
import urllib.parse
import html
import shutil
import mimetypes
import re
import json
import threading
import queue
from io import BytesIO
from urllib.parse import urlparse
import http.client

__version__ = "0.x"
__all__ = ["SimpleHTTPRequestHandler"]
__author__ = ""
__home_page__ = ""


def is_downloadable(url):
    try:
        print(url)
        parsed = urlparse(url)
        conn_class = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
        conn = conn_class(parsed.netloc)
        conn.request("HEAD", parsed.path or "/")
        response = conn.getresponse()

        content_type = response.getheader("Content-Type", "")
        content_disposition = response.getheader("Content-Disposition", "")

        conn.close()

        if "attachment" in content_disposition.lower():
            return True
        if any(ct in content_type.lower() for ct in ['application/', 'image/', 'audio/', 'video/', 'octet-stream']):
            return True
        return False
    except Exception as e:
        print("Error:", e)
        return False

def write_file(path, data):
    try:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(data)   
        return True
    except Exception as e:
        print(f"Error writing file {path}: {e}")
        return False

def load_file(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return f.read()
    else:
        return ""

def validate_confg(data):
    """Validate streams in the given config data.

    Accepts either:
    - a list of groups where each group has a 'streams' list, or
    - a flat list of stream entries (old format)

    Updates each stream's 'available' boolean in-place and returns the updated data.
    """
    q = queue.Queue()

    def worker():
        while True:
            item = q.get()
            if item is None:
                break
            parent, stream = item
            url = stream.get("link", "")
            stream["available"] = is_downloadable(url)
            q.task_done()

    # Start worker threads
    threads = []
    for _ in range(10):
        t = threading.Thread(target=worker)
        t.daemon = True
        t.start()
        threads.append(t)

    # Enqueue streams
    for entry in data:
        if isinstance(entry, dict) and isinstance(entry.get('streams'), list):
            for stream in entry['streams']:
                q.put((entry, stream))
        else:
            # old format: entry itself is a stream-like dict
            q.put((None, entry))

    # Wait for completion
    q.join()

    # Stop workers
    for _ in threads:
        q.put(None)
    for t in threads:
        t.join()

    return data

def create_config(path):
    """Scan .m3u/.m3u8 files and produce grouped config entries.

    Grouping key: (name.trim(), logo, group). Each group contains:
    - logo, group, name
    - streams: list of {link, available, id} (type is determined by client)
    """
    groups = {}

    def detect_type(link):
        l = link.lower()
        if '.m3u8' in l or '.m3u' in l:
            return 'hls'
        if '.mpd' in l:
            return 'dash'
        if '.mp3' in l:
            return 'audio'
        return 'unknown'

    for root, _, files in os.walk(path):
        for file in files:
            if file.endswith('.m3u') or file.endswith('.m3u8'):
                m3u8_path = os.path.join(root, file)
                with open(m3u8_path, encoding="utf-8") as f:
                    lines = f.readlines()
                i = 0
                while i < len(lines):
                    line = lines[i]
                    if line.startswith("#EXTINF:"):
                        tvg_id = re.search(r'tvg-id="([^"]+)"', line)
                        tvg_logo = re.search(r'tvg-logo="([^"]+)"', line)
                        group = re.search(r'group-title="([^"]+)"', line)
                        name_match = re.split(r',', line, maxsplit=1)
                        name = name_match[1].strip() if len(name_match) > 1 else ""
                        # Find the next non-empty line that is a link
                        link = ""
                        j = i + 1
                        while j < len(lines):
                            link_candidate = lines[j].strip()
                            if link_candidate and (
                                (".m3u8" in link_candidate or ".m3u" in link_candidate or ".mp3" in link_candidate or ".mpd" in link_candidate)
                                and link_candidate.startswith("http")
                            ):
                                link = link_candidate
                                break
                            j += 1
                        if link:
                            logo_val = tvg_logo.group(1) if tvg_logo else ""
                            group_val = group.group(1) if group else ""
                            id_val = tvg_id.group(1) if tvg_id else ""
                            key = (name, logo_val, group_val)
                            if key not in groups:
                                groups[key] = {
                                    "logo": logo_val,
                                    "group": group_val,
                                    "name": name,
                                    "streams": []
                                }
                            stream = {
                                "link": link,
                                "available": False,
                                "id": id_val
                            }
                            groups[key]["streams"].append(stream)
                    i += 1

    # Deduplicate streams by link within each group
    for k, v in groups.items():
        seen = set()
        unique_streams = []
        for s in v["streams"]:
            if s["link"] in seen:
                continue
            seen.add(s["link"])
            unique_streams.append(s)
        v["streams"] = unique_streams

    return list(groups.values())

class SimpleHTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    server_version = "SimpleHTTPWithUpload/" + __version__

    def do_GET(self):
        self.handle_get_head()

    def do_HEAD(self):
        self.handle_get_head(head_only=True)

    def do_POST(self):
        success, info = self.process_upload()
        print(success, info, "by:", self.client_address)
        html_body = self.render_upload_result(success, info)
        self.send_response_page(html_body)

    def handle_get_head(self, head_only=False):
        f = self.get_response_file()
        if f and not head_only:
            self.copyfile(f, self.wfile)
        if f:
            f.close()

    def get_response_file(self):
        if self.path == "/api/streams":
            return self.api_streams_response()
        elif self.path == "/api/resync":
            return self.api_resync_response()
        return self.serve_static_or_directory()

    def api_streams_response(self):
        config_path = os.path.join(os.getcwd(), "json", "config.json")
        stream_path = os.path.join(os.getcwd(), "streams")
        data = load_file(config_path) or json.dumps(create_config(stream_path), ensure_ascii=False)

        encoded = data.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        return BytesIO(encoded)
    
    def api_resync_response(self):
        config_path = os.path.join(os.getcwd(), "json", "config.json")
        config = create_config(os.path.join(os.getcwd(), "streams"))
        data = json.dumps(validate_confg(config))
        write_file(config_path, data)

        encoded = data.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        return BytesIO(encoded)

    def serve_static_or_directory(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return self.handle_directory(path)
        try:
            f = open(path, 'rb')
        except IOError:
            self.send_error(404, "File not found")
            return None
        fs = os.fstat(f.fileno())
        self.send_response(200)
        self.send_header("Content-type", self.guess_type(path))
        self.send_header("Content-Length", str(fs.st_size))
        self.send_header("Last-Modified", self.date_time_string(fs.st_mtime))
        self.end_headers()
        return f

    def handle_directory(self, path):
        if not self.path.endswith('/'):
            self.send_response(301)
            self.send_header("Location", self.path + "/")
            self.end_headers()
            return None
        for index in ("index.html", "index.htm"):
            index_path = os.path.join(path, index)
            if os.path.exists(index_path):
                return open(index_path, 'rb')
        return self.render_directory_listing(path)

    def render_directory_listing(self, path):
        try:
            listing = sorted(os.listdir(path), key=str.lower)
        except OSError:
            self.send_error(404, "No permission to list directory")
            return None
        displaypath = html.escape(urllib.parse.unquote(self.path))
        entries = []
        for name in listing:
            fullname = os.path.join(path, name)
            suffix = "/" if os.path.isdir(fullname) else "@" if os.path.islink(fullname) else ""
            entries.append(f'<li><a href="{urllib.parse.quote(name)}">{html.escape(name)}{suffix}</a></li>')
        html_content = (
            f'<!DOCTYPE html><html><title>Directory listing for {displaypath}</title>'
            f'<body><h2>Directory listing for {displaypath}</h2><hr>'
            f'<form enctype="multipart/form-data" method="post">'
            f'<input name="file" type="file"/><input type="submit" value="upload"/></form><hr>'
            f'<ul>{"".join(entries)}</ul><hr></body></html>'
        )
        return BytesIO(html_content.encode("utf-8"))

    def process_upload(self):
        content_type = self.headers.get('content-type')
        if not content_type or "boundary=" not in content_type:
            return False, "Content-Type header missing boundary"
        boundary = content_type.split("boundary=")[1].encode()
        remainbytes = int(self.headers['content-length'])

        def readline():
            nonlocal remainbytes
            line = self.rfile.readline()
            remainbytes -= len(line)
            return line

        line = readline()
        if boundary not in line:
            return False, "Content does not start with boundary"

        line = readline()  # Content-Disposition
        fn = re.findall(r'Content-Disposition.*name="file"; filename="(.*)"', line.decode())
        if not fn:
            return False, "Filename not found"
        fn = os.path.join(self.translate_path(self.path), fn[0])
        while os.path.exists(fn):
            fn += "_"

        readline()  # Content-Type
        readline()  # Blank line

        try:
            out = open(fn, 'wb')
        except IOError:
            return False, "Cannot write file—check permissions?"

        preline = readline()
        while remainbytes > 0:
            line = readline()
            if boundary in line:
                out.write(preline.rstrip(b'\r\n'))
                out.close()
                return True, f"File '{fn}' uploaded successfully!"
            out.write(preline)
            preline = line

        return False, "Unexpected end of data"

    def render_upload_result(self, success, info):
        referer = self.headers.get('referer', '/')
        body = (
            b'<!DOCTYPE html><html><title>Upload Result</title><body><h2>Upload Result</h2><hr>'
            + (b"<strong>Success:</strong>" if success else b"<strong>Failed:</strong>")
            + info.encode()
            + f'<br><a href="{referer}">back</a>'.encode()
            + b'<hr><small>Powered By: bones7456, <a href="http://li2z.cn/?s=SimpleHTTPServerWithUpload">here</a>.</small></body></html>'
        )
        return body

    def send_response_page(self, body):
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


    def translate_path(self, path):
        # abandon query parameters
        path = path.split('?', 1)[0]
        path = path.split('#', 1)[0]
        path = posixpath.normpath(urllib.parse.unquote(path))
        words = [w for w in path.split('/') if w]
        path = os.getcwd()
        for word in words:
            _, word = os.path.splitdrive(word)
            _, word = os.path.split(word)
            if word in (os.curdir, os.pardir):
                continue
            path = os.path.join(path, word)
        return path

    def copyfile(self, source, outputfile):
        shutil.copyfileobj(source, outputfile)

    def guess_type(self, path):
        base, ext = posixpath.splitext(path)
        ext = ext.lower()
        return self.extensions_map.get(ext, self.extensions_map[''])

    if not mimetypes.inited:
        mimetypes.init()  # try to read system mime.types
    extensions_map = mimetypes.types_map.copy()
    extensions_map.update({
        '': 'application/octet-stream',  # Default
        '.py': 'text/plain',
        '.c': 'text/plain',
        '.h': 'text/plain',
    })


def exec(address="", port=80):
    httpd = http.server.HTTPServer((address, port), SimpleHTTPRequestHandler)
    httpd.serve_forever()

if __name__ == '__main__':
    try:
        exec()
    except Exception as e:
        print(e)
