const SUPABASE_URL = "https://azhvlmkpvebrtqkngxzz.supabase.co";
const SUPABASE_PUBLISHABLE_KEY = "sb_publishable_YvMh-lOkjAXaibx8wcIVWQ_191jWxRJ";
const DEMO_EMAIL = "cliente@teste.com";
const supabaseClient = window.supabase.createClient(
  SUPABASE_URL,
  SUPABASE_PUBLISHABLE_KEY,
);

const loginView = document.getElementById("login-view");
const dashboardView = document.getElementById("dashboard-view");
const loginForm = document.getElementById("login-form");
const emailInput = document.getElementById("email");
const accessCodeInput = document.getElementById("access-code");
const formFeedback = document.getElementById("form-feedback");
const toggleCodeButton = document.getElementById("toggle-code");
const fillDemoButton = document.getElementById("fill-demo");
const logoutButton = document.getElementById("logout-button");
const toggleTimelineButton = document.getElementById("toggle-timeline");
const timeline = document.getElementById("timeline");
let timelineUpdates = [];
let timelineExpanded = false;

function setView(isLoggedIn) {
  loginView.hidden = isLoggedIn;
  dashboardView.hidden = !isLoggedIn;
  logoutButton.hidden = !isLoggedIn;
  document.body.classList.toggle("dashboard-open", isLoggedIn);

  if (isLoggedIn) {
    document.title = "Acompanhamento do caso | Crispim & Campos";
    window.scrollTo({ top: 0, behavior: "auto" });
  } else {
    document.title = "Área do cliente | Crispim & Campos";
    emailInput.focus();
  }
}

function clearValidation() {
  formFeedback.textContent = "";
  emailInput.classList.remove("invalid");
  accessCodeInput.classList.remove("invalid");
}

fillDemoButton.addEventListener("click", () => {
  emailInput.value = DEMO_EMAIL;
  clearValidation();
  accessCodeInput.focus();
});

toggleCodeButton.addEventListener("click", () => {
  const showingCode = accessCodeInput.type === "text";
  accessCodeInput.type = showingCode ? "password" : "text";
  toggleCodeButton.textContent = showingCode ? "Mostrar" : "Ocultar";
  toggleCodeButton.setAttribute("aria-label", showingCode ? "Mostrar código" : "Ocultar código");
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearValidation();

  const email = emailInput.value.trim().toLowerCase();
  const accessCode = accessCodeInput.value;

  if (!email || !accessCode) {
    formFeedback.textContent = "Preencha o e-mail e o código de acesso.";
    emailInput.classList.toggle("invalid", !email);
    accessCodeInput.classList.toggle("invalid", !accessCode);
    return;
  }

  const submitButton = loginForm.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  submitButton.textContent = "Entrando...";

  const { error } = await supabaseClient.auth.signInWithPassword({
    email,
    password: accessCode,
  });

  if (error) {
    formFeedback.textContent = "E-mail ou código de acesso inválido.";
    accessCodeInput.classList.add("invalid");
    submitButton.disabled = false;
    submitButton.textContent = "Acessar acompanhamento";
    return;
  }

  setView(true);
  await loadClientCase();
  submitButton.disabled = false;
  submitButton.textContent = "Acessar acompanhamento";
});

logoutButton.addEventListener("click", async () => {
  await supabaseClient.auth.signOut();
  loginForm.reset();
  clearValidation();
  setView(false);
});

toggleTimelineButton.addEventListener("click", () => {
  timelineExpanded = !timelineExpanded;
  renderTimeline();
  toggleTimelineButton.textContent = timelineExpanded
    ? "Ocultar histórico anterior"
    : "Mostrar histórico completo";
});

emailInput.addEventListener("input", clearValidation);
accessCodeInput.addEventListener("input", clearValidation);

function formatDate(value, includeTime = false) {
  if (!value) return "Não informado";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Não informado";

  return new Intl.DateTimeFormat("pt-BR", includeTime
    ? { dateStyle: "long", timeStyle: "short" }
    : { dateStyle: "long", timeZone: "UTC" }
  ).format(date);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value || "Não informado";
}

function renderTimeline() {
  const visibleUpdates = timelineExpanded ? timelineUpdates : timelineUpdates.slice(0, 2);

  timeline.innerHTML = visibleUpdates.map((update, index) => `
    <li class="${index === 0 ? "current" : ""}">
      <div class="timeline-marker"></div>
      <div class="timeline-content">
        <div>
          <time datetime="${escapeHtml(update.event_date)}">${escapeHtml(formatDate(update.event_date))}</time>
          ${index === 0 ? "<span>Atualização mais recente</span>" : ""}
        </div>
        <h3>${escapeHtml(update.title)}</h3>
        <p>${escapeHtml(update.description)}</p>
      </div>
    </li>
  `).join("");

  toggleTimelineButton.hidden = timelineUpdates.length <= 2;
}

async function loadClientCase() {
  const { data: authData } = await supabaseClient.auth.getUser();
  const user = authData.user;

  const displayName =
    user?.user_metadata?.full_name ||
    user?.user_metadata?.name ||
    "";
  setText("client-greeting", displayName ? `Olá, ${displayName}.` : "Olá.");

  const { data: cases, error } = await supabaseClient
    .from("cases")
    .select(`
      *,
      case_updates (
        id,
        event_date,
        title,
        description,
        is_highlight
      )
    `)
    .order("updated_at", { ascending: false })
    .limit(1);

  if (error || !cases?.length) {
    setText("case-title", "Nenhum caso disponível");
    setText(
      "case-explanation",
      error
        ? "Não foi possível carregar as informações. Tente novamente ou fale com a equipe."
        : "Este usuário ainda não possui um caso vinculado.",
    );
    timeline.innerHTML = "";
    toggleTimelineButton.hidden = true;
    return;
  }

  const caseData = cases[0];
  setText("case-updated-at", formatDate(caseData.updated_at, true));
  setText("case-area", caseData.area);
  setText("case-title", caseData.title);
  setText(
    "case-number",
    caseData.process_number ? `Processo nº ${caseData.process_number}` : "Número não informado",
  );
  setText("case-status", caseData.status_label);
  setText("case-explanation", caseData.status_explanation);
  setText("next-step-title", caseData.next_step_title);
  setText("next-step-description", caseData.next_step_description);
  setText(
    "next-step-estimate",
    caseData.next_step_estimate
      ? `Estimativa orientativa: ${caseData.next_step_estimate}`
      : "Sem estimativa disponível",
  );
  setText(
    "action-status",
    caseData.client_action_required ? "Ação necessária" : "Nenhuma pendência",
  );
  setText("action-text", caseData.client_action_text);
  setText("responsible-name", caseData.responsible_name);
  setText("responsible-oab", caseData.responsible_oab);
  setText("case-tribunal", caseData.tribunal);
  setText("case-court", caseData.court_name);
  setText("case-started-at", formatDate(caseData.started_at));

  const tribunalLink = document.getElementById("tribunal-link");
  if (caseData.tribunal_url) {
    tribunalLink.href = caseData.tribunal_url;
    tribunalLink.hidden = false;
  } else {
    tribunalLink.hidden = true;
  }

  timelineUpdates = [...(caseData.case_updates || [])].sort(
    (a, b) => new Date(b.event_date) - new Date(a.event_date),
  );
  timelineExpanded = false;
  toggleTimelineButton.textContent = "Mostrar histórico completo";
  renderTimeline();
}

async function initialize() {
  const { data } = await supabaseClient.auth.getSession();
  const isLoggedIn = Boolean(data.session);
  setView(isLoggedIn);

  if (isLoggedIn) {
    await loadClientCase();
  }
}

initialize();
