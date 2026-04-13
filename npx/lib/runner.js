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

async function runFromSource(args) {
  const sourceDir = path.join(os.homedir(), '.coral', 'source');
  const repoUrl = pkg.repository && pkg.repository.url ? pkg.repository.url.replace(/^git\+/, '') : null;
  const version = pkg.version;
  const gitHead = pkg.gitHead;

  if (!fs.existsSync(sourceDir)) {
    fs.mkdirSync(sourceDir, { recursive: true });
  }

  if (repoUrl) {
    try {
      const hasGit = spawnSync('git', ['--version']).status === 0;
      if (hasGit) {
        const isGit = spawnSync('git', ['rev-parse', '--is-inside-work-tree'], { cwd: sourceDir }).status === 0;
        const isSnapshot = version.includes('SNAPSHOT');
        const target = !isSnapshot ? (gitHead || `v${version}`) : 'master';

        if (!isGit) {
          console.log(`Initializing git repository in ${sourceDir} and fetching ${target}...`);
          spawnSync('git', ['init'], { cwd: sourceDir, stdio: 'inherit' });
          spawnSync('git', ['remote', 'add', 'origin', repoUrl], { cwd: sourceDir, stdio: 'inherit' });
        }

        console.log(`Fetching ${target} in ${sourceDir}...`);
        const fetch = spawnSync('git', ['fetch', 'origin', target, '--depth', '1'], { cwd: sourceDir, stdio: 'inherit' });
        if (fetch.status === 0) {
          spawnSync('git', ['checkout', 'FETCH_HEAD', '--force'], { cwd: sourceDir, stdio: 'inherit' });
        } else if (isSnapshot) {
          const fetchMain = spawnSync('git', ['fetch', 'origin', 'main', '--depth', '1'], { cwd: sourceDir, stdio: 'inherit' });
          if (fetchMain.status === 0) {
            spawnSync('git', ['checkout', 'FETCH_HEAD', '--force'], { cwd: sourceDir, stdio: 'inherit' });
          }
        }
      }
    } catch (e) {
      console.warn('Git operation failed, continuing with existing source in ' + sourceDir + ':', e.message);
    }
  }

  const gradlewPath = path.join(sourceDir, IS_WINDOWS ? 'gradlew.bat' : 'gradlew');
  const gradlePropsPath = path.join(sourceDir, 'gradle.properties');

  if (fs.existsSync(gradlePropsPath)) {
    let props = fs.readFileSync(gradlePropsPath, 'utf8');
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
    console.error('Error: gradlew not found in ' + sourceDir);
    process.exit(1);
  }

  console.log('Running from source code using gradlew run...');
  const child = spawn(gradlewPath, ['run', '--args=' + args.join(' ')], {
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

async function runServer(serverArgs, configProfile, forceFromSource) {
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

  if (forceFromSource) {
    await runFromSource(serverArgs);
    return;
  }

  const javaVersion = getJavaVersion();
  const jarPath = path.join(__dirname, '..', '..', 'coral-server.jar');

  if (javaVersion !== 24 && process.stdin.isTTY) {
    console.log(`\nJava 24 is recommended, but version ${javaVersion || 'unknown'} was detected.`);
    const answer = await askQuestion('Would you like to try running from source code? This will clone/update the repo in ~/.coral/source and use Gradle to run. (y/N): ');

    if (answer.toLowerCase() === 'y') {
      await runFromSource(serverArgs);
      return;
    }
  }

  // Fallback to java -jar
  if (!fs.existsSync(jarPath)) {
    console.error('Error: "java" command not found or version mismatch, and coral-server.jar not found at ' + jarPath);
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
