#!/usr/bin/env node
// sift-app — zero-dep ops CLI. Never deploys, never signs release builds
// (CI owns verification; Play signing stays manual).
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, accessSync, constants } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const HOME_TOOLING_JDK = join(homedir(), 'tooling', 'jdk17');
const REPO_TOOLING_JDK = join(ROOT, 'tooling', 'jdk17');
const SIFT_DEFAULT = resolve(ROOT, '..', 'Sift');

const FRESH_MACHINE_NOTES = `Fresh-machine re-setup (repo-local tooling/ is deleted with the repo):
  1. JDK 17 — https://adoptium.net/temurin/releases/?version=17 (Linux x64 .tar.gz),
     unpack to ~/tooling/jdk17 (or repo tooling/jdk17)
  2. Android cmdline-tools — https://developer.android.com/studio#command-line-tools-only
     ("Command line tools only" linux zip), unpack to ~/Android/Sdk/cmdline-tools,
     rename inner dir to "latest", then:
       sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
  3. Gradle needs no manual install — gradle/wrapper/gradle-wrapper.jar is committed
  4. Persist in ~/.bashrc:
       export JAVA_HOME=~/tooling/jdk17 ANDROID_HOME=~/Android/Sdk
       export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"`;

function isExec(p) {
  try { accessSync(p, constants.X_OK); return true; }
  catch { return false; }
}

/** Resolve a java binary: $JAVA_HOME -> PATH -> ~/tooling/jdk17 -> repo tooling/jdk17. */
function resolveJava() {
  const fromHome = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : null;
  if (fromHome && isExec(fromHome)) return { bin: fromHome, home: process.env.JAVA_HOME, via: 'JAVA_HOME' };
  try {
    runOut('java', ['-version'], { cwd: ROOT }, false);
    return { bin: 'java', home: null, via: 'PATH' };
  } catch { /* not on PATH — try portable fallbacks */ }
  for (const [home, via] of [[HOME_TOOLING_JDK, '~/tooling/jdk17'], [REPO_TOOLING_JDK, 'repo tooling/jdk17']]) {
    const bin = join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (isExec(bin)) return { bin, home, via };
  }
  return null;
}

/** Env for spawning Gradle: fills JAVA_HOME/ANDROID_HOME from resolvers
 *  so doctor/gate work in shells without exports. */
function gradleEnv() {
  const env = { ...process.env };
  const j = resolveJava();
  if (j && j.home && !env.JAVA_HOME) env.JAVA_HOME = j.home;
  const s = resolveSdk();
  if (s && !env.ANDROID_HOME && !env.ANDROID_SDK_ROOT) env.ANDROID_HOME = s.dir;
  return env;
}
/** Resolve the Android SDK: env -> local.properties sdk.dir. */
function resolveSdk() {
  const fromEnv = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '';
  if (fromEnv && existsSync(fromEnv)) return { dir: fromEnv, via: 'env' };
  try {
    const props = readFileSync(join(ROOT, 'local.properties'), 'utf8');
    const m = props.match(/^\s*sdk\.dir\s*=\s*(.+?)\s*$/m);
    if (m && existsSync(m[1])) return { dir: m[1], via: 'local.properties sdk.dir' };
  } catch { /* no local.properties — fall through */ }
  return null;
}

const HELP = `Sift App — CLI (never deploys; CI owns verification)

Usage: node bin/sift-app.mjs <command> [args]

  doctor                    toolchain, SDK packages, wrapper, env
  gate                      CI mirror: assemble + test + lint
  assemble [args]           ./gradlew assembleDebug (extra args forwarded)
  test [args]               ./gradlew testDebugUnitTest (extra args forwarded)
  lint [args]               ./gradlew lint (extra args forwarded)
  connectedCheck [args]     ./gradlew connectedCheck, needs emulator (extra args forwarded)
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
    const j = resolveJava();
    if (!j) throw new Error('no JDK found (JAVA_HOME, PATH, ~/tooling/jdk17, repo tooling/jdk17) — see fresh-machine notes below');
    const out = runOut(j.bin, ['-version'], { cwd: ROOT }, false);
    const m = out.match(/version "(?:1\.)?(\d+)/);
    const major = m ? Number(m[1]) : 0;
    if (!major || major < 17) throw new Error(`need JDK 17+, saw: ${out.split('\n')[0]}`);
    const repoLocal = j.via === 'repo tooling/jdk17' ? ' — repo-local, re-download if repo is deleted' : '';
    return `${out.split('\n')[0].replace(/"/g, '')} [via ${j.via}${repoLocal}]`;
  });
  check('Android SDK', () => {
    const s = resolveSdk();
    if (!s) throw new Error('no SDK found (ANDROID_HOME/ANDROID_SDK_ROOT, local.properties sdk.dir) — see fresh-machine notes below');
    return `${s.dir} [via ${s.via}]`;
  });
  check('SDK packages', () => {
    const s = resolveSdk();
    if (!s) throw new Error('no SDK dir to check packages against');
    for (const p of ['platforms/android-34', 'build-tools/34.0.0', 'platform-tools']) {
      if (!existsSync(join(s.dir, p))) throw new Error(`missing ${p} — sdkmanager "${p.replace('/', ';')}"`);
    }
    return 'platform-34, build-tools 34.0.0, platform-tools';
  });
  check('gradle wrapper', () => {
    if (!existsSync(join(ROOT, 'gradle/wrapper/gradle-wrapper.jar'))) throw new Error('missing wrapper jar — run `gradle wrapper`');
    return runOut(gradlew(), ['--version'], { cwd: ROOT, env: gradleEnv() }, false).split('\n').find(l => l.startsWith('Gradle ')) || 'present';
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
  if (!ok) {
    console.log(`\n${FRESH_MACHINE_NOTES}`);
    process.exit(1);
  }
}

/** Shared Gradle runner: resolves toolchain/SDK so subcommands work in
 *  shells without exports. Forwards extra CLI args (bare `--` stripped). */
function gradleTasks(tasks, extraArgs = []) {
  // Self-configure so commands work in shells without exports, as long as a
  // JDK/SDK exists in one of the known spots (see resolveJava/resolveSdk).
  const env = gradleEnv();
  const j = resolveJava();
  if (!j) fail('no JDK found — run doctor for fresh-machine re-setup notes');
  const s = resolveSdk();
  if (!s) fail('no Android SDK found — run doctor for fresh-machine re-setup notes');
  const extra = extraArgs.filter(a => a !== '--');
  const args = [...tasks];
  if (!extra.includes('--stacktrace')) args.push('--stacktrace');
  args.push(...extra);
  console.log(`sift-app: gradle ${tasks.join(' ')} (java via ${j.via}, SDK via ${s.via})`);
  run(gradlew(), args, { env });
}

function assemble(extraArgs = []) { gradleTasks(['assembleDebug'], extraArgs); }
function unitTest(extraArgs = []) { gradleTasks(['testDebugUnitTest'], extraArgs); }
function lint(extraArgs = []) { gradleTasks(['lint'], extraArgs); }
function connectedCheck(extraArgs = []) { gradleTasks(['connectedCheck'], extraArgs); }

function gate(extraArgs = []) {
  gradleTasks(['assembleDebug', 'testDebugUnitTest', 'lint'], extraArgs);
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
  const s = resolveSdk();
  if (!s) fail('no Android SDK found — run doctor for fresh-machine re-setup notes');
  const adb = join(s.dir, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  if (!existsSync(adb)) fail(`adb not found in ${s.dir}/platform-tools`);
  run(adb, ['devices', '-l']);
}

switch (cmd) {
  case 'doctor': doctor(); break;
  case 'gate': gate(args); break;
  case 'assemble': assemble(args); break;
  case 'test': unitTest(args); break;
  case 'lint': lint(args); break;
  case 'connectedCheck': connectedCheck(args); break;
  case 'sync-assets': syncAssets(); break;
  case 'devices': devices(); break;
  default:
    console.log(HELP);
    process.exit(cmd ? 1 : 0);
}
