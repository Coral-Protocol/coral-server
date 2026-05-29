export function getLayoutMetrics(width, height) {
    const safeWidth = Math.max(width || 80, 60);
    const sidebarWidth = safeWidth < 84
        ? 0
        : Math.min(48, Math.max(34, Math.floor(safeWidth * 0.34)));
    const contentWidth = sidebarWidth === 0
        ? safeWidth - 4
        : safeWidth - sidebarWidth - 8;
    return {
        width: safeWidth,
        height: Math.max(height || 24, 18),
        sidebarWidth,
        contentWidth: Math.max(contentWidth, 32),
        compact: safeWidth < 84
    };
}
export function ellipsizeMiddle(value, maxLength) {
    if (value.length <= maxLength)
        return value;
    if (maxLength <= 8)
        return value.slice(0, Math.max(maxLength, 1));
    const side = Math.floor((maxLength - 1) / 2);
    return `${value.slice(0, side)}…${value.slice(value.length - (maxLength - side - 1))}`;
}
//# sourceMappingURL=layout.js.map