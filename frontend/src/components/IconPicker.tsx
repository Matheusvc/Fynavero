import { useId } from "react";
import { Icon, ICON_CHOICES, ICON_LABELS } from "@/lib/icons";
import { cn } from "@/lib/utils";

export function IconPicker({ value, onChange, label = "Ícone" }: { value: string; onChange: (v: string) => void; label?: string }) {
  const id = useId();
  return (
    <div>
      <p id={id} className="mb-2 text-sm font-medium">
        {label}
      </p>
      <div role="radiogroup" aria-labelledby={id} className="grid grid-cols-6 gap-1.5 sm:grid-cols-8">
        {ICON_CHOICES.map((name) => (
          <button
            key={name}
            type="button"
            role="radio"
            aria-checked={value === name}
            aria-label={ICON_LABELS[name] ?? name}
            onClick={() => onChange(name)}
            className={cn(
              "grid aspect-square min-h-touch place-items-center rounded-lg border",
              value === name ? "border-primary bg-accent ring-2 ring-primary" : "bg-card hover:bg-muted",
            )}
          >
            <Icon name={name} className="size-5" />
          </button>
        ))}
      </div>
    </div>
  );
}
