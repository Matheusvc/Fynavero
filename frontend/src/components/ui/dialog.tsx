import { Dialog as D } from "radix-ui";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Modal no desktop, bottom sheet no celular. Foco preso, Esc fecha, título anunciado. */
export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  className,
  initialFocus,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
  initialFocus?: () => HTMLElement | null;
}) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-overlay data-[state=open]:animate-[fade-in_var(--duration-base)_ease-out]" />
        <D.Content
          onOpenAutoFocus={(e) => {
            const el = initialFocus?.();
            if (el) {
              e.preventDefault();
              el.focus();
            }
          }}
          className={cn(
            "fixed z-50 flex max-h-[92dvh] flex-col bg-popover text-popover-foreground shadow-lg outline-none",
            "inset-x-0 bottom-0 rounded-t-xl data-[state=open]:animate-[sheet-in_var(--duration-base)_var(--ease-standard)]",
            "sm:inset-auto sm:top-1/2 sm:left-1/2 sm:w-[min(32rem,calc(100vw-2rem))] sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:data-[state=open]:animate-[fade-in_var(--duration-base)_ease-out]",
            className,
          )}
        >
          <div className="flex items-start justify-between gap-4 border-b px-5 pt-4 pb-3">
            <div className="min-w-0">
              <D.Title className="text-lg font-semibold">{title}</D.Title>
              {description ? (
                <D.Description className="text-sm text-muted-foreground">{description}</D.Description>
              ) : (
                <D.Description className="sr-only">Formulário</D.Description>
              )}
            </div>
            <D.Close className="-mt-1 -mr-2 grid size-touch shrink-0 place-items-center rounded-lg text-muted-foreground hover:bg-muted">
              <X className="size-5" aria-hidden="true" />
              <span className="sr-only">Fechar</span>
            </D.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
          {footer && (
            <div className="flex flex-col-reverse gap-2 border-t px-5 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end">
              {footer}
            </div>
          )}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}
