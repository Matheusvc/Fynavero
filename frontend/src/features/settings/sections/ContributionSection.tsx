import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Switch } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import { invalidateFinance, useCategories, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { Section, useSave } from "../shared";

export function ContributionSection({ me }: { me: Me }) {
  const setMe = useSetMe();
  const qc = useQueryClient();
  const cats = useCategories();
  const [enabled, setEnabled] = useState(me.contribution.enabled);
  const [pct, setPct] = useState(String(me.contribution.bps / 100).replace(".", ","));
  const [categoryId, setCategoryId] = useState<string>(me.contribution.categoryId ? String(me.contribution.categoryId) : "");
  const s = useSave();
  const expense = (cats.data ?? []).filter((c) => c.kind === "DESPESA");
  const value = Number(pct.replace(",", "."));
  const valid = value > 0 && value <= 100;
  return (
    <Section id="contribuicao" title="Contribuição (dízimo ou doação)" description="Um percentual sobre o total de receitas do mês, lançado automaticamente como despesa.">
      <div className="space-y-4">
        <Switch label="Calcular contribuição automaticamente" checked={enabled} onChange={setEnabled} />
        {enabled && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Percentual" hint="Sugestão: 10%" error={valid ? null : "Use um valor entre 0,01 e 100."}>
              <Input inputMode="decimal" value={pct} onChange={(e) => setPct(e.target.value)} />
            </Field>
            <Field label="Categoria">
              <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">Contribuição (sem categoria)</option>
                {expense.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
            </Field>
          </div>
        )}
        <Button
          disabled={s.busy || (enabled && !valid)}
          onClick={() =>
            void s.run(async () => {
              setMe(await api<Me>("/api/profile/contribution", {
                method: "PUT",
                body: { enabled, bps: valid ? Math.round(value * 100) : me.contribution.bps, categoryId: categoryId ? Number(categoryId) : null },
              }));
              invalidateFinance(qc);
            }, "Contribuição salva")
          }
        >
          {s.busy && <Spinner />} Salvar contribuição
        </Button>
      </div>
    </Section>
  );
}
