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
        form.querySelectorAll("input[type='checkbox'][name]").forEach((input) => {
            payload[input.name] = input.checked;
        });
        return payload;
    }

    function validate(form, payload) {
        const errors = {};
        [...form.elements].forEach((input) => {
            if (!input.name || input.disabled || input.type === "checkbox") return;
            const value = payload[input.name] || "";
            if (input.required && !value) errors[input.name] = "Vui lòng nhập thông tin này.";
            else if (input.name === "email" && (!input.validity.valid || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))) {
                errors.email = "Email không đúng định dạng.";
            } else if (input.name === "phone" && (value.length < 9 || value.length > 15 || !/^\+?[0-9]{8,15}$/.test(value))) {
                errors.phone = "Số điện thoại có từ 9 đến 15 ký tự, gồm chữ số và dấu + ở đầu.";
            }
            else if (input.minLength > 0 && value.length < input.minLength) {
                errors[input.name] = `Vui lòng nhập ít nhất ${input.minLength} ký tự.`;
            } else if (input.maxLength > 0 && value.length > input.maxLength) {
                errors[input.name] = `Vui lòng nhập tối đa ${input.maxLength} ký tự.`;
            }
        });
        if ("confirmPassword" in payload && payload.password !== payload.confirmPassword) {
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
            if (busyStates.has(form)) return;
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
    bindForm(loginForm, () => {
        const returnTo = new URLSearchParams(window.location.search).get("returnTo");
        window.location.assign(safeReturnTo(returnTo) || localUrl("/customer/profile"));
    });
    if (loginForm) {
        const query = new URLSearchParams(window.location.search);
        if (query.get("registered") === "1") {
            showMessage(successBox, "Đăng ký thành công. Vui lòng đăng nhập để tiếp tục.");
        } else if (query.get("reason") === "session-expired") {
            showMessage(errorBox, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        } else if (query.get("loggedOut") === "1") {
            showMessage(successBox, "Bạn đã đăng xuất.");
        }
    }
    bindForm(document.getElementById("registerForm"), () => {
        window.location.assign(`${localUrl("/auth/login")}?registered=1`);
    });

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
    let actionInProgress = false;
    let listLoadInProgress = false;
    const addressUrl = (id) => `${addressForm.action}/${encodeURIComponent(id)}`;

    function resetAddressForm() {
        editingId = null;
        addressForm.reset();
        clearFieldErrors(addressForm);
        document.getElementById("address-form-title").textContent = "Thêm địa chỉ";
        document.getElementById("address-submit").textContent = "Thêm địa chỉ";
        cancel.hidden = true;
    }

    async function addressAction(action) {
        if (actionInProgress || busyStates.has(addressForm)) return;
        actionInProgress = true;
        clearMessages();
        setBusy(addressList, true);
        setBusy(addressForm, true);
        refresh.disabled = true;
        try { await action(); }
        catch (error) { handleError(error, null, true); }
        finally {
            setBusy(addressForm, false);
            setBusy(addressList, false);
            refresh.disabled = false;
            actionInProgress = false;
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
                clearFieldErrors(addressForm);
                addressForm.elements.addressLine.value = detail.addressLine;
                addressForm.elements.note.value = detail.note || "";
                addressForm.elements.isDefault.checked = detail.isDefault;
                document.getElementById("address-form-title").textContent = "Sửa địa chỉ";
                document.getElementById("address-submit").textContent = "Lưu địa chỉ";
                cancel.hidden = false;
                addressForm.scrollIntoView({behavior: "smooth", block: "center"});
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
                await loadAddresses(true);
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
                } else if (syncDefault) {
                    addressForm.elements.isDefault.checked = editingAddress.isDefault;
                }
            }
            fields.disabled = false;
        } catch (error) {
            emptyState.hidden = true;
            if (error.status === 403) {
                addressList.replaceChildren();
                resetAddressForm();
                fields.disabled = true;
            }
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
    cancel.addEventListener("click", () => { resetAddressForm(); clearMessages(); });
    refresh.addEventListener("click", () => {
        if (actionInProgress || busyStates.has(addressForm)) return;
        clearMessages();
        loadAddresses();
    });
    loadAddresses();
});
