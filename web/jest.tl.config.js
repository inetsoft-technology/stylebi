const baseConfig = require("./jest.config.js");

module.exports = {
  preset: "jest-preset-angular",
  ...baseConfig,
  rootDir: __dirname,
  testMatch: ["**/*.tl.spec.ts"],
  setupFiles: ["jest-canvas-mock"],
  setupFilesAfterEnv: ["<rootDir>/setup-jest.ts"],
  globals: {
    "ts-jest": {
      tsconfig: "<rootDir>/tsconfig.spec.json",
    },
  },
};
