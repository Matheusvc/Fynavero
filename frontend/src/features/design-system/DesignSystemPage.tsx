import { useState, type ReactNode } from "react";
import { Link } from "react-router";
import { ArrowLeft, Plus, Wallet } from "lucide-react";
import { Logo } from "@/components/Logo";
import { Money } from "@/components/Money";
import { CurrencyInput } from "@/components/CurrencyInput";
import { Bars } from "@/components/charts/Bars";
import { Donut } from "@/components/charts/Donut";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input, Select, Switch } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState, Skeleton } from "@/components/ui/feedback";
import { Progress } from "@/components/ui/progress";
import { Segmented } from "@/components/ui/segmented";
import { CategoryIcon } from "@/lib/icons";
import { useTheme, type ThemePreference } from "@/theme/theme";
import { cn } from "@/lib/utils";

const colorGroups: { title: string; tokens: string[] }[] = [
  { title: "Marca", tokens: ["primary", "primary-text", "secondary", "accent", "foreground", "background"] },
  { title: "Superfícies", tokens: ["card", "popover", "muted", "muted-foreground", "border", "input", "ring"] },
  { title: "Semânticas financeiras", tokens: ["positive", "positive-subtle", "negative", "negative-subtle", "forecast", "forecast-subtle"] },
  { title: "Gráficos", tokens: ["chart-1", "chart-2", "chart-3", "chart-4", "chart-5", "chart-6", "chart-7", "chart-8", "chart-9", "chart-10", "chart-11", "chart-12", "chart-other"] },
  { title: "Séries", tokens: ["series-income", "series-expense", "series-invest"] },
];

const typeScale = ["text-hero", "text-3xl", "text-2xl", "text-xl", "text-lg", "text-base", "text-sm", "text-xs"];

const sampleSlices = [
  { categoryId: 1, name: "Moradia", icon: "house", color: "chart-1", amountCents: 135000, bps: 4200 },
  { categoryId: 2, name: "Mercado", icon: "shopping-cart", color: "chart-2", amountCents: 98000, bps: 3050 },
  { categoryId: 3, name: "Transporte", icon: "car", color: "chart-3", amountCents: 42000, bps: 1300 },
  { categoryId: null, name: "Outros", icon: "ellipsis", color: "chart-other", amountCents: 46000, bps: 1450 },
];

const sampleBars = [
  { label: "Set", receitas: 420000, despesas: 310000, investido: 60000 },
  { label: "Out", receitas: 480000, despesas: 290000, investido: 60000 },
  { label: "Nov", receitas: 420000, despesas: 321000, investido: 40000 },
];

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h3 className="text-sm font-semibold text-muted-foreground">{title}</h3>
      {children}
    </section>
  );
}

function Panel({ theme }: { theme: "light" | "dark" }) {
  const [seg, setSeg] = useState<"a" | "b">("a");
  const [on, setOn] = useState(true);
  const [check, setCheck] = useState(true);
  const [amount, setAmount] = useState("1.234,56");
  return (
    <div className={cn(theme, "min-w-0 space-y-8 rounded-xl border bg-background p-4 text-foreground sm:p-6")}>
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">Tema {theme === "light" ? "claro" : "escuro"}</h2>
        <Logo className="text-xl" />
      </div>

      <Block title="Número principal">
        <div className="relative overflow-hidden rounded-xl bg-secondary p-5 text-secondary-foreground">
          <span className="absolute inset-y-0 left-0 w-1.5 bg-primary" aria-hidden="true" />
          <p className="text-sm opacity-80">Saldo para passar o mês</p>
          <p className="text-hero font-bold"><Money cents={184250} /></p>
        </div>
        <ul className="divide-y rounded-lg border bg-card text-sm">
          <li className="flex items-center justify-between gap-2 p-3"><span>Salário</span><Money cents={357500} tone="positive" signed className="font-semibold" /></li>
          <li className="flex items-center justify-between gap-2 p-3"><span>Mercado</span><Money cents={-12000} className="font-semibold" /></li>
          <li className="flex items-center justify-between gap-2 p-3"><span>Parcela <Badge tone="forecast">Previsto</Badge> <Badge>5/12</Badge></span><Money cents={-49000} className="font-semibold" /></li>
          <li className="flex items-center justify-between gap-2 p-3"><span>Saldo negativo</span><Money cents={-15030} tone="negative" className="font-semibold" /></li>
        </ul>
      </Block>

      {colorGroups.map((g) => (
        <Block key={g.title} title={g.title}>
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {g.tokens.map((t) => (
              <li key={t} className="space-y-1">
                <div className="h-10 rounded-md border" style={{ background: `var(--${t})` }} />
                <code className="block truncate text-xs text-muted-foreground">--{t}</code>
              </li>
            ))}
          </ul>
        </Block>
      ))}

      <Block title="Tipografia · Space Grotesk">
        <ul className="space-y-1">
          {typeScale.map((s) => (
            <li key={s} className="flex items-baseline gap-3">
              <code className="w-16 shrink-0 text-xs text-muted-foreground">{s.replace("text-", "")}</code>
              <span className={cn(s, "min-w-0 truncate")}>Para onde foi seu dinheiro</span>
            </li>
          ))}
          <li className="money pt-2 text-lg">R$ 1.111,11 · R$ 8.888,88 (algarismos tabulares)</li>
        </ul>
      </Block>

      <Block title="Botões">
        <div className="flex flex-wrap gap-2">
          <Button><Plus /> Salvar lançamento</Button>
          <Button variant="secondary">Secundário</Button>
          <Button variant="outline">Contorno</Button>
          <Button variant="ghost">Discreto</Button>
          <Button variant="destructive">Excluir</Button>
          <Button variant="link">Link</Button>
          <Button disabled>Desativado</Button>
        </div>
      </Block>

      <Block title="Campos">
        <div className="grid gap-4">
          <Field label="Valor"><CurrencyInput value={amount} onChange={setAmount} large /></Field>
          <Field label="Descrição" hint="Dica abaixo do campo"><Input placeholder="Ex.: Feira do sábado" /></Field>
          <Field label="Com erro" error="Informe um valor maior que zero."><Input defaultValue="0" /></Field>
          <Field label="Forma de pagamento"><Select><option>Pix</option><option>Crédito</option></Select></Field>
          <Switch label="Repetir todo mês" description="Cria nos próximos meses como Previsto" checked={on} onChange={setOn} />
          <Checkbox checked={check} onChange={setCheck}>Aceito os termos</Checkbox>
          <Segmented label="Exemplo" value={seg} onChange={setSeg} options={[{ value: "a", label: "Despesa" }, { value: "b", label: "Receita" }]} />
        </div>
      </Block>

      <Block title="Selos, ícones e progresso">
        <div className="flex flex-wrap items-center gap-2">
          <Badge>Neutro</Badge>
          <Badge tone="forecast">Previsto</Badge>
          <Badge tone="positive">Quitado</Badge>
          <Badge tone="negative">Atrasado</Badge>
          <Badge tone="brand">Investido</Badge>
          <CategoryIcon icon="shopping-cart" color="chart-1" />
          <CategoryIcon icon="house" color="chart-2" />
          <CategoryIcon icon="piggy-bank" color="chart-3" />
        </div>
        <Progress value={7} max={12} label="7 de 12 parcelas" />
      </Block>

      <Block title="Cartão e estados">
        <Card>
          <CardTitle>Cartão</CardTitle>
          <Skeleton className="h-6 w-2/3" />
        </Card>
        <Card>
          <EmptyState icon={<Wallet className="size-7" />} title="Nenhum lançamento" actions={<Button size="sm">Lançar</Button>}>
            Estado vazio com ação.
          </EmptyState>
        </Card>
      </Block>

      <Block title="Gráficos">
        <Card><Donut slices={sampleSlices} total={321000} title="Exemplo de rosca" /></Card>
        <Card>
          <Bars
            title="Exemplo de barras"
            data={sampleBars}
            series={[
              { key: "receitas", label: "Receitas", color: "series-income" },
              { key: "despesas", label: "Despesas", color: "series-expense" },
              { key: "investido", label: "Investido", color: "series-invest" },
            ]}
            height={200}
          />
        </Card>
      </Block>
    </div>
  );
}

export default function DesignSystemPage() {
  const { preference, setPreference } = useTheme();
  return (
    <main className="mx-auto max-w-content space-y-6 px-4 py-6 sm:px-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <Link to="/" className="mb-2 inline-flex min-h-touch items-center gap-1 text-sm font-medium text-primary-text">
            <ArrowLeft className="size-4" aria-hidden="true" /> Voltar
          </Link>
          <h1 className="text-2xl font-bold">Design system Fynavero</h1>
          <p className="text-sm text-muted-foreground">
            Laranja, preto, cinza escuro, cinza e branco · Space Grotesk. Todos os valores vêm de tokens.
          </p>
        </div>
        <Segmented<ThemePreference>
          label="Tema"
          value={preference}
          onChange={setPreference}
          className="max-w-xs"
          options={[{ value: "light", label: "Claro" }, { value: "dark", label: "Escuro" }, { value: "system", label: "Sistema" }]}
        />
      </header>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Panel theme="light" />
        <Panel theme="dark" />
      </div>
    </main>
  );
}
