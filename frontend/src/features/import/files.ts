import Papa from "papaparse";
import { type Sheet } from "@/features/import/parser";

export const MAX_BYTES = 5 * 1024 * 1024;

export async function readFile(file: File): Promise<Sheet[]> {
  if (/\.csv$/i.test(file.name)) {
    const buf = await file.arrayBuffer();
    let text = new TextDecoder("utf-8").decode(buf);
    if (text.includes("�")) text = new TextDecoder("windows-1252").decode(buf);
    const parsed = Papa.parse<string[]>(text, { skipEmptyLines: false });
    return [{ name: file.name.replace(/\.csv$/i, ""), rows: parsed.data }];
  }
  const XLSX = await import("xlsx");
  const wb = XLSX.read(await file.arrayBuffer(), { type: "array", cellDates: false });
  return wb.SheetNames.map((name) => ({
    name,
    rows: XLSX.utils.sheet_to_json<(string | number | null)[]>(wb.Sheets[name]!, { header: 1, raw: true, defval: null, blankrows: true }),
  }));
}

export async function downloadTemplate() {
  const XLSX = await import("xlsx");
  const rows = [
    ["Data", "Descrição", "Categoria", "Valor", "Tipo", "Forma de pagamento", "Parcela", "Observação"],
    ["05/11/2026", "Salário", "Salário", "3.575,00", "Receita", "Transferência", "", ""],
    ["06/11/2026", "Supermercado", "Mercado", "389,90", "Despesa", "Débito", "", ""],
    ["10/11/2026", "Geladeira", "Cartão de crédito", "490,00", "Despesa", "Crédito", "5/12", "Loja do centro"],
    ["12/11/2026", "Reserva", "Reserva de emergência", "300,00", "Investimento", "Pix", "", ""],
  ];
  const ws = XLSX.utils.aoa_to_sheet(rows);
  ws["!cols"] = [{ wch: 12 }, { wch: 22 }, { wch: 22 }, { wch: 12 }, { wch: 14 }, { wch: 20 }, { wch: 9 }, { wch: 24 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "Lançamentos");
  XLSX.writeFile(wb, "modelo-fynavero.xlsx");
}
