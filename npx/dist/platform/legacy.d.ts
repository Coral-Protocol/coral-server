export type PackageInfo = {
    name: string;
    version: string;
};
export type WizardConfigResult = {
    action: 'save' | 'cancel';
    authKey?: string | null;
    cloudApiKey?: string | null;
};
export type ConfigManager = {
    getConfigProfilePath(profileName: string): string;
    ensureConfigProfileDir(profileName: string): void;
    generateDefaultConfig(): string;
    buildConfigFromWizardResults(providers: Array<unknown>, authKey?: string | null, coralApiKey?: string | null): string;
};
export declare const configManager: ConfigManager;
export declare const constants: {
    pkg: PackageInfo;
    IS_WINDOWS: boolean;
};
