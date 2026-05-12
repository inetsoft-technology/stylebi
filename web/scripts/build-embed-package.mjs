/**
 * @fileoverview Builds the StyleBI embed elements npm package.
 * Copies compiled assets and generates package.json for distribution.
 *
 * Environment variables:
 *   EMBED_PACKAGE_NAME    - Override package name (default: @inetsoft-technology/stylebi-embed-elements)
 *   EMBED_PACKAGE_VERSION - Override package version (default: reads from package.json)
 *   VERBOSE               - Enable verbose logging (default: false)
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Path constants
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const PROJECT_ROOT = path.resolve(__dirname, "..");
export const PACKAGE_TEMPLATE_DIR = path.join(PROJECT_ROOT, "embed-package");
export const OUTPUT_DIR = path.join(PROJECT_ROOT, "target", "npm", "stylebi-embed-elements");
export const GENERATED_RESOURCES_DIR = path.join(PROJECT_ROOT, "target", "generated-resources");

const GULP_APP_DIR = path.join(GENERATED_RESOURCES_DIR, "gulp", "inetsoft", "web", "resources", "app");
const NG_ELEMENTS_ASSETS_DIR = path.join(GENERATED_RESOURCES_DIR, "ng", "inetsoft", "web", "resources", "elements", "assets");
const FALLBACK_ASSETS_DIR = path.join(PROJECT_ROOT, "projects", "portal", "src", "assets");

// Configuration
const verbose = process.env.VERBOSE === "true" || process.env.VERBOSE === "1";
const packageName = process.env.EMBED_PACKAGE_NAME || "@inetsoft-technology/stylebi-embed-elements";
const packageVersion = process.env.EMBED_PACKAGE_VERSION || readRootVersion();

/** @type {Array<{source: string, target: string}>} */
export const DISTRIBUTABLE_FILES = [
   { source: path.join(GULP_APP_DIR, "elements.js"), target: "elements.js" },
   { source: path.join(GULP_APP_DIR, "elements.css"), target: "elements.css" },
   { source: path.join(GULP_APP_DIR, "viewer-element.js"), target: "viewer-element.js" },
   { source: path.join(GULP_APP_DIR, "viewer-element.css"), target: "viewer-element.css" }
];

/**
 * Logs a message if verbose mode is enabled.
 * @param {string} message - The message to log
 */
function log(message) {
   if(verbose) {
      process.stdout.write(`[embed-package] ${message}\n`);
   }
}

/**
 * Builds the embed package by copying assets and generating package.json.
 * @returns {string} The output directory path
 * @throws {Error} If required files are missing or file operations fail
 */
export function buildEmbedPackage() {
   log(`Building package: ${packageName}@${packageVersion}`);

   ensureFilesExist(DISTRIBUTABLE_FILES.map(file => file.source));

   log(`Cleaning output directory: ${OUTPUT_DIR}`);
   fs.rmSync(OUTPUT_DIR, { recursive: true, force: true });
   fs.mkdirSync(OUTPUT_DIR, { recursive: true });

   log("Copying distributable files...");

   for(const file of DISTRIBUTABLE_FILES) {
      log(`  ${file.target}`);
      fs.copyFileSync(file.source, path.join(OUTPUT_DIR, file.target));
   }

   const assetsDir = resolveAssetsDir();
   log(`Copying assets from: ${path.relative(PROJECT_ROOT, assetsDir)}`);
   copyDir(assetsDir, path.join(OUTPUT_DIR, "assets"));

   log("Copying template files...");
   copyTemplateFiles();

   log("Writing package.json...");
   writePackageJson();

   log(`Build complete: ${OUTPUT_DIR}`);
   return OUTPUT_DIR;
}

/** @type {string[]} */
const TEMPLATE_FILES = [
   "README.md",
   "index.mjs",
   "loader.mjs",
   "register-all.mjs",
   "register-elements.mjs",
   "register-viewer.mjs"
];

/**
 * Copies template files from the embed-package directory to the output directory.
 * @throws {Error} If template files are missing or copy fails
 */
function copyTemplateFiles() {
   for(const file of TEMPLATE_FILES) {
      const sourcePath = path.join(PACKAGE_TEMPLATE_DIR, file);
      const targetPath = path.join(OUTPUT_DIR, file);

      if(!fs.existsSync(sourcePath)) {
         throw new Error(`Missing template file: ${file}`);
      }

      log(`  ${file}`);
      fs.copyFileSync(sourcePath, targetPath);
   }
}

/**
 * Generates and writes the package.json file for the npm package.
 */
function writePackageJson() {
   const packageJson = {
      name: packageName,
      version: packageVersion,
      description: "Prebuilt StyleBI embed web components.",
      license: "AGPL-3.0-or-later",
      type: "module",
      files: [
         "*.css",
         "*.js",
         "*.mjs",
         "assets/**/*",
         "README.md"
      ],
      exports: {
         ".": "./index.mjs",
         "./register-all": "./register-all.mjs",
         "./register-elements": "./register-elements.mjs",
         "./register-viewer": "./register-viewer.mjs",
         "./elements.css": "./elements.css",
         "./elements.js": "./elements.js",
         "./viewer-element.css": "./viewer-element.css",
         "./viewer-element.js": "./viewer-element.js",
         "./assets/*": "./assets/*"
      },
      sideEffects: [
         "*.css",
         "./elements.js",
         "./viewer-element.js",
         "./register-all.mjs",
         "./register-elements.mjs",
         "./register-viewer.mjs"
      ],
      publishConfig: {
         registry: "https://npm.pkg.github.com",
         access: "restricted"
      }
   };

   const outputPath = path.join(OUTPUT_DIR, "package.json");
   fs.writeFileSync(outputPath, `${JSON.stringify(packageJson, null, 2)}\n`);
   log(`  package.json (${packageName}@${packageVersion})`);
}

/**
 * Resolves the assets directory, preferring the generated ng assets over the source assets.
 * @returns {string} The resolved assets directory path
 * @throws {Error} If no assets directory can be found
 */
function resolveAssetsDir() {
   if(fs.existsSync(NG_ELEMENTS_ASSETS_DIR)) {
      log(`Using generated assets: ${path.relative(PROJECT_ROOT, NG_ELEMENTS_ASSETS_DIR)}`);
      return NG_ELEMENTS_ASSETS_DIR;
   }

   if(fs.existsSync(FALLBACK_ASSETS_DIR)) {
      log(`Using fallback assets: ${path.relative(PROJECT_ROOT, FALLBACK_ASSETS_DIR)}`);
      return FALLBACK_ASSETS_DIR;
   }

   throw new Error(
      `Unable to locate embed assets directory. Checked:\n` +
      `  - ${NG_ELEMENTS_ASSETS_DIR}\n` +
      `  - ${FALLBACK_ASSETS_DIR}`
   );
}

/**
 * Validates that all required files exist.
 * @param {string[]} files - Array of file paths to check
 * @throws {Error} If any files are missing
 */
function ensureFilesExist(files) {
   const missingFiles = files.filter(file => !fs.existsSync(file));

   if(missingFiles.length > 0) {
      const relativeFiles = missingFiles
         .map(file => path.relative(PROJECT_ROOT, file))
         .join("\n  - ");
      throw new Error(
         `Missing embed build artifacts:\n  - ${relativeFiles}\n\n` +
         `Run "npm run build:embed:prod" first.`
      );
   }
}

/**
 * Reads the version from the root package.json.
 * @returns {string} The package version
 * @throws {Error} If package.json cannot be read or parsed
 */
function readRootVersion() {
   const packageJsonPath = path.join(PROJECT_ROOT, "package.json");

   try {
      const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));

      if(!packageJson.version) {
         throw new Error("No version field found in package.json");
      }

      return packageJson.version;
   }
   catch(error) {
      if(error.code === "ENOENT") {
         throw new Error(`package.json not found at ${packageJsonPath}`);
      }

      throw error;
   }
}

/**
 * Recursively copies a directory.
 * @param {string} sourceDir - Source directory path
 * @param {string} targetDir - Target directory path
 * @throws {Error} If copy fails
 */
function copyDir(sourceDir, targetDir) {
   try {
      fs.cpSync(sourceDir, targetDir, { recursive: true });
   }
   catch(error) {
      throw new Error(`Failed to copy directory ${sourceDir} to ${targetDir}: ${error.message}`, { cause: error });
   }
}

// CLI entry point
if(process.argv[1] === __filename) {
   try {
      const packageDir = buildEmbedPackage();
      process.stdout.write(`Embed package created at ${packageDir}\n`);
   }
   catch(error) {
      process.stderr.write(`Error: ${error.message}\n`);
      process.exit(1);
   }
}
