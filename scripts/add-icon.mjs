// Выгрузка значка Material Symbols в VectorDrawable.
//
// Использование:
//   node scripts/add-icon.mjs medication calendar_month
//   node scripts/add-icon.mjs medication --fill        // залитый вариант выбранной вкладки
//   node scripts/add-icon.mjs medication --as tab_med_kits
//
// Почему скрипт, а не библиотека: `androidx.compose.material:material-icons-*` заморожена на
// 1.7.8 и с `material` 1.7.1 транзитивно не приходит; на смену ей пришли Material Symbols, и они
// раздаются картинками, а не Kotlin-объектами.
//
// Почему не рисуем руками: у Material Symbols `viewBox="0 -960 960 960"` — начало координат по Y
// отрицательное, чего VectorDrawable не умеет. Наивный перенос даёт пустую картинку, и искать
// причину потом дольше, чем написать перенос один раз.

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = resolve(root, 'app/src/main/res/drawable');
const STYLE = 'outlined'; // стиль набора: один на всё приложение

const args = process.argv.slice(2);
const fill = args.includes('--fill');
const alias = args.includes('--as') ? args[args.indexOf('--as') + 1] : null;
const names = args.filter((a, i) => !a.startsWith('--') && args[i - 1] !== '--as');

if (names.length === 0) {
  console.error('назовите значок: node scripts/add-icon.mjs <имя> [<имя>…] [--fill] [--as <имя файла>]');
  process.exit(1);
}
if (alias && names.length !== 1) {
  console.error('--as переименовывает один значок, а названо несколько');
  process.exit(1);
}

mkdirSync(OUT, { recursive: true });

for (const name of names) {
  const src = resolve(root, `node_modules/@material-symbols/svg-400/${STYLE}/${name}${fill ? '-fill' : ''}.svg`);
  if (!existsSync(src)) {
    console.error(`значка «${name}» в наборе нет; посмотреть похожие:`);
    console.error(`  ls node_modules/@material-symbols/svg-400/${STYLE}/ | grep ${name.split('_')[0]}`);
    process.exit(1);
  }

  const svg = readFileSync(src, 'utf8');
  const viewBox = svg.match(/viewBox="(-?[\d.]+) (-?[\d.]+) ([\d.]+) ([\d.]+)"/);
  if (!viewBox) throw new Error(`у ${name} не разобран viewBox`);
  const [, minX, minY, width, height] = viewBox.map(Number);

  const paths = [...svg.matchAll(/\sd="([^"]+)"/g)].map((m) => m[1]);
  if (paths.length === 0) throw new Error(`у ${name} нет ни одного пути`);

  // Цвет чёрный: `Icon()` перекрашивает по `LocalContentColor`, и тёмная тема получается сама.
  const body = paths
    .map((d) => `        <path\n            android:fillColor="#FF000000"\n            android:pathData="${d}" />`)
    .join('\n');

  const file = `${OUT}/ic_${alias ?? name}${fill ? '_filled' : ''}.xml`;
  writeFileSync(
    file,
    `<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols «${name}»${fill ? ', залитый' : ''}, выгружен scripts/add-icon.mjs. Руками не правится. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${width}"
    android:viewportHeight="${height}">
    <group
        android:translateX="${-minX}"
        android:translateY="${-minY}">
${body}
    </group>
</vector>
`
  );
  console.log(file.replace(`${root}/`, ''));
}
