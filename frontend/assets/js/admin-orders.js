document.addEventListener('DOMContentLoaded', async () => {
    const listEl = document.getElementById('adminOrderList');
    try {
        const res = await fetch('/crave/api/admin/orders');
        const data = await res.json();
        
        if (res.ok && data.success) {
            const orders = data.data;
            if (orders.length === 0) {
                listEl.innerHTML = '<tr><td colspan="6">Không có đơn hàng.</td></tr>';
                return;
            }
            
            listEl.innerHTML = '';
            orders.forEach(o => {
                const tr = document.createElement('tr');
                
                const tdId = document.createElement('td');
                tdId.textContent = '#' + o.orderId;
                
                const tdName = document.createElement('td');
                tdName.textContent = o.receiverName;
                
                const tdDate = document.createElement('td');
                tdDate.textContent = new Date(o.orderedAt).toLocaleString('vi-VN');
                
                const tdTotal = document.createElement('td');
                tdTotal.textContent = (o.totalAmount ? o.totalAmount : 0).toLocaleString('vi-VN') + ' đ';
                
                const tdStatus = document.createElement('td');
                const select = document.createElement('select');
                select.id = 'status_' + o.orderId;
                select.className = 'status-select';
                
                const options = [
                    {val: 'PENDING_CONFIRMATION', text: 'Chờ xác nhận'},
                    {val: 'PREPARING', text: 'Đang chuẩn bị'},
                    {val: 'DELIVERING', text: 'Đang giao'},
                    {val: 'COMPLETED', text: 'Hoàn tất'},
                    {val: 'CANCELLED', text: 'Đã hủy'}
                ];
                options.forEach(opt => {
                    const optionEl = document.createElement('option');
                    optionEl.value = opt.val;
                    optionEl.textContent = opt.text;
                    if (o.status === opt.val) {
                        optionEl.selected = true;
                    }
                    select.appendChild(optionEl);
                });
                tdStatus.appendChild(select);
                
                const tdAction = document.createElement('td');
                const btnUpdate = document.createElement('button');
                btnUpdate.className = 'btn-update';
                btnUpdate.textContent = 'Cập nhật';
                btnUpdate.onclick = () => updateStatus(o.orderId);
                tdAction.appendChild(btnUpdate);
                
                tr.append(tdId, tdName, tdDate, tdTotal, tdStatus, tdAction);
                listEl.appendChild(tr);
            });
        }
    } catch(e) {}
});

async function updateStatus(orderId) {
    const status = document.getElementById('status_' + orderId).value;
    const payload = { status: status, note: "Admin cập nhật" };
    
    try {
        const res = await fetch('/crave/api/admin/orders/' + orderId + '/status', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (res.ok && data.success) {
            alert('Cập nhật thành công');
        } else {
            alert('Lỗi: ' + data.message);
        }
    } catch (err) {
        alert('Lỗi kết nối');
    }
}
