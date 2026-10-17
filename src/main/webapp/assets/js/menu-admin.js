'use strict';
const adminById = id => document.getElementById(id);
function menuModal(id, open) { const modal=adminById(id); if(modal){modal.hidden=!open;modal.style.display=open?'flex':'none';} }
function menuPost(action, fields = {}, categories = false) { window.CraveAdmin.submit(categories?'/admin/categories/action':'/admin/menu/action',{action,...fields}); }
function openCreateCategoryModal(){const form=adminById('categoryForm');form.reset();form.elements.action.value='create';adminById('categoryModalTitle').textContent='Thêm danh mục mới';menuModal('categoryModal',true);}
function openEditCategoryModal(id,name,description){const form=adminById('categoryForm');form.elements.action.value='update';form.elements.id.value=id;form.elements.name.value=name;form.elements.description.value=description||'';adminById('categoryModalTitle').textContent='Chỉnh sửa danh mục';menuModal('categoryModal',true);}
function closeCategoryModal(){menuModal('categoryModal',false);}
function deleteCategory(id,name,count){if(count>0){alert('Không thể xóa danh mục đang có món ăn.');return;}if(confirm('Bạn có chắc chắn muốn xóa danh mục “'+name+'” không?'))menuPost('delete',{id},true);}
function openCreateFoodModal(){const form=adminById('foodForm');form.reset();form.elements.action.value='create';form.elements.id.value='';['categoryId','name','price','imageUrl','description'].forEach(name=>form.elements.namedItem(name).value='');form.elements.status.value='AVAILABLE';adminById('foodModalTitle').textContent='Thêm món ăn mới';menuModal('foodModal',true);}
function closeFoodModal(){menuModal('foodModal',false);}
function openEditFoodModal(id){const url=new URL(location.href);url.searchParams.set('edit',id);url.searchParams.delete('options');location.href=url;}
function toggleFoodStatus(id,status){menuPost('status',{id,status:status==='AVAILABLE'?'UNAVAILABLE':'AVAILABLE'});}
function deleteFood(id,name){if(confirm('Bạn có chắc chắn muốn xóa món “'+name+'” không?'))menuPost('delete',{id});}
function openOptionsModal(id){const url=new URL(location.href);url.searchParams.set('options',id);url.searchParams.delete('edit');location.href=url;}
function closeOptionsModal(){menuModal('optionsModal',false);}
function toggleFoodOptionStatus(button){menuPost('optionStatus',{id:button.dataset.optionId,foodId:adminById('currentOptionFoodId').value,status:button.dataset.status==='ACTIVE'?'INACTIVE':'ACTIVE'});}
function deleteFoodOption(id,name){if(confirm('Bạn có chắc muốn xóa tùy chọn “'+name+'”?'))menuPost('optionDelete',{id,foodId:adminById('currentOptionFoodId').value});}
function handleDeleteOptionClick(button){deleteFoodOption(button.dataset.id,button.dataset.name);}
function handleToggleStatusClick(button){toggleFoodStatus(button.dataset.foodId,button.dataset.status);}
function handleOpenOptionsClick(button){openOptionsModal(button.dataset.foodId);}
function handleEditFoodClick(button){openEditFoodModal(button.dataset.foodId);}
function handleDeleteFoodClick(button){deleteFood(button.dataset.foodId,button.dataset.foodName);}
function handleEditCategoryClick(button){openEditCategoryModal(button.dataset.catId,button.dataset.catName,button.dataset.catDesc);}
function handleDeleteCategoryClick(button){deleteCategory(button.dataset.catId,button.dataset.catName,Number(button.dataset.foodCount));}
document.addEventListener('DOMContentLoaded',()=>{
 if(adminById('foodModal')?.dataset.open==='true'){adminById('foodModalTitle').textContent='Chỉnh sửa món ăn: '+adminById('foodNameInput').value;adminById('foodStatusSelect').value=adminById('foodStatusSelect').dataset.value;menuModal('foodModal',true);}
 if(adminById('optionsModal')?.dataset.open==='true')menuModal('optionsModal',true);
});
