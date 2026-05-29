export function maskValue(value) {
    if (!value)
        return 'Not set';
    if (value.length <= 8)
        return '*'.repeat(value.length);
    return `${value.slice(0, 4)}${'*'.repeat(Math.min(12, value.length - 8))}${value.slice(-4)}`;
}
//# sourceMappingURL=model.js.map