# Pull Request: Make server runnable through pip

## Description

This PR adds a Python wrapper for the Coral Protocol Server to make it easily installable and runnable through pip. It addresses issue #8.

## Features

- Python package structure for distributing the server as a pip package
- Command-line interface accessible through the `coral-server` command
- Python API for programmatically managing the server
- Support for both SSE and stdio modes
- Examples showing usage
- Comprehensive installation instructions

## Requirements

- Python 3.7+
- Java Runtime Environment (JRE)

## Usage

After installation:

```bash
# Run with default settings (SSE server on port 3001)
coral-server

# Run with custom port
coral-server --port 4000

# Run in stdio mode
coral-server --stdio
```

## Testing Done

- Tested installation from source
- Verified server starts and stops correctly
- Tested CLI arguments
- Tested Python API usage

## Related Issues

Closes #8
