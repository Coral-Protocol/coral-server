#!/usr/bin/env node

const fs = require('fs');
const { parseCliArgs, printUsage } = require('./lib/cli-parser');
const { runServer, runFromSource } = require('./lib/runner');
const { runSetupWizard } = require('./lib/wizard');
const { 
  ensureConfigProfileDir, 
  getConfigProfilePath, 
  generateDefaultConfig 
} = require('./lib/config-manager');

async function main() {
  const parsed = parseCliArgs(process.argv);
  const { command, subcommand, subcommandArgs, cliFlags, serverArgs } = parsed;

  // Help or no subcommand, print usage
  if (!command || cliFlags['help']) {
    printUsage();
    process.exit(0);
  }

  if (command !== 'server') {
    console.error(`Unknown command: ${command}`);
    printUsage();
    process.exit(1);
  }

  const configProfile = cliFlags['config-profile'] || null;
  const fromSource = cliFlags['from-source'];

  switch (subcommand) {
    case 'start':
      await runServer(serverArgs, configProfile, fromSource);
      break;

    case 'configure': {
      const profileName = subcommandArgs[0] || configProfile;
      if (!profileName) {
        console.error('Error: Please specify a profile name.');
        console.log('Usage: npx coralos-dev server configure <profile-name>');
        process.exit(1);
      }
      ensureConfigProfileDir(profileName);
      const profilePath = getConfigProfilePath(profileName);
      if (!fs.existsSync(profilePath)) {
        fs.writeFileSync(profilePath, generateDefaultConfig());
        console.log(`File created at ${profilePath}`);
      }
      await runSetupWizard(profileName, { hasAuthKeysArg: false, isStartCommand: false });
      break;
    }

    default:
      if (!subcommand) {
        console.error('Error: Please specify a subcommand (start, configure).');
      } else {
        console.error(`Unknown subcommand: ${subcommand}`);
      }
      printUsage();
      process.exit(1);
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
