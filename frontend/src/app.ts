type StatusTone = "success" | "warning" | "info";
type ActivityTone = StatusTone;
type FindingSeverity = "critical" | "high" | "medium";

interface AuditFinding {
  id: string;
  title: string;
  severity: FindingSeverity;
  method: string;
  summary: string;
}

interface ActivityItem {
  id: number;
  title: string;
  detail: string;
  tone: ActivityTone;
  time: string;
}

interface DemoState {
  currentBlock: number;
  contractAddress: string;
  walletAddress: string;
  registeredAlias: string | null;
  lockedBalanceEth: number;
  unlockBlock: number | null;
  lastPayoutEth: number;
  activity: ActivityItem[];
}

const findings: AuditFinding[] = [
  {
    id: "predictable-randomness",
    title: "Predictable bonus path",
    severity: "critical",
    method: "withdraw()",
    summary:
      "The gift amount depends on block-derived randomness, making the payout path observable and manipulable.",
  },
  {
    id: "balance-overwrite",
    title: "Deposits overwrite previous balance",
    severity: "high",
    method: "deposit(uint256,address,string)",
    summary:
      "A new deposit replaces the recipient balance instead of accumulating it, which can wipe previously locked funds.",
  },
  {
    id: "silent-signup",
    title: "Duplicate signup is ignored silently",
    severity: "medium",
    method: "signup(string)",
    summary:
      "Calling signup twice does not revert or emit a signal, which can hide unexpected user flows during testing.",
  },
];

const methodCards = [
  {
    signature: "signup(string)",
    description: "Registers the recipient alias and sets a sentinel unlock block.",
  },
  {
    signature: "deposit(uint256,address,string)",
    description: "Schedules a payment for the recipient and rewrites balance state.",
  },
  {
    signature: "withdraw()",
    description: "Withdraws the current balance and may trigger the bonus path after unlock.",
  },
  {
    signature: "balance(address)",
    description: "Read-only view for the locked amount assigned to a wallet.",
  },
  {
    signature: "withdraw_time(address)",
    description: "Read-only view for the block height at which funds unlock.",
  },
  {
    signature: "user(address)",
    description: "Read-only view for the registered alias hash.",
  },
];

const state: DemoState = {
  currentBlock: 182340,
  contractAddress: "0xb4b900000000000000000000000000000000b4b9",
  walletAddress: "0xa11c00000000000000000000000000000000beef",
  registeredAlias: null,
  lockedBalanceEth: 0,
  unlockBlock: null,
  lastPayoutEth: 0,
  activity: [],
};

const ethFormatter = new Intl.NumberFormat("en-US", {
  minimumFractionDigits: 0,
  maximumFractionDigits: 4,
});

const timeFormatter = new Intl.DateTimeFormat("en-US", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

function getElement<T extends HTMLElement>(
  id: string,
  guard: new () => T,
): T {
  const element = document.getElementById(id);
  if (!(element instanceof guard)) {
    throw new Error(`Missing expected element: ${id}`);
  }
  return element;
}

const elements = {
  currentBlock: getElement("current-block", HTMLElement),
  lockedBalance: getElement("locked-balance", HTMLElement),
  unlockBlock: getElement("unlock-block", HTMLElement),
  registeredAlias: getElement("registered-alias", HTMLElement),
  walletAddress: getElement("wallet-address", HTMLElement),
  contractAddress: getElement("contract-address", HTMLElement),
  lastPayout: getElement("last-payout", HTMLElement),
  findingCount: getElement("finding-count", HTMLElement),
  statusBanner: getElement("status-banner", HTMLElement),
  methodList: getElement("method-list", HTMLDivElement),
  findingList: getElement("finding-list", HTMLDivElement),
  activityList: getElement("activity-list", HTMLDivElement),
  signupForm: getElement("signup-form", HTMLFormElement),
  depositForm: getElement("deposit-form", HTMLFormElement),
  withdrawForm: getElement("withdraw-form", HTMLFormElement),
  advanceOne: getElement("advance-one", HTMLButtonElement),
  advanceTen: getElement("advance-ten", HTMLButtonElement),
  recipientAliasInput: getElement("recipient-alias-input", HTMLInputElement),
  confirmAliasInput: getElement("confirm-alias-input", HTMLInputElement),
  lockBlocksInput: getElement("lock-blocks-input", HTMLInputElement),
  depositAmountInput: getElement("deposit-amount-input", HTMLInputElement),
};

function formatEth(value: number): string {
  return `${ethFormatter.format(value)} ETH`;
}

function pushActivity(
  title: string,
  detail: string,
  tone: ActivityTone,
): void {
  state.activity.unshift({
    id: Date.now() + Math.floor(Math.random() * 1000),
    title,
    detail,
    tone,
    time: timeFormatter.format(new Date()),
  });
  state.activity = state.activity.slice(0, 6);
}

function setStatus(message: string, tone: StatusTone): void {
  elements.statusBanner.textContent = message;
  elements.statusBanner.dataset.tone = tone;
}

function renderRuntime(): void {
  elements.currentBlock.textContent = String(state.currentBlock);
  elements.lockedBalance.textContent = formatEth(state.lockedBalanceEth);
  elements.unlockBlock.textContent =
    state.unlockBlock === null ? "Not scheduled" : String(state.unlockBlock);
  elements.registeredAlias.textContent =
    state.registeredAlias ?? "Not registered";
  elements.walletAddress.textContent = state.walletAddress;
  elements.contractAddress.textContent = state.contractAddress;
  elements.lastPayout.textContent = formatEth(state.lastPayoutEth);
  elements.findingCount.textContent = String(findings.length);
}

function renderMethods(): void {
  elements.methodList.innerHTML = methodCards
    .map(
      (method) => `
        <article class="method-chip">
          <strong>${method.signature}</strong>
          <span>${method.description}</span>
        </article>
      `,
    )
    .join("");
}

function renderFindings(): void {
  elements.findingList.innerHTML = findings
    .map(
      (finding) => `
        <article class="finding-card" data-severity="${finding.severity}">
          <header>
            <h3>${finding.title}</h3>
            <span class="finding-severity">${finding.severity}</span>
          </header>
          <p>${finding.summary}</p>
          <p><strong>Method:</strong> ${finding.method}</p>
        </article>
      `,
    )
    .join("");
}

function renderActivity(): void {
  if (state.activity.length === 0) {
    elements.activityList.innerHTML = `
      <article class="timeline-item" data-tone="info">
        <header>
          <h3>Waiting for interactions</h3>
          <span class="timeline-time">Now</span>
        </header>
        <p>Use the forms above to simulate the contract flow.</p>
      </article>
    `;
    return;
  }

  elements.activityList.innerHTML = state.activity
    .map(
      (item) => `
        <article class="timeline-item" data-tone="${item.tone}">
          <header>
            <h3>${item.title}</h3>
            <span class="timeline-time">${item.time}</span>
          </header>
          <p>${item.detail}</p>
        </article>
      `,
    )
    .join("");
}

function render(): void {
  renderRuntime();
  renderMethods();
  renderFindings();
  renderActivity();
}

function advanceBlocks(amount: number): void {
  state.currentBlock += amount;
  pushActivity(
    "Chain advanced",
    `Moved the local simulation forward by ${amount} block${amount === 1 ? "" : "s"}.`,
    "info",
  );
  setStatus(
    `Current block is now ${state.currentBlock}. Withdrawals only unlock after block ${
      state.unlockBlock ?? "N/A"
    }.`,
    "info",
  );
  render();
}

function handleSignup(event: SubmitEvent): void {
  event.preventDefault();

  const alias = elements.recipientAliasInput.value.trim();
  if (!alias) {
    setStatus("Enter a recipient alias before submitting the signup step.", "warning");
    return;
  }

  if (state.registeredAlias !== null) {
    setStatus(
      `The contract would silently ignore a second signup. Active alias remains ${state.registeredAlias}.`,
      "warning",
    );
    pushActivity(
      "Duplicate signup ignored",
      `Tried to replace ${state.registeredAlias} with ${alias}, but the contract keeps the first registration.`,
      "warning",
    );
    render();
    return;
  }

  state.registeredAlias = alias;
  elements.confirmAliasInput.value = alias;
  pushActivity(
    "Recipient registered",
    `The active wallet is now registered as ${alias}. Deposits can use the alias confirmation field.`,
    "success",
  );
  setStatus(
    `Recipient alias ${alias} registered. You can now simulate a locked deposit.`,
    "success",
  );
  render();
}

function handleDeposit(event: SubmitEvent): void {
  event.preventDefault();

  if (state.registeredAlias === null) {
    setStatus("The recipient must be registered before a deposit can be scheduled.", "warning");
    return;
  }

  const confirmAlias = elements.confirmAliasInput.value.trim();
  const lockBlocks = Number.parseInt(elements.lockBlocksInput.value, 10);
  const amount = Number.parseFloat(elements.depositAmountInput.value);

  if (confirmAlias !== state.registeredAlias) {
    setStatus(
      "The alias confirmation does not match the registered recipient alias.",
      "warning",
    );
    return;
  }

  if (!Number.isFinite(lockBlocks) || lockBlocks < 0) {
    setStatus("Lock blocks must be a zero or a positive integer.", "warning");
    return;
  }

  if (!Number.isFinite(amount) || amount <= 0) {
    setStatus("Deposit amount must be greater than zero.", "warning");
    return;
  }

  const hadExistingBalance = state.lockedBalanceEth > 0;
  state.lockedBalanceEth = amount;
  state.unlockBlock = state.currentBlock + lockBlocks;
  state.lastPayoutEth = 0;

  pushActivity(
    "Deposit scheduled",
    `Queued ${formatEth(amount)} for ${state.registeredAlias} until block ${state.unlockBlock}.`,
    hadExistingBalance ? "warning" : "success",
  );

  setStatus(
    hadExistingBalance
      ? "A new deposit replaced the previous balance, matching the overwrite behavior in the contract."
      : `Deposit locked successfully until block ${state.unlockBlock}.`,
    hadExistingBalance ? "warning" : "success",
  );

  render();
}

function handleWithdraw(event: SubmitEvent): void {
  event.preventDefault();

  if (state.lockedBalanceEth <= 0) {
    setStatus(
      "The active wallet has no locked balance. The contract would return without reverting.",
      "warning",
    );
    pushActivity(
      "Withdraw skipped",
      "No balance was available for withdrawal in the local simulation.",
      "warning",
    );
    render();
    return;
  }

  let payout = state.lockedBalanceEth;
  let bonus = 0;

  if (state.unlockBlock !== null && state.currentBlock > state.unlockBlock) {
    const lucky = state.currentBlock % 10 === 0;
    if (lucky) {
      bonus = Number((state.unlockBlock * 0.001).toFixed(4));
      payout += bonus;
    }
  }

  state.lockedBalanceEth = 0;
  state.unlockBlock = null;
  state.lastPayoutEth = payout;

  pushActivity(
    "Withdrawal executed",
    bonus > 0
      ? `Payout completed with a predictable bonus path: ${formatEth(payout)} total, including ${formatEth(bonus)} bonus.`
      : `Payout completed for ${formatEth(payout)} with no bonus triggered.`,
    bonus > 0 ? "warning" : "success",
  );

  setStatus(
    bonus > 0
      ? "The predictable bonus branch was triggered. This is the insecure randomness path highlighted in the audit panel."
      : "Withdrawal completed. Advance blocks to explore the unlock threshold and bonus path again.",
    bonus > 0 ? "warning" : "success",
  );

  render();
}

elements.signupForm.addEventListener("submit", handleSignup);
elements.depositForm.addEventListener("submit", handleDeposit);
elements.withdrawForm.addEventListener("submit", handleWithdraw);
elements.advanceOne.addEventListener("click", () => advanceBlocks(1));
elements.advanceTen.addEventListener("click", () => advanceBlocks(10));

pushActivity(
  "Frontend initialized",
  "Loaded the Baby Bank audit console in local dummy mode.",
  "info",
);

render();
