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
 * Bug #80303 (round 1 follow-up): saveScriptAs()'s http.post subscribe had no error callback,
 * so a server-side rejection of the script name (ScriptLibraryValidator.requireValidName) was
 * never shown to the user — the Save As dialog just silently did nothing.
 *
 * HTTP: http.post is spied on directly and made to error; no real HTTP/MSW involved.
 */

import "@angular/compiler";
import { throwError } from "rxjs";
import { HttpErrorResponse } from "@angular/common/http";
import { ComponentTool } from "../../common/util/component-tool";
import { ComposerTabModel } from "./composer-tab-model";
import { makeMocks, renderComponent } from "./composer-main.spec-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ComposerMainComponent — saveScriptAs: error surfacing", () => {
   it("shows the server's error message instead of failing silently", async () => {
      const mocks = makeMocks();
      mocks.modalService.open.mockReturnValue({
         result: Promise.resolve({ name: "a-b" }),
         componentInstance: {
            onCommit: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
            onCancel: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
         },
      });
      const { comp } = await renderComponent({}, mocks);

      const scriptAsset = {
         type: "script",
         id: "1^1048576^__NULL__^oldScript",
         label: "oldScript",
         isModified: true,
         newAsset: false,
      } as any;
      (comp as any)._focusedTab = new ComposerTabModel("script", scriptAsset);

      const errorResponse = new HttpErrorResponse({
         error: { message: "Invalid script name" },
         status: 500,
      });
      vi.spyOn((comp as any).http, "post").mockReturnValue(throwError(() => errorResponse));
      const showHttpErrorSpy = vi.spyOn(ComponentTool, "showHttpError");

      comp.saveScriptAs(scriptAsset);
      await Promise.resolve();
      await Promise.resolve();

      expect(showHttpErrorSpy).toHaveBeenCalledWith("_#(js:Error)", errorResponse, expect.anything());
   });
});
