import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import { OBJECTIVES } from "@/lib/labels";
import { useSetMe } from "@/lib/queries";
import type { Me, Objective } from "@/lib/types";
import { Section, useSave } from "../shared";

export function ProfileSection({ me }: { me: Me }) {
  const setMe = useSetMe();
  const [name, setName] = useState(me.name ?? "");
  const [objectives, setObjectives] = useState<Objective[]>(me.objectives ?? []);
  const s = useSave();
  return (
    <Section id="perfil" title="Perfil">
      <form
        className="grid grid-cols-1 gap-4 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          void s.run(async () => setMe(await api<Me>("/api/profile", { method: "PUT", body: { name, objectives } })), "Perfil salvo");
        }}
      >
        <Field label="Nome">
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
        </Field>
        <Field label="E-mail">
          <Input value={me.email} readOnly disabled />
        </Field>
        <fieldset className="space-y-2 sm:col-span-2">
          <legend className="text-sm font-medium">
            Objetivos <span className="font-normal text-muted-foreground">· pode marcar mais de um; personalizam as dicas do Início</span>
          </legend>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {OBJECTIVES.map((o) => (
              <Checkbox
                key={o.value}
                checked={objectives.includes(o.value)}
                onChange={(v) => setObjectives((l) => (v ? [...l, o.value] : l.filter((x) => x !== o.value)))}
              >
                {o.label}
              </Checkbox>
            ))}
          </div>
        </fieldset>
        <div className="flex items-end">
          <Button type="submit" disabled={s.busy || !name.trim()}>{s.busy && <Spinner />} Salvar perfil</Button>
        </div>
      </form>
    </Section>
  );
}
