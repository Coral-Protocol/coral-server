import { constants } from './legacy.js';
export function cliBase() {
    return `npx ${constants.pkg.name}@${constants.pkg.version}`;
}
export function configureProfileCommand(profileName) {
    return `${cliBase()} server configure ${profileName}`;
}
export function configureProfileNonInteractiveCommand(profileName) {
    return `${configureProfileCommand(profileName)} --cloud.api-key=<key> --auth.key=<key> --yes`;
}
export function startServerCommand(profileName) {
    return `${cliBase()} server start --config-profile=${profileName}`;
}
export function startServerWithAuthCommand(profileName, authKey) {
    return `${startServerCommand(profileName)} -- --auth.keys=${authKey}`;
}
//# sourceMappingURL=commands.js.map