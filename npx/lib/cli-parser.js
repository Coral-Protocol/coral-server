const { pkg } = require('./constants');

function parseCliArgs(argv) {
  const rawArgs = argv.slice(2);

  // Split on -- separator: args before are CLI args, args after are server args
  const doubleDashIndex = rawArgs.indexOf('--');
  let cliArgs, extraServerArgs;

  if (doubleDashIndex >= 0) {
    cliArgs = rawArgs.slice(0, doubleDashIndex);
    extraServerArgs = rawArgs.slice(doubleDashIndex + 1);
  } else {
    cliArgs = rawArgs;
    extraServerArgs = [];
  }

  // Extract subcommand (e.g., "server start", "server configure")
  let command = null;
  let subcommand = null;
  let subcommandArgs = [];
  let cliFlags = {};
  
  const knownCliFlags = ['config-profile', 'from-source', 'help'];
  const positional = [];
  const unrecognizedFlags = [];
  const knownFlagsUsed = [];

  for (const arg of cliArgs) {
    if (arg.startsWith('--')) {
      const eqIndex = arg.indexOf('=');
      const key = eqIndex >= 0 ? arg.substring(2, eqIndex) : arg.substring(2);
      
      if (knownCliFlags.includes(key)) {
        knownFlagsUsed.push(arg);
        if (eqIndex >= 0) {
          cliFlags[key] = arg.substring(eqIndex + 1);
        } else {
          cliFlags[key] = true;
        }
      } else {
        unrecognizedFlags.push(arg);
      }
    } else if (arg.startsWith('-') && arg !== '-') {
      // Treat short flags like -c as unrecognized for now
      unrecognizedFlags.push(arg);
    } else {
      positional.push(arg);
    }
  }

  if (unrecognizedFlags.length > 0) {
    const npxCmd = `npx ${pkg.name}@${pkg.version}`;
    const allServerArgs = [...unrecognizedFlags, ...extraServerArgs];
    
    // Help the user by suggesting the correct command with -- separator
    const quoteIfNecessary = (s) => s.includes(' ') ? `"${s}"` : s;
    const suggestion = `${npxCmd} ${positional.map(quoteIfNecessary).join(' ')} ${knownFlagsUsed.map(quoteIfNecessary).join(' ')} -- ${allServerArgs.map(quoteIfNecessary).join(' ')}`.replace(/\s+/g, ' ').trim();
    
    console.error(`Error: Unrecognized CLI parameter(s): ${unrecognizedFlags.join(', ')}`);
    console.error(`did you mean \`${suggestion}\``);
    
    if (unrecognizedFlags.some(f => f.includes('.'))) {
      console.error('\nNote: Server parameters (with dots) must follow the `--` separator.');
      console.error('If you already used `--`, your shell or `npx` may have consumed it.');
      
      const tryCmd = `npx ${pkg.name}@${pkg.version} -- ${positional.map(quoteIfNecessary).join(' ')} ${knownFlagsUsed.map(quoteIfNecessary).join(' ')} -- ${allServerArgs.map(quoteIfNecessary).join(' ')}`.replace(/\s+/g, ' ').trim();
      console.error(`Try: ${tryCmd}`);
    }
    
    process.exit(1);
  }

  command = positional[0] || null;
  subcommand = positional[1] || null;
  subcommandArgs = positional.slice(2);
  const serverArgs = [...extraServerArgs];

  return { command, subcommand, subcommandArgs, cliFlags, serverArgs };
}

function printUsage() {
  const version = pkg.version;
  console.log(`coralos-dev v${version} - Coral Server CLI\n`);
  console.log('Usage:');
  console.log('  npx coralos-dev server start [--config-profile=<name>] [--from-source[=<branch>]] [-- <server-args...>]');
  console.log('  npx coralos-dev server configure <profile-name>');
  console.log('');
  console.log('Commands:');
  console.log('  server start       Start the Coral server');
  console.log('  server configure   Run the setup wizard for a config profile');
  console.log('');
  console.log('Options:');
  console.log('  --config-profile=<name>   Use a named config profile from ~/.coral/config-profiles/');
  console.log('  --from-source[=<branch>]  Build and run from source code (optional branch name, defaults to master)');
  console.log('  --help                    Show this help message');
  console.log('');
  console.log('  Note: All parameters after the -- separator are passed verbatim to the server.');
  console.log('');
  console.log('Examples:');
  console.log(`  npx coralos-dev@${version} server start --config-profile=dev -- --auth.keys=dev`);
  console.log(`  npx coralos-dev@${version} server configure dev`);
}

module.exports = {
  parseCliArgs,
  printUsage,
};
