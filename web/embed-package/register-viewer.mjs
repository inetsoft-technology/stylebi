import { loadBundle } from "./loader.mjs";

export function registerViewer() {
   return loadBundle("./viewer-element.js", "./viewer-element.css");
}
