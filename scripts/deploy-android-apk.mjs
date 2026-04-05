#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const frontDir = path.join(repoRoot, 'front');
const androidDir = path.join(frontDir, 'android');
const capacitorPluginsGradlePath = path.join(androidDir, 'capacitor-cordova-android-plugins', 'build.gradle');
const capacitorAppGradlePath = path.join(frontDir, 'node_modules', '@capacitor', 'app', 'android', 'build.gradle');

const googleMirrorValue = 'https://maven.aliyun.com/repository/google';

function runCommand(command, args, cwd, extraEnv = {}) {
  const result = spawnSync(command, args, {
    cwd,
    env: {
      ...process.env,
      ...extraEnv,
    },
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });

  if (result.status !== 0) {
    throw new Error(`Command failed: ${command} ${args.join(' ')}`);
  }
}

function createAndroidBuildVersion() {
  const now = new Date();
  const year = String(now.getFullYear()).slice(-2);
  const startOfYear = new Date(now.getFullYear(), 0, 1);
  const dayOfYear = String(Math.floor((now - startOfYear) / 86400000) + 1).padStart(3, '0');
  const hour = String(now.getHours()).padStart(2, '0');
  const minute = String(now.getMinutes()).padStart(2, '0');

  return {
    versionCode: `${year}${dayOfYear}${hour}${minute}`,
    versionName: `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}.${hour}${minute}`,
  };
}

async function ensureAndroidGoogleMirror() {
  await Promise.all([
    patchCapacitorPluginGradle(capacitorPluginsGradlePath),
    patchCapacitorPluginGradle(capacitorAppGradlePath),
  ]);
}

async function patchCapacitorPluginGradle(gradlePath) {
  try {
    const original = await fs.readFile(gradlePath, 'utf-8');
    let next = original;

    if (!next.includes(`def googleMirror = '${googleMirrorValue}'`)) {
      next = next.replace(
        'buildscript {\n',
        `buildscript {\n    def googleMirror = '${googleMirrorValue}'\n`,
      );
    }

    next = next.replace(
      /buildscript \{\n\s+def googleMirror = 'https:\/\/maven\.aliyun\.com\/repository\/google'\n\s+repositories \{\n\s+google\(\)\n/,
      `buildscript {\n    def googleMirror = '${googleMirrorValue}'\n    repositories {\n        maven { url googleMirror }\n`,
    );

    next = next.replace(
      /repositories \{\n\s+google\(\)\n\s+mavenCentral\(\)/g,
      `repositories {\n    maven { url '${googleMirrorValue}' }\n    mavenCentral()`,
    );

    if (next !== original) {
      await fs.writeFile(gradlePath, next, 'utf-8');
    }
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
      return;
    }

    throw error;
  }
}

async function main() {
  const buildVersion = createAndroidBuildVersion();
  const buildEnv = {
    YOYUZH_ANDROID_VERSION_CODE: buildVersion.versionCode,
    YOYUZH_ANDROID_VERSION_NAME: buildVersion.versionName,
  };

  console.log(`Android versionCode=${buildVersion.versionCode}`);
  console.log(`Android versionName=${buildVersion.versionName}`);

  runCommand('npm', ['run', 'build'], frontDir, buildEnv);
  runCommand('npx', ['cap', 'sync', 'android'], frontDir, buildEnv);
  await ensureAndroidGoogleMirror();

  const gradleCommand = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  runCommand(gradleCommand, ['assembleDebug'], androidDir, buildEnv);
  runCommand('node', ['scripts/deploy-front-oss.mjs', '--skip-build'], repoRoot, buildEnv);
  runCommand('node', ['scripts/deploy-android-release.mjs'], repoRoot, buildEnv);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
