/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// Bug #76399 (Composer follow-up): PR #4972 fixed the mini-toolbar/data-tip two-writer
// position race (see mini-toolbar.component.spec.ts) by pairing [VSDataTip][miniToolbar]=true
// on the <mini-toolbar> composed in vs-object-container.component.html (Portal's viewer). That
// PR's own notes explicitly called out editable-object-container.component.html (Composer's
// canvas, which also backs Composer's own "Preview" tab -- see previewViewsheet() in
// composer-main.component.ts, which opens a viewsheet.preview=true tab still rendered through
// this same component) as one of several places composing a bare <mini-toolbar> with no
// VSDataTip pairing at all -- a deliberate no-op for the three other listed places (they never
// host data-tip content), but Composer's canvas *does* host data-tip content (the same
// vs-crosstab/vs-chart/etc. components, each already carrying their own VSDataTip directive --
// see vs-crosstab.component.html), so it was left with the exact pre-fix race instead.
//
// This was confirmed live: reporter Milk Yan reopened #76399 (Redmine journal, 2026-09-08)
// noting "Portal is ok, composer['s] preview [reproduces] it".
//
// EditableObjectContainer's own spec suite (see .test-helpers.ts) deliberately tests via direct
// class instantiation rather than compiling the real template (the component has 16 constructor
// dependencies and dozens of child assembly components), so it cannot catch a template-only
// regression like this one. mini-toolbar.component.spec.ts already covers the underlying
// mechanism generically (a synthetic host composed the same way). This test instead pins the
// one thing that actually regressed: that editable-object-container.component.html's own
// <mini-toolbar> element carries that same composition, by reading its real template source.
describe("EditableObjectContainer — mini-toolbar/data-tip composition", () => {
   const template = readFileSync(
      join(__dirname, "editable-object-container.component.html"), "utf-8");

   function miniToolbarTag(): string {
      const start = template.indexOf("<mini-toolbar");
      expect(start).toBeGreaterThan(-1);
      const end = template.indexOf("</mini-toolbar>", start);
      return template.substring(start, end);
   }

   it("pairs VSDataTip [miniToolbar]=true on the <mini-toolbar> element", () => {
      const tag = miniToolbarTag();
      expect(tag).toMatch(/\bVSDataTip\b/);
      expect(tag).toMatch(/\[miniToolbar]\s*=\s*"true"/);
   });

   it("binds VSDataTip's dataTipName to the assembly's own absoluteName", () => {
      const tag = miniToolbarTag();
      expect(tag).toMatch(/\[dataTipName]\s*=\s*"vsObject\.absoluteName"/);
   });

   // A data tip's target can be a container assembly (e.g. a Group Container), not just a
   // single leaf assembly. In that case a child's own dataTipName (its absoluteName) never
   // matches the registered data-tip value -- only its container name does, via
   // popContainerName (see VSDataTipDirective's ngDoCheck / isActiveDataTipOwner()).
   it("binds VSDataTip's popContainerName to the assembly's container", () => {
      const tag = miniToolbarTag();
      expect(tag).toMatch(/\[popContainerName]\s*=\s*"vsObject\.container"/);
   });
});
