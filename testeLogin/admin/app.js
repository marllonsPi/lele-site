const SUPABASE_URL = "https://azhvlmkpvebrtqkngxzz.supabase.co";
const SUPABASE_PUBLISHABLE_KEY = "sb_publishable_YvMh-lOkjAXaibx8wcIVWQ_191jWxRJ";
const supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY);

const loginScreen = document.getElementById("login-screen");
const adminScreen = document.getElementById("admin-screen");
const loginForm = document.getElementById("admin-login-form");
const loginFeedback = document.getElementById("login-feedback");
const logoutButton = document.getElementById("logout-button");
const adminIdentity = document.getElementById("admin-identity");
const caseList = document.getElementById("case-list");
const caseSearch = document.getElementById("case-search");
const caseEditor = document.getElementById("case-editor");
const emptyState = document.getElementById("empty-state");
const caseForm = document.getElementById("case-form");
const caseFeedback = document.getElementById("case-feedback");
const editorTitle = document.getElementById("editor-title");
const clientSelect = document.getElementById("client-select");
const deleteCaseButton = document.getElementById("delete-case-button");
const updatesSection = document.getElementById("updates-section");
const updatesList = document.getElementById("updates-list");
const updateForm = document.getElementById("update-form");
const updateFeedback = document.getElementById("update-feedback");

let profiles = [];
let cases = [];
let selectedCase = null;
let selectedUpdates = [];

function setFeedback(element, message = "", success = false) {
  element.textContent = message;
  element.classList.toggle("success", success);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatDate(value) {
  if (!value) return "";
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function getProfileName(id) {
  const profile = profiles.find((item) => item.id === id);
  return profile?.full_name || profile?.email || "Cliente sem nome";
}

function formToObject(form) {
  const data = new FormData(form);
  return Object.fromEntries(data.entries());
}

function renderClientOptions() {
  const clients = profiles.filter((profile) => profile.role === "client");
  clientSelect.innerHTML = [
    '<option value="">Selecione um cliente</option>',
    ...clients.map((profile) =>
      `<option value="${escapeHtml(profile.id)}">${escapeHtml(profile.full_name || profile.email)}</option>`
    ),
  ].join("");
}

function renderCases() {
  const term = caseSearch.value.trim().toLowerCase();
  const filtered = cases.filter((item) => {
    const haystack = [
      item.title,
      item.process_number,
      item.status_label,
      getProfileName(item.client_id),
    ].join(" ").toLowerCase();
    return haystack.includes(term);
  });

  if (!filtered.length) {
    caseList.innerHTML = '<p class="list-empty">Nenhum caso encontrado.</p>';
    return;
  }

  caseList.innerHTML = filtered.map((item) => `
    <button class="case-item ${selectedCase?.id === item.id ? "active" : ""}" data-case-id="${item.id}" type="button">
      <strong>${escapeHtml(getProfileName(item.client_id))}</strong>
      <span>${escapeHtml(item.title)}</span>
      <span>${escapeHtml(item.process_number || "Sem número informado")}</span>
      <em>${escapeHtml(item.status_label)}</em>
    </button>
  `).join("");
}

function renderUpdates() {
  if (!selectedUpdates.length) {
    updatesList.innerHTML = '<p class="list-empty">Nenhuma atualização cadastrada.</p>';
    return;
  }

  updatesList.innerHTML = selectedUpdates.map((item) => `
    <article class="update-item">
      <div>
        <time>${escapeHtml(formatDate(item.event_date))}</time>
        <h3>${escapeHtml(item.title)}</h3>
        <p>${escapeHtml(item.description)}</p>
      </div>
      <div class="update-actions">
        <button data-edit-update="${item.id}" type="button">Editar</button>
        <button class="delete" data-delete-update="${item.id}" type="button">Excluir</button>
      </div>
    </article>
  `).join("");
}

function openNewCase() {
  selectedCase = null;
  selectedUpdates = [];
  caseForm.reset();
  caseForm.elements.id.value = "";
  editorTitle.textContent = "Novo caso";
  deleteCaseButton.hidden = true;
  updatesSection.hidden = true;
  emptyState.hidden = true;
  caseEditor.hidden = false;
  renderCases();
}

function openCase(item) {
  selectedCase = item;
  caseForm.reset();
  Object.entries(item).forEach(([key, value]) => {
    const field = caseForm.elements[key];
    if (!field) return;
    if (field.type === "checkbox") field.checked = Boolean(value);
    else field.value = value ?? "";
  });
  editorTitle.textContent = item.title;
  deleteCaseButton.hidden = false;
  updatesSection.hidden = false;
  emptyState.hidden = true;
  caseEditor.hidden = false;
  setFeedback(caseFeedback);
  loadUpdates(item.id);
  renderCases();
}

async function verifyAdmin() {
  const { data: userData } = await supabaseClient.auth.getUser();
  if (!userData.user) return false;

  const { data: profile, error } = await supabaseClient
    .from("profiles")
    .select("id,email,full_name,role")
    .eq("id", userData.user.id)
    .single();

  if (error || profile?.role !== "admin") {
    await supabaseClient.auth.signOut();
    return false;
  }

  adminIdentity.textContent = profile.full_name || profile.email;
  return true;
}

async function loadData() {
  const [profilesResult, casesResult] = await Promise.all([
    supabaseClient.from("profiles").select("id,email,full_name,role").order("full_name"),
    supabaseClient.from("cases").select("*").order("updated_at", { ascending: false }),
  ]);

  if (profilesResult.error || casesResult.error) {
    setFeedback(loginFeedback, "Não foi possível carregar os dados administrativos.");
    return;
  }

  profiles = profilesResult.data || [];
  cases = casesResult.data || [];
  renderClientOptions();
  renderCases();
}

async function loadUpdates(caseId) {
  const { data, error } = await supabaseClient
    .from("case_updates")
    .select("*")
    .eq("case_id", caseId)
    .order("event_date", { ascending: false });

  selectedUpdates = error ? [] : data || [];
  renderUpdates();
}

async function showAuthenticatedState() {
  const isAdmin = await verifyAdmin();
  loginScreen.hidden = isAdmin;
  adminScreen.hidden = !isAdmin;
  logoutButton.hidden = !isAdmin;
  if (isAdmin) await loadData();
  return isAdmin;
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setFeedback(loginFeedback);
  const payload = formToObject(loginForm);
  const button = loginForm.querySelector('button[type="submit"]');
  button.disabled = true;
  button.textContent = "Entrando...";

  const { error } = await supabaseClient.auth.signInWithPassword({
    email: payload.email.trim().toLowerCase(),
    password: payload.password,
  });

  if (error || !(await showAuthenticatedState())) {
    setFeedback(loginFeedback, "Acesso negado. Verifique os dados e a permissão administrativa.");
  }

  button.disabled = false;
  button.textContent = "Entrar no painel";
});

logoutButton.addEventListener("click", async () => {
  await supabaseClient.auth.signOut();
  location.reload();
});

document.getElementById("new-case-button").addEventListener("click", openNewCase);
document.getElementById("refresh-button").addEventListener("click", loadData);
caseSearch.addEventListener("input", renderCases);

caseList.addEventListener("click", (event) => {
  const button = event.target.closest("[data-case-id]");
  if (!button) return;
  const item = cases.find((candidate) => candidate.id === button.dataset.caseId);
  if (item) openCase(item);
});

caseForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setFeedback(caseFeedback);
  const values = formToObject(caseForm);
  const id = values.id;
  delete values.id;
  values.client_action_required = caseForm.elements.client_action_required.checked;
  values.updated_at = new Date().toISOString();
  Object.keys(values).forEach((key) => {
    if (values[key] === "") values[key] = null;
  });

  const query = id
    ? supabaseClient.from("cases").update(values).eq("id", id).select().single()
    : supabaseClient.from("cases").insert(values).select().single();
  const { data, error } = await query;

  if (error) {
    setFeedback(caseFeedback, `Não foi possível salvar: ${error.message}`);
    return;
  }

  setFeedback(caseFeedback, "Caso salvo com sucesso.", true);
  await loadData();
  openCase(data);
});

deleteCaseButton.addEventListener("click", async () => {
  if (!selectedCase || !confirm("Excluir este caso e todas as atualizações?")) return;
  const { error } = await supabaseClient.from("cases").delete().eq("id", selectedCase.id);
  if (error) {
    setFeedback(caseFeedback, `Não foi possível excluir: ${error.message}`);
    return;
  }
  selectedCase = null;
  caseEditor.hidden = true;
  emptyState.hidden = false;
  await loadData();
});

document.getElementById("new-update-button").addEventListener("click", () => {
  updateForm.reset();
  updateForm.elements.id.value = "";
  updateForm.elements.event_date.value = new Date().toISOString().slice(0, 10);
  updateForm.hidden = false;
  setFeedback(updateFeedback);
});

document.getElementById("cancel-update-button").addEventListener("click", () => {
  updateForm.hidden = true;
  updateForm.reset();
});

updateForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!selectedCase) return;
  const values = formToObject(updateForm);
  const id = values.id;
  delete values.id;
  values.case_id = selectedCase.id;
  values.is_highlight = updateForm.elements.is_highlight.checked;

  const query = id
    ? supabaseClient.from("case_updates").update(values).eq("id", id)
    : supabaseClient.from("case_updates").insert(values);
  const { error } = await query;

  if (error) {
    setFeedback(updateFeedback, `Não foi possível salvar: ${error.message}`);
    return;
  }

  await supabaseClient.from("cases")
    .update({ updated_at: new Date().toISOString() })
    .eq("id", selectedCase.id);
  updateForm.hidden = true;
  updateForm.reset();
  await loadUpdates(selectedCase.id);
  await loadData();
});

updatesList.addEventListener("click", async (event) => {
  const editButton = event.target.closest("[data-edit-update]");
  const deleteButton = event.target.closest("[data-delete-update]");

  if (editButton) {
    const item = selectedUpdates.find((candidate) => candidate.id === editButton.dataset.editUpdate);
    if (!item) return;
    updateForm.elements.id.value = item.id;
    updateForm.elements.event_date.value = item.event_date;
    updateForm.elements.title.value = item.title;
    updateForm.elements.description.value = item.description;
    updateForm.elements.is_highlight.checked = item.is_highlight;
    updateForm.hidden = false;
  }

  if (deleteButton) {
    if (!confirm("Excluir esta atualização?")) return;
    const { error } = await supabaseClient
      .from("case_updates")
      .delete()
      .eq("id", deleteButton.dataset.deleteUpdate);
    if (!error) await loadUpdates(selectedCase.id);
  }
});

showAuthenticatedState();
