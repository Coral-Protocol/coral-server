export type TemplateDefinition = {
    id: string;
    name: string;
    kind: 'app' | 'agent';
    stack: string;
    status: 'planned' | 'coming-soon';
    createCommand?: string;
    description: string;
};
export declare const templateDefinitions: TemplateDefinition[];
