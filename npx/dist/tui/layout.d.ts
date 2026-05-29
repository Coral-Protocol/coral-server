export type LayoutMetrics = {
    width: number;
    height: number;
    sidebarWidth: number;
    contentWidth: number;
    compact: boolean;
};
export declare function getLayoutMetrics(width: number, height: number): LayoutMetrics;
export declare function ellipsizeMiddle(value: string, maxLength: number): string;
