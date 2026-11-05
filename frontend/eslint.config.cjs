const js = require('@eslint/js');
const tsParser = require('@typescript-eslint/parser');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const angular = require('angular-eslint');
const prettier = require('eslint-config-prettier');

module.exports = [
  { ignores: ['dist/**', '.angular/**', 'node_modules/**', 'coverage/**'] },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tsParser,
      ecmaVersion: 2022,
      sourceType: 'module',
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    processor: angular.processInlineTemplates,
    rules: {
      ...js.configs.recommended.rules,
      ...tsPlugin.configs['eslint-recommended'].overrides[0].rules,
      ...tsPlugin.configs.recommended.rules,
      ...prettier.rules,
    },
  },
  ...angular.configs.tsRecommended.map((config) => ({ ...config, files: ['**/*.ts'] })),
  ...angular.configs.templateRecommended.map((config) => ({ ...config, files: ['**/*.html'] })),
  {
    files: ['src/app/app.component.ts'],
    // Preserve the existing NgZone-based updates from the Web PKI callbacks.
    rules: { '@angular-eslint/prefer-on-push-component-change-detection': 'off' },
  },
  { files: ['**/*.ts', '**/*.html'], rules: prettier.rules },
];
