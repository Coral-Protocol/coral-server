import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Box, Text } from 'ink';
import SelectInput from 'ink-select-input';
import { theme } from './theme.js';
import { ellipsizeMiddle } from './layout.js';
export function Divider({ title, width }) {
    const label = title ? ` ${title} ` : '';
    const available = Math.max(width - label.length, 8);
    const left = '-'.repeat(title ? Math.floor(available * 0.32) : available);
    const right = title ? '-'.repeat(Math.max(available - left.length, 1)) : '';
    return (_jsxs(Box, { children: [_jsx(Text, { color: theme.border, children: left }), title ? _jsx(Text, { color: theme.brand, children: label }) : null, right ? _jsx(Text, { color: theme.border, children: right }) : null] }));
}
export function CliEquivalent({ command }) {
    return (_jsxs(Box, { flexDirection: "column", marginTop: 1, children: [_jsx(Text, { color: theme.muted, children: "CLI equivalent" }), _jsx(Text, { color: theme.brandSoft, children: command })] }));
}
export function Menu({ items, onSelect, limit }) {
    return (_jsx(SelectInput, { items: items, limit: limit ?? 10, onSelect: item => onSelect(item.value) }));
}
export function FieldRow({ label, value, labelWidth = 16, muted = false }) {
    return (_jsxs(Box, { flexDirection: "row", children: [_jsx(Box, { width: labelWidth, children: _jsx(Text, { color: muted ? theme.muted : undefined, children: label }) }), _jsx(Text, { color: value === 'Not set' ? theme.warning : theme.success, children: value })] }));
}
export function Panel({ title, children, width }) {
    return (_jsxs(Box, { borderStyle: "round", borderColor: theme.border, flexDirection: "column", paddingX: 1, paddingY: 1, width: width, children: [title ? _jsx(Text, { bold: true, color: theme.brand, children: title }) : null, children] }));
}
export function AppHeader({ profileName, profilePath, metrics }) {
    const maxPath = Math.max(metrics.width - 16, 24);
    return (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { bold: true, color: theme.brand, children: "CoralOS Developer Console" }), _jsxs(Text, { color: theme.muted, children: ["Profile ", _jsx(Text, { color: theme.brandSoft, children: profileName }), '  ', _jsx(Text, { color: theme.muted, children: ellipsizeMiddle(profilePath, maxPath) })] }), _jsx(Divider, { title: "workspace", width: metrics.width - 2 })] }));
}
export function Footer({ metrics, text }) {
    return (_jsxs(Box, { flexDirection: "column", marginTop: 1, children: [_jsx(Divider, { width: metrics.width - 2 }), _jsx(Text, { color: theme.muted, children: text })] }));
}
//# sourceMappingURL=components.js.map