# Installation Guide for Coral Protocol Server

## Prerequisites

- Python 3.7 or higher
- Java Runtime Environment (JRE)

## Installation

You can install the Coral Protocol Server Python wrapper using pip:

```bash
pip install coral-server
```

This will install the package along with the precompiled JAR file.

## Manual Installation

If you prefer to build from source:

1. Clone the repository:
   ```bash
   git clone https://github.com/Coral-Protocol/coral-server.git
   cd coral-server
   ```

2. Build the JAR file (if not already built):
   ```bash
   ./gradlew build
   ```

3. Install the Python package:
   ```bash
   pip install -e .
   ```

## Usage

### Command Line Interface

After installation, you can run the server using the `coral-server` command:

```bash
# Run with default settings (SSE server on port 3001)
coral-server

# Run with custom port
coral-server --port 4000

# Run in stdio mode
coral-server --stdio
```

### Python API

You can also use the Coral Protocol Server programmatically in your Python code:

```python
from coral_server import CoralServer

# Create and start a server with default settings (SSE on port 3001)
server = CoralServer()
server.start()

# Or with custom settings
server = CoralServer(port=4000)
server.start()

# Or in stdio mode
server = CoralServer(stdio=True)
server.start()

# You can also use the context manager
with CoralServer(port=4000) as server:
    # Server is running within this block
    # Do something with the server...
    pass  # When the block exits, the server will be stopped automatically

# Stop the server manually
server.stop()
```

## Requirements

- Python 3.7 or higher
- Java Runtime Environment (JRE)
