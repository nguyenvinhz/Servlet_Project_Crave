// Exercises real rendered JSPs and real form submissions against the running application.
const {openBrowser, login, assert} = require('./live-browser.cjs');
(async () => {
    const browser = await openBrowser();
    try {
        const base = await login(browser);
        for (const page of ['customers', 'employees', 'promotions', 'categories', 'menu']) {
            await browser.visit(base + '/admin/' + page);
            assert(await browser.evaluate('!!document.querySelector("main h1")'), page + ' missing heading');
            assert(await browser.evaluate('!document.body.innerHTML.includes("<c:") && !document.body.innerHTML.includes("$" + "{")'), page + ' contains unrendered JSP');
            assert(await browser.evaluate('[...document.querySelectorAll("main form")].every(f => ["get","post"].includes(f.method) && !(f.getAttribute("action") || "").includes("/api/"))'), page + ' form does not target a Servlet');
            assert(await browser.evaluate('!document.querySelector("script[src*=admin-management],script[src*=menu-admin]")'), page + ' loaded obsolete API scripts');
            if (['customers','employees','promotions','menu'].includes(page)) {
                await browser.evaluate('document.querySelector("main form[method=get] [name=keyword]").value="smoke-no-match-917531";document.querySelector("main form[method=get]").requestSubmit()');
                await browser.waitFor('document.readyState === "complete" && new URLSearchParams(location.search).get("keyword") === "smoke-no-match-917531"');
                assert(await browser.evaluate('document.querySelector("[name=keyword]").value === "smoke-no-match-917531"'), page + ' lost filter');
            }
            console.log('PASS rendered admin ' + page + ' and Servlet form contract');
        }
        await browser.visit(base + '/admin/categories');
        // Empty name bypasses browser validation and exercises server validation without writing data.
        await browser.evaluate('document.querySelector("#category-form form [name=name]").value="";document.querySelector("#category-form form").submit();');
        await browser.waitFor('document.readyState === "complete" && !!document.querySelector("[role=alert]")');
        assert(await browser.evaluate('document.querySelector("[role=alert]").textContent.trim().length > 0'), 'Missing server validation error');
        assert(await browser.evaluate('!location.search'), 'POST validation did not redirect to clean GET');
        console.log('PASS POST form validation and redirect feedback');
        browser.assertNoBrowserApi();
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
