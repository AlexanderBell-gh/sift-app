#!/usr/bin/env node
// sift-app — zero-dep ops CLI. Never deploys, never signs release builds
// (CI owns verification; Play signing stays manual).
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ANDROID_HOME = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '';
const SIFT_DEFAULT = resolve(ROOT, '..', 'Sift');

const HELP = `Sift App — CLI (never deploys; CI owns verification)

Usage: node bin/sift-app.mjs <command> [flags]

  doctor                    toolchain, SDK packages, wrapper, env
  gate                      CI mirror: assembleDebug + unit tests + lint
  sync-assets [--sift-dir <path>]   re-copy logos + catalog JSON from Sift repo
  devices                   list attached devices/emulators (adb)

Env: ANDROID_HOME (or ANDROID_SDK_ROOT), JAVA_HOME (JDK 17+).`;

const argv = process.argv.slice(2);
const flags = { siftDir: process.env.SIFT_DIR || SIFT_DEFAULT };
const rest = [];
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === '--sift-dir') flags.siftDir = resolve(argv[++i]);
  else if (a.startsWith('--sift-dir=')) flags.siftDir = resolve(a.slice(11));
  else if (a === '--help' || a === '-h') { console.log(HELP); process.exit(0); }
  else rest.push(a);
}
const [cmd, ...args] = rest;

function fail(msg) { console.error(`sift-app: ${msg}`); process.exit(1); }
function run(bin, binArgs, opts = {}) {
  const r = spawnSync(bin, binArgs, { stdio: 'inherit', shell: false, cwd: ROOT, ...opts });
  if (r.status !== 0) process.exit(r.status ?? 1);
}
function runOut(bin, binArgs, opts = {}, fatal = true) {
  const r = spawnSync(bin, binArgs, { encoding: 'utf8', shell: false, ...opts });
  if (r.status !== 0) {
    const msg = ((r.stderr || '').trim() || (r.error && r.error.message) || `${bin} exited ${r.status}`);
    if (!fatal) throw new Error(msg);
    if (r.stderr) process.stderr.write(r.stderr);
    else console.error(`sift-app: ${msg}`);
    process.exit(r.status ?? 1);
  }
  // java -version reports on stderr; combine so callers parse either stream.
  return ((r.stdout || '') + '\n' + (r.stderr || '')).trim();
}
const gradlew = () => (process.platform === 'win32' ? 'gradlew.bat' : './gradlew');

const LOGOS = ['tesco', 'sainsburys', 'asda', 'morrisons', 'mands', 'aldi', 'lidl', 'coop', 'waitrose', 'iceland', 'ocado'];

function doctor() {
  let ok = true;
  const check = (name, fn) => {
    try { const v = fn(); console.log(`ok   ${name}${v ? ` (${v})` : ''}`); }
    catch (e) { ok = false; console.log(`FAIL ${name}: ${e.message}`); }
  };
  check('repo root', () => {
    if (!existsSync(join(ROOT, 'settings.gradle.kts'))) throw new Error('run from the sift-app repo root');
    return 'sift-app';
  });
  check('node', () => process.versions.node);
  check('java >= 17', () => {
    const out = runOut('java', ['-version'], { cwd: ROOT }, false);
    const m = out.match(/version "(?:1\.)?(\d+)/);
    const major = m ? Number(m[1]) : 0;
    if (!major || major < 17) throw new Error(`need JDK 17+, saw: ${out.split('\n')[0]}`);
    return out.split('\n')[0].replace(/"/g, '');
  });
  check('Android SDK', () => {
    if (!ANDROID_HOME) throw new Error('ANDROID_HOME/ANDROID_SDK_ROOT unset and no local.properties sdk.dir');
    if (!existsSync(ANDROID_HOME)) throw new Error(`missing dir: ${ANDROID_HOME}`);
    return ANDROID_HOME;
  });
  check('SDK packages', () => {
    const sdk = ANDROID_HOME;
    for (const p of ['platforms/android-34', 'build-tools/34.0.0', 'platform-tools']) {
      if (!existsSync(join(sdk, p))) throw new Error(`missing ${p} — sdkmanager "${p.replace('/', ';')}"`);
    }
    return 'platform-34, build-tools 34.0.0, platform-tools';
  });
  check('gradle wrapper', () => {
    if (!existsSync(join(ROOT, 'gradle/wrapper/gradle-wrapper.jar'))) throw new Error('missing wrapper jar — run `gradle wrapper`');
    return runOut(gradlew(), ['--version'], { cwd: ROOT }, false).split('\n').find(l => l.startsWith('Gradle ')) || 'present';
  });
  check('local.properties', () => {
    if (!existsSync(join(ROOT, 'local.properties'))) return 'absent (prod Worker default; see local.properties.example)';
    return 'present (sift.apiBase override active)';
  });
  check('bundled assets', () => {
    const missingLogos = LOGOS.filter(n => !existsSync(join(ROOT, 'app/src/main/res/drawable-nodpi', `${n}.png`)));
    const catalog = readdirSync(join(ROOT, 'app/src/main/assets')).filter(f => f.startsWith('uk-') && f.endsWith('.json'));
    if (missingLogos.length) throw new Error(`missing logos: ${missingLogos.join(', ')} — run sync-assets`);
    if (catalog.length === 0) throw new Error('no uk-*.json in assets — run sync-assets');
    return `${LOGOS.length - missingLogos.length} logos, ${catalog.length} catalog files`;
  });
  if (!ok) process.exit(1);
}

function gate() {
  run(gradlew(), ['assembleDebug', 'testDebugUnitTest', 'lint', '--stacktrace']);
}

function syncAssets() {
  const sift = flags.siftDir;
  if (!existsSync(join(sift, 'public', 'tesco.png'))) fail(`Sift repo not found at ${sift} (override with --sift-dir)`);
  const logoDir = join(ROOT, 'app/src/main/res/drawable-nodpi');
  const assetDir = join(ROOT, 'app/src/main/assets');
  mkdirSync(logoDir, { recursive: true });
  mkdirSync(assetDir, { recursive: true });
  let n = 0;
  for (const name of LOGOS) {
    copyFileSync(join(sift, 'public', `${name}.png`), join(logoDir, `${name}.png`));
    n++;
  }
  const catalog = readdirSync(join(sift, 'src/data')).filter(f => f.startsWith('uk-') && f.endsWith('.json'));
  for (const f of catalog) copyFileSync(join(sift, 'src/data', f), join(assetDir, f));
  console.log(`sift-app: synced ${n} logos + ${catalog.length} catalog files from ${sift}`);
}

function devices() {
  const adb = ANDROID_HOME ? join(ANDROID_HOME, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb';
  if (!existsSync(adb) && ANDROID_HOME) fail(`adb not found in ${ANDROID_HOME}/platform-tools`);
  run(existsSync(adb) ? adb : 'adb', ['devices', '-l']);
}

switch (cmd) {
  case 'doctor': doctor(); break;
  case 'gate': gate(); break;
  case 'sync-assets': syncAssets(); break;
  case 'devices': devices(); break;
  default:
    console.log(HELP);
    process.exit(cmd ? 1 : 0);
}
