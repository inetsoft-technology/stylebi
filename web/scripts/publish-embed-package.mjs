/**
 * @fileoverview Publishes the StyleBI embed elements package to npm registry.
 *
 * Environment variables:
 *   NODE_AUTH_TOKEN / GITHUB_TOKEN - Authentication token for npm registry
 *   VERBOSE                        - Enable verbose logging (default: false)
 *
 * Usage:
 *   node publish-embed-package.mjs [npm publish options]
 *   node publish-embed-package.mjs --dry-run
 *   node publish-embed-package.mjs --tag beta
 */

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { buildEmbedPackage } from "./build-embed-package.mjs";

const __filename = fileURLToPath(import.meta.url);

// Configuration
const REGISTRY_SCOPE = "@inetsoft-technology";
const REGISTRY_URL = "https://npm.pkg.github.com";
const verbose = process.env.VERBOSE === "true" || process.env.VERBOSE === "1";

/**
 * Logs a message if verbose mode is enabled.
 * @param {string} message - The message to log
 */
function log(message) {
   if(verbose) {
      process.stdout.write(`[publish] ${message}\n`);
   }
}

/**
 * Gets the authentication token from environment variables.
 * @returns {string|undefined} The auth token or undefined if not set
 */
function getAuthToken() {
   return process.env.NODE_AUTH_TOKEN || process.env.GITHUB_TOKEN;
}

/**
 * Creates a temporary .npmrc file with registry authentication.
 * @param {string} packageDir - The package directory
 * @param {string} token - The authentication token
 * @returns {string} The path to the created .npmrc file
 */
function createNpmrc(packageDir, token) {
   const npmrcPath = path.join(packageDir, ".npmrc");
   const content = [
      `${REGISTRY_SCOPE}:registry=${REGISTRY_URL}`,
      `//${new URL(REGISTRY_URL).host}/:_authToken=${token}`
   ].join("\n") + "\n";

   fs.writeFileSync(npmrcPath, content, { mode: 0o600 });
   log(`Created .npmrc at ${npmrcPath}`);

   return npmrcPath;
}

/**
 * Removes the temporary .npmrc file if it exists.
 * @param {string} npmrcPath - The path to the .npmrc file
 */
function cleanupNpmrc(npmrcPath) {
   if(fs.existsSync(npmrcPath)) {
      fs.rmSync(npmrcPath, { force: true });
      log(`Removed .npmrc at ${npmrcPath}`);
   }
}

/**
 * Publishes the package to the npm registry.
 * @param {string} packageDir - The package directory to publish
 * @param {string[]} args - Additional npm publish arguments
 * @returns {number} The exit code from npm publish
 */
function publish(packageDir, args) {
   const npmArgs = ["publish", ...args];
   const isDryRun = args.includes("--dry-run");

   log(`Running: npm ${npmArgs.join(" ")}`);
   log(`Working directory: ${packageDir}`);

   if(isDryRun) {
      process.stdout.write("[dry-run] Running npm publish in dry-run mode (no actual publish)\n");
   }

   const result = spawnSync("npm", npmArgs, {
      cwd: packageDir,
      stdio: "inherit",
      shell: process.platform === "win32"
   });

   return result.status ?? 1;
}

/**
 * Main entry point for publishing the embed package.
 */
function main() {
   const token = getAuthToken();
   const args = process.argv.slice(2);
   let npmrcPath = null;

   try {
      // Build the package first
      log("Building embed package...");
      const packageDir = buildEmbedPackage();

      // Create .npmrc if token is available
      if(token) {
         npmrcPath = createNpmrc(packageDir, token);
      }
      else {
         log("No auth token found, using existing npm configuration");
      }

      // Publish
      const exitCode = publish(packageDir, args);

      if(exitCode !== 0) {
         process.exit(exitCode);
      }

      process.stdout.write("Package published successfully\n");
   }
   catch(error) {
      process.stderr.write(`Error: ${error.message}\n`);
      process.exit(1);
   }
   finally {
      // Always clean up the .npmrc file
      if(npmrcPath) {
         cleanupNpmrc(npmrcPath);
      }
   }
}

// CLI entry point
if(process.argv[1] === __filename) {
   main();
}
