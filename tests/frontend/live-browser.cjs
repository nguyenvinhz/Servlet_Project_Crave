// Admin sign-in adapter around the shared live browser driver.
const {launchBrowser, assert} = require('./browser-runtime.cjs');
async function openBrowser() {
    const browser = await launchBrowser();
    browser.visit = url => browser.navigate(url.slice(browser.base.length));
    return browser;
}
async function login(browser, role = 'ADMIN') {
    const email = process.env['CRAVE_SMOKE_' + role + '_EMAIL'];
    const password = process.env['CRAVE_SMOKE_' + role + '_PASSWORD'];
    assert(email && password, 'Set CRAVE_SMOKE_' + role + '_EMAIL and CRAVE_SMOKE_' + role + '_PASSWORD.');
    await browser.navigate('/auth/login');
    await browser.evaluate('document.querySelector("[name=email]").value=' + JSON.stringify(email) +
        ';document.querySelector("[name=password]").value=' + JSON.stringify(password) +
        ';document.querySelector("#loginForm").requestSubmit();');
    await browser.waitFor('document.readyState === "complete" && !location.pathname.endsWith("/auth/login")', 'Login failed');
    return browser.base;
}
module.exports = {openBrowser, login, assert};
