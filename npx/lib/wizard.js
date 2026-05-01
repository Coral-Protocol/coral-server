const { spawnSync } = require('child_process');
const fs = require('fs');
const { pkg, LLM_PROVIDERS, IS_WINDOWS } = require('./constants');
const { askQuestion } = require('./utils');
const { 
  getConfigProfilePath, 
  ensureConfigProfileDir, 
  generateDefaultConfig, 
  buildConfigFromWizardResults 
} = require('./config-manager');

async function testLlmProvider(providerId, apiKey) {
  try {
    if (providerId === 'openai') {
      const result = spawnSync('curl', [
        '-s', '-o', '/dev/null', '-w', '%{http_code}',
        '-H', `Authorization: Bearer ${apiKey}`,
        'https://api.openai.com/v1/models',
      ], { encoding: 'utf8', timeout: 15000 });
      return result.stdout && result.stdout.trim() === '200';
    } else if (providerId === 'anthropic') {
      const result = spawnSync('curl', [
        '-s', '-o', '/dev/null', '-w', '%{http_code}',
        '-H', `x-api-key: ${apiKey}`,
        '-H', 'anthropic-version: 2023-06-01',
        'https://api.anthropic.com/v1/models',
      ], { encoding: 'utf8', timeout: 15000 });
      const code = result.stdout ? result.stdout.trim() : '';
      return code === '200';
    } else if (providerId === 'openrouter') {
      const result = spawnSync('curl', [
        '-s', '-o', '/dev/null', '-w', '%{http_code}',
        '-H', `Authorization: Bearer ${apiKey}`,
        'https://openrouter.ai/api/v1/models',
      ], { encoding: 'utf8', timeout: 15000 });
      return result.stdout && result.stdout.trim() === '200';
    }
  } catch (e) {
    return false;
  }
  return false;
}

async function runSetupWizard(profileName, options = {}) {
  const { hasAuthKeysArg = false, isStartCommand = false } = options;
  const profilePath = getConfigProfilePath(profileName);
  const version = pkg.version;

  console.log(`\nWelcome! You can run through this wizard at any time by running this command:`);
  console.log(`  npx coralos-dev@${version} server configure ${profileName}\n`);

  let authKey = null;
  if (!hasAuthKeysArg) {
    const prompt = isStartCommand ? 
      'Enter an API key for your Coral Server (required for auth, press Enter to skip): ' : 
      'Enter an API key for your Coral Server (optional, press Enter to skip): ';
    authKey = await askQuestion(prompt);
    
    if (!authKey && isStartCommand) {
       console.log('\nContinuing without a key. You might need to provide one later via --auth.keys\n');
    }
  }

  // Step 1: Coral Cloud API key
  console.log('First, lets setup the LLM proxy.');
  console.log('Please visit https://coralcloud.ai/account and create an API key.');
  console.log('This will be used for running third party agents, and in the near future for LLM inference.\n');

  const coralApiKey = await askQuestion('Enter your Coral Cloud API key (or press Enter to skip): ');

  if (coralApiKey) {
    console.log('API key will be saved to your configuration file.\n');
  }

  // Step 2: LLM providers
  console.log('Please choose the LLM providers that you\'d like to use:\n');

  const configuredProviders = [];

  for (const provider of LLM_PROVIDERS) {
    const apiKey = await askQuestion(`Enter API key for ${provider.name} (or press Enter to skip and set later): `);

    if (!apiKey) {
      console.log(`  Skipped ${provider.name}.\n`);
      continue;
    }

    console.log(`  Testing ${provider.name}...`);
    const works = await testLlmProvider(provider.id, apiKey);

    if (works) {
      console.log(`  ✓ ${provider.name} is working!\n`);
      configuredProviders.push({ id: provider.id, apiKey, working: true });
    } else {
      console.log(`  ✗ ${provider.name} test failed.\n`);
      configuredProviders.push({ id: provider.id, apiKey, working: false });
    }
  }

  // Check if any working providers
  const workingCount = configuredProviders.filter(p => p.working).length;
  const failedProviders = configuredProviders.filter(p => !p.working);

  if (failedProviders.length > 0) {
    console.log('\nThe following providers failed validation:');
    for (const p of failedProviders) {
      const provider = LLM_PROVIDERS.find(lp => lp.id === p.id);
      console.log(`  - ${provider.name}`);
    }

    if (workingCount === 0) {
      console.log('\n⚠  No working LLM providers configured. It is highly recommended to have at least 1 working provider.');
    }

    const fixAnswer = await askQuestion('\nWould you like to re-enter keys for failed providers? (y/N): ');

    if (fixAnswer.toLowerCase() === 'y') {
      for (const p of failedProviders) {
        const provider = LLM_PROVIDERS.find(lp => lp.id === p.id);
        const newKey = await askQuestion(`Enter new API key for ${provider.name} (or press Enter to keep current): `);

        if (newKey) {
          console.log(`  Testing ${provider.name}...`);
          const works = await testLlmProvider(p.id, newKey);
          if (works) {
            console.log(`  ✓ ${provider.name} is now working!\n`);
            p.apiKey = newKey;
            p.working = true;
          } else {
            console.log(`  ✗ ${provider.name} still failing. The key will be commented out in config.\n`);
            p.apiKey = newKey;
          }
        }
      }
    } else {
      console.log('Non-working providers will be commented out in the config file.');
    }
  }

  // Write config
  const config = buildConfigFromWizardResults(configuredProviders, authKey, coralApiKey);
  fs.writeFileSync(profilePath, config);
  console.log(`\nConfiguration saved to ${profilePath}`);
}

async function handleFirstRun(profileName, options = {}) {
  const profilePath = getConfigProfilePath(profileName);

  ensureConfigProfileDir(profileName);
  fs.writeFileSync(profilePath, generateDefaultConfig());
  console.log(`File created at ${profilePath}\n`);

  console.log('What next?');
  console.log('1) Go through setup wizard? (recommended)');
  console.log('2) Edit config with $EDITOR');
  console.log('3) Continue to run the server with empty config');
  console.log('4) Exit');

  const choice = await askQuestion('\nChoose an option [1-4]: ');

  switch (choice) {
    case '1':
      await runSetupWizard(profileName, options);
      return true;
    case '2': {
      const editor = process.env.EDITOR || process.env.VISUAL || (IS_WINDOWS ? 'notepad' : 'vi');
      const result = spawnSync(editor, [profilePath], { stdio: 'inherit' });
      if (result.status !== 0) {
        console.error('Editor exited with non-zero status.');
      }
      return true;
    }
    case '3':
      return true;
    case '4':
      process.exit(0);
      break;
    default:
      console.log('Invalid choice, continuing with empty config.');
      return true;
  }
}

module.exports = {
  testLlmProvider,
  runSetupWizard,
  handleFirstRun,
};
