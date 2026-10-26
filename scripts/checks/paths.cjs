// Shared paths for the rescued browser checks. Inputs remain project-relative;
// every generated file belongs under the ignored artifacts/qa directory.
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const root = path.resolve(__dirname, '../..');
const script = path.parse(process.argv[1] || 'manual').name;
const output = path.join(root, 'artifacts', 'qa', script);

function resolve(relative) {
  const absolute = path.resolve(root, relative);
  return /[\\/]$/.test(relative) ? absolute + path.sep : absolute;
}
function fileUrl(relative) {
  const [,pathname,suffix] = relative.match(/^([^?#]*)(.*)$/s);
  return pathToFileURL(resolve(pathname)).href + suffix;
}
function enter() {
  process.chdir(root);
  fs.mkdirSync(output, {recursive:true});
}
function browser() {
  const candidates = [process.env.YOUROOM_BROWSER,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'];
  const executable = candidates.find(candidate => candidate && fs.existsSync(candidate));
  if (!executable) throw new Error('Set YOUROOM_BROWSER to a Chrome or Edge executable.');
  return executable;
}
function playwright() {
  for (const name of ['playwright', 'playwright-core']) {
    try { return require(name); } catch (error) {
      if (error.code !== 'MODULE_NOT_FOUND') throw error;
    }
  }
  throw new Error('Install Node Playwright first: npm install --no-save --no-package-lock playwright');
}
module.exports = {root, output, resolve, fileUrl, enter, browser, playwright};
