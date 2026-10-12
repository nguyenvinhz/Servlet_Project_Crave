document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const A=window.CraveAdmin, isReport=document.body.dataset.page==='reports';let report=null;
    const set = (id,value) => {const element=document.getElementById(id);if(element)element.textContent=value;};
    const ymd = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    function chart(data) {
        const host=document.getElementById('revenueChart');
        if(!data.length){host.innerHTML='<div class="empty-state">Chưa có dữ liệu trong kỳ.</div>';return;}
        const width=Math.max(220,Math.round(host.clientWidth)),height=230,left=58,right=20,top=18,bottom=38,plotW=width-left-right,plotH=height-top-bottom,max=Math.max(1,...data.map(d=>Number(d.revenue)||0));
        const x=i=>left+(data.length===1?plotW/2:i*plotW/(data.length-1)),y=v=>height-bottom-(Number(v)||0)/max*plotH;
        const points=data.map((d,i)=>`${x(i).toFixed(1)},${y(d.revenue).toFixed(1)}`).join(' ');
        const grid=Array.from({length:5},(_,i)=>{const value=max*i/4,pos=y(value);return `<line class="chart-grid" x1="${left}" y1="${pos}" x2="${width-right}" y2="${pos}"/><text class="chart-axis" x="${left-9}" y="${pos+4}" text-anchor="end">${value>=1000000?(value/1000000).toFixed(1)+'tr':value>=1000?Math.round(value/1000)+'k':Math.round(value)}</text>`;}).join('');
        const tickIndices=[...new Set(width<400 ? [0,data.length-1] : [0,Math.floor((data.length-1)/3),Math.floor(2*(data.length-1)/3),data.length-1])];
        host.innerHTML=`<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Doanh thu ${A.escape(A.date(data[0].date,false))} đến ${A.escape(A.date(data.at(-1).date,false))}"><defs><linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e8552b" stop-opacity=".14"/><stop offset="1" stop-color="#e8552b" stop-opacity="0"/></linearGradient></defs>${grid}<polygon points="${x(0)},${height-bottom} ${points} ${x(data.length-1)},${height-bottom}" fill="url(#revenueFill)"/><polyline points="${points}" fill="none" stroke="#e8552b" stroke-width="2.5" stroke-linejoin="round"/>${data.map((d,i)=>`<circle cx="${x(i)}" cy="${y(d.revenue)}" r="${data.length>30?1.8:3.4}" fill="#e8552b" stroke="#fff" stroke-width="1.5"><title>${A.escape(A.date(d.date,false))}: ${A.money(d.revenue)} · ${d.orderCount} đơn</title></circle>`).join('')}${tickIndices.map(i=>`<text class="chart-axis" x="${x(i)}" y="${height-10}" text-anchor="middle">${A.escape(data[i].date.slice(8,10)+'/'+data[i].date.slice(5,7))}</text>`).join('')}</svg>`;
    }
    function render(data) {
        report=data;const s=data.summary;set('summaryRevenue',A.money(s.revenue));set('summaryOrders',s.totalOrders);set('summaryCustomers',s.totalCustomers);set('summaryPending',s.pendingOrders);set('summaryCompleted',s.completedOrders);set('summaryAverage',A.money(s.averageOrderValue));set('chartRange',A.date(data.range.from,false)+' – '+A.date(data.range.to,false));chart(data.daily);
        if(isReport){
            document.getElementById('dailyReport').innerHTML=data.daily.map(day=>`<tr><td>${A.date(day.date,false)}</td><td>${day.orderCount}</td><td><strong>${A.money(day.revenue)}</strong></td></tr>`).join('');
            document.getElementById('itemsReport').innerHTML=data.topItems.map(item=>`<tr><td><strong>${A.escape(item.name)}</strong></td><td>${item.quantity}</td><td>${A.money(item.revenue)}</td></tr>`).join('') || '<tr><td colspan="3" class="empty-state">Chưa có món ăn bán được trong kỳ.</td></tr>';
            document.getElementById('exportReport').disabled=false;
        }else{
            const statuses=['PENDING_CONFIRMATION','PREPARING','DELIVERING','COMPLETED','CANCELLED'];const colors=['#f2a93b','#e8552b','#3e7cb1','#2f9e5b','#c1432c'];
            document.getElementById('statusBreakdown').innerHTML=statuses.map((status,i)=>{const count=data.statusCounts[status]||0,percent=s.totalOrders?Math.round(100*count/s.totalOrders):0;return `<div class="status-line"><span>${A.escape(A.labels[status])}</span><strong>${count} <span class="text-muted">(${percent}%)</span></strong><div class="status-bar"><span style="width:${percent}%;background:${colors[i]}"></span></div></div>`;}).join('');
            document.getElementById('recentOrders').innerHTML=data.recentOrders.map(order=>`<tr><td><a href="${A.ctx}/admin/orders"><strong>${A.escape(order.orderId)}</strong></a><small>${A.date(order.orderedAt)}</small></td><td>${A.escape(order.receiverName)}</td><td>${A.money(order.totalAmount)}</td><td>${A.badge(order.status)}</td></tr>`).join('') || '<tr><td colspan="4" class="empty-state">Chưa có đơn hàng trong kỳ.</td></tr>';
            document.getElementById('topItems').innerHTML=data.topItems.slice(0,5).map((item,i)=>`<div class="top-item"><span class="item-rank">${i+1}</span><div class="item-info"><strong>${A.escape(item.name)}</strong><small>${item.quantity} phần đã bán</small></div><div class="item-total"><strong>${A.money(item.revenue)}</strong><small>Giá trị món</small></div></div>`).join('') || '<div class="empty-state">Chưa có món ăn bán được trong kỳ.</div>';
        }
    }
    async function load(){
        const alert=document.getElementById('dashboardAlert');alert.hidden=true;
        const form=document.getElementById('reportForm'),button=form?.querySelector('button');if(button)button.disabled=true;
        try{let query='';if(isReport){const from=document.getElementById('reportFrom').value,to=document.getElementById('reportTo').value;if(from>to)throw new Error('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');query='?'+new URLSearchParams({from,to});}render(await A.request('/api/admin/overview'+query));}
        catch(error){alert.textContent=error.message;alert.hidden=false;}
        finally{if(button)button.disabled=false;}
    }
    function range(days){const end=new Date(),start=new Date(end);start.setDate(end.getDate()-days+1);document.getElementById('reportFrom').value=ymd(start);document.getElementById('reportTo').value=ymd(end);}
    window.addEventListener('resize',()=>{if(report)chart(report.daily);});
    if(isReport){range(7);document.getElementById('reportForm').addEventListener('submit',event=>{event.preventDefault();load();});document.getElementById('reportQuickRange').addEventListener('change',event=>{range(Number(event.target.value));load();});document.getElementById('exportReport').addEventListener('click',()=>{
        if(!report)return;const safe=value=>`"${String(value??'').replace(/^[=+\-@]/,"'$&").replace(/"/g,'""')}"`;
        const rows=[['Ngày','Số đơn hàng','Doanh thu đã thanh toán (VND)'],...report.daily.map(d=>[d.date,d.orderCount,d.revenue])];const blob=new Blob(['\ufeff'+rows.map(row=>row.map(safe).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8;'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=`crave-bao-cao-${report.range.from}-${report.range.to}.csv`;link.click();URL.revokeObjectURL(url);
    });}load();
});
