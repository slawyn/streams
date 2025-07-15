#!/usr/bin/env python3

"""Simple HTTP Server With Upload.

This module builds on BaseHTTPServer by implementing the standard GET
and HEAD requests in a fairly straightforward manner.

see: https://gist.github.com/UniIsland/3346170
"""


__version__ = "0.1"
__all__ = ["SimpleHTTPRequestHandler"]
__author__ = "bones7456"
__home_page__ = "http://li2z.cn/"

import os
import posixpath
import http.server
import urllib.parse
import html
import shutil
import mimetypes
import re
import json
from io import BytesIO


def load_file(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return f.read()
    else:
        return ""

def check_available_stream_files(path):
    entries = []
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
                        # Name is after the last comma
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
                        entry = {
                            "id": tvg_id.group(1) if tvg_id else "",
                            "logo": tvg_logo.group(1) if tvg_logo else "",
                            "group": group.group(1) if group else "",
                            "name": name,
                            "link": link
                        }
                        # Only add if all required fields are not empty
                        if all(entry[k] for k in ("id", "logo", "group", "name", "link")):
                            entries.append(entry)
                    i += 1
    return json.dumps(entries, ensure_ascii=False)

class SimpleHTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    server_version = "SimpleHTTPWithUpload/" + __version__

    def do_GET(self):
        """Serve a GET request."""
        f = self.send_head()
        if f:
            self.copyfile(f, self.wfile)
            f.close()

    def do_HEAD(self):
        """Serve a HEAD request."""
        f = self.send_head()
        if f:
            f.close()

    def do_POST(self):
        """Serve a POST request."""
        r, info = self.deal_post_data()
        print((r, info, "by: ", self.client_address))
        f = BytesIO()
        f.write(b'<!DOCTYPE html><html><title>Upload Result</title><body><h2>Upload Result</h2><hr>')
        f.write(b"<strong>Success:</strong>" if r else b"<strong>Failed:</strong>")
        f.write(info.encode())
        referer = self.headers.get('referer', '/')
        f.write(('<br><a href="%s">back</a>' % referer).encode())
        f.write(b'<hr><small>Powered By: bones7456, <a href="http://li2z.cn/?s=SimpleHTTPServerWithUpload">here</a>.</small></body></html>')
        length = f.tell()
        f.seek(0)
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.send_header("Content-Length", str(length))
        self.end_headers()
        self.copyfile(f, self.wfile)
        f.close()

    def deal_post_data(self):
        content_type = self.headers.get('content-type')
        if not content_type or "boundary=" not in content_type:
            return False, "Content-Type header doesn't contain boundary"
        boundary = content_type.split("boundary=")[1].encode()
        remainbytes = int(self.headers['content-length'])
        line = self.rfile.readline(); remainbytes -= len(line)
        if boundary not in line:
            return False, "Content NOT begin with boundary"
        line = self.rfile.readline(); remainbytes -= len(line)
        fn = re.findall(r'Content-Disposition.*name="file"; filename="(.*)"', line.decode())
        if not fn:
            return False, "Can't find out file name..."
        path = self.translate_path(self.path)
        fn = os.path.join(path, fn[0])
        self.rfile.readline(); remainbytes -= len(line)
        self.rfile.readline(); remainbytes -= len(line)
        try:
            out = open(fn, 'wb')
        except IOError:
            return False, "Can't create file to write, do you have permission to write?"
        preline = self.rfile.readline(); remainbytes -= len(preline)
        while remainbytes > 0:
            line = self.rfile.readline(); remainbytes -= len(line)
            if boundary in line:
                preline = preline.rstrip(b'\r\n')
                out.write(preline)
                out.close()
                return True, f"File '{fn}' upload success!"
            else:
                out.write(preline)
                preline = line
        out.close()
        return False, "Unexpected end of data."

    def send_head(self):
        if self.path == "/api/streams":
            data = load_file(os.path.join(os.getcwd(), "json", "config.json"))
            if not data:
                data = check_available_stream_files(os.path.join(os.getcwd(), "streams"))
            
            self.send_response(200)
            self.send_header("Content-type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data.encode("utf-8"))))
            self.end_headers()
            return BytesIO(data.encode("utf-8"))
        
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            if not self.path.endswith('/'):
                self.send_response(301)
                self.send_header("Location", self.path + "/")
                self.end_headers()
                return None
            for index in ("index.html", "index.htm"):
                index_path = os.path.join(path, index)
                if os.path.exists(index_path):
                    path = index_path
                    break
            else:
                return self.list_directory(path)
            
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

    def list_directory(self, path):
        """Helper to produce a directory listing (absent index.html).

        Return value is either a file object, or None (indicating an
        error).  In either case, the headers are sent, making the
        interface the same as for send_head().

        """
        try:
            listing = os.listdir(path)
        except OSError:
            self.send_error(404, "No permission to list directory")
            return None
        listing.sort(key=lambda a: a.lower())
        f = BytesIO()
        displaypath = html.escape(urllib.parse.unquote(self.path))
        f.write(f'<!DOCTYPE html><html><title>Directory listing for {displaypath}</title>'
                f'<body><h2>Directory listing for {displaypath}</h2><hr>'
                '<form ENCTYPE="multipart/form-data" method="post">'
                '<input name="file" type="file"/><input type="submit" value="upload"/></form><hr><ul>'.encode())
        for name in listing:
            fullname = os.path.join(path, name)
            displayname = linkname = name
            # Append / for directories or @ for symbolic links
            if os.path.isdir(fullname):
                displayname += "/"
                linkname += "/"
            if os.path.islink(fullname):
                displayname += "@"
                # Note: a link to a directory displays with @ and links with /
            f.write(f'<li><a href="{urllib.parse.quote(linkname)}">{html.escape(displayname)}</a>\n'.encode())
        f.write(b"</ul><hr></body></html>")
        length = f.tell()
        f.seek(0)
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.send_header("Content-Length", str(length))
        self.end_headers()
        return f

    def translate_path(self, path):
        """Translate a /-separated PATH to the local filename syntax.

        Components that mean special things to the local file system
        (e.g. drive or directory names) are ignored.  (XXX They should
        probably be diagnosed.)

        """
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
        """Copy all data between two file objects.

        The SOURCE argument is a file object open for reading
        (or anything with a read() method) and the DESTINATION
        argument is a file object open for writing (or
        anything with a write() method).

        The only reason for overriding this would be to change
        the block size or perhaps to replace newlines by CRLF
        -- note however that this the default server uses this
        to copy binary data as well.

        """
        shutil.copyfileobj(source, outputfile)

    def guess_type(self, path):
        """Guess the type of a file.

        Argument is a PATH (a filename).

        Return value is a string of the form type/subtype,
        usable for a MIME Content-type header.

        The default implementation looks the file's extension
        up in the table self.extensions_map, using application/octet-stream
        as a default; however it would be permissible (if
        slow) to look inside the data to make a better guess.

        """

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
