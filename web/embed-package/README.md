# StyleBI Embed Elements

Prebuilt StyleBI web components for embedding charts, crosstabs, and viewers in other web
applications.

## Install

Add a project-level `.npmrc` entry for the GitHub Packages scope:

```text
@inetsoft-technology:registry=https://npm.pkg.github.com
```

Install the package:

```bash
npm install @inetsoft-technology/stylebi-embed-elements
```

## Use

Register chart and crosstab elements:

```ts
import { registerElements } from "@inetsoft-technology/stylebi-embed-elements";

await registerElements();
```

Register the viewer element:

```ts
import { registerViewer } from "@inetsoft-technology/stylebi-embed-elements";

await registerViewer();
```

Register everything:

```ts
import { registerAll } from "@inetsoft-technology/stylebi-embed-elements";

await registerAll();
```

The package publishes these custom elements:

- `inetsoft-chart`
- `inetsoft-crosstab`
- `inetsoft-table`
- `inetsoft-gauge`
- `inetsoft-text`
- `inetsoft-image`
- `inetsoft-datasource-registration`
- `inetsoft-viewer`

All but the last are in `elements.js` (`registerElements()`); `inetsoft-viewer` is in
`viewer-element.js` (`registerViewer()`).

`inetsoft-datasource-registration` differs from the others: it authors content rather than
displaying it. It registers a StyleBI data source, rendering whatever form the server describes for
the chosen type.

| | |
|---|---|
| Attribute | `listing-name` — skip the type picker and go straight to that type |
| Events | `registered` (`{name, type}`), `cancelled`, `failed` (message) |

## Package Contents

- `elements.js` and `elements.css`
- `viewer-element.js` and `viewer-element.css`
- `assets/` for fonts, icons, and viewer images

## Publish From This Repository

Build the package locally:

```bash
npm run package:embed
```

Publish to GitHub Packages:

```bash
NODE_AUTH_TOKEN=<github_token> npm run publish:embed
```
