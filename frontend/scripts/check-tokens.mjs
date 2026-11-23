// Garante que nenhum valor visual fixo exista fora de src/styles/tokens.css.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../src", import.meta.url));
const allowed = new Set([join("styles", "tokens.css")]);
const rules = [
  { name: "cor hexadecimal", re: /#[0-9a-fA-F]{3,8}\b(?![\w-])/g },
  { name: "função de cor", re: /\b(?:rgba?|hsla?|oklch|oklab|lab|lch|color-mix)\(/g },
  { name: "fonte fixa", re: /font-family\s*:(?!\s*var\()/g },
  { name: "valor arbitrário de cor/sombra no Tailwind", re: /\b(?:bg|text|border|fill|stroke|shadow|ring|outline|from|to|via)-\[(?:#|rgba?\(|hsla?\(|oklch\(|oklab\(|\d+px_\d)/g },
  { name: "raio fixo", re: /\brounded(?:-\w+)?-\[(?!var\()/g },
];

const files = [];
(function walk(dir) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) walk(p);
    else if (/\.(tsx?|css)$/.test(f)) files.push(p);
  }
})(root);

const problems = [];
for (const file of files) {
  const rel = relative(root, file);
  if (allowed.has(rel)) continue;
  const lines = readFileSync(file, "utf8").split(/\r?\n/);
  lines.forEach((line, i) => {
    for (const r of rules) {
      r.re.lastIndex = 0;
      if (r.re.test(line)) problems.push(`src${sep}${rel}:${i + 1}  ${r.name}: ${line.trim()}`);
    }
  });
}

if (problems.length) {
  console.error(`Valores visuais fora dos tokens (${problems.length}):\n` + problems.join("\n"));
  process.exit(1);
}
console.log(`OK: ${files.length} arquivos sem valores visuais fixos.`);
