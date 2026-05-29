export type TemplateDefinition = {
  id: string;
  name: string;
  kind: 'app' | 'agent';
  stack: string;
  status: 'planned' | 'coming-soon';
  createCommand?: string;
  description: string;
};

export const templateDefinitions: TemplateDefinition[] = [
  {
    id: 'app-express-typescript',
    name: 'HTTP app',
    kind: 'app',
    stack: 'Express / TypeScript',
    status: 'planned',
    createCommand: 'npm create coralos-app@latest -- --template express-typescript',
    description: 'A standard web app that talks to Coral Server over HTTP.'
  },
  {
    id: 'app-ktor-kotlin',
    name: 'HTTP app',
    kind: 'app',
    stack: 'Ktor / Kotlin',
    status: 'planned',
    createCommand: 'npm create coralos-app@latest -- --template ktor-kotlin',
    description: 'A Kotlin application template for the Coral Server HTTP API.'
  },
  {
    id: 'agent-pydantic',
    name: 'Agent',
    kind: 'agent',
    stack: 'Pydantic / Python',
    status: 'planned',
    createCommand: 'npm create coralos-agent@latest -- --template pydantic-python',
    description: 'A Python agent project with a local coral-agent.toml.'
  },
  {
    id: 'agent-koog',
    name: 'Agent',
    kind: 'agent',
    stack: 'Koog / Kotlin',
    status: 'planned',
    createCommand: 'npm create coralos-agent@latest -- --template koog-kotlin',
    description: 'A Kotlin agent project with a local coral-agent.toml.'
  }
];
