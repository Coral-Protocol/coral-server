const { spawn, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { pkg, IS_WINDOWS } = require('./constants');
const { getJavaVersion, askQuestion } = require('./utils');
const { 
  getConfigProfilePath, 
  configProfileExists, 
  ensureConfigProfileDir, 
  generateDefaultConfig 
} = require('./config-manager');
const { handleFirstRun } = require('./wizard');

function showDiscordErrorAndExit() {
  console.error('\nThe script wasn\'t able to figure out how to run the requested version of the coral server on your machine.');
  console.error('Please come to our Discord for help: https://discord.gg/nsvnc3NqXT');
  process.exit(1);
}

async function runFromSource(args, targetBranch = null) {
  const sourceDir = path.join(os.homedir(), '.coral', 'source');
  const repoUrl = pkg.repository && pkg.repository.url ? pkg.repository.url.replace(/^git\+/, '') : null;
  let target = 'master';

  if (!fs.existsSync(sourceDir)) {
    fs.mkdirSync(sourceDir, { recursive: true });
  }

  if (repoUrl) {
    try {
      const hasGit = spawnSync('git', ['--version']).status === 0;
      if (hasGit) {
        const isGit = spawnSync('git', ['rev-parse', '--is-inside-work-tree'], { cwd: sourceDir }).status === 0;
        const isDefault = !targetBranch || targetBranch === true || targetBranch === 'true';
        // Use provided targetBranch if it's a string, otherwise default to master
        target = (typeof targetBranch === 'string' && targetBranch !== 'true' && targetBranch !== 'false') ? targetBranch : 'master';

        if (!isGit) {
          console.log(`Initializing git repository in ${sourceDir} and fetching ${target}...`);
          spawnSync('git', ['init'], { cwd: sourceDir, stdio: 'inherit' });
          spawnSync('git', ['remote', 'add', 'origin', repoUrl], { cwd: sourceDir, stdio: 'inherit' });
        }

        console.log(`Fetching ${target} in ${sourceDir}...`);
        const fetch = spawnSync('git', ['fetch', 'origin', target, '--depth', '1'], { cwd: sourceDir, stdio: 'inherit' });
        if (fetch.status === 0) {
          spawnSync('git', ['checkout', 'FETCH_HEAD', '--force'], { cwd: sourceDir, stdio: 'inherit' });
        } else if (isDefault) {
          const fetchMain = spawnSync('git', ['fetch', 'origin', 'main', '--depth', '1'], { cwd: sourceDir, stdio: 'inherit' });
          if (fetchMain.status === 0) {
            spawnSync('git', ['checkout', 'FETCH_HEAD', '--force'], { cwd: sourceDir, stdio: 'inherit' });
            target = 'main';
          }
        }
      }
    } catch (e) {
      console.warn('Git operation failed: ' + e.message);
    }
  }

  const gradlewPath = path.join(sourceDir, IS_WINDOWS ? 'gradlew.bat' : 'gradlew');
  const gradlePropsPath = path.join(sourceDir, 'gradle.properties');

  let sourceVersion = 'unknown';
  if (fs.existsSync(gradlePropsPath)) {
    let props = fs.readFileSync(gradlePropsPath, 'utf8');
    
    const versionMatch = props.match(/^version=(.*)$/m);
    if (versionMatch) {
      sourceVersion = versionMatch[1].trim();
    }

    let modified = false;
    if (!props.includes('org.gradle.java.installations.auto-detect=true')) {
      props += '\norg.gradle.java.installations.auto-detect=true';
      modified = true;
    }
    if (!props.includes('org.gradle.java.installations.auto-download=true')) {
      props += '\norg.gradle.java.installations.auto-download=true';
      modified = true;
    }
    if (modified) {
      fs.writeFileSync(gradlePropsPath, props);
      console.log('Added portable Gradle properties to ' + gradlePropsPath);
    }
  }

  if (!fs.existsSync(gradlewPath)) {
    showDiscordErrorAndExit();
  }

  // Get git commit info
  let commitMessage = 'unknown';
  let humanTime = 'unknown';
  try {
    const logMsg = spawnSync('git', ['log', '-1', '--format=%s'], { cwd: sourceDir, encoding: 'utf8' });
    if (logMsg.status === 0) commitMessage = logMsg.stdout.trim();
    
    const logTime = spawnSync('git', ['log', '-1', '--format=%ar'], { cwd: sourceDir, encoding: 'utf8' });
    if (logTime.status === 0) humanTime = logTime.stdout.trim();
  } catch (e) {
    // ignore
  }

  console.log('--------------------------------------------------------------------------------');
  console.log(`Running from source: branch ${target}`);
  console.log(`Version: ${sourceVersion} (note: source may contain newer changes)`);
  console.log(`Latest commit: "${commitMessage}" (${humanTime})`);
  console.log('--------------------------------------------------------------------------------');

  const argsString = args.map(arg => {
    if (arg.includes(' ') || arg.includes('"')) {
      return `"${arg.replace(/"/g, '\\"')}"`;
    }
    return arg;
  }).join(' ');

  const gradlewArgs = ['run'];
  if (argsString) {
    let arg = `--args=${argsString}`;
    // If it contains spaces, wrap it in quotes for the shell on Windows
    if (IS_WINDOWS && arg.includes(' ')) {
      arg = `"${arg}"`;
    }
    gradlewArgs.push(arg);
  }

  console.log('Running from source code using gradlew run...');
  const child = spawn(gradlewPath, gradlewArgs, {
    cwd: sourceDir,
    stdio: 'inherit',
    shell: IS_WINDOWS
  });

  child.on('exit', (code) => {
    process.exit(code !== null ? code : 1);
  });

  const killChild = () => {
    if (child.pid) child.kill('SIGINT');
  };

  process.on('SIGINT', killChild);
  process.on('SIGTERM', killChild);
}

async function runServer(serverArgs, configProfile, fromSourceValue) {
  if (serverArgs.length > 0) {
    console.log(`Passing verbatim to server: ${serverArgs.join(' ')}`);
  }

  // Check if --auth.keys is in serverArgs
  const hasAuthKeysArg = serverArgs.some(arg => arg.startsWith('--auth.keys=') || arg.includes('.auth.keys='));

  // If config profile specified, add CONFIG_FILE_PATH
  if (configProfile) {
    const profilePath = getConfigProfilePath(configProfile);

    if (!configProfileExists(configProfile)) {
      if (process.stdin.isTTY) {
        await handleFirstRun(configProfile, { hasAuthKeysArg, isStartCommand: true });
      } else {
        ensureConfigProfileDir(configProfile);
        fs.writeFileSync(profilePath, generateDefaultConfig());
        console.log(`File created at ${profilePath}`);
      }
    }

    // Set the config file path environment variable for the server
    process.env.CONFIG_FILE_PATH = profilePath;
  }

  if (fromSourceValue && fromSourceValue !== 'false') {
    await runFromSource(serverArgs, fromSourceValue);
    return;
  }

  const javaVersion = getJavaVersion();
  const jarPathInRoot = path.join(__dirname, '..', '..', 'coral-server.jar');
  const jarPathInBin = path.join(__dirname, '..', '..', 'bin', 'coral-server.jar');

  let jarPath = fs.existsSync(jarPathInBin) ? jarPathInBin : jarPathInRoot;

  if ((javaVersion === null || javaVersion < 24) && process.stdin.isTTY) {
    console.log(`\nJava 24 or newer is required to run the pre-built server, but version ${javaVersion || 'unknown'} was detected.`);
    const answer = await askQuestion('Would you like to try running from source code instead? (y/N): ');

    if (answer.toLowerCase() === 'y') {
      await runFromSource(serverArgs);
      return;
    } else {
      showDiscordErrorAndExit();
    }
  }

  // Fallback to java -jar
  if (!fs.existsSync(jarPath)) {
    if (javaVersion === null) {
      console.error('Error: "java" command not found. Please install Java 24 or later.');
    } else {
      console.error(`Error: coral-server.jar not found (checked ${jarPathInBin} and ${jarPathInRoot})`);
    }
    process.exit(1);
  }

  const child = spawn('java', ['-jar', jarPath, ...serverArgs], {
    stdio: 'inherit'
  });

  child.on('error', (err) => {
    if (err.code === 'ENOENT') {
      console.error('Error: "java" command not found. Please install Java (JRE or JDK, version 24 or later recommended).');
    } else {
      console.error('Error starting java:', err.message);
    }
    process.exit(1);
  });

  child.on('exit', (code) => {
    process.exit(code !== null ? code : 1);
  });

  const killChild = () => {
    if (child.pid) child.kill('SIGINT');
  };

  process.on('SIGINT', killChild);
  process.on('SIGTERM', killChild);
}

module.exports = {
  runFromSource,
  runServer,
};
