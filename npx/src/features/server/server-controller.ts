import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const distDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(distDir, '../../../..');
export const DEFAULT_DEV_AUTH_KEY = 'dev';

export type ServerStatus = 'stopped' | 'starting' | 'running' | 'exited' | 'error';

export type ServerSnapshot = {
  status: ServerStatus;
  exitCode: number | null;
  url: string;
  logs: string[];
  error?: string;
};

type Listener = (snapshot: ServerSnapshot) => void;

export class ServerController {
  private child: ChildProcessWithoutNullStreams | null = null;
  private snapshot: ServerSnapshot = {
    status: 'stopped',
    exitCode: null,
    url: 'http://localhost:5555/ui/console',
    logs: []
  };
  private readonly listeners = new Set<Listener>();

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    listener(this.snapshot);
    return () => this.listeners.delete(listener);
  }

  getSnapshot(): ServerSnapshot {
    return this.snapshot;
  }

  start(profilePath: string): void {
    if (this.child) return;

    const jarPath = this.findJarPath();
    if (!jarPath) {
      this.setSnapshot({
        ...this.snapshot,
        status: 'error',
        error: 'coral-server.jar was not found in bin/, project root, or build/libs.',
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

    const child = spawn('java', ['-jar', jarPath, `--auth.keys=${DEFAULT_DEV_AUTH_KEY}`], {
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

  stop(): void {
    if (!this.child) return;
    this.addLog('Stopping server process.');
    this.child.kill('SIGINT');
  }

  private findJarPath(): string | null {
    const candidates = [
      path.join(projectRoot, 'bin', 'coral-server.jar'),
      path.join(projectRoot, 'coral-server.jar')
    ];
    const direct = candidates.find(candidate => fs.existsSync(candidate));
    if (direct) return direct;

    const libsDir = path.join(projectRoot, 'build', 'libs');
    if (!fs.existsSync(libsDir)) return null;

    return fs.readdirSync(libsDir)
      .filter(name => name.startsWith('coral-server-') && name.endsWith('.jar') && !name.endsWith('-plain.jar'))
      .sort()
      .reverse()
      .map(name => path.join(libsDir, name))
      .find(candidate => fs.existsSync(candidate)) ?? null;
  }

  private addLog(value: string): void {
    const lines = value.split(/\r?\n/).filter(Boolean);
    this.setSnapshot({
      ...this.snapshot,
      logs: [...this.snapshot.logs, ...lines].slice(-80)
    });
  }

  private pushLog(value: string): string[] {
    return [...this.snapshot.logs, value].slice(-80);
  }

  private setSnapshot(snapshot: ServerSnapshot): void {
    this.snapshot = snapshot;
    this.listeners.forEach(listener => listener(snapshot));
  }
}
