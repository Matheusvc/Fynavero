import { cn } from "@/lib/utils";

const TONES = {
  primary: "bg-primary",
  positive: "bg-positive",
  warning: "bg-warning",
  negative: "bg-negative",
} as const;

export function Progress({
  value,
  max,
  label,
  tone = "primary",
  className,
}: {
  value: number;
  max: number;
  label: string;
  tone?: keyof typeof TONES;
  className?: string;
}) {
  const pct = max <= 0 ? 0 : Math.min(100, Math.max(0, (value / max) * 100));
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct)}
      className={cn("h-2.5 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <div className={cn("h-full rounded-full transition-[width] duration-(--duration-base)", TONES[tone])} style={{ width: `${pct}%` }} />
    </div>
  );
}
