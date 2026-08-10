import parser from '@typescript-eslint/parser';

export default [
  {
    ignores: ['dist', 'node_modules', '**/*.d.ts'],
    files: ['**/*.{ts,tsx}'],
    languageOptions: { parser, parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } } },
    rules: {
      'no-unused-vars': 'off',
      'no-undef': 'off'
    }
  }
];
