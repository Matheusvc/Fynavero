import { useSyncExternalStore } from "react";

/**
 * "Ocultar valores" (o olho no saldo): esconde os valores em reais em todo o app, como nos apps de banco.
 * Fica salvo neste navegador; não muda nada nos dados.
 */
const KEY = "fyn-hide-values";
const listeners = new Set<() => void>();

let hidden = (() => {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return false;
  }
})();

export const HIDDEN_MONEY = "R$ ••••••";

export function valuesHidden() {
  return hidden;
}

export function setValuesHidden(v: boolean) {
  hidden = v;
  try {
    localStorage.setItem(KEY, v ? "1" : "0");
  } catch {
    // sem armazenamento, vale só até recarregar a página
  }
  listeners.forEach((l) => l());
}

export function useHideValues(): [boolean, () => void] {
  const value = useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => hidden,
  );
  return [value, () => setValuesHidden(!hidden)];
}

/** Esconde valores em reais dentro de textos prontos (dicas do Início, avisos do sino) quando "Ocultar valores" está ligado. */
export function maskMoney(text: string, hide: boolean): string {
  return hide ? text.replace(/[−-]?R\$\s?[\d.]+(,\d{2})?/g, HIDDEN_MONEY) : text;
}
