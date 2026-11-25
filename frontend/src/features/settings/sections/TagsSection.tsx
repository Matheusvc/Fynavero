import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronDown } from "lucide-react";
import { ColorPicker } from "@/components/ColorPicker";
import { TagChipView } from "@/components/TagInput";
import { Button } from "@/components/ui/button";
import { api, errorMessage } from "@/lib/api";
import { colorName } from "@/lib/colors";
import { useTags } from "@/lib/queries";
import type { TagCount } from "@/lib/types";
import { Section } from "../shared";

const TAGS_KEY = ["transactions", "tags"];

/** Cor de cada etiqueta (#viagem, #reforma): aparece nos lançamentos, na busca e nos filtros. */
export function TagsSection() {
  const qc = useQueryClient();
  const tags = useTags();
  const [open, setOpen] = useState<string | null>(null);

  async function setColor(tag: string, color: string | null) {
    const before = qc.getQueryData<TagCount[]>(TAGS_KEY);
    qc.setQueryData<TagCount[]>(TAGS_KEY, (old) => old?.map((t) => (t.tag === tag ? { ...t, color } : t)));
    try {
      await api("/api/transactions/tags/color", { method: "PUT", body: { tag, color } });
    } catch (e) {
      qc.setQueryData(TAGS_KEY, before);
      toast.error(errorMessage(e));
    }
  }

  const list = tags.data ?? [];
  return (
    <Section
      id="etiquetas"
      title="Etiquetas"
      description="Escolha uma cor para cada etiqueta e encontre seus lançamentos mais rápido. Para criar uma etiqueta, escreva no lançamento (ex.: #viagem)."
    >
      {list.length === 0 ? (
        <p className="rounded-lg border p-4 text-sm text-muted-foreground">
          Você ainda não usou etiquetas. Ao lançar, escreva “#viagem” na descrição ou use o campo Etiquetas.
        </p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {list.map((t) => {
            const expanded = open === t.tag;
            const panel = `cor-etiqueta-${t.tag}`;
            return (
              <li key={t.tag} className="px-2 py-2 sm:px-3">
                <div className="flex items-center gap-2">
                  <TagChipView tag={t.tag} color={t.color} />
                  <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
                    {t.count} {t.count === 1 ? "lançamento" : "lançamentos"}
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-expanded={expanded}
                    aria-controls={panel}
                    onClick={() => setOpen(expanded ? null : t.tag)}
                  >
                    {colorName(t.color) ?? "Sem cor"}
                    <ChevronDown className={expanded ? "rotate-180 transition-transform" : "transition-transform"} />
                  </Button>
                </div>
                {expanded && (
                  <div id={panel} className="pt-3 pb-1">
                    <ColorPicker
                      label={`Cor da etiqueta ${t.tag}`}
                      value={t.color}
                      defaultToken="chart-other"
                      onChange={(c) => void setColor(t.tag, c)}
                    />
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Section>
  );
}
