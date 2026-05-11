import { registerElements } from "./register-elements.mjs";
import { registerViewer } from "./register-viewer.mjs";

export async function registerAll() {
   await Promise.all([registerElements(), registerViewer()]);
}
