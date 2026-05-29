import { jsx as _jsx } from "react/jsx-runtime";
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { render } from 'ink';
import { WizardApp, FirstRunApp } from './app.js';
import { configManager, constants } from '../platform/legacy.js';
function runInkApp(elementFactory) {
    if (!process.stdin.isTTY || !process.stdout.isTTY) {
        return Promise.reject(new Error('The setup wizard requires an interactive terminal.'));
    }
    return new Promise((resolve, reject) => {
        let settled = false;
        const finish = (value) => {
            settled = true;
            resolve(value);
        };
        const instance = render(elementFactory(finish), {
            exitOnCtrlC: true
        });
        instance.waitUntilExit().then(() => {
            if (!settled)
                resolve(null);
        }).catch(reject);
    });
}
export async function runSetupWizard(profileName, options = {}) {
    const { hasAuthKeysArg = false, isStartCommand = false } = options;
    const profilePath = configManager.getConfigProfilePath(profileName);
    const version = constants.pkg.version;
    console.log(`\nRun this wizard any time with:`);
    console.log(`  npx ${constants.pkg.name}@${version} server configure ${profileName}\n`);
    const result = await runInkApp(finish => (_jsx(WizardApp, { profileName: profileName, hasAuthKeysArg: hasAuthKeysArg, isStartCommand: isStartCommand, finish: finish })));
    if (!result || result.action !== 'save') {
        console.log('Configuration unchanged.');
        return false;
    }
    const config = configManager.buildConfigFromWizardResults([], result.authKey, result.cloudApiKey);
    fs.writeFileSync(profilePath, config);
    console.log(`Configuration saved to ${profilePath}`);
    return true;
}
export async function handleFirstRun(profileName, options = {}) {
    const profilePath = configManager.getConfigProfilePath(profileName);
    configManager.ensureConfigProfileDir(profileName);
    fs.writeFileSync(profilePath, configManager.generateDefaultConfig());
    console.log(`File created at ${profilePath}\n`);
    const choice = await runInkApp(finish => (_jsx(FirstRunApp, { profileName: profileName, finish: finish })));
    switch (choice) {
        case 'wizard':
            await runSetupWizard(profileName, options);
            return true;
        case 'editor': {
            const editor = process.env.EDITOR || process.env.VISUAL || (constants.IS_WINDOWS ? 'notepad' : 'vi');
            const result = spawnSync(editor, [profilePath], { stdio: 'inherit' });
            if (result.status !== 0) {
                console.error('Editor exited with non-zero status.');
            }
            return true;
        }
        case 'continue':
            return true;
        case 'exit':
        default:
            process.exit(0);
            return false;
    }
}
//# sourceMappingURL=wizard.js.map