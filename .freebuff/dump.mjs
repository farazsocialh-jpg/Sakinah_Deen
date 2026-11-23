const fs = require('fs');
const parse = (p) => {
  const t = fs.readFileSync(p, 'utf8');
  const out = new Map();
  const re = /<string\b([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(t))) {
    const names = [...m[1].matchAll(/\bname="([^"]+)"/g)];
    if (!names.length) continue;
    out.set(names[0][1], m[2].trim());
  }
  return out;
};
const en = parse('app/src/main/res/values/strings.xml');
const ar = parse('app/src/main/res/values-ar/strings.xml');
const ur = parse('app/src/main/res/values-ur/strings.xml');
const untr = new Set();
const re2 = /<string\b([^>]*translatable="false"[^>]*)>/g; let m2;
const raw = fs.readFileSync('app/src/main/res/values/strings.xml','utf8');
while ((m2 = re2.exec(raw))) { const n = m2[1].match(/name="([^"]+)"/); if (n) untr.add(n[1]); }
const missing = [...en.keys()].filter(k => !ar.has(k));
const identical = [...en.keys()].filter(k => ar.has(k) && ar.get(k) === en.get(k));
const identUr = [...en.keys()].filter(k => ur.has(k) && ur.get(k) === en.get(k));
console.log(JSON.stringify({ enTotal: en.size, arTotal: ar.size, untranslatable: [...untr], missingCount: missing.length, identicalCount: identical.length, identical, identicalUr: identUr }, null, 1));
fs.writeFileSync('.freebuff/missing.json', JSON.stringify(missing.map(k => [k, en.get(k)]), null, 1));
