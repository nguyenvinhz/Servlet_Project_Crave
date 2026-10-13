"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const contextPath = document.body.dataset.contextPath || "";
    const localUrl = (path) => `${contextPath}${path}`;
    const errorBox = document.getElementById("form-error");
    const successBox = document.getElementById("form-success");
    const busyStates = new WeakMap();

    class RequestError extends Error {
        constructor(status, error) {
            super(error?.message || (status === 403
                ? "Tài khoản của bạn không có quyền thực hiện thao tác này."
                : "Không thể thực hiện yêu cầu. Vui lòng thử lại."));
            this.status = status;
            this.fieldErrors = error?.fieldErrors || {};
        }
    }

    function showMessage(box, message) {
        if (!box) return;
        box.textContent = message || "";
        box.hidden = !message;
    }

    function clearMessages() {
        showMessage(errorBox, "");
        showMessage(successBox, "");
    }

    showMessage(errorBox, errorBox?.textContent.trim());
    showMessage(successBox, successBox?.textContent.trim());

    function setBusy(container, busy) {
        if (busy) {
            if (busyStates.has(container)) return;
            const states = [...container.querySelectorAll("input, textarea, button")]
                .map((control) => [control, control.disabled]);
            busyStates.set(container, states);
            states.forEach(([control]) => { control.disabled = true; });
            container.setAttribute("aria-busy", "true");
            if (container.tagName === "FORM") {
                const status = document.createElement("p");
                status.className = "field-hint form-loading";
                status.setAttribute("role", "status");
                status.textContent = "Đang xử lý...";
                container.append(status);
            }
        } else {
            (busyStates.get(container) || []).forEach(([control, disabled]) => {
                control.disabled = disabled;
            });
            busyStates.delete(container);
            container.removeAttribute("aria-busy");
            container.querySelector(".form-loading")?.remove();
        }
    }

    async function request(url, method = "GET", payload) {
        let response;
        try {
            response = await fetch(url, {
                method, credentials: "same-origin",
                headers: {"Accept": "application/json",
                    ...(payload === undefined ? {} : {"Content-Type": "application/json"})},
                ...(payload === undefined ? {} : {body: JSON.stringify(payload)})
            });
        } catch {
            throw new Error("Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.");
        }
        if (response.status === 204) return null;
        let result;
        try { result = await response.json(); }
        catch { throw new RequestError(response.status); }
        if (!response.ok || result?.success !== true) {
            throw new RequestError(response.status, result?.error);
        }
        return result.data;
    }

    function safeReturnTo(value) {
        if (!value || !value.startsWith("/") || value.startsWith("//")
                || /[\\\u0000-\u001f\u007f]/.test(value)) return null;
        try {
            const url = new URL(value, window.location.origin);
            const path = decodeURIComponent(url.pathname);
            if (url.origin !== window.location.origin || /%(?:2f|5c)/i.test(url.pathname)
                    || /[\\\u0000-\u001f\u007f]/.test(path)
                    || (contextPath && !path.startsWith(`${contextPath}/`))) return null;
            const route = path.substring(contextPath.length);
            if (route.startsWith("//") || /^\/(?:api|auth)(?:[;/]|$)/.test(route)) return null;
            return `${url.pathname}${url.search}${url.hash}`;
        } catch { return null; }
    }

    function loginRedirect() {
        clearCheckoutVoucher();
        const returnTo = safeReturnTo(`${window.location.pathname}${window.location.search}`);
        const query = new URLSearchParams({reason: "session-expired"});
        if (returnTo) query.set("returnTo", returnTo);
        window.location.assign(`${localUrl("/auth/login")}?${query}`);
    }

    function clearFieldErrors(form) {
        form.querySelectorAll(".field-error").forEach((element) => element.remove());
        form.querySelectorAll("[aria-invalid]").forEach((input) => {
            input.removeAttribute("aria-invalid");
            const descriptions = (input.getAttribute("aria-describedby") || "").split(" ")
                .filter((id) => id && id !== `${input.id}-error`);
            if (descriptions.length) input.setAttribute("aria-describedby", descriptions.join(" "));
            else input.removeAttribute("aria-describedby");
        });
    }

    function showFieldErrors(form, errors) {
        let firstInvalid;
        Object.entries(errors).forEach(([name, message]) => {
            const input = form.elements.namedItem(name);
            if (!input || !input.id || typeof message !== "string") return;
            const hint = document.createElement("p");
            hint.className = "field-error";
            hint.id = `${input.id}-error`;
            hint.textContent = message;
            input.setAttribute("aria-invalid", "true");
            input.setAttribute("aria-describedby", `${input.getAttribute("aria-describedby") || ""} ${hint.id}`.trim());
            input.insertAdjacentElement("afterend", hint);
            firstInvalid ||= input;
        });
        firstInvalid?.focus();
    }

    function handleError(error, form, protectedPage = false) {
        if (error.status === 401 && protectedPage) return loginRedirect();
        if (error.status === 403 && protectedPage) {
            if (document.getElementById("addressForm")) clearForbiddenAddresses();
            if (document.getElementById("profileForm")) {
                profileForm.reset();
                clearFieldErrors(profileForm);
                profileFields.disabled = true;
                document.getElementById("profile-readonly").hidden = true;
                document.getElementById("profile-retry").hidden = false;
            }
        }
        showMessage(errorBox, error.message);
        if (form) showFieldErrors(form, error.fieldErrors || {});
    }

    function formPayload(form) {
        const payload = {};
        new FormData(form).forEach((value, name) => {
            const input = form.elements.namedItem(name);
            payload[name] = input.type === "checkbox" ? input.checked
                : input.type === "password" ? value : value.trim();
        });
        form.querySelectorAll("input[type='checkbox'][name]:not(:disabled)").forEach((input) => {
            payload[input.name] = input.checked;
        });
        return payload;
    }

    function validate(form, payload, selectedNames) {
        const errors = {};
        [...form.elements].forEach((input) => {
            if (!input.name || input.matches(":disabled") || input.type === "checkbox"
                    || (selectedNames && !selectedNames.has(input.name))) return;
            const value = payload[input.name] || "";
            if (input.required && !value) errors[input.name] = "Vui lòng nhập thông tin này.";
            else if (input.name === "email" && (!input.validity.valid || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))) {
                errors.email = "Email không đúng định dạng.";
            } else if (input.name === "phone" && (value.length < 9 || value.length > 15 || !/^\+?[0-9]{8,15}$/.test(value))) {
                errors.phone = "Số điện thoại có từ 9 đến 15 ký tự, gồm chữ số và dấu + ở đầu.";
            }
            else if (input.name === "verificationCode" && !/^[0-9]{6}$/.test(value)) {
                errors.verificationCode = "Mã xác nhận cần đủ 6 chữ số.";
            }
            else if (input.minLength > 0 && value.length < input.minLength) {
                errors[input.name] = `Vui lòng nhập ít nhất ${input.minLength} ký tự.`;
            } else if (input.maxLength > 0 && value.length > input.maxLength) {
                errors[input.name] = `Vui lòng nhập tối đa ${input.maxLength} ký tự.`;
            }
        });
        if ("confirmPassword" in payload && (!selectedNames || selectedNames.has("confirmPassword"))
                && payload.password !== payload.confirmPassword) {
            errors.confirmPassword = "Mật khẩu nhập lại chưa khớp.";
        }
        showFieldErrors(form, errors);
        return Object.keys(errors).length === 0;
    }

    function bindForm(form, onSuccess, protectedPage = false, endpoint) {
        if (!form) return;
        form.noValidate = true;
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (busyStates.has(form) || form.querySelector("fieldset:disabled")) return;
            clearMessages();
            clearFieldErrors(form);
            const payload = formPayload(form);
            if (!validate(form, payload)) return;
            setBusy(form, true);
            try {
                const target = endpoint ? endpoint() : {url: form.action, method: form.dataset.method || "POST"};
                await onSuccess(await request(target.url, target.method, payload));
            } catch (error) {
                setBusy(form, false);
                handleError(error, form, protectedPage);
            }
            finally { setBusy(form, false); }
        });
    }

    const loginForm = document.getElementById("loginForm");
    const returnTo = safeReturnTo(new URLSearchParams(window.location.search).get("returnTo"));
    document.querySelectorAll("[data-auth-switch]").forEach((link) => {
        if (!returnTo) return;
        const url = new URL(link.href, window.location.origin);
        url.searchParams.set("returnTo", returnTo);
        link.href = `${url.pathname}${url.search}`;
    });
    function accountDestination(user) {
        if (user?.accountType !== "EMPLOYEE") return localUrl("/menu");
        return localUrl({ADMIN: "/admin", MENU_MANAGER: "/admin/menu", ORDER_STAFF: "/admin/orders", PROMOTION_MANAGER: "/admin/promotions", HR_MANAGER: "/admin/employees"}[user.role] || "/menu");
    }

    function compatibleReturnTo(value, user) {
        if (!value) return null;
        const route = new URL(value, window.location.origin).pathname.substring(contextPath.length);
        if (user?.accountType !== "EMPLOYEE") {
            if (/^\/admin(?:[;/]|$)/.test(route)) return null;
            if (/^\/customer\/profile(?:[;/]|$)/.test(route)) return localUrl("/menu");
            return value;
        }
        if (/^\/(?:cart|checkout|orders|customer\/addresses)(?:[;/]|$)/.test(route)) return null;
        if (!/^\/admin(?:[;/]|$)/.test(route) || user.role === "ADMIN") return value;
        const allowed = {MENU_MANAGER: /^\/admin\/(?:menu|categories)(?:[;/]|$)/, ORDER_STAFF: /^\/admin\/orders(?:[;/]|$)/, PROMOTION_MANAGER: /^\/admin\/promotions(?:[;/]|$)/, HR_MANAGER: /^\/admin\/(?:customers|employees)(?:[;/]|$)/};
        return allowed[user.role]?.test(route) ? value : null;
    }

    bindForm(loginForm, (user) => {
        clearCheckoutVoucher();
        window.location.assign(compatibleReturnTo(returnTo, user) || accountDestination(user));
    });
    if (loginForm) {
        const query = new URLSearchParams(window.location.search);
        if (query.get("registered") === "1") {
            showMessage(successBox, "Đăng ký thành công. Vui lòng đăng nhập để tiếp tục.");
        } else if (query.get("passwordReset") === "1" || query.get("passwordChanged") === "1") {
            showMessage(successBox, "Mật khẩu đã được cập nhật. Vui lòng đăng nhập bằng mật khẩu mới.");
        } else if (query.get("reason") === "session-expired") {
            showMessage(errorBox, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        } else if (query.get("loggedOut") === "1") {
            showMessage(successBox, "Bạn đã đăng xuất.");
        }
    }
    function maskedEmail(email) {
        const [name, domain] = email.split("@");
        return `${name.slice(0, name.length > 2 ? 2 : 1)}•••@${domain}`;
    }

    function bindOtpForm(form, kind) {
        if (!form) return;
        form.noValidate = true;
        const otpFields = form.querySelector("[data-otp-fields]");
        const resend = document.getElementById("otp-resend");
        const edit = document.getElementById("otp-edit");
        const submit = document.getElementById("otp-submit");
        const summary = document.getElementById("otp-summary");
        const changingPassword = kind === "change";
        const identity = form.elements.namedItem(changingPassword ? "currentPassword" : "email");
        const initialNames = kind === "register"
            ? new Set(["fullName", "email", "phone", "password", "confirmPassword"])
            : new Set([changingPassword ? "currentPassword" : "email"]);
        const finalNames = kind === "register"
            ? ["fullName", "email", "phone", "password", "confirmPassword", "verificationCode"]
            : [...(changingPassword ? [] : ["email"]), "verificationCode", "password", "confirmPassword"];
        let awaitingCode = false;
        let ready = !changingPassword;
        let sessionLoading = false;
        let targetEmail = "";
        let resendAt = 0;
        let cooldownTimer;

        function updateCooldown() {
            const seconds = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
            resend.disabled = seconds > 0 || busyStates.has(form);
            resend.textContent = seconds ? `Gửi lại mã sau ${seconds}s` : "Gửi lại mã";
            if (!seconds && cooldownTimer) {
                clearInterval(cooldownTimer);
                cooldownTimer = undefined;
            }
        }

        function startCooldown(seconds = 60) {
            resendAt = Date.now() + Math.max(1, Math.min(3600, Number(seconds) || 60)) * 1000;
            if (cooldownTimer) clearInterval(cooldownTimer);
            cooldownTimer = setInterval(updateCooldown, 1000);
            updateCooldown();
        }

        function setCodeStage(active) {
            awaitingCode = active;
            form.dataset.otpStage = active ? "verify" : "send";
            otpFields.hidden = !active;
            otpFields.disabled = !active;
            identity.readOnly = active;
            submit.textContent = active
                ? (kind === "register" ? "Xác nhận và tạo tài khoản" : "Xác nhận mật khẩu mới")
                : "Gửi mã xác nhận";
            if (!active) form.elements.verificationCode.value = "";
        }
        setCodeStage(false);

        async function sendCode(isResend = false) {
            if (!ready || busyStates.has(form)) return;
            clearMessages();
            clearFieldErrors(form);
            const payload = formPayload(form);
            const selectedNames = isResend
                ? new Set([changingPassword ? "currentPassword" : "email"]) : initialNames;
            if (!validate(form, payload, selectedNames)) return;
            if (Date.now() < resendAt) {
                showMessage(errorBox, "Vui lòng chờ hết thời gian đếm ngược trước khi gửi lại mã.");
                return;
            }
            setBusy(form, true);
            let sent = false;
            try {
                const codePayload = changingPassword
                    ? {currentPassword: payload.currentPassword} : {email: payload.email};
                const result = await request(form.dataset.codeUrl, "POST", codePayload);
                if (!changingPassword) targetEmail = payload.email;
                setCodeStage(true);
                form.elements.verificationCode.value = "";
                summary.textContent = kind === "reset"
                    ? `Nếu ${maskedEmail(targetEmail)} thuộc tài khoản đang hoạt động, mã xác nhận đã được gửi.`
                    : `Mã xác nhận được gửi đến ${maskedEmail(targetEmail)}.`;
                showMessage(successBox, kind === "reset"
                    ? "Nếu email thuộc tài khoản đang hoạt động, bạn sẽ nhận được mã xác nhận. Kiểm tra cả hộp thư rác."
                    : "Đã gửi mã xác nhận. Kiểm tra email và hộp thư rác của bạn.");
                startCooldown(result?.resendAfterSeconds || 60);
                sent = true;
            } catch (error) {
                if (error.status === 429) startCooldown();
                setBusy(form, false);
                handleError(error, form, changingPassword);
            } finally {
                setBusy(form, false);
                updateCooldown();
                if (sent) form.elements.verificationCode.focus();
            }
        }

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!ready || busyStates.has(form)) return;
            if (!awaitingCode) return sendCode();
            clearMessages();
            clearFieldErrors(form);
            const allValues = formPayload(form);
            if (!validate(form, allValues)) return;
            const payload = Object.fromEntries(finalNames.map((name) => [name, allValues[name]]));
            // Keep the verified email bound to the code even if an autofill tool changes the field.
            if (!changingPassword) payload.email = targetEmail;
            setBusy(form, true);
            try {
                await request(form.action, "POST", payload);
                clearCheckoutVoucher();
                const flag = {register: "registered", reset: "passwordReset", change: "passwordChanged"}[kind];
                const query = new URLSearchParams({[flag]: "1"});
                if (returnTo) query.set("returnTo", returnTo);
                window.location.assign(`${localUrl("/auth/login")}?${query}`);
            } catch (error) {
                setBusy(form, false);
                handleError(error, form, changingPassword);
            } finally {
                setBusy(form, false);
                updateCooldown();
            }
        });
        resend.addEventListener("click", () => sendCode(true));
        edit.addEventListener("click", () => {
            if (busyStates.has(form)) return;
            setCodeStage(false);
            clearMessages();
            clearFieldErrors(form);
            identity.focus();
        });

        if (!changingPassword) return;
        const sessionFields = document.getElementById("password-change-fields");
        const accountHint = document.getElementById("password-account");
        const retry = document.getElementById("password-session-retry");
        async function loadSession() {
            if (sessionLoading) return;
            sessionLoading = true;
            ready = false;
            sessionFields.disabled = true;
            retry.hidden = true;
            clearMessages();
            accountHint.textContent = "Đang kiểm tra tài khoản...";
            try {
                const user = await request(localUrl("/api/auth/session"));
                if (!user?.email || !user.email.includes("@")) {
                    throw new Error("Chưa tải được email tài khoản. Vui lòng thử lại.");
                }
                targetEmail = user.email;
                accountHint.textContent = `Mã xác nhận sẽ được gửi đến ${maskedEmail(targetEmail)}.`;
                sessionFields.disabled = false;
                ready = true;
            } catch (error) {
                accountHint.textContent = "Chưa tải được thông tin tài khoản.";
                retry.hidden = error.status === 401;
                handleError(error, null, true);
            } finally { sessionLoading = false; }
        }
        retry.addEventListener("click", loadSession);
        loadSession();
    }

    bindOtpForm(document.getElementById("registerForm"), "register");
    bindOtpForm(document.getElementById("resetPasswordForm"), "reset");
    bindOtpForm(document.getElementById("changePasswordForm"), "change");

    const logoutForm = document.getElementById("logoutForm");
    function clearCheckoutVoucher() {
        try { sessionStorage.removeItem("crave_applied_voucher"); } catch (_) { }
    }
    if (logoutForm) logoutForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (busyStates.has(logoutForm)) return;
        const error = document.getElementById("logout-error");
        showMessage(error, "");
        setBusy(logoutForm, true);
        try {
            await request(logoutForm.action, "POST");
            clearCheckoutVoucher();
            window.location.assign(`${localUrl("/auth/login")}?loggedOut=1`);
        } catch (failure) {
            if (failure.status === 401) {
                clearCheckoutVoucher();
                window.location.assign(localUrl("/auth/login"));
            }
            else showMessage(error, failure.message);
        } finally { setBusy(logoutForm, false); }
    });

    const profileForm = document.getElementById("profileForm");
    const profileFields = document.getElementById("profile-fields");
    let profileLoadInProgress = false;
    function fillProfile(profile) {
        ["fullName", "email", "phone"].forEach((name) => {
            profileForm.elements.namedItem(name).value = profile[name] || "";
        });
        const employee = profile.accountType === "EMPLOYEE";
        profileFields.disabled = employee;
        document.getElementById("profile-readonly").hidden = !employee;
        document.getElementById("profile-loading").hidden = true;
    }
    bindForm(profileForm, (profile) => {
        fillProfile(profile);
        showMessage(successBox, "Đã cập nhật hồ sơ.");
    }, true);
    async function loadProfile() {
        if (profileLoadInProgress || busyStates.has(profileForm)) return;
        profileLoadInProgress = true;
        setBusy(profileForm, true);
        const retry = document.getElementById("profile-retry");
        retry.hidden = true;
        showMessage(errorBox, "");
        document.getElementById("profile-loading").hidden = false;
        try { fillProfile(await request(profileForm.action)); }
        catch (error) {
            document.getElementById("profile-loading").hidden = true;
            retry.hidden = false;
            handleError(error, null, true);
        }
        finally {
            setBusy(profileForm, false);
            profileLoadInProgress = false;
        }
    }
    if (profileForm) {
        document.getElementById("profile-retry").addEventListener("click", loadProfile);
        loadProfile();
    }

    const addressForm = document.getElementById("addressForm");
    if (!addressForm) return;
    const addressList = document.getElementById("address-list");
    const loading = document.getElementById("address-loading");
    const emptyState = document.getElementById("address-empty-state");
    const refresh = document.getElementById("address-refresh");
    const cancel = document.getElementById("address-cancel");
    const fields = document.getElementById("address-fields");
    let editingId = null;
    let editingDefault = null;
    let actionInProgress = false;
    let listLoadInProgress = false;
    const addressUrl = (id) => `${addressForm.action}/${encodeURIComponent(id)}`;

    function resetAddressForm() {
        editingId = null;
        editingDefault = null;
        addressForm.reset();
        clearFieldErrors(addressForm);
        document.getElementById("address-form-title").textContent = "Thêm địa chỉ";
        document.getElementById("address-submit").textContent = "Thêm địa chỉ";
        cancel.hidden = true;
    }

    function clearForbiddenAddresses() {
        addressList.replaceChildren();
        resetAddressForm();
        fields.disabled = true;
        emptyState.hidden = true;
    }

    async function addressAction(action) {
        if (actionInProgress || busyStates.has(addressForm)) return;
        actionInProgress = true;
        clearMessages();
        setBusy(addressList, true);
        setBusy(addressForm, true);
        refresh.disabled = true;
        let focusTarget;
        try { focusTarget = await action(); }
        catch (error) { handleError(error, null, true); }
        finally {
            setBusy(addressForm, false);
            setBusy(addressList, false);
            refresh.disabled = false;
            actionInProgress = false;
            focusTarget?.focus();
        }
    }

    function addressButton(label, action, className = "") {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `address-action ${className}`.trim();
        button.textContent = label;
        button.addEventListener("click", () => addressAction(action));
        return button;
    }

    function renderAddresses(addresses) {
        addressList.replaceChildren();
        emptyState.hidden = addresses.length !== 0;
        addresses.forEach((address) => {
            const item = document.createElement("article");
            item.className = "address-item";
            const title = document.createElement("h3");
            title.textContent = address.addressLine;
            item.append(title);
            if (address.isDefault) {
                const badge = document.createElement("span");
                badge.className = "address-badge";
                badge.textContent = "Mặc định";
                item.append(badge);
            }
            if (address.note) {
                const note = document.createElement("p");
                note.textContent = address.note;
                item.append(note);
            }
            const actions = document.createElement("div");
            actions.className = "address-actions";
            actions.append(addressButton("Sửa", async () => {
                const detail = await request(addressUrl(address.id));
                editingId = detail.id;
                editingDefault = detail.isDefault;
                clearFieldErrors(addressForm);
                addressForm.elements.addressLine.value = detail.addressLine;
                addressForm.elements.note.value = detail.note || "";
                addressForm.elements.isDefault.checked = detail.isDefault;
                document.getElementById("address-form-title").textContent = "Sửa địa chỉ";
                document.getElementById("address-submit").textContent = "Lưu địa chỉ";
                cancel.hidden = false;
                const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
                addressForm.scrollIntoView({behavior: reducedMotion ? "auto" : "smooth", block: "center"});
                return addressForm.elements.addressLine;
            }));
            if (!address.isDefault) actions.append(addressButton("Đặt mặc định", async () => {
                const current = await request(addressUrl(address.id));
                await request(addressUrl(address.id), "PUT", {
                    addressLine: current.addressLine, note: current.note, isDefault: true
                });
                showMessage(successBox, "Đã chọn địa chỉ mặc định.");
                await loadAddresses(true);
            }));
            actions.append(addressButton("Xóa", async () => {
                if (!window.confirm("Bạn muốn xóa địa chỉ này?")) return;
                await request(addressUrl(address.id), "DELETE");
                if (editingId === address.id) resetAddressForm();
                showMessage(successBox, "Đã xóa địa chỉ.");
                await loadAddresses();
            }, "danger"));
            item.append(actions);
            addressList.append(item);
        });
    }

    async function loadAddresses(syncDefault = false) {
        if (listLoadInProgress) return;
        listLoadInProgress = true;
        const ownsFormBusy = !busyStates.has(addressForm);
        const ownsListBusy = !busyStates.has(addressList);
        if (ownsFormBusy) setBusy(addressForm, true);
        if (ownsListBusy) setBusy(addressList, true);
        loading.hidden = false;
        emptyState.hidden = true;
        refresh.disabled = true;
        try {
            const addresses = await request(addressForm.action);
            if (!Array.isArray(addresses)) throw new Error("Máy chủ trả về danh sách địa chỉ không hợp lệ.");
            renderAddresses(addresses);
            if (editingId !== null) {
                const editingAddress = addresses.find((address) => address.id === editingId);
                if (!editingAddress) {
                    resetAddressForm();
                    showMessage(errorBox, "Địa chỉ đang sửa không còn tồn tại. Vui lòng chọn lại địa chỉ.");
                } else {
                    const checkbox = addressForm.elements.isDefault;
                    if (syncDefault || checkbox.checked === editingDefault) {
                        checkbox.checked = editingAddress.isDefault;
                    }
                    editingDefault = editingAddress.isDefault;
                }
            }
            fields.disabled = false;
        } catch (error) {
            emptyState.hidden = true;
            handleError(error, null, true);
        } finally {
            listLoadInProgress = false;
            if (ownsFormBusy) setBusy(addressForm, false);
            if (ownsListBusy) setBusy(addressList, false);
            loading.hidden = true;
            refresh.disabled = actionInProgress;
        }
    }

    bindForm(addressForm, async () => {
        const wasEditing = editingId !== null;
        resetAddressForm();
        showMessage(successBox, wasEditing ? "Đã cập nhật địa chỉ." : "Đã thêm địa chỉ.");
        await loadAddresses();
    }, true, () => ({
        url: editingId === null ? addressForm.action : addressUrl(editingId),
        method: editingId === null ? "POST" : "PUT"
    }));
    cancel.addEventListener("click", () => {
        resetAddressForm();
        clearMessages();
        addressForm.elements.addressLine.focus();
    });
    refresh.addEventListener("click", () => {
        if (actionInProgress || busyStates.has(addressForm)) return;
        clearMessages();
        loadAddresses();
    });
    loadAddresses();
});
