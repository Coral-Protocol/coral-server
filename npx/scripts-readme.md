# Scripts

This directory contains the Node.js scripts intended to be executed by developers using `npx`.
The Ink TUI requires Node.js 22 or newer.

## Usage

### Running the setup wizard

```bash
npx coralos-dev@RC-1.2.0 server configure dev
```

Or from source (run from the project root):
```bash
node npx/coral-server.js server configure dev
```

The setup wizard uses an Ink TUI to configure the server API auth key and Coral Cloud API key. The config profile "dev" means that the output will be written to
~/.coral/config-profiles/dev/dev-coral-server-config.toml. Direct LLM provider backup settings can still be edited in the config file manually.

For non-interactive setup:

```bash
npx coralos-dev@RC-1.2.0 server configure dev --cloud.api-key=ck_... --auth.key=dev --yes
```

The TUI also exposes workspace areas for setup, local server running, develop/build templates, and sharing. Template actions are data-backed placeholders until the template repositories exist.

### Running the server

```bash
npx coralos-dev@RC-1.2.0 server start --config-profile=dev -- --auth.keys=test
```

Or from source (run from the project root):
```bash
node npx/coral-server.js server start --config-profile=dev -- --auth.keys=test
```

Starts the LLM proxy server using the configuration from the "dev" profile.

Everything after `--` is passed to the server as CLI args, behaving the same as is described in `../README.md`.
In this case, the coral server will run with its authenticated APIs accessible with "Authorization: Bearer test", or just "test" in the console.

If the config profile doesn't exist, it will prompt to create it. If it's not specified, only the passed CLI args will be used.
