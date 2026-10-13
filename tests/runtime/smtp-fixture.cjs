// Disposable CI/local SMTP inbox. Both listeners bind only to loopback; never deploy with the WAR.
const net = require('node:net');
const http = require('node:http');

const inbox = new Map();
const smtpPort = Number(process.env.CRAVE_SMOKE_SMTP_PORT || 2525);
const inboxPort = Number(process.env.CRAVE_SMOKE_INBOX_PORT || 2526);
const key = (email, purpose) => email.toLowerCase() + ':' + purpose;
function capture(recipient, raw) {
    const separator = raw.indexOf('\r\n\r\n');
    const headers = raw.slice(0, separator);
    let body = raw.slice(separator + 4);
    if (/Content-Transfer-Encoding:\s*base64/i.test(headers)) body = Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8');
    else if (/Content-Transfer-Encoding:\s*quoted-printable/i.test(headers)) {
        body = Buffer.from(body.replace(/=\r\n/g, '').replace(/=([0-9a-f]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16))), 'latin1').toString('utf8');
    }
    const code = body.match(/\b[0-9]{6}\b/)?.[0];
    const purpose = body.includes('xác thực đăng ký') ? 'REGISTER' : body.includes('đặt lại mật khẩu') ? 'PASSWORD_RESET' : body.includes('đổi mật khẩu') ? 'PASSWORD_CHANGE' : null;
    if (!recipient || !code || !purpose) throw new Error('Invalid fixture message');
    inbox.set(key(recipient, purpose), {email: recipient, purpose, code, receivedAt: Date.now()});
}

const smtp = net.createServer(socket => {
    socket.setTimeout(10000, () => socket.destroy());
    socket.on('error', () => {});
    socket.write('220 localhost Crave test SMTP\r\n');
    let buffer = '', data = '', recipient = '', receiving = false;
    socket.on('data', chunk => {
        buffer += chunk.toString('utf8');
        if (buffer.length + data.length > 65536) return socket.destroy();
        while (buffer.includes('\r\n')) {
            const end = buffer.indexOf('\r\n'), line = buffer.slice(0, end);
            buffer = buffer.slice(end + 2);
            if (receiving) {
                if (line !== '.') { data += line.replace(/^\.\./, '.') + '\r\n'; continue; }
                receiving = false;
                try { capture(recipient, data); socket.write('250 Accepted\r\n'); }
                catch { socket.write('550 Invalid test message\r\n'); }
                data = ''; continue;
            }
            if (/^EHLO\b/i.test(line)) socket.write('250-localhost\r\n250-8BITMIME\r\n250 SIZE 65536\r\n');
            else if (/^HELO\b|^MAIL FROM:|^RSET\b|^NOOP\b/i.test(line)) socket.write('250 OK\r\n');
            else if (/^RCPT TO:/i.test(line)) { recipient = line.match(/<([^>]+)>/)?.[1] || ''; socket.write('250 OK\r\n'); }
            else if (/^DATA$/i.test(line)) { receiving = true; socket.write('354 End with a dot\r\n'); }
            else if (/^QUIT$/i.test(line)) { socket.end('221 Bye\r\n'); return; }
            else socket.write('502 Unsupported\r\n');
        }
    });
});
const server = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    if (url.pathname === '/health') return response.end('{"ready":true}');
    const email = url.searchParams.get('email') || '', purpose = url.searchParams.get('purpose') || '';
    if (url.pathname !== '/messages' || !email || !purpose) { response.statusCode = 400; return response.end('{}'); }
    if (request.method === 'DELETE') { inbox.delete(key(email, purpose)); return response.end('{}'); }
    if (request.method !== 'GET') { response.statusCode = 405; return response.end('{}'); }
    const message = inbox.get(key(email, purpose));
    response.statusCode = message ? 200 : 404;
    response.end(JSON.stringify(message || {}));
});
smtp.listen(smtpPort, '127.0.0.1');
server.listen(inboxPort, '127.0.0.1');
const stop = () => { smtp.close(); server.close(() => process.exit(0)); };
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
