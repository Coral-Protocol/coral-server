# Scripts

This directory contains the Node.js scripts intended to be executed by developers using `npx`.

## Usage

### Running the setup wizard

```bash
npx coralos-dev@RC-1.2.0 server --config-profile=dev configure
```

The setup wizard guides you through setting up the LLM proxy's providers. The config profile "dev" means that the output will be written to
~/.coral/config-profiles/dev/dev-coral-server-config.toml.

### Running the server

```bash
npx coralos-dev@RC-1.2.0 server --config-profile=dev start -- --auth.keys=test
```

Starts the LLM proxy server using the configuration from the "dev" profile.

Everything after `--` is passed to the server as CLI args, behaving the same as is described in `../README.md`.
In this case, the coral server will run with its authenticated APIs accessible with "Authorization: Bearer test", or just "test" in the console.

If the config profile doesn't exist, it will prompt to create it. If it's not specified, only the passed CLI args will be used.

