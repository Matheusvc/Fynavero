import { useState, type KeyboardEvent } from "react";
import { Hash, X } from "lucide-react";
import { cn } from "@/lib/utils";

const MAX_TAGS = 10;

/** Mesma regra do back-end: sem "#", minúsculas, espaços viram "-", só letras, números, "-" e "_". */
export function cleanTag(raw: string): string {
  return raw
    .trim()
    .replace(/^#+/, "")
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^\p{L}\p{N}_-]/gu, "")
    .slice(0, 30);
}

/** Etiquetas (#viagem, #reforma) em chips. Enter, vírgula ou espaço adicionam; Backspace remove a última. */
export function TagInput({
  value,
  onChange,
  suggestions = [],
  id,
  ...aria
}: {
  value: string[];
  onChange: (v: string[]) => void;
  suggestions?: string[];
  id?: string;
  "aria-describedby"?: string;
}) {
  const [draft, setDraft] = useState("");

  /** Adiciona uma ou mais etiquetas de uma vez (ex.: "viagem, praia" colado de outro lugar). */
  const add = (raw: string, rest = "") => {
    setDraft(rest);
    const next = [...value];
    for (const part of raw.split(/[,\s]+/)) {
      const t = cleanTag(part);
      if (t && !next.includes(t) && next.length < MAX_TAGS) next.push(t);
    }
    if (next.length !== value.length) onChange(next);
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" || e.key === "," || e.key === " ") {
      if (!draft.trim()) {
        if (e.key === "Enter") e.preventDefault();
        return;
      }
      e.preventDefault();
      add(draft);
    } else if (e.key === "Backspace" && !draft && value.length) {
      onChange(value.slice(0, -1));
    }
  };

  const typed = cleanTag(draft);
  const options = suggestions.filter((s) => !value.includes(s) && (!typed || s.startsWith(typed))).slice(0, 6);

  return (
    <div>
      <div
        className={cn(
          "flex min-h-touch w-full flex-wrap items-center gap-1.5 rounded-lg border border-input bg-card px-2 py-1.5",
          "focus-within:outline-2 focus-within:outline-offset-1 focus-within:outline-ring",
        )}
      >
        {value.map((t) => (
          <span key={t} className="inline-flex items-center gap-0.5 rounded-full bg-accent py-0.5 pr-1 pl-2 text-sm text-accent-foreground">
            #{t}
            <button
              type="button"
              onClick={() => onChange(value.filter((x) => x !== t))}
              className="grid size-6 place-items-center rounded-full hover:bg-muted"
              aria-label={`Remover etiqueta ${t}`}
            >
              <X className="size-3.5" aria-hidden="true" />
            </button>
          </span>
        ))}
        <input
          {...aria}
          id={id}
          value={draft}
          onChange={(e) => {
            const v = e.target.value;
            // Teclados de celular nem sempre avisam a tecla: vírgula ou espaço no texto fecham a etiqueta.
            // O que vem depois do último separador continua sendo digitado.
            const cut = Math.max(v.lastIndexOf(","), v.search(/\s\S*$/));
            if (cut >= 0 && v.slice(0, cut).trim()) add(v.slice(0, cut), v.slice(cut + 1).trimStart());
            else setDraft(v.replace(/^[,\s]+/, ""));
          }}
          onKeyDown={onKey}
          onBlur={() => draft.trim() && add(draft)}
          placeholder={value.length ? "" : "Ex.: viagem, reforma"}
          className="min-w-24 flex-1 bg-transparent px-1 text-base text-foreground outline-none placeholder:text-muted-foreground"
          enterKeyHint="done"
        />
      </div>
      {options.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Etiquetas já usadas">
          {options.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => add(s)}
              className="inline-flex min-h-8 items-center gap-0.5 rounded-full border bg-card px-2.5 text-sm text-muted-foreground hover:bg-muted"
            >
              <Hash className="size-3" aria-hidden="true" />
              {s}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** Etiquetas como chips pequenos, para listas. */
export function TagList({ tags, onSelect, className }: { tags: string[]; onSelect?: (t: string) => void; className?: string }) {
  if (!tags.length) return null;
  return (
    <span className={cn("inline-flex flex-wrap gap-1", className)}>
      {tags.map((t) =>
        onSelect ? (
          <button
            key={t}
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onSelect(t);
            }}
            className="rounded-md bg-accent px-1.5 py-0.5 text-xs font-medium text-accent-foreground hover:underline"
          >
            #{t}
          </button>
        ) : (
          <span key={t} className="rounded-md bg-accent px-1.5 py-0.5 text-xs font-medium text-accent-foreground">
            #{t}
          </span>
        ),
      )}
    </span>
  );
}
