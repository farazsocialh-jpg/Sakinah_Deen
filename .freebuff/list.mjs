const fs = require('fs');
const p = JSON.parse(fs.readFileSync('.freebuff/missing.json','utf8'));
const skip = new Set(['notif_prayer_window','guide_step_badge']);
let i=0;
for (const [k,v] of p) { if (skip.has(k)) continue; i++; console.log(`${i}\t${k}\t${v.replace(/\n/g,' ')}`); }
