document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const A = window.CraveAdmin, list = document.getElementById('adminOrderList'), modal = document.getElementById('orderDetailModal');
    let orders = [], page = 1, selectedId = null;
    const pageSize = 12;
    const orderTransitions = order => ({PENDING_CONFIRMATION:['PREPARING','CANCELLED'],PREPARING:[order.fulfillmentType === 'PICKUP' ? 'COMPLETED' : 'DELIVERING','CANCELLED'],DELIVERING:['COMPLETED']}[order.status] || []);
    const paymentTransitions = order => ({PENDING:order.status === 'CANCELLED' ? ['FAILED'] : ['SUCCESS','FAILED'],FAILED:order.status === 'CANCELLED' ? ['PENDING'] : ['PENDING','SUCCESS'],SUCCESS:['REFUNDED']}[order.payment?.status] || []);
    function showError(message) {const alert = document.getElementById('adminAlert');alert.textContent=message;alert.hidden=false;}
    function filterOrders() {
        const query = document.getElementById('orderSearch').value.trim().toLocaleLowerCase('vi-VN'), status = document.getElementById('orderStatusFilter').value, type = document.getElementById('orderTypeFilter').value;
        return orders.filter(order => (!status || order.status === status) && (!type || order.fulfillmentType === type) && (!query || [order.orderId,order.receiverName,order.receiverPhone].some(value=>String(value || '').toLocaleLowerCase('vi-VN').includes(query))));
    }
    function render() {
        A.closeDropdown();
        const filtered=filterOrders(), max=Math.max(1,Math.ceil(filtered.length/pageSize));page=Math.min(page,max);
        list.innerHTML = filtered.slice((page-1)*pageSize,page*pageSize).map(order=>`<tr><td><strong>${A.escape(order.orderId)}</strong><small>${order.fulfillmentType==='PICKUP'?'Nhận tại cửa hàng':'Giao tận nơi'}</small></td><td><strong>${A.escape(order.receiverName || 'Khách hàng')}</strong><small>${A.escape(order.receiverPhone)}</small></td><td>${A.date(order.orderedAt)}</td><td><strong class="price-text">${A.money(order.totalAmount)}</strong></td><td>${A.badge(order.status)}</td><td>${A.badge(order.payment?.status)}<small>${order.payment?.method==='COD'?'Tiền mặt':'Chuyển khoản'}</small></td><td><div class="row-actions"><button class="button outline btn-sm" type="button" data-order-id="${A.escape(order.orderId)}">Chi tiết và xử lý đơn</button></div></td></tr>`).join('') || '<tr><td colspan="7" class="empty-state">Không có đơn hàng phù hợp với bộ lọc.</td></tr>';
        list.querySelectorAll('[data-order-id]').forEach(button => button.addEventListener('click', () => openDetail(button.dataset.orderId)));
        A.enhanceActions(list);
        document.getElementById('orderResultCount').textContent=`${filtered.length} đơn hàng · Trang ${page}/${max}`;
        document.getElementById('orderPrev').disabled=page<=1;document.getElementById('orderNext').disabled=page>=max;
    }
    async function loadOrders() {
        document.getElementById('adminAlert').hidden=true;document.getElementById('refreshBtn').disabled=true;
        try {
            orders=await A.request('/api/admin/orders');orders.sort((a,b)=>String(b.orderedAt).localeCompare(String(a.orderedAt)));
            document.getElementById('orderTotal').textContent=orders.length;
            document.getElementById('orderPending').textContent=orders.filter(o=>o.status==='PENDING_CONFIRMATION').length;
            document.getElementById('orderCompleted').textContent=orders.filter(o=>o.status==='COMPLETED').length;
            document.getElementById('orderProcessing').textContent=orders.filter(o=>['PREPARING','DELIVERING'].includes(o.status)).length;
            render(); if(selectedId && !modal.hidden)openDetail(selectedId);
        } catch(error){showError(error.message);list.innerHTML='<tr><td colspan="7" class="empty-state">Không thể tải đơn hàng. Hãy bấm “Tải lại dữ liệu”.</td></tr>';}
        finally{document.getElementById('refreshBtn').disabled=false;}
    }
    const options = statuses => statuses.map(s=>`<option value="${s}">${A.escape(A.labels[s])}</option>`).join('');
    function openDetail(id) {
        const order=orders.find(o=>o.orderId===id);if(!order)return;selectedId=id;
        document.getElementById('orderDetailTitle').textContent='Đơn hàng '+order.orderId;
        document.getElementById('orderDetailSubtitle').textContent=A.date(order.orderedAt)+' · '+(order.fulfillmentType==='PICKUP'?'Nhận tại cửa hàng':'Giao tận nơi');
        const nextOrders=orderTransitions(order),nextPayments=paymentTransitions(order);
        document.getElementById('orderDetailBody').innerHTML=`<dl class="detail-grid"><div><dt>Người nhận</dt><dd>${A.escape(order.receiverName)}</dd></div><div><dt>Số điện thoại</dt><dd>${A.escape(order.receiverPhone)}</dd></div><div><dt>Địa chỉ nhận hàng</dt><dd>${A.escape(order.deliveryAddress || 'Nhận tại cửa hàng')}</dd></div><div><dt>Ghi chú</dt><dd>${A.escape(order.customerNote || 'Không có ghi chú')}</dd></div></dl><div class="table-responsive"><table class="admin-table-sub"><thead><tr><th>Món ăn</th><th>Số lượng</th><th>Thành tiền</th></tr></thead><tbody>${(order.items||[]).map(item=>`<tr><td><strong>${A.escape(item.foodNameSnapshot || item.foodName)}</strong><small>${A.escape((item.options||[]).map(o=>o.optionName).join(', '))}</small>${item.note?`<small>${A.escape(item.note)}</small>`:''}</td><td>${item.quantity}</td><td>${A.money(item.lineTotal)}</td></tr>`).join('')}</tbody></table></div><dl class="detail-grid" style="margin-top:18px"><div><dt>Tạm tính</dt><dd>${A.money(order.subtotal)}</dd></div><div><dt>Giảm giá</dt><dd>${A.money(order.discountAmount)}</dd></div><div><dt>Phí giao hàng</dt><dd>${A.money(order.deliveryFee)}</dd></div><div><dt>Thanh toán</dt><dd>${A.badge(order.payment?.status)} · ${order.payment?.method==='COD'?'Tiền mặt':'Chuyển khoản'}</dd></div></dl><div class="order-detail-total"><span>Tổng cộng</span><strong>${A.money(order.totalAmount)}</strong></div><h4>Tiến độ đơn hàng ${A.badge(order.status)}</h4>${nextOrders.length?`<div class="order-status-control"><select id="detailOrderStatus" aria-label="Trạng thái đơn mới">${options(nextOrders)}</select><button type="button" class="button primary" id="saveOrderStatus">Cập nhật đơn</button></div>`:'<p class="text-muted">Đơn hàng đã kết thúc.</p>'}${order.payment&&nextPayments.length?`<div class="order-status-control"><select id="detailPaymentStatus" aria-label="Trạng thái thanh toán mới">${options(nextPayments)}</select><button type="button" class="button outline" id="savePaymentStatus">Cập nhật thanh toán</button></div>`:''}<h4 style="margin-top:24px">Lịch sử xử lý</h4><div class="history-list">${(order.history||[]).map(entry=>`<p>${A.escape(A.labels[entry.status] || entry.status)}<time>${A.date(entry.changedAt)}</time>${A.escape(entry.note || '')}</p>`).join('') || '<p>Chưa có lịch sử.</p>'}</div>`;
        document.getElementById('saveOrderStatus')?.addEventListener('click',event=>update(event.currentTarget,`/api/admin/orders/${encodeURIComponent(order.orderId)}/status`,{status:document.getElementById('detailOrderStatus').value,note:'Nhân viên cập nhật qua trang quản trị'}));
        document.getElementById('savePaymentStatus')?.addEventListener('click',event=>update(event.currentTarget,`/api/admin/payments/${encodeURIComponent(order.payment.paymentId)}/status`,{status:document.getElementById('detailPaymentStatus').value,transactionRef:'ADMIN_'+Date.now()}));
        modal.hidden=false;modal.style.display='flex';document.getElementById('closeOrderDetail').focus();
    }
    async function update(button,path,payload){
        button.disabled=true;
        try{await A.request(path,{method:'PATCH',body:JSON.stringify(payload)});A.toast('Đã cập nhật thành công.');await loadOrders();}
        catch(error){A.toast(error.message,'error');}finally{button.disabled=false;}
    }
    function close(){modal.hidden=true;modal.style.display='none';selectedId=null;}
    document.getElementById('closeOrderDetail').addEventListener('click',close);document.getElementById('closeOrderDetailFooter').addEventListener('click',close);
    modal.addEventListener('click',event=>{if(event.target===modal)close();});
    document.getElementById('orderSearch').addEventListener('input',A.debounce(()=>{page=1;render();}));
    for(const id of ['orderStatusFilter','orderTypeFilter'])document.getElementById(id).addEventListener('change',()=>{page=1;render();});
    document.getElementById('orderPrev').addEventListener('click',()=>{page--;render();});document.getElementById('orderNext').addEventListener('click',()=>{page++;render();});
    document.getElementById('refreshBtn').addEventListener('click',loadOrders);loadOrders();
});
