import React, { useEffect, useMemo, useState } from 'react';
import { Box, Text, useApp, useInput, useStdout } from 'ink';
import TextInput from 'ink-text-input';
import { configManager } from '../platform/legacy.js';
import {
  configureProfileNonInteractiveCommand,
  configureProfileCommand,
  startServerCommand
} from '../platform/commands.js';
import { getLayoutMetrics, ellipsizeMiddle, type LayoutMetrics } from './layout.js';
import { theme } from './theme.js';
import {
  AppHeader,
  CliEquivalent,
  FieldRow,
  Footer,
  Menu,
  Panel
} from './components.js';
import { maskValue, type SetupState } from '../features/setup/model.js';
import { ServerController, type ServerSnapshot } from '../features/server/server-controller.js';
import { templateDefinitions } from '../features/templates/templates.js';

type MainSection = 'setup' | 'server' | 'develop' | 'share';
type View =
  | 'home'
  | 'edit-auth'
  | 'edit-cloud'
  | 'save'
  | 'exit'
  | 'templates'
  | 'skills'
  | 'apps'
  | 'sharing';

export type WizardResult = {
  action: 'save' | 'cancel';
  authKey?: string | null;
  cloudApiKey?: string | null;
};

export type WizardAppProps = {
  profileName: string;
  hasAuthKeysArg: boolean;
  isStartCommand: boolean;
  finish: (result: WizardResult) => void;
  serverController?: ServerController;
};

export type FirstRunAppProps = {
  profileName: string;
  finish: (choice: 'wizard' | 'editor' | 'continue' | 'exit') => void;
};

function statusColor(status: ServerSnapshot['status']) {
  if (status === 'running') return theme.success;
  if (status === 'starting') return theme.warning;
  if (status === 'error') return theme.danger;
  return theme.muted;
}

function useServerSnapshot(controller: ServerController): ServerSnapshot {
  const [snapshot, setSnapshot] = useState(controller.getSnapshot());

  useEffect(() => controller.subscribe(setSnapshot), [controller]);

  return snapshot;
}

function Sidebar({
  metrics,
  profileName,
  profilePath,
  setup,
  hasAuthKeysArg,
  server
}: {
  metrics: LayoutMetrics;
  profileName: string;
  profilePath: string;
  setup: SetupState;
  hasAuthKeysArg: boolean;
  server: ServerSnapshot;
}) {
  if (metrics.compact) {
    return null;
  }

  return (
    <Box marginRight={2}>
      <Panel title="Workspace" width={metrics.sidebarWidth}>
        <Text color={theme.brandSoft}>{profileName}</Text>
        <Text color={theme.muted}>{ellipsizeMiddle(profilePath, metrics.sidebarWidth - 4)}</Text>
        <Box marginTop={1} flexDirection="column">
          <Text bold>Setup</Text>
          <FieldRow
            label="Server auth"
            value={hasAuthKeysArg ? 'CLI argument' : maskValue(setup.authKey.trim())}
            muted={hasAuthKeysArg}
          />
          <FieldRow label="Coral Cloud" value={maskValue(setup.cloudApiKey.trim())} />
          <FieldRow label="LLM providers" value="Manual" />
        </Box>
        <Box marginTop={1} flexDirection="column">
          <Text bold>Server</Text>
          <FieldRow label="Status" value={server.status} />
          <Text color={statusColor(server.status)}>{server.url}</Text>
        </Box>
      </Panel>
    </Box>
  );
}

function MainNav({ onSelect }: { onSelect: (section: MainSection) => void }) {
  return (
    <Box flexDirection="column">
      <Text bold>What are you doing?</Text>
      <Text color={theme.muted}>Choose a workspace area. Every action shows its CLI equivalent when one exists.</Text>
      <Box marginTop={1}>
        <Menu
          items={[
            { label: 'Setup dev profile', value: 'setup' },
            { label: 'Run local server', value: 'server' },
            { label: 'Develop / build agents and apps', value: 'develop' },
            { label: 'Share agents', value: 'share' }
          ]}
          onSelect={onSelect}
        />
      </Box>
    </Box>
  );
}

function SetupPane({
  profileName,
  setup,
  hasAuthKeysArg,
  onAction,
  onBack
}: {
  profileName: string;
  setup: SetupState;
  hasAuthKeysArg: boolean;
  onAction: (view: View) => void;
  onBack: () => void;
}) {
  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>Setup</Text>
      <Text color={theme.muted}>A working Coral Cloud API key is expected for current CoralOS development workflows.</Text>
      <Box marginTop={1} flexDirection="column">
        <FieldRow label="Server auth" value={hasAuthKeysArg ? 'CLI argument' : maskValue(setup.authKey.trim())} muted={hasAuthKeysArg} />
        <FieldRow label="Coral Cloud" value={maskValue(setup.cloudApiKey.trim())} />
      </Box>
      <Box marginTop={1}>
        <Menu
          items={[
            ...(!hasAuthKeysArg ? [{ label: 'Edit server API auth key', value: 'edit-auth' as const }] : []),
            { label: 'Edit Coral Cloud API key', value: 'edit-cloud' },
            { label: 'Save setup profile', value: 'save' },
            { label: 'Back to workspace areas', value: 'home' }
          ]}
          onSelect={value => value === 'home' ? onBack() : onAction(value)}
        />
      </Box>
      <CliEquivalent command={configureProfileCommand(profileName)} />
      <Text color={theme.muted}>Noninteractive: <Text color={theme.brandSoft}>{configureProfileNonInteractiveCommand(profileName)}</Text></Text>
    </Box>
  );
}

function ServerPane({
  profileName,
  profilePath,
  server,
  controller,
  onBack
}: {
  profileName: string;
  profilePath: string;
  server: ServerSnapshot;
  controller: ServerController;
  onBack: () => void;
}) {
  const logs = server.logs.slice(-Math.min(12, Math.max(4, process.stdout.rows ? process.stdout.rows - 18 : 8)));

  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>Run local server</Text>
      <Text color={theme.muted}>Start Coral Server with this profile and inspect process output without leaving the TUI.</Text>
      <Box marginTop={1} flexDirection="column">
        <FieldRow label="Status" value={server.status} />
        <Text color={statusColor(server.status)}>Web URL: {server.url}</Text>
        {server.error ? <Text color={theme.danger}>{server.error}</Text> : null}
      </Box>
      <Box marginTop={1}>
        <Menu
          items={[
            { label: server.status === 'running' || server.status === 'starting' ? 'Stop server' : 'Start server' , value: 'toggle' },
            { label: 'Back to workspace areas', value: 'home' }
          ]}
          onSelect={value => {
            if (value === 'home') {
              onBack();
              return;
            }
            if (server.status === 'running' || server.status === 'starting') controller.stop();
            else controller.start(profilePath);
          }}
        />
      </Box>
      <CliEquivalent command={startServerCommand(profileName)} />
      <Box marginTop={1} flexDirection="column">
        <Text bold>stdout / stderr</Text>
        {logs.length === 0 ? <Text color={theme.muted}>No process output yet.</Text> : logs.map((line, index) => (
          <Text key={`${index}-${line}`} color={theme.muted}>{ellipsizeMiddle(line, 110)}</Text>
        ))}
      </Box>
    </Box>
  );
}

function DevelopPane({ onAction, onBack }: { onAction: (view: View) => void; onBack: () => void }) {
  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>Develop / build</Text>
      <Text color={theme.muted}>Templates are declared as data now, so new stacks can be added without reshaping the TUI.</Text>
      <Box marginTop={1}>
        <Menu
          items={[
            { label: 'Create an agent from a template', value: 'templates' },
            { label: 'Create an app from a template', value: 'apps' },
            { label: 'Set up Coral skill for coding agents', value: 'skills' },
            { label: 'Back to workspace areas', value: 'home' }
          ]}
          onSelect={value => value === 'home' ? onBack() : onAction(value)}
        />
      </Box>
    </Box>
  );
}

function TemplatePane({ kind, onBack }: { kind: 'app' | 'agent'; onBack: () => void }) {
  const templates = templateDefinitions.filter(template => template.kind === kind);

  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>{kind === 'app' ? 'Application templates' : 'Agent templates'}</Text>
      <Text color={theme.muted}>
        {kind === 'app'
          ? 'CoralOS apps are ordinary HTTP API clients. These templates will just help you start faster.'
          : 'Agent templates will create a project and help connect its coral-agent.toml to your dev profile.'}
      </Text>
      <Box marginTop={1} flexDirection="column">
        {templates.map(template => (
          <Box key={template.id} flexDirection="column" marginBottom={1}>
            <Text color={theme.brandSoft}>{template.name}: {template.stack}</Text>
            <Text color={theme.muted}>{template.description}</Text>
            <Text color={theme.warning}>{template.createCommand ?? 'Template command coming soon'}</Text>
          </Box>
        ))}
      </Box>
      <Box marginTop={1}>
        <Menu items={[{ label: 'Back to develop / build', value: 'back' }]} onSelect={onBack} />
      </Box>
    </Box>
  );
}

function PlaceholderPane({ title, body, command, onBack }: { title: string; body: string; command?: string; onBack: () => void }) {
  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>{title}</Text>
      <Text color={theme.muted}>{body}</Text>
      {command ? <CliEquivalent command={command} /> : <Text color={theme.warning}>Coming soon</Text>}
      <Box marginTop={1}>
        <Menu items={[{ label: 'Back', value: 'back' }]} onSelect={onBack} />
      </Box>
    </Box>
  );
}

function EditorPane({
  title,
  description,
  value,
  onChange,
  onDone,
  onCancel
}: {
  title: string;
  description: string;
  value: string;
  onChange: (value: string) => void;
  onDone: () => void;
  onCancel: () => void;
}) {
  useInput((input, key) => {
    if (key.return || /[\r\n]/.test(input)) onDone();
    if (key.escape) onCancel();
    if (input === 'u' && key.ctrl) onChange('');
  });

  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>{title}</Text>
      <Text color={theme.muted}>{description}</Text>
      <Box marginTop={1}>
        <Text color={theme.brand}>{'> '}</Text>
        <TextInput
          value={value}
          onChange={nextValue => onChange(nextValue.replace(/[\r\n]/g, ''))}
          onSubmit={onDone}
          mask="*"
          placeholder="leave blank to skip"
          showCursor
        />
      </Box>
      <Text color={theme.muted}>Enter: accept  Ctrl+U: clear  Esc: back</Text>
    </Box>
  );
}

function ConfirmPane({
  title,
  body,
  confirmLabel,
  onConfirm,
  onCancel
}: {
  title: string;
  body: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  useInput((input, key) => {
    if (key.escape || input === 'q') onCancel();
  });

  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>{title}</Text>
      <Text color={theme.muted}>{body}</Text>
      <Box marginTop={1}>
        <Menu
          items={[
            { label: confirmLabel, value: 'confirm' },
            { label: 'Return', value: 'cancel' }
          ]}
          onSelect={value => value === 'confirm' ? onConfirm() : onCancel()}
        />
      </Box>
    </Box>
  );
}

export function WizardApp({
  profileName,
  hasAuthKeysArg,
  isStartCommand,
  finish,
  serverController
}: WizardAppProps) {
  const { exit } = useApp();
  const { stdout } = useStdout();
  const metrics = getLayoutMetrics(stdout.columns, stdout.rows);
  const profilePath = configManager.getConfigProfilePath(profileName);
  const controller = useMemo(() => serverController ?? new ServerController(), [serverController]);
  const server = useServerSnapshot(controller);
  const [setup, setSetup] = useState<SetupState>({ authKey: '', cloudApiKey: '' });
  const [section, setSection] = useState<MainSection | null>(null);
  const [view, setView] = useState<View>('home');

  const close = (result: WizardResult) => {
    finish(result);
    exit();
  };

  useInput((input, key) => {
    if (view === 'edit-auth' || view === 'edit-cloud') return;
    if (key.escape || input === 'q') setView('exit');
  });

  let content: React.ReactNode;
  let footer = 'Arrow keys: move  Enter: select  Esc/q: exit';

  if (view === 'edit-auth') {
    content = (
      <EditorPane
        title="Server API auth key"
        description={isStartCommand ? 'Recommended before starting. Clients use this key to authenticate with your local Coral Server API.' : 'Optional. Clients use this key to authenticate with your local Coral Server API.'}
        value={setup.authKey}
        onChange={authKey => setSetup(current => ({ ...current, authKey }))}
        onDone={() => setView('home')}
        onCancel={() => setView('home')}
      />
    );
    footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
  } else if (view === 'edit-cloud') {
    content = (
      <EditorPane
        title="Coral Cloud API key"
        description="Required for the supported cloud-backed LLM proxy path. Create a key at https://coralcloud.ai/account."
        value={setup.cloudApiKey}
        onChange={cloudApiKey => setSetup(current => ({ ...current, cloudApiKey }))}
        onDone={() => setView('home')}
        onCancel={() => setView('home')}
      />
    );
    footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
  } else if (view === 'save') {
    content = (
      <ConfirmPane
        title={setup.cloudApiKey.trim() ? 'Save setup profile?' : 'Save without Coral Cloud key?'}
        body={setup.cloudApiKey.trim()
          ? 'This will rewrite the selected profile with the current setup values.'
          : 'Coral Cloud is expected for current workflows. You can save anyway, but setup is incomplete.'}
        confirmLabel="Save and exit"
        onConfirm={() => close({
          action: 'save',
          authKey: hasAuthKeysArg ? null : setup.authKey.trim(),
          cloudApiKey: setup.cloudApiKey.trim()
        })}
        onCancel={() => setView('home')}
      />
    );
  } else if (view === 'exit') {
    content = (
      <ConfirmPane
        title="Exit without saving?"
        body="No setup changes will be written to the profile."
        confirmLabel="Exit without saving"
        onConfirm={() => close({ action: 'cancel' })}
        onCancel={() => setView('home')}
      />
    );
  } else if (view === 'templates') {
    content = <TemplatePane kind="agent" onBack={() => setView('home')} />;
  } else if (view === 'apps') {
    content = <TemplatePane kind="app" onBack={() => setView('home')} />;
  } else if (view === 'skills') {
    content = <PlaceholderPane title="Coding agent skill" body="This will install or print setup instructions for the Coral skill once the exact skill package is available." onBack={() => setView('home')} />;
  } else if (view === 'sharing') {
    content = <PlaceholderPane title="Share agents" body="Open-source sharing will link to a guide for now. Remote consumption is planned and will be surfaced here when available." onBack={() => setView('home')} />;
  } else if (section === 'setup') {
    content = <SetupPane profileName={profileName} setup={setup} hasAuthKeysArg={hasAuthKeysArg} onAction={setView} onBack={() => setSection(null)} />;
  } else if (section === 'server') {
    content = <ServerPane profileName={profileName} profilePath={profilePath} server={server} controller={controller} onBack={() => setSection(null)} />;
  } else if (section === 'develop') {
    content = <DevelopPane onAction={setView} onBack={() => setSection(null)} />;
  } else if (section === 'share') {
    content = <PlaceholderPane title="Share agents" body="Open-source sharing guide link coming first. Remote consumption is coming soon." onBack={() => setSection(null)} />;
  } else {
    content = <MainNav onSelect={setSection} />;
  }

  return (
    <Box flexDirection="column" paddingX={1}>
      <AppHeader profileName={profileName} profilePath={profilePath} metrics={metrics} />
      <Box flexDirection={metrics.compact ? 'column' : 'row'}>
        <Sidebar
          metrics={metrics}
          profileName={profileName}
          profilePath={profilePath}
          setup={setup}
          hasAuthKeysArg={hasAuthKeysArg}
          server={server}
        />
        <Box flexDirection="column" width={metrics.contentWidth}>
          {content}
        </Box>
      </Box>
      <Footer metrics={metrics} text={footer} />
    </Box>
  );
}

export function FirstRunApp({ profileName, finish }: FirstRunAppProps) {
  const { exit } = useApp();
  const { stdout } = useStdout();
  const metrics = getLayoutMetrics(stdout.columns, stdout.rows);
  const profilePath = configManager.getConfigProfilePath(profileName);

  useInput((input, key) => {
    if (key.escape || input === 'q') {
      finish('exit');
      exit();
    }
  });

  return (
    <Box flexDirection="column" paddingX={1}>
      <AppHeader profileName={profileName} profilePath={profilePath} metrics={metrics} />
      <Text bold color={theme.brand}>New profile created</Text>
      <Text color={theme.muted}>Choose the next step for this profile.</Text>
      <Box marginTop={1}>
        <Menu
          items={[
            { label: 'Run setup wizard', value: 'wizard' },
            { label: 'Edit config with $EDITOR', value: 'editor' },
            { label: 'Continue with empty config', value: 'continue' },
            { label: 'Exit', value: 'exit' }
          ]}
          onSelect={value => {
            finish(value);
            exit();
          }}
        />
      </Box>
      <Footer metrics={metrics} text="Arrow keys: move  Enter: select  Esc/q: exit" />
    </Box>
  );
}
