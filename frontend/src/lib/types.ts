export type Kind = "DESPESA" | "RECEITA" | "INVESTIMENTO";
export type Status = "REALIZADO" | "PREVISTO";
export type Origin = "MANUAL" | "IMPORTADO";
export type Objective = "ORGANIZAR" | "DIVIDAS" | "INVESTIR" | "OBJETIVO";
export type PaymentMethod = "PIX" | "DINHEIRO" | "DEBITO" | "CREDITO" | "VA_VR" | "VT" | "BOLETO" | "TRANSFERENCIA";
export type Scope = "ONE" | "FOLLOWING";

export type Contribution = { enabled: boolean; bps: number; categoryId?: number | null };

export type Me = {
  id: number;
  name?: string;
  email: string;
  objectives: Objective[];
  onboardingDone: boolean;
  contribution: Contribution;
  hasPassword: boolean;
  emailVerified: boolean;
};

export type Providers = { google: boolean; magicLink: boolean };

export type CatalogItem = { id: number; name: string; icon: string; group: string };

export type Category = {
  id: number;
  name: string;
  kind: Kind;
  group: string;
  icon: string;
  color: string;
  sortOrder: number;
  hidden: boolean;
  loan: boolean;
  /** Despesa: limite de gasto do mês. Investimento: valor a juntar (objetivo de economia). */
  goalCents?: number | null;
  goalDeadline?: string | null;
};

export type Tx = {
  id: number;
  kind: Kind;
  date: string;
  description?: string;
  categoryId: number;
  amountCents: number;
  paymentMethod?: PaymentMethod;
  status: Status;
  installmentNumber?: number;
  installmentTotal?: number;
  recurrenceId?: number;
  installmentPlanId?: number;
  origin: Origin;
  importBatchId?: number;
  note?: string;
  tags: string[];
};

export type TxInput = {
  kind: Kind;
  amountCents: number;
  categoryId: number;
  date?: string;
  description?: string;
  paymentMethod?: PaymentMethod | null;
  note?: string;
  repeatMonthly?: boolean;
  installments?: number | null;
  /** true: amountCents é o valor de cada parcela; false/ausente: valor total da compra. */
  installmentValue?: boolean;
  loan?: LoanRepayment | null;
  tags?: string[];
};

export type LoanRepayment = { count: number; installmentCents: number; firstDate?: string; categoryId?: number | null };

export type InstallmentConvert = {
  amountCents: number;
  categoryId: number;
  date: string;
  description?: string;
  paymentMethod?: PaymentMethod | null;
  note?: string;
  count: number;
  installmentValue: boolean;
  tags?: string[];
};

export type TxUpdate = {
  amountCents: number;
  categoryId: number;
  date: string;
  description?: string;
  paymentMethod?: PaymentMethod | null;
  note?: string;
  status?: Status;
  tags?: string[];
};

export type Totals = {
  receitas: number;
  emprestimos: number;
  despesas: number;
  despesasLancadas: number;
  contribuicao: number;
  sobra: number;
  investido: number;
  saldo: number;
};

export type Slice = { categoryId?: number | null; name: string; icon: string; color: string; amountCents: number; bps: number };
export type MonthBar = { month: string; label: string; receitas: number; despesas: number; investido: number };
export type Upcoming = {
  id: number;
  date: string;
  description?: string;
  categoryId: number;
  amountCents: number;
  installmentNumber?: number;
  installmentTotal?: number;
};

export type Dashboard = {
  month: string;
  empty: boolean;
  current: Totals;
  previous: Totals;
  breakdown: Slice[];
  lastMonths: MonthBar[];
  upcoming: Upcoming[];
  insights: string[];
};

export type Plan = {
  id: number;
  description?: string;
  categoryId: number;
  installmentCents: number;
  totalCents: number;
  count: number;
  paid: number;
  current: number;
  remainingCents: number;
  lastMonth: string;
  firstDate: string;
  finished: boolean;
};
export type Commitment = { month: string; label: string; installments: number; fixedBills: number };
export type Installments = { plans: Plan[]; timeline: Commitment[]; monthTotal: number };

export type InvestmentCategory = {
  categoryId: number;
  name: string;
  icon: string;
  color: string;
  monthCents: number;
  accumulatedCents: number;
  goalCents?: number | null;
};
export type Investments = {
  month: string;
  monthTotal: number;
  accumulatedTotal: number;
  categories: InvestmentCategory[];
  history: MonthBar[];
};

export type ReportRow = { categoryId?: number | null; label: string; months: number[]; total: number; average: number };
export type ReportGroup = { group: string; rows: ReportRow[]; subtotal: ReportRow };
export type AnnualReport = {
  year: number;
  monthLabels: string[];
  receitas: ReportRow[];
  emprestimos: ReportRow[];
  despesas: ReportGroup[];
  totalDespesas: ReportRow;
  investimentos: ReportRow[];
  totalInvestido: ReportRow;
  resultado: ReportRow[];
};
export type CompareRow = { categoryId?: number | null; label: string; current: number; previous: number; average: number };
export type CompareReport = { month: string; previousMonth: string; totals: CompareRow[]; categories: CompareRow[] };

export type IncomeSource = { id: number; name: string; amountCents: number; fixed: boolean; categoryId?: number; day?: number };
export type Recurrence = {
  id: number;
  kind: Kind;
  categoryId: number;
  description?: string;
  amountCents: number;
  day: number;
  paymentMethod?: PaymentMethod;
  startDate: string;
  endDate?: string;
  fixedBill: boolean;
};

export type ImportBatch = {
  id: number;
  fileName: string;
  rowCount: number;
  createdAt: string;
  undoneAt?: string;
  receitas: number;
  despesas: number;
  investido: number;
};

export type ImportRowInput = {
  kind: Kind;
  date: string;
  description?: string;
  categoryId: number;
  amountCents: number;
  paymentMethod?: PaymentMethod | null;
  installmentNumber?: number | null;
  installmentTotal?: number | null;
  note?: string;
};

export type SearchResult = { items: Tx[]; receitasCents: number; despesasCents: number; investidoCents: number; truncated: boolean };
export type TagCount = { tag: string; count: number };

export type BudgetLevel = "OK" | "ALERTA" | "ESTOURADO";
export type Budget = {
  categoryId: number;
  name: string;
  icon: string;
  color: string;
  limitCents: number;
  spentCents: number;
  pctBps: number;
  level: BudgetLevel;
};

export type GoalStatus = "CONCLUIDO" | "NO_RITMO" | "ATENCAO" | "PRAZO_VENCIDO" | "SEM_PRAZO";
export type Goal = {
  id: number;
  name: string;
  icon: string;
  color: string;
  targetCents: number;
  savedCents: number;
  remainingCents: number;
  pctBps: number;
  deadline?: string | null;
  monthsLeft?: number | null;
  monthlyNeededCents?: number | null;
  savedThisMonthCents: number;
  status: GoalStatus;
};
export type GoalInput = { name: string; targetCents: number; deadline?: string | null; icon?: string; initialCents?: number | null };

export type NotificationType = "CONTA_VENCIDA" | "CONTA_HOJE" | "CONTA_AMANHA" | "LIMITE_ALERTA" | "LIMITE_ESTOURADO";
export type AppNotification = {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  date?: string | null;
  transactionId?: number | null;
  categoryId?: number | null;
  amountCents?: number | null;
};
export type NotificationPreferences = { bills: boolean; limits: boolean; summary: boolean; emailConfigured: boolean };
export type BillAction = { description: string; amountCents: number; date: string; paid: boolean };
