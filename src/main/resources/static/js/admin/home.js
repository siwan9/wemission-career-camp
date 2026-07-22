document.addEventListener("DOMContentLoaded", () => {
    hideFlashAlerts();
    bindRecruitmentCreateToggle();
    bindRecruitmentStatusDragAndDrop();
});

function hideFlashAlerts() {
    document.querySelectorAll(".admin-home-alert").forEach((alert) => {
        setTimeout(() => {
            alert.classList.add("is-hidden");
            setTimeout(() => {
                alert.remove();
            }, 260);
        }, 3200);
    });
}

function bindRecruitmentCreateToggle() {
    const recruitmentCreate = document.querySelector("[data-recruitment-create]");
    const toggleButton = document.querySelector("[data-recruitment-create-toggle]");

    if (!recruitmentCreate || !toggleButton) {
        return;
    }

    const syncExpanded = () => {
        const expanded = recruitmentCreate.classList.contains("is-open");
        toggleButton.setAttribute("aria-expanded", expanded ? "true" : "false");
        toggleButton.setAttribute("aria-label", expanded ? "모집 추가 폼 닫기" : "모집 추가 폼 열기");
    };

    syncExpanded();

    toggleButton.addEventListener("click", () => {
        recruitmentCreate.classList.toggle("is-open");
        syncExpanded();
    });
}

function bindRecruitmentStatusDragAndDrop() {
    const recruitmentRows = document.querySelectorAll("[data-recruitment-id]");
    const statusGroups = document.querySelectorAll("[data-recruitment-status]");
    let draggedRow = null;

    recruitmentRows.forEach((row) => {
        row.addEventListener("dragstart", (event) => {
            if (event.target.closest("button, form")) {
                event.preventDefault();
                return;
            }

            draggedRow = row;
            row.classList.add("is-dragging");
            event.dataTransfer.effectAllowed = "move";
            event.dataTransfer.setData("text/plain", row.dataset.recruitmentId);
        });

        row.addEventListener("dragend", () => {
            row.classList.remove("is-dragging");
            draggedRow = null;
            statusGroups.forEach((group) => group.classList.remove("is-drag-over"));
        });
    });

    statusGroups.forEach((group) => {
        group.addEventListener("dragover", (event) => {
            if (!draggedRow || group.dataset.recruitmentStatus === draggedRow.dataset.recruitmentCurrentStatus) {
                return;
            }

            event.preventDefault();
            event.dataTransfer.dropEffect = "move";
            group.classList.add("is-drag-over");
        });

        group.addEventListener("dragleave", (event) => {
            if (!group.contains(event.relatedTarget)) {
                group.classList.remove("is-drag-over");
            }
        });

        group.addEventListener("drop", async (event) => {
            event.preventDefault();
            group.classList.remove("is-drag-over");

            if (!draggedRow) {
                return;
            }

            const recruitmentId = draggedRow.dataset.recruitmentId;
            const recruitmentName = draggedRow.dataset.recruitmentName;
            const currentStatus = draggedRow.dataset.recruitmentCurrentStatus;
            const nextStatus = group.dataset.recruitmentStatus;
            const nextStatusLabel = group.dataset.recruitmentStatusLabel;

            if (currentStatus === nextStatus) {
                return;
            }

            if (!confirmStatusChange(recruitmentName, nextStatus, nextStatusLabel)) {
                return;
            }

            await changeRecruitmentStatus(recruitmentId, nextStatus);
        });
    });
}

function confirmStatusChange(recruitmentName, nextStatus, nextStatusLabel) {
    const impactMessageByStatus = {
        OPEN: "사용자 신청 화면에 바로 반영됩니다.",
        WAITING: "신청은 막고 강좌 목록만 공개됩니다.",
        CLOSED: "신청은 종료되며 진행성 모집이 없을 때 결과 확인용으로 공개될 수 있습니다."
    };
    const impactMessage = impactMessageByStatus[nextStatus] || "모집 상태가 변경됩니다.";

    return window.confirm(`'${recruitmentName}' 모집을 ${nextStatusLabel}(으)로 변경할까요?\n${impactMessage}`);
}

async function changeRecruitmentStatus(recruitmentId, nextStatus) {
    const body = new URLSearchParams();
    body.set("status", nextStatus);

    try {
        const response = await fetch(`/admin/recruitments/${recruitmentId}/status`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                "X-Requested-With": "XMLHttpRequest"
            },
            body
        });

        if (window.handleAdminSessionExpiry && await window.handleAdminSessionExpiry(response)) {
            return;
        }

        const result = await response.json().catch(() => ({
            success: false,
            message: "모집 상태 변경 응답을 확인할 수 없습니다."
        }));

        if (!response.ok || !result.success) {
            window.alert(result.message || "모집 상태 변경에 실패했습니다.");
            return;
        }

        window.location.reload();
    } catch (error) {
        window.alert("네트워크 상태가 불안정해 모집 상태를 변경하지 못했습니다.");
    }
}
