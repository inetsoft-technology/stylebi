const baseConfig = require("./jest.config.js");

module.exports = {
  preset: "jest-preset-angular",
  ...baseConfig,
  testMatch: ["**/*.tl.spec.ts"],
  setupFiles: ["jest-canvas-mock"],
  setupFilesAfterEnv: ["<rootDir>/setup-jest.ts"],
};
