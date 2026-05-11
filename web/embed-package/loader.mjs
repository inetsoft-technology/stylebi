const loadedResources = new Map();

function loadScript(relativePath) {
   const url = assetUrl(relativePath);
   const cacheKey = `script:${url}`;

   if(loadedResources.has(cacheKey)) {
      return loadedResources.get(cacheKey);
   }

   const pending = new Promise((resolve, reject) => {
      const existingScript = document.querySelector(`script[data-stylebi-embed="${url}"]`);

      if(existingScript) {
         resolve();
         return;
      }

      const script = document.createElement("script");
      script.async = true;
      script.dataset.stylebiEmbed = url;
      script.src = url;
      script.addEventListener("load", () => resolve(), { once: true });
      script.addEventListener("error", () => reject(new Error(`Failed to load ${url}`)),
         { once: true });
      document.head.appendChild(script);
   });

   loadedResources.set(cacheKey, pending);
   return pending;
}

function loadStylesheet(relativePath) {
   const url = assetUrl(relativePath);
   const cacheKey = `style:${url}`;

   if(loadedResources.has(cacheKey)) {
      return loadedResources.get(cacheKey);
   }

   const pending = new Promise((resolve, reject) => {
      const existingLink = document.querySelector(`link[data-stylebi-embed="${url}"]`);

      if(existingLink) {
         resolve();
         return;
      }

      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.dataset.stylebiEmbed = url;
      link.href = url;
      link.addEventListener("load", () => resolve(), { once: true });
      link.addEventListener("error", () => reject(new Error(`Failed to load ${url}`)),
         { once: true });
      document.head.appendChild(link);
   });

   loadedResources.set(cacheKey, pending);
   return pending;
}

function ensureBrowser() {
   if(typeof document === "undefined") {
      throw new Error("StyleBI embed elements can only be loaded in a browser environment.");
   }
}

export function assetUrl(relativePath) {
   return new URL(relativePath, import.meta.url).toString();
}

export async function loadBundle(scriptPath, stylesheetPath) {
   ensureBrowser();
   await Promise.all([
      loadStylesheet(stylesheetPath),
      loadScript(scriptPath)
   ]);
}
