/**
 * Crave Account & Auth Form Client Integration
 * Intercepts account form submissions, sending application/json requests
 * with correct HTTP methods (POST, PUT) according to OpenAPI specifications.
 */
document.addEventListener("DOMContentLoaded", () => {
    const forms = document.querySelectorAll("form[action*='/api/']");
    forms.forEach((form) => {
        form.addEventListener("submit", async (event) => {
            event.preventDefault();

            const errorContainer = document.getElementById("form-error");
            const successContainer = document.getElementById("form-success");

            if (errorContainer) {
                errorContainer.textContent = "";
                errorContainer.style.display = "none";
            }
            if (successContainer) {
                successContainer.textContent = "";
                successContainer.style.display = "none";
            }

            const method = (form.dataset.method || form.method || "POST").toUpperCase();
            const action = form.action;

            const formData = new FormData(form);
            const payload = {};

            formData.forEach((value, key) => {
                const input = form.elements[key];
                if (input && input.type === "checkbox") {
                    payload[key] = input.checked;
                } else if (input && input.type === "number") {
                    payload[key] = value === "" ? null : Number(value);
                } else {
                    payload[key] = value;
                }
            });

            // Handle unchecked checkboxes
            form.querySelectorAll("input[type='checkbox']").forEach((cb) => {
                if (!(cb.name in payload)) {
                    payload[cb.name] = false;
                }
            });

            const submitBtn = form.querySelector("button[type='submit']");
            const originalBtnText = submitBtn ? submitBtn.textContent : "";
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = "Đang xử lý...";
            }

            try {
                const response = await fetch(action, {
                    method: method,
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json"
                    },
                    body: JSON.stringify(payload)
                });

                let result;
                try {
                    result = await response.json();
                } catch {
                    result = null;
                }

                if (response.status === 501) {
                    // Day 1 stub: contract route verified
                    const msg = result?.error?.message || "Chức năng đã được chốt hợp đồng API và sẽ hoạt động đầy đủ trong Ngày 2.";
                    if (successContainer) {
                        successContainer.textContent = `[Giao thức Day 1] ${msg}`;
                        successContainer.style.display = "block";
                    } else if (errorContainer) {
                        errorContainer.textContent = msg;
                        errorContainer.style.display = "block";
                    }
                } else if (!response.ok) {
                    const errorMsg = result?.error?.message || `Lỗi yêu cầu: ${response.status} ${response.statusText}`;
                    if (errorContainer) {
                        errorContainer.textContent = errorMsg;
                        errorContainer.style.display = "block";
                    }
                } else {
                    const successMsg = result?.data?.message || "Thao tác thành công.";
                    if (successContainer) {
                        successContainer.textContent = successMsg;
                        successContainer.style.display = "block";
                    }
                }
            } catch (err) {
                if (errorContainer) {
                    errorContainer.textContent = "Lỗi kết nối máy chủ: " + err.message;
                    errorContainer.style.display = "block";
                }
            } finally {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = originalBtnText;
                }
            }
        });
    });
});
