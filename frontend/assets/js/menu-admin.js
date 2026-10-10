/**
 * Menu & Catalog Admin JavaScript (Ngày 2)
 * Nguyễn Minh Huân
 */

const contextPath = (typeof window.CONTEXT_PATH === 'string')
    ? window.CONTEXT_PATH
    : (window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1)) || '');

function getApiUrl(endpoint) {
    return (contextPath.startsWith('/') && contextPath.length > 1 ? contextPath : '') + endpoint;
}

// ==========================================
// Toast Feedback
// ==========================================

function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : 'ℹ️');
    toast.innerHTML = `<span class="toast-icon">${icon}</span> <span class="toast-msg">${message}</span>`;

    container.appendChild(toast);
    setTimeout(() => {
        toast.classList.add('show');
    }, 10);

    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// ==========================================
// Category Management
// ==========================================

function openCreateCategoryModal() {
    const modal = document.getElementById('categoryModal');
    if (!modal) return;
    document.getElementById('categoryModalTitle').textContent = 'Thêm danh mục mới';
    document.getElementById('categoryModalId').value = '';
    document.getElementById('catNameInput').value = '';
    document.getElementById('catDescInput').value = '';
    hideAlert('categoryFormAlert');
    modal.style.display = 'flex';
}

function openEditCategoryModal(id, name, desc) {
    const modal = document.getElementById('categoryModal');
    if (!modal) return;
    document.getElementById('categoryModalTitle').textContent = 'Chỉnh sửa danh mục';
    document.getElementById('categoryModalId').value = id;
    document.getElementById('catNameInput').value = name;
    document.getElementById('catDescInput').value = desc && desc !== '—' ? desc : '';
    hideAlert('categoryFormAlert');
    modal.style.display = 'flex';
}

function closeCategoryModal() {
    const modal = document.getElementById('categoryModal');
    if (modal) modal.style.display = 'none';
}

async function submitCategoryForm() {
    const id = document.getElementById('categoryModalId').value;
    const name = document.getElementById('catNameInput').value.trim();
    const description = document.getElementById('catDescInput').value.trim();

    if (!name) {
        showAlert('categoryFormAlert', 'Tên danh mục không được để trống.');
        return;
    }

    const payload = { name, description };
    const isEdit = !!id;
    const url = isEdit ? getApiUrl(`/api/admin/categories/${id}`) : getApiUrl('/api/admin/categories');
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (response.ok && data.success) {
            closeCategoryModal();
            showToast(isEdit ? 'Đã cập nhật danh mục!' : 'Đã thêm danh mục mới!');
            setTimeout(() => window.location.reload(), 600);
        } else {
            const errorMsg = data.error?.message || 'Đã xảy ra lỗi khi lưu danh mục.';
            showAlert('categoryFormAlert', errorMsg);
        }
    } catch (e) {
        showAlert('categoryFormAlert', 'Không thể kết nối đến máy chủ: ' + e.message);
    }
}

async function deleteCategory(id, name, foodCount) {
    if (foodCount > 0) {
        alert(`Không thể xóa danh mục "${name}" vì đang có ${foodCount} món ăn. Vui lòng xóa hoặc chuyển danh mục các món trước.`);
        return;
    }

    if (!confirm(`Bạn có chắc chắn muốn xóa danh mục "${name}" không?`)) {
        return;
    }

    try {
        const response = await fetch(getApiUrl(`/api/admin/categories/${id}`), {
            method: 'DELETE'
        });
        const data = await response.json();
        if (response.ok && data.success) {
            showToast('Đã xóa danh mục thành công!');
            const row = document.getElementById(`catRow-${id}`);
            if (row) row.remove();
        } else {
            showToast(data.error?.message || 'Không thể xóa danh mục.', 'error');
        }
    } catch (e) {
        showToast('Lỗi kết nối máy chủ: ' + e.message, 'error');
    }
}

// ==========================================
// Food Management
// ==========================================

function openCreateFoodModal() {
    const modal = document.getElementById('foodModal');
    if (!modal) return;
    document.getElementById('foodModalTitle').textContent = 'Thêm món ăn mới';
    document.getElementById('foodModalId').value = '';
    document.getElementById('foodCategorySelect').value = '';
    document.getElementById('foodNameInput').value = '';
    document.getElementById('foodPriceInput').value = '';
    document.getElementById('foodImageInput').value = '';
    document.getElementById('foodDescInput').value = '';
    document.getElementById('foodStatusSelect').value = 'AVAILABLE';
    hideAlert('foodFormAlert');
    modal.style.display = 'flex';
}

async function openEditFoodModal(id) {
    const modal = document.getElementById('foodModal');
    if (!modal) return;
    hideAlert('foodFormAlert');

    try {
        const res = await fetch(getApiUrl(`/api/admin/foods/${id}`));
        const data = await res.json();
        if (!res.ok || !data.success) {
            showToast(data.error?.message || 'Không thể tải thông tin món ăn.', 'error');
            return;
        }

        const food = data.data;
        document.getElementById('foodModalTitle').textContent = 'Chỉnh sửa món ăn: ' + food.name;
        document.getElementById('foodModalId').value = food.id;
        document.getElementById('foodCategorySelect').value = food.categoryId;
        document.getElementById('foodNameInput').value = food.name;
        document.getElementById('foodPriceInput').value = food.price;
        document.getElementById('foodImageInput').value = food.imageUrl || '';
        document.getElementById('foodDescInput').value = food.description || '';
        document.getElementById('foodStatusSelect').value = food.status;

        modal.style.display = 'flex';
    } catch (e) {
        showToast('Lỗi kết nối: ' + e.message, 'error');
    }
}

function closeFoodModal() {
    const modal = document.getElementById('foodModal');
    if (modal) modal.style.display = 'none';
}

async function submitFoodForm() {
    const id = document.getElementById('foodModalId').value;
    const categoryId = document.getElementById('foodCategorySelect').value;
    const name = document.getElementById('foodNameInput').value.trim();
    const price = parseFloat(document.getElementById('foodPriceInput').value);
    const imageUrl = document.getElementById('foodImageInput').value.trim();
    const description = document.getElementById('foodDescInput').value.trim();
    const status = document.getElementById('foodStatusSelect').value;

    if (!categoryId) {
        showAlert('foodFormAlert', 'Vui lòng chọn danh mục cho món ăn.');
        return;
    }
    if (!name) {
        showAlert('foodFormAlert', 'Tên món ăn không được để trống.');
        return;
    }
    if (isNaN(price) || price <= 0) {
        showAlert('foodFormAlert', 'Giá món ăn phải lớn hơn 0.');
        return;
    }

    const payload = { categoryId, name, price, imageUrl, description, status };
    const isEdit = !!id;
    const url = isEdit ? getApiUrl(`/api/admin/foods/${id}`) : getApiUrl('/api/admin/foods');
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (response.ok && data.success) {
            closeFoodModal();
            showToast(isEdit ? 'Đã cập nhật món ăn!' : 'Đã tạo món ăn mới thành công!');
            setTimeout(() => window.location.reload(), 600);
        } else {
            const errorMsg = data.error?.message || 'Đã xảy ra lỗi khi lưu món ăn.';
            showAlert('foodFormAlert', errorMsg);
        }
    } catch (e) {
        showAlert('foodFormAlert', 'Lỗi kết nối: ' + e.message);
    }
}

async function toggleFoodStatus(id, currentStatus) {
    const nextStatus = currentStatus === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE';

    try {
        const response = await fetch(getApiUrl(`/api/admin/foods/${id}/status`), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: nextStatus })
        });

        const data = await response.json();
        if (response.ok && data.success) {
            showToast(`Đã chuyển trạng thái sang: ${nextStatus === 'AVAILABLE' ? 'Đang bán' : 'Tạm hết'}`);
            setTimeout(() => window.location.reload(), 500);
        } else {
            showToast(data.error?.message || 'Không thể đổi trạng thái.', 'error');
        }
    } catch (e) {
        showToast('Lỗi kết nối: ' + e.message, 'error');
    }
}

async function deleteFood(id, name) {
    if (!confirm(`Bạn có chắc chắn muốn xóa món "${name}" không?`)) {
        return;
    }

    try {
        const response = await fetch(getApiUrl(`/api/admin/foods/${id}`), {
            method: 'DELETE'
        });
        const data = await response.json();
        if (response.ok && data.success) {
            showToast('Đã xóa món ăn thành công!');
            const row = document.getElementById(`foodRow-${id}`);
            if (row) row.remove();
        } else {
            showToast(data.error?.message || 'Không thể xóa món ăn.', 'error');
        }
    } catch (e) {
        showToast('Lỗi kết nối máy chủ: ' + e.message, 'error');
    }
}

// ==========================================
// Food Options Management
// ==========================================

async function openOptionsModal(foodId, foodName) {
    const modal = document.getElementById('optionsModal');
    if (!modal) return;

    document.getElementById('currentOptionFoodId').value = foodId;
    document.getElementById('optionsModalFoodName').textContent = 'Món ăn: ' + foodName;
    hideAlert('optionsModalAlert');
    modal.style.display = 'flex';

    await loadFoodOptions(foodId);
}

function closeOptionsModal() {
    const modal = document.getElementById('optionsModal');
    if (modal) modal.style.display = 'none';
}

async function loadFoodOptions(foodId) {
    const tbody = document.getElementById('optionsTableBody');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-2">Đang tải tùy chọn...</td></tr>';

    try {
        const res = await fetch(getApiUrl(`/api/admin/foods/${foodId}/options`));
        const data = await res.json();
        if (res.ok && data.success) {
            const options = data.data;
            if (!options || options.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center py-2 text-muted">Chưa có tùy chọn nào cho món này.</td></tr>';
                return;
            }

            tbody.innerHTML = options.map(opt => `
                <tr id="optRow-${opt.id}">
                    <td><code>${opt.optionType}</code></td>
                    <td><strong>${opt.name}</strong></td>
                    <td>+${(opt.extraPrice || 0).toLocaleString('vi-VN')} ₫</td>
                    <td><span class="badge ${opt.status === 'ACTIVE' ? 'available' : 'unavailable'}">${opt.status === 'ACTIVE' ? 'Khả dụng' : 'Khóa'}</span></td>
                    <td style="text-align: right;">
                        <button type="button" class="action-btn delete-btn" onclick="deleteFoodOption('${opt.id}', '${opt.name}')" title="Xóa">
                            🗑️
                        </button>
                    </td>
                </tr>
            `).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="5" class="text-center py-2 text-error">${data.error?.message || 'Lỗi tải tùy chọn'}</td></tr>`;
        }
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-2 text-error">Lỗi kết nối: ${e.message}</td></tr>`;
    }
}

async function submitAddOption() {
    const foodId = document.getElementById('currentOptionFoodId').value;
    const optionType = document.getElementById('newOptType').value;
    const name = document.getElementById('newOptName').value.trim();
    const extraPrice = parseFloat(document.getElementById('newOptExtra').value) || 0;

    if (!name) {
        showAlert('optionsModalAlert', 'Tên tùy chọn không được để trống.');
        return;
    }

    const payload = { foodId, optionType, name, extraPrice, status: 'ACTIVE' };

    try {
        const res = await fetch(getApiUrl(`/api/admin/foods/${foodId}/options`), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        if (res.ok && data.success) {
            showToast('Đã thêm tùy chọn mới!');
            document.getElementById('newOptName').value = '';
            document.getElementById('newOptExtra').value = '0';
            hideAlert('optionsModalAlert');
            await loadFoodOptions(foodId);
        } else {
            showAlert('optionsModalAlert', data.error?.message || 'Không thể tạo tùy chọn.');
        }
    } catch (e) {
        showAlert('optionsModalAlert', 'Lỗi kết nối: ' + e.message);
    }
}

async function deleteFoodOption(optionId, optName) {
    if (!confirm(`Bạn có chắc muốn xóa tùy chọn "${optName}"?`)) {
        return;
    }

    try {
        const res = await fetch(getApiUrl(`/api/admin/options/${optionId}`), {
            method: 'DELETE'
        });
        const data = await res.json();
        if (res.ok && data.success) {
            showToast('Đã xóa tùy chọn!');
            const row = document.getElementById(`optRow-${optionId}`);
            if (row) row.remove();
        } else {
            showToast(data.error?.message || 'Không thể xóa tùy chọn.', 'error');
        }
    } catch (e) {
        showToast('Lỗi kết nối: ' + e.message, 'error');
    }
}

// Helpers
function showAlert(elementId, msg) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = msg;
    el.style.display = 'block';
}

function hideAlert(elementId) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = '';
    el.style.display = 'none';
}
