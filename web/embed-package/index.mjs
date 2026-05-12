import { assetUrl } from "./loader.mjs";

export { assetUrl } from "./loader.mjs";
export { registerAll } from "./register-all.mjs";
export { registerElements } from "./register-elements.mjs";
export { registerViewer } from "./register-viewer.mjs";

export const elementsScriptUrl = assetUrl("./elements.js");
export const elementsStylesheetUrl = assetUrl("./elements.css");
export const viewerScriptUrl = assetUrl("./viewer-element.js");
export const viewerStylesheetUrl = assetUrl("./viewer-element.css");
