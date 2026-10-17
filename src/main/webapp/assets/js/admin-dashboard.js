document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const A=window.CraveAdmin;
    const daily=[...document.querySelectorAll('[data-daily]')].map(node=>({date:node.dataset.date,orderCount:Number(node.dataset.orderCount),revenue:Number(node.dataset.revenue)}));
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

    document.querySelectorAll('[data-date]:not([data-daily])').forEach(node=>node.textContent=A.date(node.dataset.date,node.hasAttribute('data-time')));
    document.querySelectorAll('[data-order-status]').forEach(node=>node.innerHTML=A.badge(node.dataset.orderStatus));
    const colors={PENDING_CONFIRMATION:'#f2a93b',PREPARING:'#e8552b',DELIVERING:'#3e7cb1',COMPLETED:'#2f9e5b',CANCELLED:'#c1432c'};
    document.querySelectorAll('[data-status-line]').forEach(node=>{const percent=Number(node.dataset.total)?Math.round(100*Number(node.dataset.count)/Number(node.dataset.total)):0;node.querySelector('[data-status-label]').textContent=A.labels[node.dataset.status];node.querySelector('[data-percent]').textContent='('+percent+'%)';const bar=node.querySelector('.status-bar span');bar.style.width=percent+'%';bar.style.background=colors[node.dataset.status];});
    chart(daily);window.addEventListener('resize',()=>chart(daily));
    const form=document.getElementById('reportForm');
    if(!form)return;
    form.addEventListener('submit',event=>{if(form.elements.from.value>form.elements.to.value){event.preventDefault();const alert=document.getElementById('dashboardAlert');alert.textContent='Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.';alert.hidden=false;}});
    document.getElementById('reportQuickRange').addEventListener('change',event=>{const end=new Date(),start=new Date(end);start.setDate(end.getDate()-Number(event.target.value)+1);const ymd=date=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);form.elements.from.value=ymd(start);form.elements.to.value=ymd(end);form.requestSubmit();});
    document.getElementById('exportReport').addEventListener('click',()=>{
        const safe=value=>'"'+String(value??'').replace(/^[=+\-@]/,"'$&").replace(/"/g,'""')+'"';
        const rows=[['Ngày','Số đơn hàng','Doanh thu đã thanh toán (VND)'],...daily.map(day=>[day.date,day.orderCount,day.revenue])];
        const blob=new Blob(['\ufeff'+rows.map(row=>row.map(safe).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8;'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='crave-bao-cao-'+form.elements.from.value+'-'+form.elements.to.value+'.csv';link.click();URL.revokeObjectURL(url);
    });
});
