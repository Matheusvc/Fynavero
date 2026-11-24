import { CircleAlert, Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

/** Estados de carregando, vazio e erro. */

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-lg bg-muted", className)} aria-hidden="true" />;
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("size-4 animate-spin", className)} aria-hidden="true" />;
}

export function EmptyState({
  icon,
  title,
  children,
  actions,
}: {
  icon?: ReactNode;
  title: string;
  children?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-4 py-10 text-center">
      {icon && <div className="grid size-14 place-items-center rounded-full bg-accent text-primary-text">{icon}</div>}
      <h3 className="text-lg font-semibold">{title}</h3>
      {children && <p className="max-w-sm text-sm text-muted-foreground">{children}</p>}
      {actions && <div className="mt-2 flex flex-wrap justify-center gap-2">{actions}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-xl border bg-card px-4 py-8 text-center">
      <CircleAlert className="size-8 text-negative" aria-hidden="true" />
      <p className="text-sm">{message}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Tentar de novo
        </Button>
      )}
    </div>
  );
}