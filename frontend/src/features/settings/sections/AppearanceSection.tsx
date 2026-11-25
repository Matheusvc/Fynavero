import { toast } from "sonner";
import { ColorPicker } from "@/components/ColorPicker";
import { Button } from "@/components/ui/button";
import { Segmented } from "@/components/ui/segmented";
import { api, errorMessage } from "@/lib/api";
import { SERIES, cssColor, useSeriesColors, type SeriesKey } from "@/lib/colors";
import { useMe, useSetMe } from "@/lib/queries";
import type { ChartColors, Me } from "@/lib/types";
import { useTheme, type ThemePreference } from "@/theme/theme";
import { Section } from "../shared";

export function AppearanceSection() {
  const { preference, setPreference } = useTheme();
  return (
    <Section id="aparencia" title="Aparência">
      <Segmented<ThemePreference>
        label="Tema"
        value={preference}
        onChange={setPreference}
        className="max-w-sm"
        options={[{ value: "light", label: "Claro" }, { value: "dark", label: "Escuro" }, { value: "system", label: "Sistema" }]}
      />
      <ChartColorsPicker />
    </Section>
  );
}

/** Cor das linhas Receitas, Despesas e Investido nos gráficos do Início e de Investimentos. */
function ChartColorsPicker() {
  const me = useMe().data;
  const setMe = useSetMe();
  const current = useSeriesColors();
  const chosen: ChartColors = me?.chartColors ?? {};
  const changed = SERIES.some((s) => chosen[s.key]);

  async function save(next: ChartColors) {
    if (!me) return;
    setMe({ ...me, chartColors: next }); // muda na hora; volta se der erro
    try {
      setMe(await api<Me>("/api/profile/chart-colors", { method: "PUT", body: next }));
    } catch (e) {
      setMe(me);
      toast.error(errorMessage(e));
    }
  }

  const pick = (key: SeriesKey, token: string | null) => void save({ ...chosen, [key]: token });

  return (
    <div className="mt-6 border-t pt-5">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold">Cores dos gráficos</h3>
          <p className="text-sm text-muted-foreground">Escolha a cor de cada linha. A cor das categorias muda em Categorias.</p>
        </div>
        {changed && (
          <Button variant="ghost" size="sm" onClick={() => void save({ income: null, expense: null, invest: null })}>
            Voltar às cores padrão
          </Button>
        )}
      </div>
      <div className="space-y-4">
        {SERIES.map((s) => (
          <div key={s.key}>
            <p className="mb-2 flex items-center gap-2 text-sm font-medium">
              <span aria-hidden="true" className="h-2.5 w-6 rounded-full" style={{ backgroundColor: cssColor(current[s.key]) }} />
              {s.label}
            </p>
            <ColorPicker label={`Cor de ${s.label}`} value={chosen[s.key]} defaultToken={s.fallback} onChange={(t) => pick(s.key, t)} />
          </div>
        ))}
      </div>
    </div>
  );
}
