// Reuse the real JSP/API fixture runner for the complete email verification flow.
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const result = spawnSync(process.execPath, [
    path.join(__dirname, 'account-browser-smoke.cjs'),
    '^(?:otp-|register-(?:client-validation|redirect)$)'
], {stdio: 'inherit', env: process.env, windowsHide: true});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
