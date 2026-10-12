'use strict';

document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page;
    if (!['customers', 'employees', 'promotions'].includes(page)) return;

    const admin = window.CraveAdmin;
    const byId = id => document.getElementById(id);
    const items = { values: [], loadRevision: 0 };
    const employeePage = page === 'employees';
    const promotionPage = page === 'promotions';
    const endpoint = '/api/admin/' + page;
    const roleLabels = {
        ADMIN: 'Quản trị viên', MENU_MANAGER: 'Quản lý thực đơn', ORDER_STAFF: 'Xử lý đơn hàng',
        PROMOTION_MANAGER: 'Quản lý khuyến mãi', HR_MANAGER: 'Quản lý nhân sự'
    };
    const search = byId('managementSearch');
    const statusFilter = byId('managementStatus');
    const roleFilter = byId('managementRole');
    const feedback = byId('managementFeedback');
    let confirmation = null;
    let editingEmployee = null;
    let editingPromotion = null;

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined && text !== null) node.textContent = String(text);
        if (className) node.className = className;
        return node;
    }

    function notify(message, success = false) {
        feedback.textContent = message;
        feedback.dataset.tone = success ? 'success' : 'error';
        feedback.hidden = false;
        if (success && admin.toast) admin.toast(message);
    }

    function errorMessage(error) {
        const details = error?.details || error?.data?.error?.details;
        return details && Object.keys(details).length ? Object.values(details).join(' ') : error.message || 'Không thể thực hiện. Vui lòng thử lại.';
    }

    function normalized(value) {
        return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    }

    function dateValue(value) {
        if (!value) return null;
        const text = String(value);
        return new Date(/Z$|[+-]\d{2}:\d{2}$/.test(text) ? text : text.includes('T') ? text + '+07:00' : text + 'T00:00:00+07:00');
    }

    function dateLabel(value, time = false) {
        const date = dateValue(value);
        if (!date || Number.isNaN(date.getTime())) return '—';
        return new Intl.DateTimeFormat('vi-VN', {
            timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
            ...(time ? { hour: '2-digit', minute: '2-digit' } : {})
        }).format(date);
    }

    function datetimeInput(date) {
        const parts = Object.fromEntries(new Intl.DateTimeFormat('en-GB', {
            timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).formatToParts(date).map(part => [part.type, part.value]));
        return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
    }

    function badge(text, tone = 'active') {
        const node = element('span', text, 'management-badge');
        node.dataset.tone = tone;
        return node;
    }

    function button(text, action, title) {
        const node = element('button', text, 'button outline');
        node.type = 'button';
        if (title) node.title = title;
        node.addEventListener('click', action);
        return node;
    }

    function cell(value) {
        const td = element('td');
        td.append(value instanceof Node ? value : document.createTextNode(String(value ?? '—')));
        return td;
    }

    function identity(item) {
        const wrapper = element('div', null, 'management-identity');
        const name = item.fullName || 'Chưa có tên';
        const initials = name.trim().split(/\s+/).slice(-2).map(word => word[0]).join('').toUpperCase();
        wrapper.append(element('span', initials, 'management-avatar'));
        const labels = element('div');
        labels.append(element('strong', name), element('small', item.id));
        wrapper.append(labels);
        return wrapper;
    }

    function formBusy(form, busy) {
        form.dataset.busy = String(busy);
        [...form.querySelectorAll('button')].forEach(node => { node.disabled = busy; });
        form.setAttribute('aria-busy', String(busy));
    }

    function setFormError(id, message = '') {
        const node = byId(id);
        node.textContent = message;
        node.hidden = !message;
    }

    function promotionState(item) {
        if (item.status !== 'ACTIVE') return 'INACTIVE';
        const now = Date.now();
        if (now < dateValue(item.startAt).getTime()) return 'UPCOMING';
        if (now > dateValue(item.endAt).getTime()) return 'EXPIRED';
        return 'CURRENT';
    }

    function filteredItems() {
        const query = normalized(search.value);
        return items.values.filter(item => {
            const text = normalized([item.fullName, item.id, item.email, item.phone, item.name, item.code].join(' '));
            const status = promotionPage ? promotionState(item) : item.status;
            return text.includes(query) && (!statusFilter.value || status === statusFilter.value)
                && (!roleFilter || !roleFilter.value || item.role === roleFilter.value);
        });
    }

    function render() {
        const visible = filteredItems();
        byId('managementCount').textContent = `${visible.length} / ${items.values.length} ${promotionPage ? 'chương trình' : employeePage ? 'nhân viên' : 'khách hàng'}`;
        if (promotionPage) renderPromotions(visible);
        else {
            const active = items.values.filter(item => item.status === (employeePage ? 'WORKING' : 'ACTIVE')).length;
            byId('managementTotal').textContent = items.values.length;
            byId('managementActive').textContent = active;
            byId('managementInactive').textContent = items.values.length - active;
            renderAccounts(visible);
        }
    }

    function renderAccounts(visible) {
        const rows = byId('managementRows');
        rows.replaceChildren();
        if (!visible.length) {
            const row = element('tr');
            const empty = cell('Không tìm thấy ' + (employeePage ? 'nhân viên' : 'khách hàng') + ' phù hợp.');
            empty.colSpan = employeePage ? 7 : 8;
            empty.className = 'management-empty';
            row.append(empty);
            rows.append(row);
            return;
        }
        visible.forEach(item => {
            const row = element('tr');
            const active = item.status === (employeePage ? 'WORKING' : 'ACTIVE');
            const actions = element('div', null, 'management-row-actions');
            const isAdminAccount = employeePage && item.role === 'ADMIN';
            const currentAccount = employeePage && item.id === document.body.dataset.userId;
            const mayChange = !currentAccount && (!isAdminAccount || document.body.dataset.role === 'ADMIN');
            if (employeePage && mayChange) actions.append(button('Đổi chức vụ', () => openRole(item), 'Đổi chức vụ của ' + item.fullName));
            if (mayChange) actions.append(button(active ? (employeePage ? 'Cho nghỉ việc' : 'Khóa') : (employeePage ? 'Làm việc lại' : 'Mở khóa'),
                () => confirmStatus(item), 'Cập nhật trạng thái của ' + item.fullName));
            if (!mayChange) actions.append(element('span', currentAccount ? 'Tài khoản hiện tại' : 'Quản trị viên quản lý', 'management-count'));
            row.append(cell(identity(item)));
            if (employeePage) row.append(cell(roleLabels[item.role] || 'Chưa phân công'));
            row.append(cell(item.phone), cell(item.email), cell(dateLabel(employeePage ? item.hireDate : item.registeredAt)));
            if (!employeePage) row.append(cell(item.orderCount || 0), cell(admin.money(item.totalSpent || 0)));
            row.append(cell(badge(active ? (employeePage ? 'Đang làm việc' : 'Hoạt động') : (employeePage ? 'Đã nghỉ việc' : 'Đã khóa'),
                active ? 'active' : employeePage ? 'inactive' : 'locked')), cell(actions));
            rows.append(row);
        });
    }

    function renderPromotions(visible) {
        const list = byId('managementPromotionList');
        list.replaceChildren();
        if (!visible.length) {
            list.append(element('div', 'Không tìm thấy chương trình khuyến mãi phù hợp.', 'admin-card management-empty'));
            return;
        }
        const stateLabels = { CURRENT: 'Đang áp dụng', UPCOMING: 'Sắp diễn ra', EXPIRED: 'Hết hạn', INACTIVE: 'Đã tắt' };
        visible.forEach(item => {
            const card = element('article', null, 'management-promo-card');
            const value = element('div', null, 'management-promo-value');
            value.append(element('span', item.code), element('strong', item.discountType === 'PERCENT' ? `−${item.discountValue}%` : '−' + admin.money(item.discountValue)));
            const content = element('div', null, 'management-promo-body');
            const top = element('div', null, 'management-promo-top');
            const state = promotionState(item);
            top.append(element('h2', item.name), badge(stateLabels[state], state === 'CURRENT' ? 'active' : state === 'UPCOMING' ? 'upcoming' : 'inactive'));
            const dates = element('p', `${dateLabel(item.startAt, true)} – ${dateLabel(item.endAt, true)}`, 'management-promo-dates');
            const rules = element('p', 'Đơn từ ' + admin.money(item.minimumOrderValue || 0)
                + (item.maximumDiscount ? ' · Giảm tối đa ' + admin.money(item.maximumDiscount) : ''), 'management-promo-rules');
            const progress = element('div', null, 'management-promo-progress');
            const fill = element('span');
            const start = dateValue(item.startAt).getTime();
            const end = dateValue(item.endAt).getTime();
            fill.style.width = Math.max(0, Math.min(100, ((Date.now() - start) / (end - start)) * 100)) + '%';
            progress.append(fill);
            const actions = element('div', null, 'management-promo-actions');
            actions.append(button('Chỉnh sửa', () => openPromotion(item)), button(item.status === 'ACTIVE' ? 'Tắt áp dụng' : 'Bật áp dụng', () => confirmStatus(item)));
            content.append(top, dates, rules, progress, actions);
            card.append(value, content);
            list.append(card);
        });
    }

    async function load() {
        const revision = ++items.loadRevision;
        const refresh = byId('managementRefresh');
        refresh.disabled = true;
        refresh.textContent = 'Đang tải…';
        feedback.hidden = true;
        try {
            const values = await admin.request(endpoint);
            if (revision !== items.loadRevision) return;
            items.values = Array.isArray(values) ? values : [];
            render();
        } catch (error) {
            if (revision !== items.loadRevision) return;
            notify(errorMessage(error));
            const target = byId(promotionPage ? 'managementPromotionList' : 'managementRows');
            target.replaceChildren();
            if (promotionPage) target.append(element('div', 'Không thể tải dữ liệu. Chọn Tải lại để thử lại.', 'admin-card management-empty'));
            else {
                const row = element('tr');
                const empty = cell('Không thể tải dữ liệu. Chọn Tải lại để thử lại.');
                empty.colSpan = employeePage ? 7 : 8;
                empty.className = 'management-empty';
                row.append(empty);
                target.append(row);
            }
        } finally {
            if (revision === items.loadRevision) {
                refresh.disabled = false;
                refresh.textContent = 'Tải lại';
            }
        }
    }

    async function mutate(path, method, data) {
        return admin.request(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
    }

    function replaceItem(updated) {
        const id = promotionPage ? 'promotionId' : 'id';
        const existing = items.values.findIndex(item => item[id] === updated[id]);
        if (existing >= 0) items.values[existing] = updated;
        else items.values.unshift(updated);
        render();
    }

    function confirmStatus(item) {
        const enabled = item.status === (promotionPage ? 'ACTIVE' : employeePage ? 'WORKING' : 'ACTIVE');
        const status = promotionPage ? (enabled ? 'INACTIVE' : 'ACTIVE') : employeePage ? (enabled ? 'ON_LEAVE' : 'WORKING') : (enabled ? 'LOCKED' : 'ACTIVE');
        const name = promotionPage ? item.name : item.fullName;
        const action = promotionPage ? (enabled ? 'Tắt khuyến mãi' : 'Bật khuyến mãi') : employeePage ? (enabled ? 'Cho nhân viên nghỉ việc' : 'Cho nhân viên làm việc lại') : (enabled ? 'Khóa tài khoản' : 'Mở khóa tài khoản');
        byId('managementConfirmTitle').textContent = action;
        byId('managementConfirmSubmit').textContent = action;
        byId('managementConfirmMessage').textContent = promotionPage
            ? `${action} “${name}”? Khách hàng ${enabled ? 'sẽ không thể áp dụng chương trình này cho đơn mới.' : 'có thể áp dụng chương trình khi đủ điều kiện và trong thời gian hiệu lực.'}`
            : `${action} “${name}”? ${enabled ? 'Tài khoản sẽ không thể đăng nhập cho đến khi được kích hoạt lại.' : 'Tài khoản có thể đăng nhập trở lại.'}`;
        confirmation = { id: promotionPage ? item.promotionId : item.id, status };
        byId('managementConfirm').showModal();
    }

    byId('managementConfirmForm').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        if (form.dataset.busy === 'true' || !confirmation) return;
        formBusy(form, true);
        try {
            const updated = await mutate(`${endpoint}/${encodeURIComponent(confirmation.id)}/status`, 'PATCH', { status: confirmation.status });
            replaceItem(updated);
            byId('managementConfirm').close();
            notify('Đã cập nhật trạng thái.', true);
        } catch (error) {
            byId('managementConfirm').close();
            notify(errorMessage(error));
        } finally {
            formBusy(form, false);
            confirmation = null;
        }
    });

    function openRole(item) {
        editingEmployee = item;
        byId('employeeRoleName').textContent = item.fullName + ' · ' + item.id;
        byId('employeeNewRole').value = item.role;
        setFormError('employeeRoleError');
        byId('employeeRoleDialog').showModal();
    }

    if (employeePage) {
        if (document.body.dataset.role !== 'ADMIN') {
            document.querySelectorAll('[data-admin-only]').forEach(option => option.remove());
        }
        byId('employeeCreate').addEventListener('click', () => {
            byId('employeeCreateForm').reset();
            byId('employeeHireDate').value = datetimeInput(new Date()).slice(0, 10);
            setFormError('employeeCreateError');
            byId('employeeCreateDialog').showModal();
        });
        byId('employeeCreateForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            if (form.dataset.busy === 'true') return;
            const data = Object.fromEntries(new FormData(form));
            data.address = data.address.trim() || null;
            formBusy(form, true);
            setFormError('employeeCreateError');
            try {
                replaceItem(await mutate(endpoint, 'POST', data));
                form.reset();
                byId('employeeCreateDialog').close();
                notify('Đã tạo tài khoản nhân viên.', true);
            } catch (error) {
                setFormError('employeeCreateError', errorMessage(error));
            } finally {
                formBusy(form, false);
            }
        });
        byId('employeeRoleForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            if (form.dataset.busy === 'true' || !editingEmployee) return;
            formBusy(form, true);
            setFormError('employeeRoleError');
            try {
                replaceItem(await mutate(`${endpoint}/${encodeURIComponent(editingEmployee.id)}/role`, 'PATCH', { role: byId('employeeNewRole').value }));
                byId('employeeRoleDialog').close();
                notify('Đã cập nhật chức vụ nhân viên.', true);
            } catch (error) {
                setFormError('employeeRoleError', errorMessage(error));
            } finally {
                formBusy(form, false);
            }
        });
    }

    function promotionTypeChanged() {
        const percent = byId('promotionType').value === 'PERCENT';
        byId('promotionValueLabel').textContent = percent ? 'Giảm giá (%)' : 'Số tiền giảm (đ)';
        byId('promotionValue').max = percent ? '100' : '999999999999';
    }

    function openPromotion(item = null) {
        editingPromotion = item;
        const form = byId('promotionForm');
        form.reset();
        byId('promotionDialogTitle').textContent = item ? 'Chỉnh sửa khuyến mãi' : 'Thêm khuyến mãi';
        if (item) {
            ['code', 'name', 'discountType', 'discountValue', 'minimumOrderValue', 'maximumDiscount', 'status'].forEach(name => {
                form.elements.namedItem(name).value = item[name] ?? '';
            });
            byId('promotionStart').value = String(item.startAt).slice(0, 16);
            byId('promotionEnd').value = String(item.endAt).slice(0, 16);
        } else {
            byId('promotionStart').value = datetimeInput(new Date());
            byId('promotionEnd').value = datetimeInput(new Date(Date.now() + 7 * 86400000));
        }
        promotionTypeChanged();
        setFormError('promotionFormError');
        byId('promotionDialog').showModal();
    }

    if (promotionPage) {
        byId('promotionCreate').addEventListener('click', () => openPromotion());
        byId('promotionType').addEventListener('change', promotionTypeChanged);
        byId('promotionForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            if (form.dataset.busy === 'true') return;
            const data = Object.fromEntries(new FormData(form));
            if (data.endAt <= data.startAt) {
                setFormError('promotionFormError', 'Thời gian kết thúc phải sau thời gian bắt đầu.');
                return;
            }
            data.discountValue = Number(data.discountValue);
            data.minimumOrderValue = Number(data.minimumOrderValue);
            data.maximumDiscount = data.maximumDiscount === '' ? null : Number(data.maximumDiscount);
            data.startAt += ':00';
            data.endAt += ':00';
            formBusy(form, true);
            setFormError('promotionFormError');
            try {
                const path = editingPromotion ? `${endpoint}/${encodeURIComponent(editingPromotion.promotionId)}` : endpoint;
                replaceItem(await mutate(path, editingPromotion ? 'PATCH' : 'POST', data));
                byId('promotionDialog').close();
                notify(editingPromotion ? 'Đã cập nhật khuyến mãi.' : 'Đã tạo khuyến mãi.', true);
            } catch (error) {
                setFormError('promotionFormError', errorMessage(error));
            } finally {
                formBusy(form, false);
            }
        });
    }

    document.querySelectorAll('[data-close-dialog]').forEach(node => node.addEventListener('click', () => node.closest('dialog').close()));
    document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('cancel', event => {
        if (dialog.querySelector('form')?.dataset.busy === 'true') event.preventDefault();
    }));
    search.addEventListener('input', render);
    statusFilter.addEventListener('change', render);
    roleFilter?.addEventListener('change', render);
    byId('managementRefresh').addEventListener('click', load);
    load();
});
