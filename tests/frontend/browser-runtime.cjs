// Shared Chrome/Edge driver for smoke tests against the running Servlet application.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8081/crave').replace(/\/$/, '');
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const assert = (condition, message) => { if (!condition) throw new Error(message); };

async function launchBrowser() {
    const chrome = process.env.CHROME_PATH || [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
        '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
    ].find(candidate => fs.existsSync(candidate));
    assert(chrome, 'Set CHROME_PATH to a Chrome or Edge executable.');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-servlet-browser-'));
    const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu',
        '--disable-background-networking', '--disable-extensions', '--no-first-run',
        '--no-default-browser-check', '--user-data-dir=' + directory, '--remote-debugging-port=0', 'about:blank'],
        {windowsHide:true, stdio:'ignore'});
    let startupError;
    child.on('error', error => { startupError = error; });
    let socket;
    for (let attempt = 0; attempt < 200 && !socket; attempt++) {
        if (startupError) throw startupError;
        const portFile = path.join(directory, 'DevToolsActivePort');
        if (fs.existsSync(portFile)) {
            try {
                const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
                const targets = await fetch('http://127.0.0.1:' + port + '/json').then(response => response.json());
                const target = targets.find(candidate => candidate.type === 'page');
                if (target) {
                    socket = new WebSocket(target.webSocketDebuggerUrl);
                    await new Promise((resolve, reject) => {
                        socket.addEventListener('open', resolve, {once:true});
                        socket.addEventListener('error', reject, {once:true});
                    });
                }
            } catch (_) { socket = undefined; }
        }
        if (!socket) await pause(50);
    }
    if (!socket) { child.kill(); throw new Error('Browser startup timed out.'); }
    let sequence = 0;
    const pending = new Map();
    const requests = [];
    socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id) { pending.get(message.id)?.(message); pending.delete(message.id); }
        if (message.method === 'Network.requestWillBeSent') {
            const request = message.params.request;
            requests.push({url:request.url, method:request.method, headers:request.headers});
        }
    });
    function send(method, params = {}) {
        return new Promise((resolve, reject) => {
            const id = ++sequence;
            const timer = setTimeout(() => { pending.delete(id); reject(new Error('Browser command timed out: ' + method)); }, 15000);
            pending.set(id, message => { clearTimeout(timer); resolve(message); });
            socket.send(JSON.stringify({id, method, params}));
        });
    }
    async function evaluate(expression) {
        const message = await send('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true});
        if (message.error) throw new Error(message.error.message);
        if (message.result.exceptionDetails) throw new Error(message.result.exceptionDetails.text + ': ' +
            (message.result.exceptionDetails.exception?.description || ''));
        return message.result.result?.value;
    }
    async function waitFor(expression, message) {
        const deadline = Date.now() + 20000;
        while (Date.now() < deadline) {
            try { if (await evaluate(expression)) return; } catch (_) { /* A navigation can replace the JS context. */ }
            await pause(50);
        }
        throw new Error(message || 'Browser condition timed out.');
    }
    await send('Page.enable');
    await send('Network.enable');
    await send('Network.setBlockedURLs', {urls:['https://fonts.googleapis.com/*', 'https://fonts.gstatic.com/*']});
    return {
        base, requests, evaluate, waitFor,
        async navigate(route) {
            await send('Page.navigate', {url:base + route});
            await waitFor('location.href === ' + JSON.stringify(base + route) + ' && document.readyState === "complete"',
                'Page did not load: ' + route);
        },
        assertNoBrowserApi() {
            for (const request of requests.filter(request => request.url.startsWith(base + '/'))) {
                assert(!new URL(request.url).pathname.includes('/api/'), 'Browser still uses an API route.');
                const contentType = Object.entries(request.headers).find(([key]) => key.toLowerCase() === 'content-type')?.[1] || '';
                assert(!contentType.includes('application/json'), 'Browser still submits JSON.');
            }
        },
        async close() {
            try { await send('Browser.close'); } catch (_) { }
            socket.close();
            await Promise.race([new Promise(resolve => child.once('close', resolve)), pause(1000)]);
            if (child.exitCode === null) child.kill();
        }
    };
}
module.exports = {launchBrowser, assert};
