import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useMemo, useState } from 'react';
import { Box, Text, useApp, useInput, useStdout } from 'ink';
import TextInput from 'ink-text-input';
import { configManager } from '../platform/legacy.js';
import { configureProfileNonInteractiveCommand, configureProfileCommand, startServerCommand } from '../platform/commands.js';
import { getLayoutMetrics, ellipsizeMiddle } from './layout.js';
import { theme } from './theme.js';
import { AppHeader, CliEquivalent, FieldRow, Footer, Menu, Panel } from './components.js';
import { maskValue } from '../features/setup/model.js';
import { ServerController } from '../features/server/server-controller.js';
import { templateDefinitions } from '../features/templates/templates.js';
function statusColor(status) {
    if (status === 'running')
        return theme.success;
    if (status === 'starting')
        return theme.warning;
    if (status === 'error')
        return theme.danger;
    return theme.muted;
}
function useServerSnapshot(controller) {
    const [snapshot, setSnapshot] = useState(controller.getSnapshot());
    useEffect(() => controller.subscribe(setSnapshot), [controller]);
    return snapshot;
}
function Sidebar({ metrics, profileName, profilePath, setup, hasAuthKeysArg, server }) {
    if (metrics.compact) {
        return null;
    }
    return (_jsx(Box, { marginRight: 2, children: _jsxs(Panel, { title: "Workspace", width: metrics.sidebarWidth, children: [_jsx(Text, { color: theme.brandSoft, children: profileName }), _jsx(Text, { color: theme.muted, children: ellipsizeMiddle(profilePath, metrics.sidebarWidth - 4) }), _jsxs(Box, { marginTop: 1, flexDirection: "column", children: [_jsx(Text, { bold: true, children: "Setup" }), _jsx(FieldRow, { label: "Server auth", value: hasAuthKeysArg ? 'CLI argument' : maskValue(setup.authKey.trim()), muted: hasAuthKeysArg }), _jsx(FieldRow, { label: "Coral Cloud", value: maskValue(setup.cloudApiKey.trim()) }), _jsx(FieldRow, { label: "LLM providers", value: "Manual" })] }), _jsxs(Box, { marginTop: 1, flexDirection: "column", children: [_jsx(Text, { bold: true, children: "Server" }), _jsx(FieldRow, { label: "Status", value: server.status }), _jsx(Text, { color: statusColor(server.status), children: server.url })] })] }) }));
}
function MainNav({ onSelect }) {
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, children: "What are you doing?" }), _jsx(Text, { color: theme.muted, children: "Choose a workspace area. Every action shows its CLI equivalent when one exists." }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        { label: 'Setup dev profile', value: 'setup' },
                        { label: 'Run local server', value: 'server' },
                        { label: 'Develop / build agents and apps', value: 'develop' },
                        { label: 'Share agents', value: 'share' }
                    ], onSelect: onSelect }) })] }));
}
function SetupPane({ profileName, setup, hasAuthKeysArg, onAction, onBack }) {
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: "Setup" }), _jsx(Text, { color: theme.muted, children: "A working Coral Cloud API key is expected for current CoralOS development workflows." }), _jsxs(Box, { marginTop: 1, flexDirection: "column", children: [_jsx(FieldRow, { label: "Server auth", value: hasAuthKeysArg ? 'CLI argument' : maskValue(setup.authKey.trim()), muted: hasAuthKeysArg }), _jsx(FieldRow, { label: "Coral Cloud", value: maskValue(setup.cloudApiKey.trim()) })] }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        ...(!hasAuthKeysArg ? [{ label: 'Edit server API auth key', value: 'edit-auth' }] : []),
                        { label: 'Edit Coral Cloud API key', value: 'edit-cloud' },
                        { label: 'Save setup profile', value: 'save' },
                        { label: 'Back to workspace areas', value: 'home' }
                    ], onSelect: value => value === 'home' ? onBack() : onAction(value) }) }), _jsx(CliEquivalent, { command: configureProfileCommand(profileName) }), _jsxs(Text, { color: theme.muted, children: ["Noninteractive: ", _jsx(Text, { color: theme.brandSoft, children: configureProfileNonInteractiveCommand(profileName) })] })] }));
}
function ServerPane({ profileName, profilePath, server, controller, onBack }) {
    const logs = server.logs.slice(-Math.min(12, Math.max(4, process.stdout.rows ? process.stdout.rows - 18 : 8)));
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: "Run local server" }), _jsx(Text, { color: theme.muted, children: "Start Coral Server with this profile and inspect process output without leaving the TUI." }), _jsxs(Box, { marginTop: 1, flexDirection: "column", children: [_jsx(FieldRow, { label: "Status", value: server.status }), _jsxs(Text, { color: statusColor(server.status), children: ["Web URL: ", server.url] }), server.error ? _jsx(Text, { color: theme.danger, children: server.error }) : null] }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        { label: server.status === 'running' || server.status === 'starting' ? 'Stop server' : 'Start server', value: 'toggle' },
                        { label: 'Back to workspace areas', value: 'home' }
                    ], onSelect: value => {
                        if (value === 'home') {
                            onBack();
                            return;
                        }
                        if (server.status === 'running' || server.status === 'starting')
                            controller.stop();
                        else
                            controller.start(profilePath);
                    } }) }), _jsx(CliEquivalent, { command: startServerCommand(profileName) }), _jsxs(Box, { marginTop: 1, flexDirection: "column", children: [_jsx(Text, { bold: true, children: "stdout / stderr" }), logs.length === 0 ? _jsx(Text, { color: theme.muted, children: "No process output yet." }) : logs.map((line, index) => (_jsx(Text, { color: theme.muted, children: ellipsizeMiddle(line, 110) }, `${index}-${line}`)))] })] }));
}
function DevelopPane({ onAction, onBack }) {
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: "Develop / build" }), _jsx(Text, { color: theme.muted, children: "Templates are declared as data now, so new stacks can be added without reshaping the TUI." }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        { label: 'Create an agent from a template', value: 'templates' },
                        { label: 'Create an app from a template', value: 'apps' },
                        { label: 'Set up Coral skill for coding agents', value: 'skills' },
                        { label: 'Back to workspace areas', value: 'home' }
                    ], onSelect: value => value === 'home' ? onBack() : onAction(value) }) })] }));
}
function TemplatePane({ kind, onBack }) {
    const templates = templateDefinitions.filter(template => template.kind === kind);
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: kind === 'app' ? 'Application templates' : 'Agent templates' }), _jsx(Text, { color: theme.muted, children: kind === 'app'
                    ? 'CoralOS apps are ordinary HTTP API clients. These templates will just help you start faster.'
                    : 'Agent templates will create a project and help connect its coral-agent.toml to your dev profile.' }), _jsx(Box, { marginTop: 1, flexDirection: "column", children: templates.map(template => (_jsxs(Box, { flexDirection: "column", marginBottom: 1, children: [_jsxs(Text, { color: theme.brandSoft, children: [template.name, ": ", template.stack] }), _jsx(Text, { color: theme.muted, children: template.description }), _jsx(Text, { color: theme.warning, children: template.createCommand ?? 'Template command coming soon' })] }, template.id))) }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [{ label: 'Back to develop / build', value: 'back' }], onSelect: onBack }) })] }));
}
function PlaceholderPane({ title, body, command, onBack }) {
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: title }), _jsx(Text, { color: theme.muted, children: body }), command ? _jsx(CliEquivalent, { command: command }) : _jsx(Text, { color: theme.warning, children: "Coming soon" }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [{ label: 'Back', value: 'back' }], onSelect: onBack }) })] }));
}
function EditorPane({ title, description, value, onChange, onDone, onCancel }) {
    useInput((input, key) => {
        if (key.return || /[\r\n]/.test(input))
            onDone();
        if (key.escape)
            onCancel();
        if (input === 'u' && key.ctrl)
            onChange('');
    });
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: title }), _jsx(Text, { color: theme.muted, children: description }), _jsxs(Box, { marginTop: 1, children: [_jsx(Text, { color: theme.brand, children: '> ' }), _jsx(TextInput, { value: value, onChange: nextValue => onChange(nextValue.replace(/[\r\n]/g, '')), onSubmit: onDone, mask: "*", placeholder: "leave blank to skip", showCursor: true })] }), _jsx(Text, { color: theme.muted, children: "Enter: accept  Ctrl+U: clear  Esc: back" })] }));
}
function ConfirmPane({ title, body, confirmLabel, onConfirm, onCancel }) {
    useInput((input, key) => {
        if (key.escape || input === 'q')
            onCancel();
    });
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: title }), _jsx(Text, { color: theme.muted, children: body }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        { label: confirmLabel, value: 'confirm' },
                        { label: 'Return', value: 'cancel' }
                    ], onSelect: value => value === 'confirm' ? onConfirm() : onCancel() }) })] }));
}
export function WizardApp({ profileName, hasAuthKeysArg, isStartCommand, finish, serverController }) {
    const { exit } = useApp();
    const { stdout } = useStdout();
    const metrics = getLayoutMetrics(stdout.columns, stdout.rows);
    const profilePath = configManager.getConfigProfilePath(profileName);
    const controller = useMemo(() => serverController ?? new ServerController(), [serverController]);
    const server = useServerSnapshot(controller);
    const [setup, setSetup] = useState({ authKey: '', cloudApiKey: '' });
    const [section, setSection] = useState(null);
    const [view, setView] = useState('home');
    const close = (result) => {
        finish(result);
        exit();
    };
    useInput((input, key) => {
        if (view === 'edit-auth' || view === 'edit-cloud')
            return;
        if (key.escape || input === 'q')
            setView('exit');
    });
    let content;
    let footer = 'Arrow keys: move  Enter: select  Esc/q: exit';
    if (view === 'edit-auth') {
        content = (_jsx(EditorPane, { title: "Server API auth key", description: isStartCommand ? 'Recommended before starting. Clients use this key to authenticate with your local Coral Server API.' : 'Optional. Clients use this key to authenticate with your local Coral Server API.', value: setup.authKey, onChange: authKey => setSetup(current => ({ ...current, authKey })), onDone: () => setView('home'), onCancel: () => setView('home') }));
        footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
    }
    else if (view === 'edit-cloud') {
        content = (_jsx(EditorPane, { title: "Coral Cloud API key", description: "Required for the supported cloud-backed LLM proxy path. Create a key at https://coralcloud.ai/account.", value: setup.cloudApiKey, onChange: cloudApiKey => setSetup(current => ({ ...current, cloudApiKey })), onDone: () => setView('home'), onCancel: () => setView('home') }));
        footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
    }
    else if (view === 'save') {
        content = (_jsx(ConfirmPane, { title: setup.cloudApiKey.trim() ? 'Save setup profile?' : 'Save without Coral Cloud key?', body: setup.cloudApiKey.trim()
                ? 'This will rewrite the selected profile with the current setup values.'
                : 'Coral Cloud is expected for current workflows. You can save anyway, but setup is incomplete.', confirmLabel: "Save and exit", onConfirm: () => close({
                action: 'save',
                authKey: hasAuthKeysArg ? null : setup.authKey.trim(),
                cloudApiKey: setup.cloudApiKey.trim()
            }), onCancel: () => setView('home') }));
    }
    else if (view === 'exit') {
        content = (_jsx(ConfirmPane, { title: "Exit without saving?", body: "No setup changes will be written to the profile.", confirmLabel: "Exit without saving", onConfirm: () => close({ action: 'cancel' }), onCancel: () => setView('home') }));
    }
    else if (view === 'templates') {
        content = _jsx(TemplatePane, { kind: "agent", onBack: () => setView('home') });
    }
    else if (view === 'apps') {
        content = _jsx(TemplatePane, { kind: "app", onBack: () => setView('home') });
    }
    else if (view === 'skills') {
        content = _jsx(PlaceholderPane, { title: "Coding agent skill", body: "This will install or print setup instructions for the Coral skill once the exact skill package is available.", onBack: () => setView('home') });
    }
    else if (view === 'sharing') {
        content = _jsx(PlaceholderPane, { title: "Share agents", body: "Open-source sharing will link to a guide for now. Remote consumption is planned and will be surfaced here when available.", onBack: () => setView('home') });
    }
    else if (section === 'setup') {
        content = _jsx(SetupPane, { profileName: profileName, setup: setup, hasAuthKeysArg: hasAuthKeysArg, onAction: setView, onBack: () => setSection(null) });
    }
    else if (section === 'server') {
        content = _jsx(ServerPane, { profileName: profileName, profilePath: profilePath, server: server, controller: controller, onBack: () => setSection(null) });
    }
    else if (section === 'develop') {
        content = _jsx(DevelopPane, { onAction: setView, onBack: () => setSection(null) });
    }
    else if (section === 'share') {
        content = _jsx(PlaceholderPane, { title: "Share agents", body: "Open-source sharing guide link coming first. Remote consumption is coming soon.", onBack: () => setSection(null) });
    }
    else {
        content = _jsx(MainNav, { onSelect: setSection });
    }
    return (_jsxs(Box, { flexDirection: "column", paddingX: 1, children: [_jsx(AppHeader, { profileName: profileName, profilePath: profilePath, metrics: metrics }), _jsxs(Box, { flexDirection: metrics.compact ? 'column' : 'row', children: [_jsx(Sidebar, { metrics: metrics, profileName: profileName, profilePath: profilePath, setup: setup, hasAuthKeysArg: hasAuthKeysArg, server: server }), _jsx(Box, { flexDirection: "column", width: metrics.contentWidth, children: content })] }), _jsx(Footer, { metrics: metrics, text: footer })] }));
}
export function FirstRunApp({ profileName, finish }) {
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
    return (_jsxs(Box, { flexDirection: "column", paddingX: 1, children: [_jsx(AppHeader, { profileName: profileName, profilePath: profilePath, metrics: metrics }), _jsx(Text, { bold: true, color: theme.brand, children: "New profile created" }), _jsx(Text, { color: theme.muted, children: "Choose the next step for this profile." }), _jsx(Box, { marginTop: 1, children: _jsx(Menu, { items: [
                        { label: 'Run setup wizard', value: 'wizard' },
                        { label: 'Edit config with $EDITOR', value: 'editor' },
                        { label: 'Continue with empty config', value: 'continue' },
                        { label: 'Exit', value: 'exit' }
                    ], onSelect: value => {
                        finish(value);
                        exit();
                    } }) }), _jsx(Footer, { metrics: metrics, text: "Arrow keys: move  Enter: select  Esc/q: exit" })] }));
}
//# sourceMappingURL=app.js.map