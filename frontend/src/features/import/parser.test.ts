import { describe, expect, it } from "vitest";
import { analyze, detectHeader, extractRows, parseDate, parseInstallment, parseMoney, parseSheetMonth, type Sheet } from "./parser";

describe("parseMoney", () => {
  it.each([
    ["1.160,42", 116042],
    ["R$ 1.160,42", 116042],
    ["R$ -10,00", -1000],
    ["(25,90)", -2590],
    ["1160.42", 116042],
    ["1.160", 116000],
    ["1,234,567", 123456700],
    ["12,5", 1250],
    ["0,05", 5],
    ["  89  ", 8900],
    ["1.234.567,89", 123456789],
  ] as const)("%s → %i centavos", (input, cents) => {
    expect(parseMoney(input)).toBe(cents);
  });

  it("números do Excel sem erro de ponto flutuante", () => {
    expect(parseMoney(1160.42)).toBe(116042);
    expect(parseMoney(0.1 + 0.2)).toBe(30);
  });

  it("rejeita texto", () => {
    expect(parseMoney("abc")).toBeNull();
    expect(parseMoney("")).toBeNull();
  });
});

describe("parseSheetMonth", () => {
  it.each([
    ["Nov 26", "2026-11"],
    ["novembro/2026", "2026-11"],
    ["Março 2025", "2025-03"],
    ["11-2026", "2026-11"],
    ["2026-01", "2026-01"],
    ["Dez", "2026-12"],
  ])("%s → %s", (name, ym) => {
    expect(parseSheetMonth(name, 2026)).toBe(ym);
  });
  it("aba sem mês", () => expect(parseSheetMonth("Lançamentos", 2026)).toBeNull());
});

describe("parseDate e parcelas", () => {
  it("datas em vários formatos", () => {
    expect(parseDate("05/11/2026", null)).toBe("2026-11-05");
    expect(parseDate("5/11/26", null)).toBe("2026-11-05");
    expect(parseDate("2026-11-05", null)).toBe("2026-11-05");
    expect(parseDate(46331, null)).toBe("2026-11-05");
    expect(parseDate(15, "2026-11")).toBe("2026-11-15");
    expect(parseDate("31/02/2026", null)).toBeNull();
  });
  it("5/12", () => {
    expect(parseInstallment("5/12")).toEqual({ n: 5, total: 12 });
    expect(parseInstallment("3 de 10")).toEqual({ n: 3, total: 10 });
    expect(parseInstallment("13/12")).toBeNull();
  });
});

describe("planilhas inteiras", () => {
  it("cabeçalho fora da linha 1", () => {
    const rows = [["Minha planilha"], [], ["Data", "Descrição", "Categoria", "Valor"], ["01/11/2026", "Feira", "Mercado", "120,50"]];
    expect(detectHeader(rows)?.row).toBe(2);
  });

  it("uma aba por mês, receitas no topo e totais iguais centavo a centavo", () => {
    const sheets: Sheet[] = [
      {
        name: "Out 26",
        rows: [
          ["Salário", "3.575,00"],
          ["Freela", 500],
          [],
          ["Dia", "Descrição", "Categoria", "Valor", "Parcela"],
          [3, "Aluguel", "Moradia", "1.160,42", ""],
          [10, "TV", "Compras", 490, "5/12"],
          ["", "Total", "", "1.650,42", ""],
        ],
      },
      {
        name: "Nov 26",
        rows: [
          ["Salário", "3.575,00"],
          ["Dia", "Descrição", "Categoria", "Valor"],
          [3, "Aluguel", "Moradia", "1.160,42"],
          [7, "Mercado", "Mercado", "R$ 389,99"],
          [8, "", "Mercado", "abc"],
        ],
      },
    ];
    const plans = analyze(sheets, 2026);
    expect(plans.map((p) => p.month)).toEqual(["2026-10", "2026-11"]);
    expect(plans[0]!.topIncomes.map((t) => t.cents)).toEqual([357500, 50000]);

    const rows = extractRows(sheets, plans, "DESPESA", "2026-11");
    const valid = rows.filter((r) => r.errors.length === 0);
    const errors = rows.filter((r) => r.errors.length > 0);
    expect(errors).toHaveLength(1);
    expect(errors[0]!.errors[0]).toBe("Valor inválido");

    const sum = (k: string) => valid.filter((r) => r.kind === k).reduce((s, r) => s + r.amountCents!, 0);
    expect(sum("RECEITA")).toBe(357500 + 50000 + 357500);
    expect(sum("DESPESA")).toBe(116042 + 49000 + 116042 + 38999);
    const tv = valid.find((r) => r.description === "TV")!;
    expect(tv.date).toBe("2026-10-10");
    expect([tv.installmentNumber, tv.installmentTotal]).toEqual([5, 12]);
  });

  it("valores com sinal sem coluna de tipo", () => {
    const sheets: Sheet[] = [
      { name: "Extrato", rows: [["Data", "Histórico", "Valor"], ["01/11/2026", "Salário", "4.000,00"], ["02/11/2026", "Padaria", "-12,00"]] },
    ];
    const rows = extractRows(sheets, analyze(sheets, 2026), "DESPESA", "2026-11");
    expect(rows.map((r) => [r.kind, r.amountCents])).toEqual([
      ["RECEITA", 400000],
      ["DESPESA", 1200],
    ]);
  });
});
