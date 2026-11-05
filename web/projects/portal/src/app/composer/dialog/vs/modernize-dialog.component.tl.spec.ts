/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { NO_ERRORS_SCHEMA, Input, Component } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { fireEvent, render, screen, waitFor } from "@testing-library/angular";
import { delay, http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ModelService } from "../../../widget/services/model.service";
import { ModernizeDialogModel, ModernizeDialogResult } from "../../data/vs/modernize-dialog-model";
import { ModernizeDialogComponent } from "./modernize-dialog.component";

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderStub {
   @Input() title: any;
   @Input() cshid: any;
}

let checks: any[] = [];
let checkResponse: any = { exists: false, permissionDenied: "", invalidName: "" };

function baseModel(over: Partial<ModernizeDialogModel> = {}): ModernizeDialogModel {
   return { keepAvailable: true, neverSaved: false, suggestedName: "Sales (modern)", parentId: "",
      embedsDashboards: false, scriptWarning: null, ...over };
}

async function renderDialog(model: ModernizeDialogModel, modified = false) {
   checks = [];
   server.use(http.post("*/api/composer/vs/modernize-dialog-model/check-name/*", async ({ request }) => {
      checks.push(await request.json());
      return HttpResponse.json(checkResponse);
   }));
   const commits: ModernizeDialogResult[] = [];
   const result = await render(ModernizeDialogComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: ModalHeaderComponent, with: ModalHeaderStub }],
      providers: [provideHttpClient(), ModelService],
      inputs: { model, runtimeId: "vs-1", modified },
      on: { onCommit: (r: ModernizeDialogResult) => commits.push(r) },
   });
   return { ...result, commits };
}

const keepRadio = () => screen.getByRole("radio", { name: "_#(composer.vs.modernize.keep)" }) as HTMLInputElement;
const resetRadio = () => screen.getByRole("radio", { name: "_#(composer.vs.modernize.reset)" }) as HTMLInputElement;
const saveAs = () => screen.getByLabelText("_#(composer.vs.modernize.saveAs)") as HTMLInputElement;
const primary = () => document.querySelector(".modal-footer .btn-primary") as HTMLButtonElement;

async function settled() {
   await waitFor(() => expect(checks.length).toBeGreaterThan(0));
   await new Promise(r => setTimeout(r, 50));
}

function type(value: string) {
   fireEvent.input(saveAs(), { target: { value } });
   fireEvent.blur(saveAs());
}

beforeEach(() => {
   checkResponse = { exists: false, permissionDenied: "", invalidName: "" };
});

describe("ModernizeDialogComponent", () => {
   it("selects keep by default and emits keep", async () => {
      const { commits } = await renderDialog(baseModel());
      expect(keepRadio().checked).toBe(true);
      expect(resetRadio().checked).toBe(false);
      expect(screen.queryByLabelText("_#(composer.vs.modernize.saveAs)")).toBeNull();
      expect(primary().textContent).toContain("_#(composer.vs.modernize.action)");
      fireEvent.click(primary());
      expect(commits).toEqual([{ choice: "keep" }]);
   });

   it("disables keep and preselects reset when nothing is unmarked", async () => {
      await renderDialog(baseModel({ keepAvailable: false }));
      expect(keepRadio().disabled).toBe(true);
      expect(resetRadio().checked).toBe(true);
      expect(screen.getByText("_#(composer.vs.modernize.keep.none)")).toBeTruthy();
      expect(saveAs().value).toBe("Sales (modern)");
   });

   it("disables reset with the save-first line for a never-saved sheet", async () => {
      await renderDialog(baseModel({ neverSaved: true }));
      expect(resetRadio().disabled).toBe(true);
      expect(keepRadio().checked).toBe(true);
      expect(screen.getByText("_#(composer.vs.modernize.reset.neverSaved)")).toBeTruthy();
   });

   it("shows the save-as field, links line, unsaved, embedded and script lines for reset", async () => {
      await renderDialog(baseModel({ embedsDashboards: true, scriptWarning: "Scripts set formatting on Chart1." }), true);
      expect(screen.queryByText("_#(composer.vs.modernize.links)")).toBeNull();
      fireEvent.click(resetRadio());
      await settled();
      expect(saveAs().value).toBe("Sales (modern)");
      expect(screen.getByText("_#(composer.vs.modernize.links)")).toBeTruthy();
      expect(screen.getByText("_#(composer.vs.modernize.embedded)")).toBeTruthy();
      expect(screen.getByText("Scripts set formatting on Chart1.")).toBeTruthy();
      expect(document.querySelector(".modernize-unsaved")).toBeTruthy();
      expect(primary().textContent).toContain("_#(composer.vs.modernize.createCopy)");
      expect(checks).toEqual([{ name: "Sales (modern)" }]);
   });

   it("shows the name-exists error and keeps the button disabled", async () => {
      checkResponse = { exists: true, permissionDenied: "", invalidName: "" };
      await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      await waitFor(() => expect(screen.getByText("_#(composer.vs.modernize.nameExists)")).toBeTruthy());
      expect(primary().disabled).toBe(true);
   });

   it("shows the permission error from the server", async () => {
      checkResponse = { exists: false, permissionDenied: "No write permission here", invalidName: "" };
      await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      await waitFor(() => expect(screen.getByText("No write permission here")).toBeTruthy());
      expect(primary().disabled).toBe(true);
   });

   it("rejects a name with banned characters", async () => {
      await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      checks.length = 0;
      type("bad<name");
      await waitFor(() => expect(primary().disabled).toBe(true));
      expect(saveAs().classList.contains("is-invalid")).toBe(true);
      expect(document.querySelector(".invalid-feedback")).toBeTruthy();
      await new Promise(r => setTimeout(r, 450));
      expect(checks.length).toBe(0);
   });

   it("emits copy with the trimmed name once even when clicked twice", async () => {
      const { commits } = await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      checks.length = 0;
      type("  My Copy  ");
      await waitFor(() => expect(checks).toEqual([{ name: "My Copy" }]));
      await waitFor(() => expect(primary().disabled).toBe(false));
      fireEvent.click(primary());
      fireEvent.click(primary());
      expect(commits).toEqual([{ choice: "copy", name: "My Copy" }]);
   });

   it("ignores a slow response for an older name", async () => {
      await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      let aResponded = false;
      server.use(http.post("*/api/composer/vs/modernize-dialog-model/check-name/*", async ({ request }) => {
         const body: any = await request.json();
         checks.push(body);

         if(body.name === "a") {
            await delay(150);
            aResponded = true;
         }

         return HttpResponse.json({ exists: false, permissionDenied: "", invalidName: "" });
      }));
      checks.length = 0;
      type("a");
      await waitFor(() => expect(checks).toEqual([{ name: "a" }]));
      type("ab");
      await waitFor(() => expect(aResponded).toBe(true));
      await new Promise(r => setTimeout(r, 60));
      expect(checks).toEqual([{ name: "a" }]);
      expect(primary().disabled).toBe(true);
      await waitFor(() => expect(checks).toEqual([{ name: "a" }, { name: "ab" }]));
      await waitFor(() => expect(primary().disabled).toBe(false));
   });

   it("recovers from a failed check when the name is edited", async () => {
      await renderDialog(baseModel({ keepAvailable: false }));
      await settled();
      let calls = 0;
      server.use(http.post("*/api/composer/vs/modernize-dialog-model/check-name/*", async () => {
         calls++;
         return calls === 1 ? new HttpResponse(null, { status: 500 })
            : HttpResponse.json({ exists: false, permissionDenied: "", invalidName: "" });
      }));
      type("first");
      await waitFor(() => expect(calls).toBe(1));
      await new Promise(r => setTimeout(r, 100));
      expect(primary().disabled).toBe(true);
      type("second");
      await waitFor(() => expect(calls).toBe(2));
      await waitFor(() => expect(primary().disabled).toBe(false));
   });

   it("sends no check for a never-saved sheet", async () => {
      await renderDialog(baseModel({ keepAvailable: false, neverSaved: true }));
      await new Promise(r => setTimeout(r, 450));
      expect(checks.length).toBe(0);
      expect(primary().disabled).toBe(true);
   });
});
