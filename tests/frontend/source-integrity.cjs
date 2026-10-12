// Prevent invalid UTF-8 and common double-decoded Vietnamese text entering the WAR.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const project = path.resolve(__dirname, '../..');
const decoder = new TextDecoder('utf-8', {fatal: true});
const extensions = new Set(['.java', '.jsp', '.jspf', '.js', '.cjs', '.css', '.xml', '.sql', '.md', '.yaml', '.yml', '.properties', '.ps1', '.sh', '.py', '.html', '.svg', '.drawio', '.txt']);
const corruptText = /\uFFFD|[\u00C2\u00C3][\u0080-\u00BF]|\u00E1[\u00BA\u00BB]|\u00E2\u20AC[\u0153\u009D\u2122\u201C\u201D]|\u00EF\u00BF\u00BD/;
let checked = 0;
function scan(directory) {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        if (['target', 'node_modules', '.git', '.runtime'].includes(entry.name)) continue;
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) { scan(file); continue; }
        if (!extensions.has(path.extname(file))) continue;
        const relative = path.relative(project, file);
        const text = decoder.decode(fs.readFileSync(file));
        assert(!corruptText.test(text), `${relative}: broken Unicode or mojibake`);
        if (/\.jspf?$/.test(file)) {
            assert(/pageEncoding\s*=\s*"UTF-8"/.test(text), `${relative}: missing JSP source encoding`);
        }
        checked++;
    }
}
for (const folder of ['frontend', 'backend/src', 'database', 'scripts', 'docs', '.github']) scan(path.join(project, folder));
const webXml = fs.readFileSync(path.join(project, 'frontend/WEB-INF/web.xml'), 'utf8');
assert(webXml.includes('<request-character-encoding>UTF-8</request-character-encoding>'));
assert(webXml.includes('<response-character-encoding>UTF-8</response-character-encoding>'));
const fontCss = fs.readFileSync(path.join(project, 'frontend/assets/css/fonts.css'), 'utf8');
for (const match of fontCss.matchAll(/url\(([^)]+)\)/g)) {
    assert(fs.existsSync(path.resolve(project, 'frontend/assets/css', match[1])), `Font missing: ${match[1]}`);
}
console.log(`Source integrity: ${checked} text files are valid UTF-8; JSP encodings and local fonts verified.`);
