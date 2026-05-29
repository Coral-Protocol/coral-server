const { spawnSync } = require('child_process');
const fs = require('fs');
const { pkg, IS_WINDOWS } = require('./constants');
const {
  getConfigProfilePath,
  ensureConfigProfileDir,
  generateDefaultConfig,
  buildConfigFromWizardResults
} = require('./config-manager');

async function loadInk() {
  const ReactModule = await import('react');
  const ink = await import('ink');
  const SelectInputModule = await import('ink-select-input');
  const TextInputModule = await import('ink-text-input');

  return {
    React: ReactModule.default || ReactModule,
    ink,
    SelectInput: SelectInputModule.default,
    TextInput: TextInputModule.default
  };
}

function maskValue(value) {
  if (!value) return 'Not set';
  if (value.length <= 8) return '*'.repeat(value.length);
  return `${value.slice(0, 4)}${'*'.repeat(Math.min(12, value.length - 8))}${value.slice(-4)}`;
}

function runInkApp(App, props = {}) {
  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    return Promise.reject(new Error('The setup wizard requires an interactive terminal.'));
  }

  return loadInk().then(({ React, ink, SelectInput, TextInput }) => new Promise((resolve, reject) => {
    let settled = false;
    const finish = value => {
      settled = true;
      resolve(value);
    };

    const instance = ink.render(React.createElement(App, {
      ...props,
      finish,
      React,
      ink,
      SelectInput,
      TextInput
    }), {
      exitOnCtrlC: true
    });

    instance.waitUntilExit().then(() => {
      if (!settled) resolve(null);
    }).catch(reject);
  }));
}

function FieldRow({ React, ink, label, value, muted = false }) {
  const { Box, Text } = ink;
  return React.createElement(Box, { flexDirection: 'row' },
    React.createElement(Box, { width: 15 },
      React.createElement(Text, { color: muted ? 'gray' : undefined }, label)
    ),
    React.createElement(Text, { color: value === 'Not set' ? 'yellow' : 'green' }, value)
  );
}

function StatusPanel({ React, ink, profileName, profilePath, hasAuthKeysArg, authKey, cloudApiKey }) {
  const { Box, Text } = ink;
  return React.createElement(Box, {
    borderStyle: 'round',
    borderColor: 'gray',
    flexDirection: 'column',
    paddingX: 1,
    paddingY: 1,
    width: 40,
    marginRight: 2
  },
    React.createElement(Text, { bold: true }, 'Profile'),
    React.createElement(Text, { color: 'cyan' }, profileName),
    React.createElement(Text, { color: 'gray', wrap: 'truncate-middle' }, profilePath),
    React.createElement(Box, { marginTop: 1, flexDirection: 'column' },
      React.createElement(Text, { bold: true }, 'Setup State'),
      React.createElement(FieldRow, {
        React,
        ink,
        label: 'Server auth',
        value: hasAuthKeysArg ? 'CLI argument' : maskValue(authKey.trim()),
        muted: hasAuthKeysArg
      }),
      React.createElement(FieldRow, {
        React,
        ink,
        label: 'Coral Cloud',
        value: maskValue(cloudApiKey.trim())
      }),
      React.createElement(FieldRow, {
        React,
        ink,
        label: 'LLM providers',
        value: 'Manual'
      })
    )
  );
}

function DividerLine({ React, ink, title }) {
  const { Box, Text } = ink;
  const label = title ? ` ${title} ` : '';
  const left = '-'.repeat(title ? 18 : 72);
  const right = title ? '-'.repeat(42) : '';

  return React.createElement(Box, null,
    React.createElement(Text, { color: 'gray' }, left),
    title ? React.createElement(Text, { color: 'cyan' }, label) : null,
    right ? React.createElement(Text, { color: 'gray' }, right) : null
  );
}

function Shell({ React, ink, profileName, profilePath, children, footer }) {
  const { Box, Text } = ink;
  return React.createElement(Box, { flexDirection: 'column', paddingX: 1 },
    React.createElement(Box, { flexDirection: 'column' },
      React.createElement(Text, { bold: true, color: 'cyan' }, 'Coral Server setup'),
      React.createElement(Text, { color: 'gray' }, `Profile "${profileName}" at ${profilePath}`),
      React.createElement(DividerLine, { React, ink, title: 'configuration' })
    ),
    children,
    React.createElement(Box, { marginTop: 1, flexDirection: 'column' },
      React.createElement(DividerLine, { React, ink }),
      React.createElement(Text, { color: 'gray' }, footer || 'Arrow keys: move  Enter: select  Esc/q: exit')
    )
  );
}

function MainMenu({ React, ink, SelectInput, hasAuthKeysArg, onSelect }) {
  const { Box, Text } = ink;
  const items = [
    ...(!hasAuthKeysArg ? [{
      label: 'Edit server API auth key',
      value: 'edit-auth'
    }] : []),
    {
      label: 'Edit Coral Cloud API key',
      value: 'edit-cloud'
    },
    {
      label: 'Save configuration',
      value: 'save'
    },
    {
      label: 'Exit without saving',
      value: 'exit'
    }
  ];

  return React.createElement(Box, { flexDirection: 'column', flexGrow: 1 },
    React.createElement(Text, { bold: true }, 'Actions'),
    React.createElement(Text, { color: 'gray' }, 'Use the arrow keys to choose what to configure.'),
    React.createElement(Box, { marginTop: 1 },
      React.createElement(SelectInput, {
        items,
        limit: 8,
        onSelect: item => onSelect(item.value)
      })
    )
  );
}

function EditorPane({ React, ink, TextInput, title, description, value, setValue, onDone, onCancel }) {
  const { Box, Text, useInput } = ink;

  useInput((input, key) => {
    if (key.return || /[\r\n]/.test(input)) onDone();
    if (key.escape) onCancel();
    if (input === 'u' && key.ctrl) setValue('');
  });

  return React.createElement(Box, { flexDirection: 'column', flexGrow: 1 },
    React.createElement(Text, { bold: true }, title),
    React.createElement(Text, { color: 'gray' }, description),
    React.createElement(Box, { marginTop: 1 },
      React.createElement(Text, { color: 'cyan' }, '> '),
      React.createElement(TextInput, {
        value,
        onChange: nextValue => setValue(nextValue.replace(/[\r\n]/g, '')),
        onSubmit: onDone,
        mask: '*',
        placeholder: 'leave blank to skip',
        showCursor: true
      })
    ),
    React.createElement(Box, { marginTop: 1, flexDirection: 'column' },
      React.createElement(Text, { color: 'gray' }, 'Enter: accept value  Ctrl+U: clear field  Esc: return to actions'),
      React.createElement(Text, { color: 'gray' }, 'Secrets are masked on screen and written to the selected config profile.')
    )
  );
}

function ConfirmPane({ React, ink, SelectInput, title, message, confirmLabel, cancelLabel, onConfirm, onCancel }) {
  const { Box, Text, useInput } = ink;

  useInput((input, key) => {
    if (key.escape || input === 'q') onCancel();
  });

  return React.createElement(Box, { flexDirection: 'column', flexGrow: 1 },
    React.createElement(Text, { bold: true }, title),
    React.createElement(Text, { color: 'gray' }, message),
    React.createElement(Box, { marginTop: 1 },
      React.createElement(SelectInput, {
        items: [
          { label: confirmLabel, value: true },
          { label: cancelLabel, value: false }
        ],
        onSelect: item => item.value ? onConfirm() : onCancel()
      })
    )
  );
}

function WizardApp({ React, ink, SelectInput, TextInput, finish, profileName, hasAuthKeysArg, isStartCommand }) {
  const { Box, Text, useApp, useInput } = ink;
  const { exit } = useApp();
  const [view, setView] = React.useState('menu');
  const [authKey, setAuthKey] = React.useState('');
  const [cloudApiKey, setCloudApiKey] = React.useState('');
  const profilePath = getConfigProfilePath(profileName);
  const isEditing = view === 'edit-auth' || view === 'edit-cloud';

  const close = result => {
    finish(result);
    exit();
  };

  useInput((input, key) => {
    if (isEditing) return;

    if (key.escape || input === 'q') {
      setView('confirm-exit');
    }
  });

  let content;
  let footer = 'Arrow keys: move  Enter: select  Esc/q: exit';

  if (view === 'edit-auth') {
    content = React.createElement(EditorPane, {
      React,
      ink,
      TextInput,
      title: isStartCommand ? 'Server API auth key' : 'Server API auth key',
      description: isStartCommand
        ? 'Recommended before starting: clients use this key to authenticate with your local Coral Server API.'
        : 'Optional: clients use this key to authenticate with your local Coral Server API.',
      value: authKey,
      setValue: setAuthKey,
      onDone: () => setView('menu'),
      onCancel: () => setView('menu')
    });
    footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
  } else if (view === 'edit-cloud') {
    content = React.createElement(EditorPane, {
      React,
      ink,
      TextInput,
      title: 'Coral Cloud API key',
      description: 'Configures cloud-backed LLM proxy access through [cloud].apiKey. Create a key at https://coralcloud.ai/account.',
      value: cloudApiKey,
      setValue: setCloudApiKey,
      onDone: () => setView('menu'),
      onCancel: () => setView('menu')
    });
    footer = 'Enter: accept  Ctrl+U: clear  Esc: back';
  } else if (view === 'save') {
    content = React.createElement(ConfirmPane, {
      React,
      ink,
      SelectInput,
      title: 'Save configuration?',
      message: 'This will rewrite the selected profile using the current wizard selections.',
      confirmLabel: 'Save and exit',
      cancelLabel: 'Return to actions',
      onConfirm: () => close({
        action: 'save',
        authKey: hasAuthKeysArg ? null : authKey.trim(),
        cloudApiKey: cloudApiKey.trim()
      }),
      onCancel: () => setView('menu')
    });
    footer = 'Arrow keys: choose  Enter: confirm  Esc/q: back';
  } else if (view === 'confirm-exit') {
    content = React.createElement(ConfirmPane, {
      React,
      ink,
      SelectInput,
      title: 'Exit without saving?',
      message: 'No changes will be written to the profile.',
      confirmLabel: 'Exit without saving',
      cancelLabel: 'Return to actions',
      onConfirm: () => close({ action: 'cancel' }),
      onCancel: () => setView('menu')
    });
    footer = 'Arrow keys: choose  Enter: confirm  Esc/q: back';
  } else {
    content = React.createElement(Box, { flexDirection: 'column', flexGrow: 1 },
      React.createElement(MainMenu, {
        React,
        ink,
        SelectInput,
        hasAuthKeysArg,
        onSelect: action => {
          if (action === 'save') setView('save');
          else if (action === 'exit') setView('confirm-exit');
          else setView(action);
        }
      }),
      React.createElement(Box, { marginTop: 1, flexDirection: 'column' },
        React.createElement(Text, { color: 'gray' }, 'Coral Cloud is the supported default for LLM proxy setup in this wizard.'),
        React.createElement(Text, { color: 'gray' }, 'Direct OpenAI, Anthropic, or other provider blocks can still be edited manually in the config file.')
      )
    );
  }

  return React.createElement(Shell, {
    React,
    ink,
    profileName,
    profilePath,
    footer
  },
    React.createElement(Box, { flexDirection: 'row' },
      React.createElement(StatusPanel, {
        React,
        ink,
        profileName,
        profilePath,
        hasAuthKeysArg,
        authKey,
        cloudApiKey
      }),
      content
    )
  );
}

function FirstRunApp({ React, ink, SelectInput, finish, profileName }) {
  const { Box, Text, useApp, useInput } = ink;
  const { exit } = useApp();
  const profilePath = getConfigProfilePath(profileName);
  const choices = [
    { label: 'Run setup wizard', value: 'wizard' },
    { label: 'Edit config with $EDITOR', value: 'editor' },
    { label: 'Continue with empty config', value: 'continue' },
    { label: 'Exit', value: 'exit' }
  ];

  useInput((input, key) => {
    if (key.escape || input === 'q') {
      finish('exit');
      exit();
    }
  });

  return React.createElement(Shell, {
    React,
    ink,
    profileName,
    profilePath,
    footer: 'Arrow keys: move  Enter: select  Esc/q: exit'
  },
    React.createElement(Box, { flexDirection: 'column' },
      React.createElement(Text, { bold: true }, 'New profile created'),
      React.createElement(Text, { color: 'gray' }, 'Choose the next step for this profile.'),
      React.createElement(Box, { marginTop: 1 },
        React.createElement(SelectInput, {
          items: choices,
          onSelect: item => {
            finish(item.value);
            exit();
          }
        })
      )
    )
  );
}

async function runSetupWizard(profileName, options = {}) {
  const { hasAuthKeysArg = false, isStartCommand = false } = options;
  const profilePath = getConfigProfilePath(profileName);
  const version = pkg.version;

  console.log(`\nRun this wizard any time with:`);
  console.log(`  npx ${pkg.name}@${version} server configure ${profileName}\n`);

  const result = await runInkApp(WizardApp, {
    profileName,
    hasAuthKeysArg,
    isStartCommand
  });

  if (!result || result.action !== 'save') {
    console.log('Configuration unchanged.');
    return false;
  }

  const config = buildConfigFromWizardResults([], result.authKey, result.cloudApiKey);
  fs.writeFileSync(profilePath, config);
  console.log(`Configuration saved to ${profilePath}`);
  return true;
}

async function handleFirstRun(profileName, options = {}) {
  const profilePath = getConfigProfilePath(profileName);

  ensureConfigProfileDir(profileName);
  fs.writeFileSync(profilePath, generateDefaultConfig());
  console.log(`File created at ${profilePath}\n`);

  const choice = await runInkApp(FirstRunApp, { profileName });

  switch (choice) {
    case 'wizard':
      await runSetupWizard(profileName, options);
      return true;
    case 'editor': {
      const editor = process.env.EDITOR || process.env.VISUAL || (IS_WINDOWS ? 'notepad' : 'vi');
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
  }
}

module.exports = {
  runSetupWizard,
  handleFirstRun,
};
