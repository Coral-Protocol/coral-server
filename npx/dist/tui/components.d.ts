import React from 'react';
import { type LayoutMetrics } from './layout.js';
export type MenuItem<T extends string = string> = {
    label: string;
    value: T;
};
export declare function Divider({ title, width }: {
    title?: string;
    width: number;
}): import("react/jsx-runtime").JSX.Element;
export declare function CliEquivalent({ command }: {
    command: string;
}): import("react/jsx-runtime").JSX.Element;
export declare function Menu<T extends string>({ items, onSelect, limit }: {
    items: Array<MenuItem<T>>;
    onSelect: (value: T) => void;
    limit?: number;
}): import("react/jsx-runtime").JSX.Element;
export declare function FieldRow({ label, value, labelWidth, muted }: {
    label: string;
    value: string;
    labelWidth?: number;
    muted?: boolean;
}): import("react/jsx-runtime").JSX.Element;
export declare function Panel({ title, children, width }: {
    title?: string;
    children: React.ReactNode;
    width?: number;
}): import("react/jsx-runtime").JSX.Element;
export declare function AppHeader({ profileName, profilePath, metrics }: {
    profileName: string;
    profilePath: string;
    metrics: LayoutMetrics;
}): import("react/jsx-runtime").JSX.Element;
export declare function Footer({ metrics, text }: {
    metrics: LayoutMetrics;
    text: string;
}): import("react/jsx-runtime").JSX.Element;
