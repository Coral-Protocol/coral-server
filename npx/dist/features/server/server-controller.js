import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const distDir = path.dirname(fileURLToPath(import.meta.url));
const npxRoot = path.resolve(distDir, '../../..');
export class ServerController {
    child = null;
    snapshot = {
        status: 'stopped',
        exitCode: null,
        url: 'http://localhost:5555',
        logs: []
    };
    listeners = new Set();
    subscribe(listener) {
        this.listeners.add(listener);
        listener(this.snapshot);
        return () => this.listeners.delete(listener);
    }
    getSnapshot() {
        return this.snapshot;
    }
    start(profilePath) {
        if (this.child)
            return;
        const jarPath = this.findJarPath();
        if (!jarPath) {
            this.setSnapshot({
                ...this.snapshot,
                status: 'error',
                error: 'coral-server.jar was not found in bin/ or project root.',
                logs: this.pushLog('Unable to start: coral-server.jar was not found.')
            });
            return;
        }
        this.setSnapshot({
            ...this.snapshot,
            status: 'starting',
            exitCode: null,
            error: undefined,
            logs: this.pushLog(`Starting server with CONFIG_FILE_PATH=${profilePath}`)
        });
        const child = spawn('java', ['-jar', jarPath], {
            env: {
                ...process.env,
                CONFIG_FILE_PATH: profilePath
            },
            stdio: 'pipe'
        });
        this.child = child;
        child.stdout.on('data', data => this.addLog(String(data)));
        child.stderr.on('data', data => this.addLog(String(data)));
        child.on('spawn', () => {
            this.setSnapshot({
                ...this.snapshot,
                status: 'running',
                logs: this.pushLog('Server process started.')
            });
        });
        child.on('error', error => {
            this.child = null;
            this.setSnapshot({
                ...this.snapshot,
                status: 'error',
                error: error.message,
                logs: this.pushLog(`Server error: ${error.message}`)
            });
        });
        child.on('exit', code => {
            this.child = null;
            this.setSnapshot({
                ...this.snapshot,
                status: 'exited',
                exitCode: code,
                logs: this.pushLog(`Server exited with code ${code ?? 'unknown'}.`)
            });
        });
    }
    stop() {
        if (!this.child)
            return;
        this.addLog('Stopping server process.');
        this.child.kill('SIGINT');
    }
    findJarPath() {
        const candidates = [
            path.join(npxRoot, 'bin', 'coral-server.jar'),
            path.join(npxRoot, 'coral-server.jar')
        ];
        return candidates.find(candidate => fs.existsSync(candidate)) ?? null;
    }
    addLog(value) {
        const lines = value.split(/\r?\n/).filter(Boolean);
        this.setSnapshot({
            ...this.snapshot,
            logs: [...this.snapshot.logs, ...lines].slice(-80)
        });
    }
    pushLog(value) {
        return [...this.snapshot.logs, value].slice(-80);
    }
    setSnapshot(snapshot) {
        this.snapshot = snapshot;
        this.listeners.forEach(listener => listener(snapshot));
    }
}
//# sourceMappingURL=server-controller.js.map