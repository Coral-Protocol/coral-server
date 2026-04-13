const { pkg } = require('./constants');

function parseCliArgs(argv) {
  const rawArgs = argv.slice(2);

  // Split on -- separator: args before are CLI args, args after are server args
  const doubleDashIndex = rawArgs.indexOf('--');
  let cliArgs, serverArgs;

  if (doubleDashIndex >= 0) {
    cliArgs = rawArgs.slice(0, doubleDashIndex);
    serverArgs = rawArgs.slice(doubleDashIndex + 1);
  } else {
    cliArgs = rawArgs;
    serverArgs = [];
  }

  // Extract subcommand (e.g., "server start", "server configure")
  let command = null;
  let subcommand = null;
  let subcommandArgs = [];
  let cliFlags = {};

  const positional = [];
  for (const arg of cliArgs) {
    if (arg.startsWith('--')) {
      const eqIndex = arg.indexOf('=');
      if (eqIndex >= 0) {
        const key = arg.substring(2, eqIndex);
        const value = arg.substring(eqIndex + 1);
        cliFlags[key] = value;
      } else {
        cliFlags[arg.substring(2)] = true;
      }
    } else {
      positional.push(arg);
    }
  }

  command = positional[0] || null;
  subcommand = positional[1] || null;
  subcommandArgs = positional.slice(2);

  return { command, subcommand, subcommandArgs, cliFlags, serverArgs };
}

function printUsage() {
  const version = pkg.version;
  console.log(`coralos-dev v${version} - Coral Server CLI\n`);
  console.log('Usage:');
  console.log('  npx coralos-dev server start [--config-profile=<name>] [--from-source] [-- <server-args...>]');
  console.log('  npx coralos-dev server configure <profile-name>');
  console.log('');
  console.log('Commands:');
  console.log('  server start       Start the Coral server');
  console.log('  server configure   Run the setup wizard for a config profile');
  console.log('');
  console.log('Options:');
  console.log('  --config-profile=<name>   Use a named config profile from ~/.coral/config-profiles/');
  console.log('  --from-source             Build and run from source code');
  console.log('');
  console.log('Examples:');
  console.log(`  npx coralos-dev@${version} server start --config-profile=dev -- --auth.keys=dev`);
  console.log(`  npx coralos-dev@${version} server configure dev`);
}

module.exports = {
  parseCliArgs,
  printUsage,
};
