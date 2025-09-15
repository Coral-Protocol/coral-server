# Migrating to v1

Up until Coral Server V1 agents were configured using a single `application.yaml` file.  In V1, this file has been split 
into multiple files, one registry file and multiple agent files.

An old application.yaml file might look like this:

<details>
<summary>application.yaml</summary>

```yaml
applications:
  - id: "app"
    name: "Default Application"
    description: "Default application for testing"
    privacyKeys:
      - "priv"

registry:
  interface:
    options:
      - name: "MODEL_API_KEY"
        type: "string"
        description: "API key for the model provider"
      - name: "MODEL_NAME"
        type: "string"
        description: "What model to use (e.g 'gpt-4.1')"
        default: "gpt-4.1"
      - name: "MODEL_PROVIDER"
        type: "string"
        description: "What model provider to use (e.g 'openai', etc)"
        default: "openai"
      - name: "MODEL_MAX_TOKENS"
        type: "string"
        description: "Max tokens to use"
        default: "16000"
      - name: "MODEL_TEMPERATURE"
        type: "string"
        description: "What model temperature to use"
        default: "0.3"
      - name: "TIMEOUT_MS"
        type: "string"
        description: "Connection/tool timeouts in ms"
        default: 60000

    runtime:
      type: "executable"
      command: [ "bash", "-c", "../agents/interface/run_agent.sh ../agents/interface/main.py" ]
      environment:
        - option: "MODEL_API_KEY"
        - option: "MODEL_NAME"
        - option: "MODEL_PROVIDER"
        - option: "MODEL_MAX_TOKENS"
        - option: "MODEL_TEMPERATURE"
        - option: "TIMEOUT_MS"

  github:
    options:
      - name: "MODEL_API_KEY"
        type: "string"
        description: "API key for the model provider"
      - name: "GITHUB_PERSONAL_ACCESS_TOKEN"
        type: "string"
        description: "Github token for the service"
      - name: "MODEL_NAME"
        type: "string"
        description: "What model to use (e.g 'gpt-4.1')"
        default: "gpt-4.1"
      - name: "MODEL_PROVIDER"
        type: "string"
        description: "What model provider to use (e.g 'openai', etc)"
        default: "openai"
      - name: "MODEL_MAX_TOKENS"
        type: "string"
        description: "Max tokens to use"
        default: "16000"
      - name: "MODEL_TEMPERATURE"
        type: "string"
        description: "What model temperature to use"
        default: "0.3"
      - name: "TIMEOUT_MS"
        type: "string"
        description: "Connection/tool timeouts in ms"
        default: 300
    runtime:
      type: "executable"
      command: [ "bash", "-c", "../agents/github/run_agent.sh ../agents/github/main.py" ]
      environment:
        - option: "MODEL_API_KEY"
        - option: "GITHUB_PERSONAL_ACCESS_TOKEN"
        - option: "MODEL_NAME"
        - option: "MODEL_PROVIDER"
        - option: "MODEL_MAX_TOKENS"
        - option: "MODEL_TEMPERATURE"
        - option: "TIMEOUT_MS"

  firecrawl:
    options:
      - name: "MODEL_API_KEY"
        type: "string"
        description: "API key for the model provider"
      - name: "FIRECRAWL_API_KEY"
        type: "string"
        description: "FIRECRAWL API KEY for the service"
      - name: "MODEL_NAME"
        type: "string"
        description: "What model to use (e.g 'gpt-4.1')"
        default: "gpt-4.1"
      - name: "MODEL_PROVIDER"
        type: "string"
        description: "What model provider to use (e.g 'openai', etc)"
        default: "openai"
      - name: "MODEL_MAX_TOKENS"
        type: "string"
        description: "Max tokens to use"
        default: "16000"
      - name: "MODEL_TEMPERATURE"
        type: "string"
        description: "What model temperature to use"
        default: "0.3"
      - name: "TIMEOUT_MS"
        type: "string"
        description: "Connection/tool timeouts in ms"
        default: 300
    runtime:
      type: "executable"
      command: ["bash", "-c", "../agents/firecrawl/run_agent.sh ../agents/firecrawl/main.py"]
      environment:
        - option: "MODEL_API_KEY"
        - option: "FIRECRAWL_API_KEY"
        - option: "MODEL_NAME"
        - option: "MODEL_PROVIDER"
        - option: "MODEL_MAX_TOKENS"
        - option: "MODEL_TEMPERATURE"
        - option: "TIMEOUT_MS"
```
</details>

After migration, it becomes 3 agent files:

<details>
<summary>interface/coral-agent.toml</summary>

```toml
[agent]
name = "interface"
version = "0.0.1"

[options.MODEL_API_KEY]
type = "string"
description = "API key for the model provider"

[options.MODEL_NAME]
type = "string"
description = "What model to use (e.g 'gpt-4.1')"
default = "gpt-4.1"

[options.MODEL_PROVIDER]
type = "string"
description = "What model provider to use (e.g 'openai', etc)"
default = "openai"

[options.MODEL_MAX_TOKENS]
type = "string"
description = "Max tokens to use"
default = "16000"

[options.MODEL_TEMPERATURE]
type = "string"
description = "What model temperature to use"
default = "0.3"

[options.TIMEOUT_MS]
type = "string"
description = "Connection/tool timeouts in ms"
default = "60000"

[runtimes.executable]
command = ["bash", "-c", "../agents/interface/run_agent.sh ../agents/interface/main.py"]
```
</details>

<details>
<summary>github/coral-agent.toml</summary>

```toml
[agent]
name = "github"
version = "0.0.1"

[options.MODEL_API_KEY]
type = "string"
description = "API key for the model provider"

[options.GITHUB_PERSONAL_ACCESS_TOKEN]
type = "string"
description = "Github token for the service"

[options.MODEL_NAME]
type = "string"
description = "What model to use (e.g 'gpt-4.1')"
default = "gpt-4.1"

[options.MODEL_PROVIDER]
type = "string"
description = "What model provider to use (e.g 'openai', etc)"
default = "openai"

[options.MODEL_MAX_TOKENS]
type = "string"
description = "Max tokens to use"
default = "16000"

[options.MODEL_TEMPERATURE]
type = "string"
description = "What model temperature to use"
default = "0.3"

[options.TIMEOUT_MS]
type = "string"
description = "Connection/tool timeouts in ms"
default = "300"

[runtimes.executable]
command = ["bash", "-c", "../agents/github/run_agent.sh ../agents/github/main.py"]
```

</details>

<details>
<summary>firecrawl/coral-agent.toml</summary>

```toml
[agent]
name = "firecrawl"
version = "0.0.1"

[options.MODEL_API_KEY]
type = "string"
description = "API key for the model provider"

[options.FIRECRAWL_API_KEY]
type = "string"
description = "FIRECRAWL API KEY for the service"

[options.MODEL_NAME]
type = "string"
description = "What model to use (e.g 'gpt-4.1')"
default = "gpt-4.1"

[options.MODEL_PROVIDER]
type = "string"
description = "What model provider to use (e.g 'openai', etc)"
default = "openai"

[options.MODEL_MAX_TOKENS]
type = "string"
description = "Max tokens to use"
default = "16000"

[options.MODEL_TEMPERATURE]
type = "string"
description = "What model temperature to use"
default = "0.3"

[options.TIMEOUT_MS]
type = "string"
description = "Connection/tool timeouts in ms"
default = "300"

[runtimes.executable]
command = ["bash", "-c", "../agents/firecrawl/run_agent.sh ../agents/firecrawl/main.py"]
```

</details>

And one registry file:

<details>
<summary>registry.toml</summary>

```toml
[[local-agent]]
path = "interface"

[[local-agent]]
path = "github"

[[local-agent]]
path = "firecrawl"
```

</details>

Presuming a directory structure of:
```
my project
│   registry.toml
│
└───interface
│   │   coral-agent.toml
│   │   ...
└───github
│   │   coral-agent.toml
│   │   ...
└───firwecrawl
│   │   coral-agent.toml
│   │   ...
```

# New features of the v1/registry

See [the full registry example](src/main/resources/full-registry.toml) 

### Agents now support definitions using the "description" 

The description field can be injected into the prompts of other agents, making it an easy way to add agents to an existing graph 
```toml
[agent]
name = "github"
description = "Has access to public github repositories"
```

### Agents are now versioned
```toml
[agent]
name = "github"
version = "2.0.0"
```

### The registry.toml file can be reference agents via a path, git or the marketplace:

Local path:
```toml
[[local-agent]]
path = "github"
```

Git path:
```toml
[[git-agent]]
repo = "https://github.com/Coral-Protocol/ca-context7"

```

Marketplace (released soon):
```toml
[[indexed-agent]]
name = "ca-context7"
```