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
 * ObjectWizardToolBarComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — openFullEditor(): runtimeId + assemblyName params built correctly;
 *                        onFullEditor emitted after HTTP success
 *   Group 2  [baseline] — done() / cancel(): onClose output contracts
 *   Group 3  [baseline] — getAssemblyTypeIcon(): 9-type switch (all branches including default)
 *   Group 4  [baseline] — helpLink getter
 *   Group 5  [baseline] — openAiAssistantDialog(): sets lastBindingObject + calls dialog service
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Memory-leak suspicion — openFullEditor() subscribes to an HTTP observable but stores no
 *   Subscription reference and has no ngOnDestroy. If the component is destroyed before the
 *   response arrives, the subscribe callback will fire on the destroyed instance. Angular's
 *   HttpClient observables complete on response so the risk is low in normal usage, but long
 *   network delays could trigger a post-destroy emit.
 *
 * Out of scope:
 *   actionGroup getter — structural wrapper; action closures covered by done/cancel/openFullEditor
 *   actions array — covered transitively via openFullEditor/done/cancel groups
 */

// Must be first import: @angular/common/http's PlatformLocation uses ɵɵngDeclareInjectable
// which requires the JIT compiler when running outside the Angular builder pipeline.
import "@angular/compiler";
import { Component, Directive, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { of } from "rxjs";

import { ObjectWizardToolBarComponent } from "./object-wizard-tool-bar.component";
import { ToolbarGroup } from "../../widget/toolbar/toolbar-group/toolbar-group.component";
import { HelpLinkDirective } from "../../widget/help-link/help-link.directive";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";

@Component({ selector: "toolbar-group", template: "", standalone: true })
class ToolbarGroupStub {
   @Input() actionGroup: any;
   @Input() showTooltips: any;
   @Input() asMenu: any;
}

@Directive({ selector: "[helpLink]", standalone: true })
class HelpLinkStub {
   @Input() helpLink: any;
}

const HTTP_MOCK = { get: vi.fn() };
const AI_DIALOG_MOCK = { openAiAssistantDialog: vi.fn() };
const AI_SERVICE_MOCK = { lastBindingObject: "" };
const CONTEXT_MOCK = { vsWizard: true };

function makeVsObject(objectType: string): VSObjectModel {
   return { absoluteName: "TestObj", objectType } as unknown as VSObjectModel;
}

async function renderComponent(opts: {
   runtimeId?: string;
   vsObject?: VSObjectModel | null;
   isFullEditorVisible?: boolean;
} = {}) {
   const result = await render(ObjectWizardToolBarComponent, {
      inputs: {
         runtimeId: opts.runtimeId ?? "rt-test",
         vsObject: opts.vsObject !== undefined ? opts.vsObject : makeVsObject("VSChart"),
         isFullEditorVisible: opts.isFullEditorVisible ?? false,
      },
      providers: [
         { provide: HttpClient, useValue: HTTP_MOCK },
         { provide: AiAssistantDialogService, useValue: AI_DIALOG_MOCK },
         { provide: AiAssistantService, useValue: AI_SERVICE_MOCK },
         { provide: ContextProvider, useValue: CONTEXT_MOCK },
      ],
      importOverrides: [
         { replace: ToolbarGroup, with: ToolbarGroupStub },
         { replace: HelpLinkDirective, with: HelpLinkStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}

beforeEach(() => {
   HTTP_MOCK.get.mockClear();
   HTTP_MOCK.get.mockReturnValue(of({}));
   AI_DIALOG_MOCK.openAiAssistantDialog.mockClear();
   AI_SERVICE_MOCK.lastBindingObject = "";
});

// ---------------------------------------------------------------------------
// Group 1 — openFullEditor() [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardToolBarComponent — openFullEditor()", () => {
   // 🔁 Regression-sensitive: if runtimeId or assemblyName is missing from the
   // request params, the server opens the wrong binding editor session.
   it("should include runtimeId param in the HTTP request", async () => {
      const { comp } = await renderComponent({ runtimeId: "rt-abc", vsObject: makeVsObject("VSChart") });

      comp.openFullEditor();

      expect(HTTP_MOCK.get).toHaveBeenCalledOnce();
      const params: HttpParams = HTTP_MOCK.get.mock.calls[0][1].params;
      expect(params.get("id")).toBe("rt-abc");
   });

   it("should include assemblyName param when vsObject is set", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ runtimeId: "rt-abc", vsObject });

      comp.openFullEditor();

      const params: HttpParams = HTTP_MOCK.get.mock.calls[0][1].params;
      expect(params.get("assemblyName")).toBe("TestObj");
   });

   it("should omit assemblyName param when vsObject is null", async () => {
      const { comp } = await renderComponent({ runtimeId: "rt-xyz", vsObject: null });

      comp.openFullEditor();

      const params: HttpParams = HTTP_MOCK.get.mock.calls[0][1].params;
      expect(params.get("assemblyName")).toBeNull();
   });

   it("should emit onFullEditor with the vsObject after HTTP success", async () => {
      const vsObject = makeVsObject("VSCrosstab");
      const { comp } = await renderComponent({ vsObject });
      const emitted: VSObjectModel[] = [];
      comp.onFullEditor.subscribe((v: VSObjectModel) => emitted.push(v));

      comp.openFullEditor();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — done() / cancel() [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardToolBarComponent — done() / cancel()", () => {
   it("done() should emit onClose with true", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.done();

      expect(emitted).toEqual([true]);
   });

   it("cancel() should emit onClose with false", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.cancel();

      expect(emitted).toEqual([false]);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getAssemblyTypeIcon() [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardToolBarComponent — getAssemblyTypeIcon()", () => {
   const cases: [string, string][] = [
      ["VSChart", "chart-icon"],
      ["VSCrosstab", "crosstab-icon"],
      ["VSTable", "table-icon"],
      ["VSGauge", "gauge-icon"],
      ["VSText", "text-icon"],
      ["VSSelectionList", "selection-list-icon"],
      ["VSSelectionTree", "selection-tree-icon"],
      ["VSCalendar", "calendar-icon"],
      ["VSRangeSlider", "range-slider-icon"],
   ];

   for(const [objectType, expectedIcon] of cases) {
      it(`should return "${expectedIcon}" for objectType "${objectType}"`, async () => {
         const { comp } = await renderComponent({ vsObject: makeVsObject(objectType) });
         expect(comp.getAssemblyTypeIcon()).toBe(expectedIcon);
      });
   }

   it("should return empty string for unrecognized objectType", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSUnknown") });
      expect(comp.getAssemblyTypeIcon()).toBe("");
   });

   it("should return empty string when vsObject is null", async () => {
      const { comp } = await renderComponent({ vsObject: null });
      expect(comp.getAssemblyTypeIcon()).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — helpLink getter [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardToolBarComponent — helpLink getter", () => {
   it("should return the CreatingaViewsheet help link key", async () => {
      const { comp } = await renderComponent();
      expect(comp.helpLink).toBe("CreatingaViewsheet");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — openAiAssistantDialog() [baseline]
// ---------------------------------------------------------------------------

describe("ObjectWizardToolBarComponent — openAiAssistantDialog()", () => {
   it("should set lastBindingObject to objectType^^absoluteName before opening dialog", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.openAiAssistantDialog();

      expect(AI_SERVICE_MOCK.lastBindingObject).toBe("VSChart^^TestObj");
      expect(AI_DIALOG_MOCK.openAiAssistantDialog).toHaveBeenCalledOnce();
   });

   it("should set lastBindingObject to empty string when vsObject is null", async () => {
      const { comp } = await renderComponent({ vsObject: null });

      comp.openAiAssistantDialog();

      expect(AI_SERVICE_MOCK.lastBindingObject).toBe("");
   });
});
