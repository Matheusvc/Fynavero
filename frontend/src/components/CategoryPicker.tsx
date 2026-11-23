import { useId } from "react";
import { CategoryIcon } from "@/lib/icons";
import type { Category } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Grade de categorias selecionáveis (radiogroup, navegável por teclado). */
export function CategoryPicker({
  categories,
  value,
  onChange,
  label,
  error,
}: {
  categories: Category[];
  value: number | null;
  onChange: (id: number) => void;
  label: string;
  error?: string | null;
}) {
  const id = useId();
  return (
    <div>
      <p id={id} className="mb-2 text-sm font-medium">
        {label}
      </p>
      <div role="radiogroup" aria-labelledby={id} className="grid grid-cols-3 gap-2 sm:grid-cols-4">
        {categories.map((c) => {
          const selected = value === c.id;
          return (
            <button
              key={c.id}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => onChange(c.id)}
              className={cn(
                "flex min-h-20 flex-col items-center justify-center gap-1 rounded-lg border p-2 text-center text-xs font-medium transition-colors duration-(--duration-fast)",
                selected ? "border-primary bg-accent ring-2 ring-primary" : "bg-card hover:bg-muted",
              )}
            >
              <CategoryIcon icon={c.icon} color={c.color} size="sm" />
              <span className="line-clamp-2 leading-tight">{c.name}</span>
            </button>
          );
        })}
      </div>
      {error && (
        <p className="mt-2 text-sm text-negative" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
