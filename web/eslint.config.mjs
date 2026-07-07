import { defineConfig } from "eslint/config";
import eslint from "@eslint/js";
import tseslint from "typescript-eslint";
import angular from "angular-eslint";

export default defineConfig(
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      angular.configs.tsRecommended
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "@angular-eslint/component-class-suffix": "off",
      "@angular-eslint/component-selector": [
        "error",
        {
          "type": "element",
          "prefix": "",
          "style": "kebab-case"
        }
      ],
      "@angular-eslint/directive-class-suffix": "off",
      "@angular-eslint/directive-selector": [
        "error",
        {
          "type": "attribute",
          "prefix": "",
          "style": "camelCase"
        }
      ],
      "@angular-eslint/no-conflicting-lifecycle": "off",
      "@angular-eslint/no-output-on-prefix": "off",
      "@angular-eslint/no-output-native": "off",
      "@angular-eslint/no-empty-lifecycle-method": "off",
      "@angular-eslint/prefer-inject": "off",
      "@typescript-eslint/consistent-type-definitions": "error",
      "@typescript-eslint/consistent-type-assertions": "off",
      "@typescript-eslint/dot-notation": "off",
      "@typescript-eslint/explicit-member-accessibility": [
        "off",
        {
          "accessibility": "explicit"
        }
      ],
      "@typescript-eslint/member-ordering": "off",
      "@typescript-eslint/naming-convention": "off",
      "@typescript-eslint/no-duplicate-enum-values": "off",
      "@typescript-eslint/no-empty-function": "off",
      "@typescript-eslint/no-empty-interface": "off",
      "@typescript-eslint/no-empty-object-type": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-namespace": "off",
      "@typescript-eslint/no-inferrable-types": "off",
      "@typescript-eslint/no-require-imports": "off",
      "@typescript-eslint/no-unsafe-function-type": "off",
      "@typescript-eslint/no-unused-expressions": "off",
      "@typescript-eslint/no-unused-vars": "off",
      "@typescript-eslint/no-var-requires": "off",
      "@typescript-eslint/no-wrapper-object-types": "off",
      "@typescript-eslint/prefer-for-of": "off",
      "quotes": ["error", "double", { "avoidEscape": true, "allowTemplateLiterals": true }],
      "arrow-body-style": "off",
      "brace-style": ["off", "off"],
      "consistent-return": "off",
      "eol-last": "off",
      "eqeqeq": ["off", "always"],
      "id-blacklist": "off",
      "id-match": "off",
      "import/no-deprecated": "off",
      "jsdoc/newline-after-description": "off",
      "jsdoc/no-types": "off",
      "max-len": "off",
      "no-bitwise": "off",
      "no-case-declarations": "off",
      "no-constant-binary-expression": "off",
      "no-constant-condition": "off",
      "no-dupe-else-if": "off",
      "no-empty": "off",
      "no-extra-bind": "off",
      "no-extra-boolean-cast": "off",
      "no-inner-declarations": "off",
      "no-prototype-builtins": "off",
      "no-unassigned-vars": "off",
      "no-underscore-dangle": "off",
      "no-useless-assignment": "off",
      "no-useless-escape": "off",
      "no-var": "off",
      "object-shorthand": "off",
      "prefer-const": "off",
      "prefer-arrow/prefer-arrow-functions": "off",
      "quote-props": "off"
    }
  },
  {
    files: ["projects/em/**/*.ts"],
    rules: {
      "@angular-eslint/directive-selector": [
        "error",
        {
          "type": "attribute",
          "prefix": "em",
          "style": "camelCase"
        }
      ],
      "@angular-eslint/component-selector": [
        "error",
        {
          "type": "element",
          "prefix": "em",
          "style": "kebab-case"
        }
      ]
    }
  },
  {
    files: ["**/*.html"],
    extends: [
      angular.configs.templateRecommended
    ],
    rules: {
      "@angular-eslint/template/eqeqeq": "off",
      "@angular-eslint/template/prefer-control-flow": "off"
    }
  }
);
