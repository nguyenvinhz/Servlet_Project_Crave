// Real Servlet/JSP reporting, CSV download and order form contracts.
const {openBrowser, login, assert} = require('./live-browser.cjs');
(async () => {
    const browser = await openBrowser();
    try {
        const base = await login(browser);
        await browser.visit(base + '/admin');
        assert(await browser.evaluate('!!document.querySelector(".revenue-chart svg")'), 'Overview chart missing');
        assert(await browser.evaluate('document.querySelectorAll(".stats-grid .stat-num").length === 4 && !document.querySelector(".stats-grid").textContent.includes("—")'), 'Overview metrics missing');
        assert(await browser.evaluate('!document.querySelector("script[src*=admin-dashboard]")'), 'Overview loaded obsolete API script');
        console.log('PASS server-rendered overview and chart');
        await browser.visit(base + '/admin/reports?from=2026-10-01&to=2026-10-03');
        assert(await browser.evaluate('document.querySelectorAll("table")[0].tBodies[0].rows.length === 3'), 'Report omitted days with zero orders');
        assert(await browser.evaluate('document.querySelector("[name=from]").value === "2026-10-01" && document.querySelector("[name=to]").value === "2026-10-03"'), 'Report date filter lost');
        // CSV is streamed by the Servlet and uses the same authenticated browser session.
        const csv = await browser.evaluate('(async()=>{const link=[...document.querySelectorAll("a")].find(a=>a.textContent.trim()==="Xuất CSV");const response=await fetch(link.href);return {status:response.status,type:response.headers.get("content-type"),disposition:response.headers.get("content-disposition"),text:await response.text()};})()');
        assert(csv.status === 200 && csv.type.startsWith('text/csv') && csv.disposition.includes('attachment'), 'CSV download headers invalid');
        assert(csv.text.includes('2026-10-01') && csv.text.includes('2026-10-03'), 'CSV range differs from report');
        console.log('PASS date filters and authenticated CSV download');
        await browser.visit(base + '/admin/orders');
        assert(await browser.evaluate('!!document.querySelector("main h1")'), 'Orders page missing');
        assert(await browser.evaluate('[...document.querySelectorAll("main form")].every(f => !(f.getAttribute("action") || "").includes("/api/") && ["get","post"].includes(f.method))'), 'Order form does not target Servlet');
        assert(await browser.evaluate('!document.body.innerHTML.includes("<c:") && !document.body.innerHTML.includes("$" + "{")'), 'Orders JSP failed to render');
        const actions = await browser.evaluate('(()=>{const editors=[...document.querySelectorAll(".order-row-actions")];const closed=editors.every(editor=>!editor.open);if(!editors.length)return {closed,formsVisible:true};const editor=editors[0];editor.querySelector("summary").click();const formsVisible=editor.open&&[...editor.querySelectorAll("form")].every(form=>form.getBoundingClientRect().height>0);editor.open=false;return {closed,formsVisible};})()');
        assert(actions.closed && actions.formsVisible, 'Order actions must start compact and open their existing forms');
        console.log('PASS server-rendered order page and form contracts');
        browser.assertNoBrowserApi();
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
