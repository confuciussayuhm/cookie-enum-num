#!/usr/bin/env python3
"""
Simple Cookie Testing Server for Burp Suite Cookie Analyzer
Sets 10 cookies, only 2 are required for successful access.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
from http.cookies import SimpleCookie
import random
import json
from urllib.parse import parse_qs, urlparse

# Configuration - which cookies are required (randomly chosen on startup)
ALL_COOKIES = [f"cookie_{i}" for i in range(10)]
REQUIRED_COOKIES = random.sample(ALL_COOKIES, 2)
print(f"\n{'='*60}")
print(f"🍪 Cookie Test Server Started")
print(f"{'='*60}")
print(f"Total cookies: 10 (cookie_0 through cookie_9)")
print(f"Required cookies: {', '.join(REQUIRED_COOKIES)}")
print(f"{'='*60}\n")


class CookieTestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        """Custom log format"""
        print(f"[{self.log_date_time_string()}] {format % args}")

    def do_GET(self):
        parsed_path = urlparse(self.path)
        path = parsed_path.path

        if path == '/':
            self.serve_home()
        elif path == '/set-cookies':
            self.set_all_cookies()
        elif path == '/test':
            self.test_cookies()
        elif path == '/api/test':
            self.api_test()
        elif path == '/reset':
            self.reset_required_cookies()
        else:
            self.send_error(404, "Not Found")

    def serve_home(self):
        """Serve the home page with instructions"""
        html = f"""
<!DOCTYPE html>
<html>
<head>
    <title>Cookie Test Server</title>
    <style>
        body {{
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
            background: #f5f5f5;
        }}
        .container {{
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }}
        h1 {{
            color: #333;
            border-bottom: 3px solid #4CAF50;
            padding-bottom: 10px;
        }}
        .info-box {{
            background: #e3f2fd;
            padding: 15px;
            border-left: 4px solid #2196F3;
            margin: 20px 0;
        }}
        .success {{
            background: #e8f5e9;
            border-left-color: #4CAF50;
        }}
        .warning {{
            background: #fff3e0;
            border-left-color: #ff9800;
        }}
        button {{
            background: #4CAF50;
            color: white;
            border: none;
            padding: 10px 20px;
            font-size: 16px;
            border-radius: 5px;
            cursor: pointer;
            margin: 5px;
        }}
        button:hover {{
            background: #45a049;
        }}
        .danger {{
            background: #f44336;
        }}
        .danger:hover {{
            background: #da190b;
        }}
        code {{
            background: #f5f5f5;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: monospace;
        }}
        .required {{
            color: #4CAF50;
            font-weight: bold;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🍪 Cookie Requirement Test Server</h1>

        <div class="info-box success">
            <h3>Currently Required Cookies:</h3>
            <p class="required">{', '.join(REQUIRED_COOKIES)}</p>
        </div>

        <div class="info-box">
            <h3>How to Use:</h3>
            <ol>
                <li>Click "Set All Cookies" to receive all 10 cookies</li>
                <li>In Burp Suite, right-click the request and select "Analyze Required Cookies"</li>
                <li>The analyzer should identify the 2 required cookies: <code>{REQUIRED_COOKIES[0]}</code> and <code>{REQUIRED_COOKIES[1]}</code></li>
            </ol>
        </div>

        <div class="info-box warning">
            <h3>Endpoints:</h3>
            <ul>
                <li><code>/set-cookies</code> - Sets all 10 cookies and redirects to /test</li>
                <li><code>/test</code> - HTML page that checks for required cookies</li>
                <li><code>/api/test</code> - JSON API that returns cookie validation status</li>
                <li><code>/reset</code> - Randomize which cookies are required (restart test)</li>
            </ul>
        </div>

        <div style="margin-top: 20px;">
            <button onclick="window.location.href='/set-cookies'">Set All Cookies</button>
            <button onclick="window.location.href='/test'">Test Cookies</button>
            <button onclick="window.location.href='/reset'" class="danger">Reset Required Cookies</button>
        </div>
    </div>
</body>
</html>
"""
        self.send_response(200)
        self.send_header('Content-Type', 'text/html')
        self.send_header('Content-Length', len(html.encode()))
        self.end_headers()
        self.wfile.write(html.encode())

    def set_all_cookies(self):
        """Set all 10 cookies and redirect to test page"""
        self.send_response(302)
        self.send_header('Location', '/test')

        # Set all 10 cookies
        for cookie_name in ALL_COOKIES:
            cookie_value = f"value_{cookie_name}_{random.randint(1000, 9999)}"
            self.send_header('Set-Cookie', f'{cookie_name}={cookie_value}; Path=/; SameSite=Lax')

        self.end_headers()
        print(f"✓ Set all 10 cookies")

    def test_cookies(self):
        """Test if required cookies are present"""
        cookies = self.parse_cookies()
        has_required = all(cookie in cookies for cookie in REQUIRED_COOKIES)
        missing = [c for c in REQUIRED_COOKIES if c not in cookies]
        present = list(cookies.keys())

        if has_required:
            # Success - all required cookies present
            # NOTE: Response body must be IDENTICAL regardless of extra cookies
            # This ensures the cookie analyzer can properly detect which cookies are required
            html = """
<!DOCTYPE html>
<html>
<head>
    <title>Test Result: SUCCESS</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
            background: #f5f5f5;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .success {
            background: #e8f5e9;
            padding: 20px;
            border-left: 4px solid #4CAF50;
            margin: 20px 0;
        }
        h1 { color: #4CAF50; }
    </style>
</head>
<body>
    <div class="container">
        <h1>✅ SUCCESS - Access Granted</h1>
        <div class="success">
            <p><strong>Authentication successful! All required cookies are present.</strong></p>
            <p>You have been granted access to the protected resource.</p>
        </div>
        <p><a href="/">← Back to Home</a></p>
    </div>
</body>
</html>
"""
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.send_header('Content-Length', len(html.encode()))
            self.end_headers()
            self.wfile.write(html.encode())
            print(f"✓ Test PASSED - All required cookies present (received {len(cookies)}: {', '.join(present)})")
        else:
            # Failure - missing required cookies
            html = f"""
<!DOCTYPE html>
<html>
<head>
    <title>Test Result: FAILED</title>
    <style>
        body {{
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
            background: #f5f5f5;
        }}
        .container {{
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }}
        .error {{
            background: #ffebee;
            padding: 20px;
            border-left: 4px solid #f44336;
            margin: 20px 0;
        }}
        h1 {{ color: #f44336; }}
        code {{
            background: #f5f5f5;
            padding: 2px 6px;
            border-radius: 3px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>❌ FAILED - Access Denied</h1>
        <div class="error">
            <p><strong>Missing required cookies!</strong></p>
            <p>Required: {', '.join(REQUIRED_COOKIES)}</p>
            <p>Missing: {', '.join(missing) if missing else 'None'}</p>
            <p>Received {len(cookies)} cookies: {', '.join(present) if present else 'None'}</p>
        </div>
        <p><a href="/set-cookies">Set All Cookies</a> | <a href="/">Back to Home</a></p>
    </div>
</body>
</html>
"""
            self.send_response(403)
            self.send_header('Content-Type', 'text/html')
            self.send_header('Content-Length', len(html.encode()))
            self.end_headers()
            self.wfile.write(html.encode())
            print(f"✗ Test FAILED - Missing cookies: {', '.join(missing)}")

    def api_test(self):
        """JSON API endpoint for testing"""
        cookies = self.parse_cookies()
        has_required = all(cookie in cookies for cookie in REQUIRED_COOKIES)

        result = {
            "success": has_required,
            "required_cookies": REQUIRED_COOKIES,
            "received_cookies": list(cookies.keys()),
            "missing_cookies": [c for c in REQUIRED_COOKIES if c not in cookies],
            "total_cookies": len(cookies)
        }

        response = json.dumps(result, indent=2)

        status = 200 if has_required else 403
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', len(response.encode()))
        self.end_headers()
        self.wfile.write(response.encode())

    def reset_required_cookies(self):
        """Randomize which cookies are required"""
        global REQUIRED_COOKIES
        REQUIRED_COOKIES = random.sample(ALL_COOKIES, 2)

        print(f"\n{'='*60}")
        print(f"🔄 Required cookies have been reset!")
        print(f"New required cookies: {', '.join(REQUIRED_COOKIES)}")
        print(f"{'='*60}\n")

        html = f"""
<!DOCTYPE html>
<html>
<head>
    <title>Cookies Reset</title>
    <meta http-equiv="refresh" content="3;url=/">
    <style>
        body {{
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
            background: #f5f5f5;
            text-align: center;
        }}
        .container {{
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }}
        h1 {{ color: #ff9800; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🔄 Required Cookies Reset</h1>
        <p>New required cookies: <strong>{', '.join(REQUIRED_COOKIES)}</strong></p>
        <p>Redirecting to home page...</p>
    </div>
</body>
</html>
"""
        # Clear all cookies
        self.send_response(200)
        self.send_header('Content-Type', 'text/html')
        for cookie_name in ALL_COOKIES:
            self.send_header('Set-Cookie', f'{cookie_name}=; Path=/; Max-Age=0')
        self.send_header('Content-Length', len(html.encode()))
        self.end_headers()
        self.wfile.write(html.encode())

    def parse_cookies(self):
        """Parse cookies from request headers"""
        cookie_header = self.headers.get('Cookie', '')
        cookies = {}
        if cookie_header:
            cookie = SimpleCookie()
            cookie.load(cookie_header)
            cookies = {key: morsel.value for key, morsel in cookie.items()}
        return cookies


def run_server(port=8000):
    """Start the test server"""
    server_address = ('', port)
    httpd = HTTPServer(server_address, CookieTestHandler)
    print(f"Server running at http://localhost:{port}/")
    print(f"Press Ctrl+C to stop\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\n🛑 Server stopped")


if __name__ == '__main__':
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run_server(port)
