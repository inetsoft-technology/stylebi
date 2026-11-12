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

/**
 * Every caller that opens the viewsheet Conditions or Highlight dialog must hand it the socket
 * (bug #80459), or the Connect to Claude button silently never renders for that assembly type.
 *
 * The three plain-class handlers are exercised behaviourally. The four directives are checked
 * against their source -- they need a full Angular host to construct -- which also guards a
 * later caller being added without the socket: every dialog opened with `VSConditionDialog` or
 * `HighlightDialog` must assign `dialog.socketConnection`, and the number of such call sites is
 * pinned so a removed one is noticed too.
 */
import { readFileSync } from "fs";
import { dirname, resolve } from "path";
import { fileURLToPath } from "url";
import { of } from "rxjs";
import { CalcTableActionHandler } from "../../../../vsobjects/objects/table/calc-table-action-handler";
import { CrosstabActionHandler } from "../../../../vsobjects/objects/table/crosstab-action-handler";
import { TableActionHandler } from "../../../../vsobjects/objects/table/table-action-handler";

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = resolve(HERE, "../../../..");

/** The dialog a handler opened, captured instead of shown. */
function stubbedHandler(handler: any): { dialogs: any[] } {
   const dialogs: any[] = [];
   handler.showDialog = vi.fn(() => {
      const dialog: any = {};
      dialogs.push(dialog);
      return dialog;
   });
   return { dialogs };
}

describe.each([
   ["TableActionHandler",
    (m: any, s: any, c: any) => new TableActionHandler(m, s, {} as any, c)],
   ["CrosstabActionHandler",
    (m: any, s: any, c: any) => new CrosstabActionHandler(m, s, {} as any, c)],
   ["CalcTableActionHandler",
    (m: any, s: any, c: any) => new CalcTableActionHandler(m, s, {} as any, c)],
])("%s hands the socket to the dialogs it opens (bug #80459)", (_name, create) => {
   const modelService = { getModel: vi.fn(() => of({})) };
   const socket = { sendEvent: vi.fn() };
   // Only the design-time Composer pairs an agent; the viewer and the preview share these
   // handlers but must not offer Connect to Claude to an end user.
   const composer: any = { composer: true };
   const viewer: any = { composer: false, viewer: true };
   const preview: any = { composer: false, preview: true };

   it("gives the Conditions dialog the socket", async () => {
      const handler: any = create(modelService, socket, composer);
      const { dialogs } = stubbedHandler(handler);

      handler.showConditionDialog("rt-1", "Table1", []);
      await vi.waitFor(() => expect(dialogs.length).toBe(1));

      expect(dialogs[0].runtimeId).toBe("rt-1");
      expect(dialogs[0].assemblyName).toBe("Table1");
      expect(dialogs[0].socketConnection).toBe(socket);
   });

   it("gives the Highlight dialog the socket", async () => {
      const handler: any = create(modelService, socket, composer);
      const { dialogs } = stubbedHandler(handler);

      handler.showHighlightDialog("rt-1", "Table1", 0, 0, []);
      await vi.waitFor(() => expect(dialogs.length).toBe(1));

      expect(dialogs[0].runtimeId).toBe("rt-1");
      expect(dialogs[0].socketConnection).toBe(socket);
   });

   it.each([["viewer", viewer], ["preview", preview]])(
      "does not give the Conditions or Highlight dialog the socket in the %s", async (_n, ctx) => {
      const handler: any = create(modelService, socket, ctx);
      const { dialogs } = stubbedHandler(handler);

      handler.showConditionDialog("rt-1", "Table1", []);
      handler.showHighlightDialog("rt-1", "Table1", 0, 0, []);
      await vi.waitFor(() => expect(dialogs.length).toBe(2));

      expect(dialogs[0].socketConnection).toBeNull();
      expect(dialogs[1].socketConnection).toBeNull();
   });
});

describe.each([
   ["composer/gui/vs/action/chart-action-handler.directive.ts", 2],
   ["composer/gui/vs/action/gauge-action-handler.directive.ts", 1],
   ["composer/gui/vs/action/image-action-handler.directive.ts", 2],
   ["composer/gui/vs/action/text-action-handler.directive.ts", 2],
   ["vsobjects/objects/table/table-action-handler.ts", 2],
   ["vsobjects/objects/table/crosstab-action-handler.ts", 2],
   ["vsobjects/objects/table/calc-table-action-handler.ts", 2],
])("%s opens the condition dialogs with the socket (bug #80459)", (file, expectedCallSites) => {
   it("assigns dialog.socketConnection at every VSConditionDialog/HighlightDialog call site", () => {
      const source = readFileSync(resolve(APP, file), "utf8");
      // One segment per dialog-opening call; the dialog type is the call's first argument.
      const segments = source.split(/\.show(?:Table|Crosstab|Chart)?Dialog\b/).slice(1);
      const conditionOrHighlight = segments.filter(
         s => /^[^;]{0,120}?\b(?:VSConditionDialog|HighlightDialog)\b/.test(s.replace(/\s+/g, " ")));

      expect(conditionOrHighlight.length).toBe(expectedCallSites);

      for(const segment of conditionOrHighlight) {
         // up to the next dialog-opening call (or the end of the file)
         expect(segment).toContain("dialog.socketConnection =");
      }
   });
});
