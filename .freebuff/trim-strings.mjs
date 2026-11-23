import { readFileSync, writeFileSync } from "node:fs";

// A translation that is byte-identical to the English default is not a translation: keeping it
// prevents Android's fallback and hides the gap from translators. Drop those entries so the
// resource falls back to values/strings.xml, which renders the same text.
const files = [
  "app/src/main/res/values-ar/strings.xml",
  "app/src/main/res/values-ur/strings.xml",
];

const STRING_LINE = /^\s*<string\b[^>]*>(.*?)<\/string>\s*$/;

for (const path of files) {
  const raw = readFileSync(path, "utf8").replace(/\r\n/g, "\n");
  const lines = raw.split("\n");
  const kept = [];
  let removed = 0;
  for (const line of lines) {
    const m = STRING_LINE.exec(line);
    if (m) {
      const value = m[1];
      const hasNonLatin = /[^\x00-\x7F]/.test(value);
      const untranslatable = /translatable="false"/.test(line);
      if (!hasNonLatin && !untranslatable) {
        removed++;
        continue;
      }
    }
    kept.push(line);
  }
  // collapse the blank lines left behind into at most one between entries
  const cleaned = [];
  for (const line of kept) {
    if (line.trim() === "" && cleaned.length && cleaned[cleaned.length - 1].trim() === "") continue;
    cleaned.push(line);
  }
  writeFileSync(path, cleaned.join("\n"), "utf8");
  const remaining = cleaned.filter((l) => /<string\b/.test(l)).length;
  console.log(`${path}: removed ${removed} English placeholders, ${remaining} translated entries remain`);
}
