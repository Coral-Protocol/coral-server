const path = require('path');
const os = require('os');
const fs = require('fs');

const pkgPath = path.join(__dirname, '..', '..', 'package.json');
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));

const CORAL_HOME = path.join(os.homedir(), '.coral');
const CONFIG_PROFILES_DIR = path.join(CORAL_HOME, 'config-profiles');

const LLM_PROVIDERS = [
  { id: 'openai',     name: 'OpenAI',     envVar: 'OPENAI_API_KEY',     prefix: 'sk-' },
  { id: 'anthropic',  name: 'Anthropic',   envVar: 'ANTHROPIC_API_KEY',  prefix: 'sk-ant-' },
  { id: 'openrouter', name: 'OpenRouter',  envVar: 'OPENROUTER_API_KEY', prefix: 'sk-or-' },
];

const IS_WINDOWS = process.platform === 'win32';

module.exports = {
  pkg,
  CORAL_HOME,
  CONFIG_PROFILES_DIR,
  LLM_PROVIDERS,
  IS_WINDOWS,
};
