import { ServerController } from '../features/server/server-controller.js';
export type WizardResult = {
    action: 'save' | 'cancel';
    cloudApiKey?: string | null;
};
export type WizardAppProps = {
    profileName: string;
    hasAuthKeysArg?: boolean;
    isStartCommand?: boolean;
    finish: (result: WizardResult) => void;
    serverController?: ServerController;
};
export type FirstRunAppProps = {
    profileName: string;
    finish: (choice: 'wizard' | 'editor' | 'continue' | 'exit') => void;
};
export type ProfilePickerAppProps = {
    profiles: string[];
    finish: (profileName: string | null) => void;
};
export declare function WizardApp({ profileName, finish, serverController }: WizardAppProps): import("react/jsx-runtime").JSX.Element;
export declare function ProfilePickerApp({ profiles, finish }: ProfilePickerAppProps): import("react/jsx-runtime").JSX.Element;
export declare function FirstRunApp({ profileName, finish }: FirstRunAppProps): import("react/jsx-runtime").JSX.Element;
