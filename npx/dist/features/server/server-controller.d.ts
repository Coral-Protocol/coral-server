export type ServerStatus = 'stopped' | 'starting' | 'running' | 'exited' | 'error';
export type ServerSnapshot = {
    status: ServerStatus;
    exitCode: number | null;
    url: string;
    logs: string[];
    error?: string;
};
type Listener = (snapshot: ServerSnapshot) => void;
export declare class ServerController {
    private child;
    private snapshot;
    private readonly listeners;
    subscribe(listener: Listener): () => void;
    getSnapshot(): ServerSnapshot;
    start(profilePath: string): void;
    stop(): void;
    private findJarPath;
    private addLog;
    private pushLog;
    private setSnapshot;
}
export {};
