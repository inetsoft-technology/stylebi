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
 * ViewsheetPropertyDialog — single pass (+竞态+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — testScript(): clean result (no timeout, no body) → emit model; result with
 *     body → show confirm dialog → emit on "yes", no-op on "no"; timeout result → show confirm
 *     → emit on "yes", call testScript(false) on "no"
 *   Group 2 [Risk 3] — handleError(): TimeoutError → returns of({timeout: true}); other errors
 *     → returns of(error) (passthrough)
 *   Group 3 [Risk 2] — ngOnInit: form created with viewsheetOptionsPaneForm and screensPaneForm
 *     subgroups; formValid returns true when model + form are present and valid
 *   Group 4 [Risk 2] — cancelChanges(): emits onCancel("cancel")
 *   Group 5 [Risk 2] — saveChanges(): calls testScript(true)
 *   Group 6 [Risk 1] — isDefaultOrgAsset(): returns false when viewsheet is absent; returns false
 *     when viewsheet.id is absent
 *
 * Confirmed bugs (it.fails):
 *   Bug — constructor subscribe leak: appInfoService.getCurrentOrgInfo().subscribe() stores no
 *     Subscription. If the component is destroyed while the service observable is still live the
 *     callback runs and sets this.orgInfo on a destroyed component. Fix: store the subscription
 *     and unsubscribe in ngOnDestroy.
 *
 * Out of scope:
 *   Full modal rendering — tabbed-dialog is complex multi-component; DOM assertions are
 *     integration-level.
 *   isDefaultOrgAsset org-mismatch path — requires createAssetEntry to parse a real asset ID and
 *     match organization; integration-level.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";
import { ViewsheetPropertyDialog } from "./viewsheet-property-dialog.component";
import { ViewsheetPropertyDialogModel } from "../../data/vs/viewsheet-property-dialog-model";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { ComponentTool } from "../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const HTTP_MOCK = { post: vi.fn() };
const MODAL_SERVICE_MOCK = { open: vi.fn() };
const APP_INFO_MOCK = { getCurrentOrgInfo: vi.fn(() => of({ key: "org1", value: "Org 1" })) };

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<ViewsheetPropertyDialogModel> = {}): ViewsheetPropertyDialogModel {
   return {
      vsOptionsPane: {
         viewsheetParametersDialogModel: { enabledParameters: [], disabledParameters: [] },
         selectDataSourceDialogModel: { title: "", dataSource: null },
         useMetaData: false, promptForParams: false, selectionAssociation: true,
         createMv: false, onDemandMvEnabled: false, maxRows: 0, snapGrid: 10,
         alias: "", desc: "", serverSideUpdate: false, touchInterval: 60,
         listOnPortalTree: true, worksheet: false,
      } as any,
      filtersPane: { filters: [], sharedFilters: [] },
      screensPane: { layoutInfos: [], newFormat: false } as any,
      vsScriptPane: { scriptDefinitions: null, expression: "", onLoad: "", onRefresh: "" } as any,
      onDemandMVEnabled: false,
      width: 800,
      height: 600,
      preview: false,
      id: "vs-test-1",
      ...overrides,
   };
}

async function renderComponent(modelOverrides: Partial<ViewsheetPropertyDialogModel> = {}) {
   const model = createModel(modelOverrides);
   const { fixture } = await render(ViewsheetPropertyDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: HttpClient, useValue: HTTP_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         { provide: AppInfoService, useValue: APP_INFO_MOCK },
      ],
      componentInputs: {
         model,
         viewsheet: { id: "vs-test-1", preview: false } as any,
         isPrintLayout: false,
      },
   });
   const comp = fixture.componentInstance as ViewsheetPropertyDialog;
   return { comp, fixture, model };
}

beforeEach(() => {
   HTTP_MOCK.post.mockReset();
   MODAL_SERVICE_MOCK.open.mockReset();
   APP_INFO_MOCK.getCurrentOrgInfo.mockReturnValue(of({ key: "org1", value: "Org 1" }));
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: testScript() [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — testScript", () => {
   // 🔁 Regression-sensitive: clean result (null timeout, null body) must emit the model so the
   //    parent dialog can commit.
   it("should emit model when result has no timeout and no body", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of(null));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should emit model when result is an empty object (no body, no timeout)", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({}));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should show confirm dialog when result has body, emit on 'yes'", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ body: "script error details" }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should NOT emit when result has body and user clicks 'no'", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ body: "script error details" }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should show confirm dialog when result has timeout=true, emit on 'yes'", async () => {
      const { comp, model } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ timeout: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should call testScript(false) when timeout and user clicks 'no'", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of({ timeout: true }));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");
      const testScriptSpy = vi.spyOn(comp, "testScript");

      comp.testScript();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      // Called once with default arg (true), then once with false after user clicks 'no'
      expect(testScriptSpy).toHaveBeenCalledWith(false);
   });

   // Bug: httpClient.post().pipe().subscribe() stores no Subscription reference. After component
   // destruction the callback still runs and emits onCommit. Fix: store the subscription and
   // unsubscribe in ngOnDestroy.
   it.fails("should not emit after component is destroyed (subscribe leak)", async () => {
      const { comp, fixture } = await renderComponent();
      const subject = new Subject<any>();
      HTTP_MOCK.post.mockReturnValue(subject.asObservable());
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.testScript();
      fixture.destroy();

      subject.next(null);
      await Promise.resolve();

      // With fix: emit should NOT have been called after destroy
      // Currently: FAILS — callback still runs
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: handleError [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — handleError", () => {
   it("should return observable of {timeout:true} for TimeoutError", async () => {
      const { comp } = await renderComponent();
      const timeoutError = { name: "TimeoutError" };
      let result: any;
      comp.handleError(timeoutError).subscribe(v => (result = v));
      expect(result).toEqual({ timeout: true });
   });

   it("should return observable of the error itself for non-TimeoutError", async () => {
      const { comp } = await renderComponent();
      const networkError = { name: "HttpErrorResponse", status: 500 };
      let result: any;
      comp.handleError(networkError).subscribe(v => (result = v));
      expect(result).toBe(networkError);
   });

   it("should return observable of the error for an error with no name", async () => {
      const { comp } = await renderComponent();
      const unknownError = { status: 400 };
      let result: any;
      comp.handleError(unknownError).subscribe(v => (result = v));
      expect(result).toBe(unknownError);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit — form creation + formValid [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — ngOnInit + formValid", () => {
   it("should create viewsheetOptionsPaneForm subgroup on init", async () => {
      const { comp } = await renderComponent();
      expect(comp["form"].controls["viewsheetOptionsPaneForm"]).toBeDefined();
   });

   it("should create screensPaneForm subgroup on init", async () => {
      const { comp } = await renderComponent();
      expect(comp["form"].controls["screensPaneForm"]).toBeDefined();
   });

   it("formValid should return true when model and form are present and form is valid", async () => {
      const { comp } = await renderComponent();
      expect(comp.formValid()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancelChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — cancelChanges", () => {
   it("should emit onCancel with 'cancel' string", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancelChanges();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});

// ---------------------------------------------------------------------------
// Group 5: saveChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — saveChanges", () => {
   it("should call testScript(true) when saveChanges is invoked", async () => {
      const { comp } = await renderComponent();
      HTTP_MOCK.post.mockReturnValue(of(null));
      const testScriptSpy = vi.spyOn(comp, "testScript");

      comp.saveChanges();

      expect(testScriptSpy).toHaveBeenCalledWith(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isDefaultOrgAsset [Risk 1]
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — isDefaultOrgAsset", () => {
   it("should return false when viewsheet input is null", async () => {
      const { comp } = await renderComponent();
      comp["viewsheet"] = null;
      expect(comp.isDefaultOrgAsset()).toBe(false);
   });

   it("should return false when viewsheet.id is falsy", async () => {
      const { comp } = await renderComponent();
      comp["viewsheet"] = { id: null } as any;
      expect(comp.isDefaultOrgAsset()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Constructor subscribe leak (it.fails — tracks unfixed bug)
// ---------------------------------------------------------------------------

describe("ViewsheetPropertyDialog — constructor subscribe leak", () => {
   // Bug: constructor calls appInfoService.getCurrentOrgInfo().subscribe() without storing the
   // Subscription. After component destruction the callback still runs and sets this.orgInfo.
   // Fix: store the subscription and unsubscribe in ngOnDestroy.
   it.fails("should not set orgInfo after component is destroyed (constructor subscribe leak)", async () => {
      const subject = new Subject<{ key: string; value: string }>();
      APP_INFO_MOCK.getCurrentOrgInfo.mockReturnValue(subject.asObservable());
      const { comp, fixture } = await renderComponent();

      fixture.destroy();
      subject.next({ key: "org2", value: "Org 2" });
      await Promise.resolve();

      // With fix: orgInfo should remain null (subject emitted after destroy)
      // Currently: FAILS — callback still runs and sets orgInfo
      expect(comp["orgInfo"]).toBeNull();
   });
});
