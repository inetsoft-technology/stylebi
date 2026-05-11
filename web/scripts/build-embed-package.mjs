import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..");
const packageTemplateDir = path.join(projectRoot, "embed-package");
const outputDir = path.join(projectRoot, "target", "npm", "stylebi-embed-elements");

const packageName =
   process.env.EMBED_PACKAGE_NAME || "@inetsoft-technology/stylebi-embed-elements";
const packageVersion = process.env.EMBED_PACKAGE_VERSION || readRootVersion();

const distributableFiles = [
   {
      source: path.join(projectRoot, "target", "generated-resources", "gulp",
         "inetsoft", "web", "resources", "app", "elements.js"),
      target: "elements.js"
   },
   {
      source: path.join(projectRoot, "target", "generated-resources", "gulp",
         "inetsoft", "web", "resources", "app", "elements.css"),
      target: "elements.css"
   },
   {
      source: path.join(projectRoot, "target", "generated-resources", "gulp",
         "inetsoft", "web", "resources", "app", "viewer-element.js"),
      target: "viewer-element.js"
   },
   {
      source: path.join(projectRoot, "target", "generated-resources", "gulp",
         "inetsoft", "web", "resources", "app", "viewer-element.css"),
      target: "viewer-element.css"
   }
];

export function buildEmbedPackage() {
   ensureFilesExist(distributableFiles.map(file => file.source));

   fs.rmSync(outputDir, { recursive: true, force: true });
   fs.mkdirSync(outputDir, { recursive: true });

   for(const file of distributableFiles) {
      fs.copyFileSync(file.source, path.join(outputDir, file.target));
   }

   copyDir(resolveAssetsDir(), path.join(outputDir, "assets"));
   copyTemplateFiles();
   writePackageJson();

   return outputDir;
}

function copyTemplateFiles() {
   const templateFiles = [
      "README.md",
      "index.mjs",
      "loader.mjs",
      "register-all.mjs",
      "register-elements.mjs",
      "register-viewer.mjs"
   ];

   for(const file of templateFiles) {
      fs.copyFileSync(path.join(packageTemplateDir, file), path.join(outputDir, file));
   }
}

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

   fs.writeFileSync(
      path.join(outputDir, "package.json"),
      `${JSON.stringify(packageJson, null, 2)}\n`
   );
}

function resolveAssetsDir() {
   const preferredAssetsDir = path.join(projectRoot, "target", "generated-resources", "ng",
      "inetsoft", "web", "resources", "elements", "assets");
   const fallbackAssetsDir = path.join(projectRoot, "projects", "portal", "src", "assets");

   if(fs.existsSync(preferredAssetsDir)) {
      return preferredAssetsDir;
   }

   if(fs.existsSync(fallbackAssetsDir)) {
      return fallbackAssetsDir;
   }

   throw new Error("Unable to locate embed assets directory.");
}

function ensureFilesExist(files) {
   const missingFiles = files.filter(file => !fs.existsSync(file));

   if(missingFiles.length > 0) {
      const relativeFiles = missingFiles
         .map(file => path.relative(projectRoot, file))
         .join(", ");
      throw new Error(
         `Missing embed build artifacts: ${relativeFiles}. Run "npm run build:embed:prod" first.`
      );
   }
}

function readRootVersion() {
   const packageJsonPath = path.join(projectRoot, "package.json");
   const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));
   return packageJson.version;
}

function copyDir(sourceDir, targetDir) {
   fs.cpSync(sourceDir, targetDir, { recursive: true });
}

if(process.argv[1] === __filename) {
   const packageDir = buildEmbedPackage();
   process.stdout.write(`Embed package created at ${packageDir}\n`);
}
