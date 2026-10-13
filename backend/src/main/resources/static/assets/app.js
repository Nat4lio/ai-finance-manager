const DEMO_ACCOUNT = { email: "demo@example.com", password: "Demo123!Finance" };
const TOKEN_KEY = "vault.access-token";
const COLORS = ["#5498ff", "#12d6a0", "#aa84ff", "#f4c95d", "#fb7185", "#f3985b"];
const ICONS = { INCOME: "↗", EXPENSE: "↘" };
const moneyFormatters = new Map();

const authScreen = document.querySelector("#auth-screen");
const appShell = document.querySelector("#app-shell");
const authForm = document.querySelector("#login-form");
const authError = document.querySelector("#auth-error");
const loading = document.querySelector("#loading");
const dashboardContent = document.querySelector("#dashboard-content");
const dashboardError = document.querySelector("#dashboard-error");
let activeRange = getPresetRange("6m");
let selectedGoalId = null;
let selectedGoalName = "";
let goalMovementKind = "ADD";

function dateInputValue(date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function getPresetRange(preset) {
  const today = new Date();
  let from;
  let to = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  if (preset === "1m") {
    from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    to = new Date(today.getFullYear(), today.getMonth(), 0);
  } else {
    const months = preset === "3m" ? 3 : preset === "1y" ? 12 : 6;
    from = new Date(today.getFullYear(), today.getMonth() - months + 1, 1);
  }
  return { from: dateInputValue(from), to: dateInputValue(to) };
}

function displayDate(date) {
  return new Intl.DateTimeFormat("pt-PT", { day: "numeric", month: "short", year: "numeric" })
    .format(new Date(`${date}T12:00:00`));
}

function currentPeriodCaption() {
  if (document.querySelector("#period-select").value === "custom") {
    return `${displayDate(activeRange.from)} – ${displayDate(activeRange.to)}`;
  }
  return document.querySelector("#period-select").selectedOptions[0].textContent;
}

function currency(value, code = "EUR", compact = false) {
  const key = `${code}-${compact}`;
  if (!moneyFormatters.has(key)) {
    moneyFormatters.set(key, new Intl.NumberFormat("pt-PT", {
      style: "currency",
      currency: code,
      notation: compact ? "compact" : "standard",
      maximumFractionDigits: compact ? 1 : 2
    }));
  }
  return moneyFormatters.get(key).format(Number(value ?? 0));
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set("Content-Type", "application/json");
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 401 && token) {
      sessionStorage.removeItem(TOKEN_KEY);
      showLogin();
    }
    throw new Error(body.message || `O pedido falhou (${response.status}).`);
  }
  return response.status === 204 ? null : response.json();
}

function showLogin(message = "") {
  authScreen.hidden = false;
  appShell.hidden = true;
  authError.textContent = message;
  authError.hidden = !message;
  dashboardContent.hidden = true;
  loading.hidden = false;
}

function showDashboard() {
  authScreen.hidden = true;
  appShell.hidden = false;
  dashboardError.hidden = true;
  loading.hidden = false;
  dashboardContent.hidden = true;
  loadDashboard();
}

async function signIn(email, password) {
  authError.hidden = true;
  const button = authForm.querySelector('button[type="submit"]');
  button.disabled = true;
  button.textContent = "A entrar…";
  try {
    const result = await request("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });
    sessionStorage.setItem(TOKEN_KEY, result.accessToken);
    showDashboard();
  } catch (error) {
    authError.textContent = error.message;
    authError.hidden = false;
  } finally {
    button.disabled = false;
    button.innerHTML = 'Entrar no painel <span aria-hidden="true">→</span>';
  }
}

async function loadDashboard() {
  try {
    const params = new URLSearchParams(activeRange);
    const data = await request(`/api/v1/dashboard?${params}`);
    renderDashboard(data);
    loading.hidden = true;
    dashboardContent.hidden = false;
  } catch (error) {
    if (!sessionStorage.getItem(TOKEN_KEY)) return;
    loading.hidden = true;
    dashboardError.textContent = `${error.message} Confirma se a API está a usar o modo de demonstração e tenta novamente.`;
    dashboardError.hidden = false;
  }
}

function renderDashboard(data) {
  const { summary, accounts, cashFlow, spending, transactions, goals } = data;
  document.querySelector("#greeting").textContent = `Bom dia, ${summary.displayName.split(" ")[0]} 👋`;
  document.querySelector("#avatar").textContent = summary.displayName.trim().charAt(0).toUpperCase();
  document.querySelector("#net-worth").textContent = currency(summary.netWorth, summary.currency);
  document.querySelector("#monthly-income").textContent = currency(summary.monthlyIncome, summary.currency);
  document.querySelector("#total-spent").textContent = currency(summary.totalSpent, summary.currency);
  document.querySelector("#income-period-label").textContent = `RENDIMENTOS · ${currentPeriodCaption().toLocaleUpperCase("pt-PT")}`;
  document.querySelector("#expenses-period-label").textContent = `DESPESAS · ${currentPeriodCaption().toLocaleUpperCase("pt-PT")}`;
  document.querySelector("#spending-period-caption").textContent = `Distribuição por categoria · ${currentPeriodCaption()}`;
  document.querySelector("#savings-rate").textContent = `${Number(summary.savingsRate).toLocaleString("pt-PT", { maximumFractionDigits: 1 })}%`;
  document.querySelector("#savings-target").replaceChildren(
    element("span", "metric-check", "↗"),
    document.createTextNode(` Objetivo ${Number(summary.savingsTarget).toLocaleString("pt-PT")}%`)
  );

  renderCashFlow(cashFlow, summary.currency);
  renderSpending(spending, summary.totalSpent, summary.currency);
  renderTransactions(transactions, "#recent-transactions", 4);
  renderTransactions(transactions, "#all-transactions");
  renderGoals(goals, "#goal-list-preview", 2, summary.currency);
  renderGoals(goals, "#all-goals", undefined, summary.currency);
  renderAccountOptions(accounts, summary.currency);
}

function renderAccountOptions(accounts, defaultCurrency) {
  const select = document.querySelector("#transaction-account");
  const previousValue = select.value;
  select.replaceChildren();
  if (!accounts.length) {
    const option = element("option", "", "Será criada uma conta principal");
    option.value = "";
    option.dataset.currency = defaultCurrency;
    select.append(option);
    updateTransactionCurrencyLabel();
    return;
  }
  accounts.forEach(account => {
    const option = element("option", "", `${account.name} · ${account.currency}`);
    option.value = account.id;
    option.dataset.currency = account.currency;
    select.append(option);
  });
  if (accounts.some(account => account.id === previousValue)) select.value = previousValue;
  updateTransactionCurrencyLabel();
}

function updateTransactionCurrencyLabel() {
  const currencyCode = document.querySelector("#transaction-account").selectedOptions[0]?.dataset.currency || "EUR";
  document.querySelector("#transaction-amount-label").textContent = `Valor (${currencyCode})`;
}

function renderCashFlow(months, code) {
  const svg = document.querySelector("#cashflow-chart");
  svg.replaceChildren();
  if (!months.length) return;
  const ns = "http://www.w3.org/2000/svg";
  const makeSvg = (tag, attributes = {}) => {
    const node = document.createElementNS(ns, tag);
    for (const [name, value] of Object.entries(attributes)) node.setAttribute(name, value);
    return node;
  };
  const defs = makeSvg("defs");
  const incomeGradient = makeSvg("linearGradient", { id: "income-fill", x1: "0", y1: "0", x2: "0", y2: "1" });
  incomeGradient.append(makeSvg("stop", { offset: "0%", "stop-color": "#12d6a0", "stop-opacity": ".25" }));
  incomeGradient.append(makeSvg("stop", { offset: "100%", "stop-color": "#12d6a0", "stop-opacity": "0" }));
  const expenseGradient = makeSvg("linearGradient", { id: "expense-fill", x1: "0", y1: "0", x2: "0", y2: "1" });
  expenseGradient.append(makeSvg("stop", { offset: "0%", "stop-color": "#fb7185", "stop-opacity": ".14" }));
  expenseGradient.append(makeSvg("stop", { offset: "100%", "stop-color": "#fb7185", "stop-opacity": "0" }));
  defs.append(incomeGradient, expenseGradient);
  svg.append(defs);

  const chart = { left: 15, right: 700, top: 17, bottom: 187 };
  const max = Math.max(1, ...months.flatMap(item => [Number(item.income), Number(item.expenses)])) * 1.14;
  const x = index => chart.left + (months.length === 1 ? 0 : index * (chart.right - chart.left) / (months.length - 1));
  const y = value => chart.bottom - Number(value) / max * (chart.bottom - chart.top);
  for (let line = 0; line < 4; line++) {
    const gy = chart.top + line * (chart.bottom - chart.top) / 3;
    svg.append(makeSvg("line", { x1: chart.left, y1: gy, x2: chart.right, y2: gy, class: "chart-grid-line" }));
  }

  const smoothPath = values => {
    const points = values.map((item, index) => [x(index), y(item)]);
    if (points.length === 1) return `M ${points[0][0]} ${points[0][1]}`;
    return points.reduce((path, point, index) => {
      if (index === 0) return `M ${point[0]} ${point[1]}`;
      const previous = points[index - 1];
      const midX = (previous[0] + point[0]) / 2;
      return `${path} C ${midX} ${previous[1]}, ${midX} ${point[1]}, ${point[0]} ${point[1]}`;
    }, "");
  };
  for (const [key, areaClass, lineClass] of [
    ["income", "chart-income-area", "chart-income-line"],
    ["expenses", "chart-expense-area", "chart-expense-line"]
  ]) {
    const values = months.map(item => Number(item[key]));
    const linePath = smoothPath(values);
    svg.append(makeSvg("path", {
      d: `${linePath} L ${x(values.length - 1)} ${chart.bottom} L ${x(0)} ${chart.bottom} Z`,
      class: areaClass
    }));
    svg.append(makeSvg("path", { d: linePath, class: lineClass }));
    values.forEach((value, index) => {
      if (index === values.length - 1 || values.length < 4) {
        svg.append(makeSvg("circle", {
          cx: x(index), cy: y(value), r: index === values.length - 1 ? 4 : 3,
          fill: key === "income" ? "#12d6a0" : "#fb7185", class: "chart-point"
        }));
      }
    });
  }
  const labelStep = Math.max(1, Math.ceil(months.length / 8));
  months.forEach((item, index) => {
    if (index % labelStep !== 0 && index !== months.length - 1) return;
    const label = makeSvg("text", { x: x(index), y: 216, "text-anchor": "middle", class: "chart-axis-label" });
    label.textContent = item.month;
    svg.append(label);
  });
  svg.setAttribute("aria-label", `Fluxo de caixa de ${months.length} meses. Rendimentos entre ${currency(Math.min(...months.map(m => Number(m.income))), code)} e ${currency(Math.max(...months.map(m => Number(m.income))), code)}.`);
}

function renderSpending(categories, total, code) {
  const chart = document.querySelector("#spending-chart");
  const legend = document.querySelector("#category-legend");
  const colors = categories.map((_, index) => COLORS[index % COLORS.length]);
  const totalAmount = Number(total);
  let position = 0;
  const slices = categories.map((category, index) => {
    const start = position;
    position += totalAmount > 0 ? Number(category.amount) / totalAmount * 100 : 0;
    return `${colors[index]} ${start}% ${position}%`;
  });
  chart.style.background = slices.length ? `conic-gradient(${slices.join(", ")})` : "#242536";
  document.querySelector("#donut-total").textContent = currency(total, code, true);
  legend.replaceChildren();
  categories.forEach((category, index) => {
    const row = element("div", "category-row");
    const label = element("span", "category-name");
    const swatch = element("i", "category-swatch");
    swatch.style.backgroundColor = colors[index];
    label.append(swatch, element("span", "category-name-text", category.name));
    row.append(label, element("span", "category-amount", `${Math.round(Number(category.percentage))}% · ${currency(category.amount, code, true)}`));
    legend.append(row);
  });
  if (!categories.length) legend.append(element("p", "transaction-meta", "Ainda não há despesas registadas neste período."));
}

function renderTransactions(transactions, selector, limit) {
  const list = document.querySelector(selector);
  list.replaceChildren();
  const rows = limit ? transactions.slice(0, limit) : transactions;
  rows.forEach(transaction => {
    const row = element("article", "transaction-row");
    const icon = element("span", "transaction-icon", ICONS[transaction.kind] || "•");
    icon.setAttribute("aria-hidden", "true");
    const detail = element("div");
    detail.append(element("div", "transaction-description", transaction.description));
    const date = new Intl.DateTimeFormat("pt-PT", { day: "numeric", month: "short" }).format(new Date(`${transaction.date}T12:00:00`));
    detail.append(element("div", "transaction-meta", `${transaction.category || "Sem categoria"} · ${date}`));
    const amount = Number(transaction.amount);
    const formatted = currency(Math.abs(amount), transaction.currency);
    const amountNode = element("span", `transaction-amount ${amount >= 0 ? "positive" : "negative"}`, `${amount >= 0 ? "+" : "−"}${formatted}`);
    row.append(icon, detail, amountNode);
    list.append(row);
  });
  if (!rows.length) list.append(element("p", "transaction-meta", "Ainda não há transações registadas."));
}

function renderGoals(goals, selector, limit, code) {
  const list = document.querySelector(selector);
  list.replaceChildren();
  const rows = limit ? goals.slice(0, limit) : goals;
  rows.forEach((goal, index) => {
    const item = element("article", "goal-item");
    const top = element("div", "goal-top");
    const name = element("span", "goal-name");
    name.append(element("span", "goal-emoji", index === 0 ? "⌂" : "✧"), document.createTextNode(goal.name));
    const progress = Number(goal.targetAmount) > 0
      ? Math.min(100, Number(goal.currentAmount) / Number(goal.targetAmount) * 100)
      : 0;
    top.append(name, element("span", "goal-amount", `${currency(goal.currentAmount, goal.currency || code, true)} / ${currency(goal.targetAmount, goal.currency || code, true)}`));
    const track = element("div", "progress-track");
    track.setAttribute("role", "progressbar");
    track.setAttribute("aria-label", goal.name);
    track.setAttribute("aria-valuemin", "0");
    track.setAttribute("aria-valuemax", "100");
    track.setAttribute("aria-valuenow", String(Math.round(progress)));
    const fill = element("div", "progress-fill");
    fill.style.width = `${progress}%`;
    track.append(fill);
    const foot = element("div", "goal-foot");
    foot.append(element("span", "", `${Math.round(progress)}% concluído`));
    if (goal.targetDate) {
      foot.append(element("span", "", `Até ${new Intl.DateTimeFormat("pt-PT", { month: "short", year: "numeric" }).format(new Date(`${goal.targetDate}T12:00:00`))}`));
    }
    const actions = element("div", "goal-actions");
    for (const [action, label, className] of [
      ["add", "Adicionar dinheiro", "goal-action-add"],
      ["withdraw", "Retirar dinheiro", "goal-action-withdraw"],
      ["delete", "Eliminar", "goal-action-delete"]
    ]) {
      const button = element("button", `goal-action ${className}`, label);
      button.type = "button";
      button.dataset.goalAction = action;
      button.dataset.goalId = goal.id;
      button.dataset.goalName = goal.name;
      button.dataset.goalAmount = String(goal.currentAmount);
      button.dataset.goalCurrency = goal.currency || code;
      if (action === "withdraw") {
        button.disabled = Number(goal.currentAmount) <= 0;
        button.title = button.disabled ? "Este objetivo ainda não tem dinheiro para retirar." : "";
      }
      actions.append(button);
    }
    item.append(top, track, foot, actions);
    list.append(item);
  });
  if (!rows.length) list.append(element("p", "transaction-meta", "Ainda não há objetivos. Cria o primeiro para começares a poupar."));
}

const goalDialog = document.querySelector("#goal-dialog");
const goalForm = document.querySelector("#goal-form");
const goalMovementDialog = document.querySelector("#goal-movement-dialog");
const goalMovementForm = document.querySelector("#goal-movement-form");

function openGoalDialog() {
  goalForm.reset();
  document.querySelector("#goal-form-error").hidden = true;
  document.querySelector("#goal-name").focus();
  goalDialog.showModal();
}

function openGoalMovement(goalId, name, amount, currencyCode, kind) {
  selectedGoalId = goalId;
  selectedGoalName = name;
  goalMovementKind = kind;
  const adding = kind === "ADD";
  document.querySelector("#goal-movement-title").textContent = adding ? "Adicionar dinheiro" : "Retirar dinheiro";
  document.querySelector("#goal-movement-description").textContent = `${name} · guardado: ${currency(amount, currencyCode)}`;
  document.querySelector("#goal-movement-amount-label").textContent = `Valor (${currencyCode})`;
  document.querySelector("#goal-movement-amount").value = "";
  document.querySelector("#goal-movement-amount").max = adding ? "" : String(amount);
  document.querySelector("#goal-movement-error").hidden = true;
  document.querySelector("#save-goal-movement").textContent = adding ? "Adicionar ao objetivo" : "Retirar do objetivo";
  goalMovementDialog.showModal();
  document.querySelector("#goal-movement-amount").focus();
}

for (const buttonId of ["create-goal-preview", "create-goal-full"]) {
  document.querySelector(`#${buttonId}`).addEventListener("click", openGoalDialog);
}
document.querySelector("#close-goal-dialog").addEventListener("click", () => goalDialog.close());
document.querySelector("#cancel-goal").addEventListener("click", () => goalDialog.close());
goalForm.addEventListener("submit", async event => {
  event.preventDefault();
  const error = document.querySelector("#goal-form-error");
  const button = document.querySelector("#save-goal");
  error.hidden = true;
  button.disabled = true;
  button.textContent = "A criar…";
  const formData = new FormData(goalForm);
  try {
    await request("/api/v1/goals", {
      method: "POST",
      body: JSON.stringify({
        name: String(formData.get("name")).trim(),
        targetAmount: Number(formData.get("targetAmount")),
        targetDate: formData.get("targetDate") || null
      })
    });
    goalDialog.close();
    await loadDashboard();
  } catch (failure) {
    error.textContent = failure.message;
    error.hidden = false;
  } finally {
    button.disabled = false;
    button.textContent = "Criar objetivo";
  }
});

document.querySelector("#close-goal-movement-dialog").addEventListener("click", () => goalMovementDialog.close());
document.querySelector("#cancel-goal-movement").addEventListener("click", () => goalMovementDialog.close());
goalMovementForm.addEventListener("submit", async event => {
  event.preventDefault();
  const error = document.querySelector("#goal-movement-error");
  const button = document.querySelector("#save-goal-movement");
  error.hidden = true;
  button.disabled = true;
  button.textContent = "A guardar…";
  const amount = Number(new FormData(goalMovementForm).get("amount"));
  try {
    await request(`/api/v1/goals/${encodeURIComponent(selectedGoalId)}/movements`, {
      method: "POST",
      body: JSON.stringify({ kind: goalMovementKind, amount })
    });
    goalMovementDialog.close();
    await loadDashboard();
  } catch (failure) {
    error.textContent = failure.message;
    error.hidden = false;
  } finally {
    button.disabled = false;
    button.textContent = goalMovementKind === "ADD" ? "Adicionar ao objetivo" : "Retirar do objetivo";
  }
});

for (const listSelector of ["#goal-list-preview", "#all-goals"]) {
  document.querySelector(listSelector).addEventListener("click", async event => {
    const button = event.target.closest("button[data-goal-action]");
    if (!button) return;
    const { goalAction, goalId, goalName, goalAmount, goalCurrency } = button.dataset;
    if (goalAction === "add") {
      openGoalMovement(goalId, goalName, goalAmount, goalCurrency, "ADD");
      return;
    }
    if (goalAction === "withdraw") {
      openGoalMovement(goalId, goalName, goalAmount, goalCurrency, "WITHDRAW");
      return;
    }
    if (!window.confirm(`Queres eliminar o objetivo "${goalName}"? Os movimentos já registados não serão apagados.`)) return;
    button.disabled = true;
    try {
      await request(`/api/v1/goals/${encodeURIComponent(goalId)}`, { method: "DELETE" });
      await loadDashboard();
    } catch (failure) {
      dashboardError.textContent = failure.message;
      dashboardError.hidden = false;
    } finally {
      button.disabled = false;
    }
  });
}

const transactionDialog = document.querySelector("#transaction-dialog");
const transactionForm = document.querySelector("#transaction-form");
const transactionFormError = document.querySelector("#transaction-form-error");
let transactionKind = "INCOME";

function openTransactionDialog(kind) {
  transactionKind = kind;
  const isIncome = kind === "INCOME";
  const label = isIncome ? "receita" : "despesa";
  document.querySelector("#transaction-kind-label").textContent = isIncome ? "NOVA RECEITA" : "NOVA DESPESA";
  document.querySelector("#transaction-dialog-title").textContent = `Adicionar ${label}`;
  document.querySelector("#save-transaction").textContent = `Guardar ${label}`;
  document.querySelector("#transaction-category").value = "";
  document.querySelector("#transaction-amount").value = "";
  document.querySelector("#transaction-description").value = "";
  document.querySelector("#transaction-date").value = localDateValue();
  const categories = isIncome
    ? ["Salário", "MB WAY", "Presente de aniversário", "Outros"]
    : ["Casa", "Alimentação", "Transportes", "Saúde", "Lazer", "Outros"];
  const suggestions = document.querySelector("#transaction-category-suggestions");
  suggestions.replaceChildren(...categories.map(category => {
    const option = element("option");
    option.value = category;
    return option;
  }));
  transactionFormError.hidden = true;
  transactionFormError.textContent = "";
  transactionDialog.showModal();
  document.querySelector("#transaction-amount").focus();
}

function localDateValue() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

document.querySelector("#period-from").max = localDateValue();
document.querySelector("#period-to").max = localDateValue();
document.querySelector("#goal-target-date").min = localDateValue();

document.querySelector("#period-select").addEventListener("change", event => {
  const custom = event.target.value === "custom";
  const controls = document.querySelector("#custom-period-controls");
  controls.hidden = !custom;
  document.querySelector("#custom-period-error").hidden = true;
  if (custom) {
    document.querySelector("#period-from").value = activeRange.from;
    document.querySelector("#period-to").value = activeRange.to;
    return;
  }
  activeRange = getPresetRange(event.target.value);
  loadDashboard();
});

document.querySelector("#apply-custom-period").addEventListener("click", () => {
  const from = document.querySelector("#period-from").value;
  const to = document.querySelector("#period-to").value;
  const error = document.querySelector("#custom-period-error");
  if (!from || !to || from > to) {
    error.textContent = "Escolhe um intervalo válido: a data inicial deve ser anterior à data final.";
    error.hidden = false;
    return;
  }
  const [endYear, endMonth, endDay] = to.split("-").map(Number);
  const earliestYear = endYear - 5;
  const earliestDay = Math.min(endDay, new Date(earliestYear, endMonth, 0).getDate());
  const earliestAllowed = `${earliestYear}-${String(endMonth).padStart(2, "0")}-${String(earliestDay).padStart(2, "0")}`;
  if (from < earliestAllowed) {
    error.textContent = "O intervalo personalizado não pode exceder cinco anos.";
    error.hidden = false;
    return;
  }
  error.hidden = true;
  activeRange = { from, to };
  loadDashboard();
});

document.querySelector("#add-income").addEventListener("click", () => openTransactionDialog("INCOME"));
document.querySelector("#add-expense").addEventListener("click", () => openTransactionDialog("EXPENSE"));
document.querySelector("#transaction-account").addEventListener("change", updateTransactionCurrencyLabel);
document.querySelector("#close-transaction-dialog").addEventListener("click", () => transactionDialog.close());
document.querySelector("#cancel-transaction").addEventListener("click", () => transactionDialog.close());
transactionForm.addEventListener("submit", async event => {
  event.preventDefault();
  transactionFormError.hidden = true;
  const saveButton = document.querySelector("#save-transaction");
  saveButton.disabled = true;
  saveButton.textContent = "A guardar…";
  const formData = new FormData(transactionForm);
  try {
    await request("/api/v1/transactions", {
      method: "POST",
      body: JSON.stringify({
        accountId: formData.get("accountId") || null,
        kind: transactionKind,
        description: String(formData.get("description")).trim(),
        category: String(formData.get("category")).trim(),
        amount: Number(formData.get("amount")),
        date: formData.get("date")
      })
    });
    transactionDialog.close();
    await loadDashboard();
  } catch (error) {
    transactionFormError.textContent = error.message;
    transactionFormError.hidden = false;
  } finally {
    saveButton.disabled = false;
    saveButton.textContent = `Guardar ${transactionKind === "INCOME" ? "receita" : "despesa"}`;
  }
});

authForm.addEventListener("submit", event => {
  event.preventDefault();
  const formData = new FormData(authForm);
  signIn(String(formData.get("email")).trim(), String(formData.get("password")));
});

document.querySelector("#demo-login").addEventListener("click", () => {
  document.querySelector("#email").value = DEMO_ACCOUNT.email;
  document.querySelector("#password").value = DEMO_ACCOUNT.password;
  signIn(DEMO_ACCOUNT.email, DEMO_ACCOUNT.password);
});

document.querySelector("#logout").addEventListener("click", () => {
  sessionStorage.removeItem(TOKEN_KEY);
  showLogin();
});

document.querySelectorAll("[data-view]").forEach(button => {
  button.addEventListener("click", () => {
    const view = button.dataset.view;
    document.querySelectorAll(".view-panel").forEach(panel => {
      panel.hidden = panel.dataset.panel !== view;
    });
    document.querySelectorAll(".side-nav [data-view]").forEach(link => {
      link.classList.toggle("is-active", link.dataset.view === view);
    });
    if (view === "overview") document.querySelector(".topbar").scrollIntoView({ behavior: "smooth", block: "start" });
    else document.querySelector(`[data-panel="${view}"]`).scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

if (sessionStorage.getItem(TOKEN_KEY)) showDashboard();
else showLogin();
