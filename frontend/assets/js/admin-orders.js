document.addEventListener('DOMContentLoaded',()=>{
    'use strict';
    const A=window.CraveAdmin,byId=id=>document.getElementById(id),rows=[...document.querySelectorAll('[data-order-row]')],modal=byId('orderDetailModal');
    const pageSize=12;let page=1;
    document.querySelectorAll('[data-date]').forEach(node=>node.textContent=A.date(node.dataset.date));
    document.querySelectorAll('[data-order-status]').forEach(node=>node.innerHTML=A.badge(node.dataset.orderStatus));
    document.querySelectorAll('[data-status-label]').forEach(node=>node.textContent=A.labels[node.dataset.statusLabel]||node.dataset.statusLabel);
    function render(){
        const query=byId('orderSearch').value.trim().toLocaleLowerCase('vi-VN'),status=byId('orderStatusFilter').value,type=byId('orderTypeFilter').value;
        const filtered=rows.filter(row=>{const d=row.dataset;return(!status||d.status===status)&&(!type||d.type===type)&&(!query||[d.orderId,d.name,d.phone].some(value=>String(value||'').toLocaleLowerCase('vi-VN').includes(query)));});
        const max=Math.max(1,Math.ceil(filtered.length/pageSize));page=Math.min(Math.max(1,page),max);rows.forEach(row=>row.hidden=true);filtered.slice((page-1)*pageSize,page*pageSize).forEach(row=>row.hidden=false);byId('orderEmpty').hidden=filtered.length>0;byId('orderResultCount').textContent=filtered.length+' đơn hàng · Trang '+page+'/'+max;byId('orderPrev').disabled=page<=1;byId('orderNext').disabled=page>=max;
    }
    for(const id of ['orderSearch','orderStatusFilter','orderTypeFilter'])byId(id).addEventListener('input',()=>{page=1;render();});
    byId('orderPrev').addEventListener('click',()=>{page--;render();});byId('orderNext').addEventListener('click',()=>{page++;render();});byId('refreshBtn').addEventListener('click',()=>location.reload());
    document.querySelectorAll('button[data-order-id]').forEach(button=>button.addEventListener('click',()=>{const url=new URL(location.href);url.searchParams.set('detail',button.dataset.orderId);location.href=url;}));
    function fill(id,statuses){const select=byId(id);if(!select)return;statuses.forEach(status=>{const option=document.createElement('option');option.value=status;option.textContent=A.labels[status]||status;select.appendChild(option);});select.closest('form').hidden=!statuses.length;select.closest('form').style.display=statuses.length?'flex':'none';}
    if(modal.dataset.open==='true'){
        const orderStatus=modal.dataset.status,paymentStatus=modal.dataset.paymentStatus;
        const orders={PENDING_CONFIRMATION:['PREPARING','CANCELLED'],PREPARING:[modal.dataset.type==='PICKUP'?'COMPLETED':'DELIVERING','CANCELLED'],DELIVERING:['COMPLETED']}[orderStatus]||[];
        const payments={PENDING:orderStatus==='CANCELLED'?['FAILED']:['SUCCESS','FAILED'],FAILED:orderStatus==='CANCELLED'?['PENDING']:['PENDING','SUCCESS'],SUCCESS:['REFUNDED']}[paymentStatus]||[];
        fill('detailOrderStatus',orders);fill('detailPaymentStatus',payments);byId('orderFinished').hidden=orders.length>0;modal.hidden=false;modal.style.display='flex';byId('closeOrderDetail').focus();
    }
    byId('paymentStatusForm')?.addEventListener('submit',event=>{event.currentTarget.elements.transactionRef.value='ADMIN_'+Date.now();});
    function close(){modal.hidden=true;modal.style.display='none';}
    byId('closeOrderDetail').addEventListener('click',close);byId('closeOrderDetailFooter').addEventListener('click',close);modal.addEventListener('click',event=>{if(event.target===modal)close();});render();
});
