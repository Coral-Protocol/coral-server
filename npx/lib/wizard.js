async function loadWizard() {
  return import('../dist/tui/wizard.js');
}

async function runSetupWizard(profileName, options = {}) {
  const wizard = await loadWizard();
  return wizard.runSetupWizard(profileName, options);
}

async function handleFirstRun(profileName, options = {}) {
  const wizard = await loadWizard();
  return wizard.handleFirstRun(profileName, options);
}

module.exports = {
  runSetupWizard,
  handleFirstRun,
};
