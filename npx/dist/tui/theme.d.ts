export declare const theme: {
    readonly brand: "#f97316";
    readonly brandSoft: "#fbbf24";
    readonly text: "white";
    readonly muted: "gray";
    readonly border: "#7c2d12";
    readonly success: "green";
    readonly warning: "yellow";
    readonly danger: "red";
    readonly info: "cyan";
};
export type ThemeColor = (typeof theme)[keyof typeof theme];
