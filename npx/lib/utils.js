const { spawnSync } = require('child_process');
const readline = require('readline');

function getJavaVersion() {
  try {
    const result = spawnSync('java', ['-version'], { encoding: 'utf8' });
    const output = result.stderr || result.stdout;
    if (!output) return null;
    const match = output.match(/(?:version|openjdk version) "(\d+)/i);
    return match ? parseInt(match[1]) : null;
  } catch (e) {
    return null;
  }
}

function askQuestion(query) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise(resolve => rl.question(query, ans => {
    rl.close();
    resolve(ans.trim());
  }));
}

module.exports = {
  getJavaVersion,
  askQuestion,
};
