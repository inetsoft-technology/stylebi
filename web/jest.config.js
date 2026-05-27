/**
 * Workspace-level Jest configuration delta.
 *
 * @angular-builders/jest merges this on top of its default config for all projects.
 *
 * MSW v2 and its dependencies (@mswjs/interceptors) use package.json "exports"
 * subpath fields that Jest's default module resolver (moduleResolution: node)
 * does not support. Each subpath is mapped explicitly to its CJS build artifact.
 */
module.exports = {
  // jest-fixed-jsdom extends jest-environment-jsdom and injects Node 18's built-in
  // Fetch API globals (Response, Request, Headers, fetch, etc.) into the JSDOM
  // environment. This is required for MSW v2 which depends on the Fetch API.
  testEnvironment: "jest-fixed-jsdom",
  moduleNameMapper: {
    // msw subpath exports
    "^msw/node$": "<rootDir>/node_modules/msw/lib/node/index.js",
    // @mswjs/interceptors subpath exports
    "^@mswjs/interceptors$": "<rootDir>/node_modules/@mswjs/interceptors/lib/node/index.cjs",
    "^@mswjs/interceptors/ClientRequest$": "<rootDir>/node_modules/@mswjs/interceptors/lib/node/interceptors/ClientRequest/index.cjs",
    "^@mswjs/interceptors/XMLHttpRequest$": "<rootDir>/node_modules/@mswjs/interceptors/lib/node/interceptors/XMLHttpRequest/index.cjs",
    "^@mswjs/interceptors/fetch$": "<rootDir>/node_modules/@mswjs/interceptors/lib/node/interceptors/fetch/index.cjs",
  },
  // MSW v2 depends on `until-async` which is ESM-only.
  // Preserve jest-preset-angular's default rule (transform *.mjs files) and also
  // force-transform `until-async` so its ESM syntax is converted to CJS.
  transformIgnorePatterns: [
    "node_modules/(?!(until-async)|.*\\.mjs$)",
  ],
};
