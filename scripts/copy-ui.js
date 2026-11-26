const fs = require('fs');
const path = require('path');

const source = path.resolve('src/main/resources/static/dist');
const target = path.resolve('src/main/resources/static');
const index = path.join(source, 'index.html');

if (!fs.existsSync(index)) throw new Error('UI build output was not found');
fs.copyFileSync(index, path.join(target, 'index.html'));

const sourceAssets = path.join(source, 'assets');
const targetAssets = path.join(target, 'assets');
fs.mkdirSync(targetAssets, { recursive: true });
for (const entry of fs.readdirSync(sourceAssets)) {
  fs.copyFileSync(path.join(sourceAssets, entry), path.join(targetAssets, entry));
}
