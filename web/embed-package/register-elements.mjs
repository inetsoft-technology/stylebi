import { loadBundle } from "./loader.mjs";

export function registerElements() {
   return loadBundle("./elements.js", "./elements.css");
}
