import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { buildEmbedPackage } from "./build-embed-package.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");

const packageDir = buildEmbedPackage();
const token = process.env.NODE_AUTH_TOKEN || process.env.GITHUB_TOKEN;
const npmrcPath = path.join(packageDir, ".npmrc");
const args = ["publish", ...process.argv.slice(2)];

try {
   if(token) {
      fs.writeFileSync(
         npmrcPath,
         "@inetsoft-technology:registry=https://npm.pkg.github.com\n" +
         `//npm.pkg.github.com/:_authToken=${token}\n`
      );
   }

   const publishResult = spawnSync("npm", args, {
      cwd: packageDir,
      stdio: "inherit",
      shell: process.platform === "win32"
   });

   if(publishResult.status !== 0) {
      process.exit(publishResult.status ?? 1);
   }
}
finally {
   if(fs.existsSync(npmrcPath)) {
      fs.rmSync(npmrcPath, { force: true });
   }
}
