document.addEventListener('DOMContentLoaded', async () => {
    const container = document.getElementById('orderContent');
    // Lấy orderId từ URL. Ví dụ /crave/orders/C12345
    const pathSegments = window.location.pathname.split('/');
    const orderId = pathSegments[pathSegments.length - 1];

    try {
        const res = await fetch('/crave/api/orders/' + orderId);
        const data = await res.json();

        if (res.ok && data.success) {
            const o = data.data;
            container.innerHTML = '';
            
            // Header
            const header = document.createElement('div');
            header.className = 'order-header';
            const h2 = document.createElement('h2');
            h2.textContent = 'Đơn hàng #' + o.orderId;
            const pStatus = document.createElement('p');
            pStatus.innerHTML = 'Trạng thái: <strong>' + o.status + '</strong>';
            const pDate = document.createElement('p');
            pDate.textContent = 'Ngày đặt: ' + new Date(o.orderedAt).toLocaleString('vi-VN');
            header.append(h2, pStatus, pDate);
            
            // Info Grid
            const grid = document.createElement('div');
            grid.className = 'info-grid';
            
            const col1 = document.createElement('div');
            const h4_1 = document.createElement('h4');
            h4_1.textContent = 'Thông tin giao hàng';
            const pName = document.createElement('p');
            pName.textContent = 'Tên: ' + o.receiverName;
            const pPhone = document.createElement('p');
            pPhone.textContent = 'SĐT: ' + o.receiverPhone;
            const pAddr = document.createElement('p');
            pAddr.textContent = 'Địa chỉ: ' + (o.deliveryAddress || 'Nhận tại quán');
            col1.append(h4_1, pName, pPhone, pAddr);
            
            const col2 = document.createElement('div');
            const h4_2 = document.createElement('h4');
            h4_2.textContent = 'Thanh toán';
            const pFulfill = document.createElement('p');
            pFulfill.textContent = 'Hình thức: ' + (o.fulfillmentType === 'DELIVERY' ? 'Giao tận nơi' : 'Lấy tại quán');
            const pNote = document.createElement('p');
            pNote.textContent = 'Ghi chú: ' + (o.customerNote || 'Không có');
            col2.append(h4_2, pFulfill, pNote);
            
            grid.append(col1, col2);
            
            // Items
            const itemList = document.createElement('div');
            itemList.className = 'item-list';
            const h4_3 = document.createElement('h4');
            h4_3.textContent = 'Danh sách món';
            itemList.appendChild(h4_3);
            
            if (o.items && o.items.length > 0) {
                o.items.forEach(item => {
                    const row = document.createElement('div');
                    row.className = 'item-row';
                    
                    const detailsDiv = document.createElement('div');
                    detailsDiv.className = 'item-details';
                    const strongItem = document.createElement('strong');
                    strongItem.textContent = item.quantity + 'x ' + item.foodNameSnapshot;
                    const smallNote = document.createElement('small');
                    smallNote.textContent = item.note || '';
                    detailsDiv.append(strongItem, smallNote);
                    
                    const priceDiv = document.createElement('div');
                    priceDiv.textContent = (item.unitPrice * item.quantity).toLocaleString('vi-VN') + ' đ';
                    
                    row.append(detailsDiv, priceDiv);
                    itemList.appendChild(row);
                });
            } else {
                const noItemP = document.createElement('p');
                noItemP.textContent = 'Chưa có chi tiết món ăn';
                itemList.appendChild(noItemP);
            }
            
            // Totals
            const totals = document.createElement('div');
            totals.className = 'totals';
            const pSub = document.createElement('p');
            pSub.textContent = 'Tạm tính: ' + (o.subtotal || 0).toLocaleString('vi-VN') + ' đ';
            const pFee = document.createElement('p');
            pFee.textContent = 'Phí giao hàng: ' + (o.deliveryFee || 0).toLocaleString('vi-VN') + ' đ';
            const h3Total = document.createElement('h3');
            h3Total.textContent = 'Tổng cộng: ' + (o.totalAmount || (o.subtotal + o.deliveryFee)).toLocaleString('vi-VN') + ' đ';
            totals.append(pSub, pFee, h3Total);
            
            container.append(header, grid, itemList, totals);
        } else {
            container.innerHTML = '';
            const p = document.createElement('p');
            p.style.color = 'red';
            p.textContent = data.message;
            container.appendChild(p);
        }
    } catch (err) {
        container.innerHTML = '';
        const p = document.createElement('p');
        p.style.color = 'red';
        p.textContent = 'Lỗi kết nối máy chủ.';
        container.appendChild(p);
    }
});
