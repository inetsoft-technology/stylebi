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
 * ViewsheetOptionsPane — single pass (+subscribe leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — notSelected + changeSource: maxRows disabled/enabled (Bug #17036, #17303);
 *     when no dataSource the maxRows control is disabled; when a dataSource is set it is enabled
 *   Group 2 [Risk 3] — clear(): nullifies dataSource (Bug #10157); clears
 *     model.selectDataSourceDialogModel.dataSource to null
 *   Group 3 [Risk 3] — convertToWorksheet: confirm guard before HTTP call; model updated on
 *     success; MV warning shown when hasMvs=true; subscribe leak (it.fails)
 *   Group 4 [Risk 2] — ngOnInit: 4 form controls added (maxRows/alias/touchInterval/snapGrid);
 *     alias/description override applied when dataSource has alias (Bug #20438)
 *   Group 5 [Risk 2] — changeServerSideUpdate + isServerSideUpdate: touchInterval enable/disable
 *     follows model.serverSideUpdate
 *   Group 6 [Risk 2] — isLogicModelDataSource: returns true for LOGIC_MODEL type; false for
 *     other types and null dataSource
 *   Group 7 [Risk 2] — showViewsheetParametersDialog: model updated on commit, no-op on dismiss
 *   Group 8 [Risk 2] — showSelectDataSourceDialog: model updated + changeSource called on commit
 *   Group 9 [Risk 1] — snapToGrid: reads localStorage "snap-to-grid" key
 *
 * Confirmed bugs (it.fails):
 *   Bug — doConvert() subscribe leak (Group 3): doConvert() calls modelService.getModel().pipe().subscribe()
 *     without storing the Subscription reference. If the component is destroyed while the HTTP call is
 *     in-flight the callback still fires and mutates this.model. Fix: store the subscription in a field
 *     and unsubscribe in ngOnDestroy.
 *
 * Out of scope:
 *   showViewsheetParametersDialog / showSelectDataSourceDialog full modal flow — depend on
 *     ViewChild TemplateRef unavailable in unit tests; modal plumbing is integration-level.
 *   initForm validators — FormValidators are library-level; validator correctness is their own test.
 *   defaultOrgAsset — passed through to child template; no component logic branch on it.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";
import { HttpClient } from "@angular/common/http";
import { ViewsheetOptionsPane } from "./viewsheet-options-pane.component";
import { ViewsheetOptionsPaneModel } from "../../data/vs/viewsheet-options-pane-model";
import { ModelService } from "../../../widget/services/model.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { AssetType } from "../../../../../../shared/data/asset-type";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const MODEL_SERVICE_MOCK = { getModel: vi.fn() };
const MODAL_SERVICE_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<ViewsheetOptionsPaneModel> = {}): ViewsheetOptionsPaneModel {
   return {
      viewsheetParametersDialogModel: { enabledParameters: [], disabledParameters: [] },
      selectDataSourceDialogModel: { title: "Select Data Source", dataSource: null },
      useMetaData: false,
      promptForParams: false,
      selectionAssociation: true,
      createMv: false,
      onDemandMvEnabled: false,
      maxRows: 0,
      snapGrid: 10,
      alias: "",
      desc: "",
      serverSideUpdate: false,
      touchInterval: 60,
      listOnPortalTree: true,
      worksheet: false,
      ...overrides,
   };
}

async function renderComponent(modelOverrides: Partial<ViewsheetOptionsPaneModel> = {}) {
   const form = new UntypedFormGroup({});
   const model = createModel(modelOverrides);
   const { fixture } = await render(ViewsheetOptionsPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: HttpClient, useValue: { get: vi.fn(), post: vi.fn() } },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
      ],
      componentInputs: {
         model,
         form,
         runtimeId: "vs-test-1",
         defaultOrgAsset: false,
      },
   });
   const comp = fixture.componentInstance as ViewsheetOptionsPane;
   return { comp, fixture, form, model };
}

beforeEach(() => {
   MODEL_SERVICE_MOCK.getModel.mockReset();
   MODAL_SERVICE_MOCK.open.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: notSelected + changeSource [Risk 3] — Bug #17036, #17303
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — notSelected + changeSource", () => {
   // 🔁 Regression-sensitive (Bug #17036): maxRows must be disabled when no dataSource is
   //    selected; enabling it without a source causes spurious validation errors.
   it("should disable maxRows when no dataSource is set (notSelected=true)", async () => {
      const { form } = await renderComponent();
      expect(form.controls["maxRows"].disabled).toBe(true);
   });

   // 🔁 Regression-sensitive (Bug #17303): when a dataSource is present maxRows must be enabled
   //    so the user can configure the row limit.
   it("should enable maxRows when a dataSource is present", async () => {
      const { form } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.DATA_SOURCE, description: "ds/myds", alias: null } as any,
         },
      } as any);
      expect(form.controls["maxRows"].enabled).toBe(true);
   });

   it("should disable maxRows via changeSource when dataSource is cleared", async () => {
      const { comp, form } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.DATA_SOURCE, description: "ds/myds", alias: null } as any,
         },
      } as any);
      comp.model.selectDataSourceDialogModel.dataSource = null;
      comp.changeSource();
      expect(form.controls["maxRows"].disabled).toBe(true);
   });

   it("should enable maxRows via changeSource when a dataSource is assigned", async () => {
      const { comp, form } = await renderComponent();
      comp.model.selectDataSourceDialogModel.dataSource = {
         type: AssetType.DATA_SOURCE, description: "ds/myds", alias: null,
      } as any;
      comp.model.worksheet = false;
      comp.changeSource();
      expect(form.controls["maxRows"].enabled).toBe(true);
   });

   it("should disable maxRows via changeSource when worksheet=true even with a dataSource", async () => {
      const { comp, form } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.DATA_SOURCE, description: "ds/ws", alias: null } as any,
         },
         worksheet: true,
      } as any);
      comp.changeSource();
      expect(form.controls["maxRows"].disabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: clear() [Risk 3] — Bug #10157
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — clear", () => {
   // 🔁 Regression-sensitive (Bug #10157): clear() must null out the data source reference so
   //    downstream validation does not reference stale server state.
   it("should set model.selectDataSourceDialogModel.dataSource to null", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.DATA_SOURCE, description: "ds/myds", alias: null } as any,
         },
      } as any);

      comp.clear();

      expect(comp.model.selectDataSourceDialogModel.dataSource).toBeNull();
   });

   it("should be safe to call clear() when dataSource is already null", async () => {
      const { comp } = await renderComponent();
      expect(() => comp.clear()).not.toThrow();
      expect(comp.model.selectDataSourceDialogModel.dataSource).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: convertToWorksheet [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — convertToWorksheet", () => {
   it("should call ComponentTool.showConfirmDialog when convertToWorksheet is invoked", async () => {
      const { comp } = await renderComponent();
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      comp.convertToWorksheet();

      expect(confirmSpy).toHaveBeenCalled();
   });

   it("should NOT call modelService.getModel when user declines the confirm dialog", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      comp.convertToWorksheet();
      await Promise.resolve();

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
   });

   it("should update model when user confirms and getModel returns a result without MVs", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const newSrc = { title: "WS", dataSource: { type: "WORKSHEET", description: "ws/1", alias: null } as any };
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of({ model: newSrc, hasMvs: false }));

      comp.convertToWorksheet();
      await Promise.resolve(); // flush confirm microtask
      await Promise.resolve(); // flush observable subscribe

      expect(comp.model.selectDataSourceDialogModel).toBe(newSrc);
      expect(comp.dataSource).toBe(newSrc.dataSource);
   });

   it("should show MV warning dialog when hasMvs=true before updating model", async () => {
      const { comp } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const newSrc = { title: "WS", dataSource: { type: "WORKSHEET", description: "ws/1", alias: null } as any };
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of({ model: newSrc, hasMvs: true }));
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);

      comp.convertToWorksheet();
      await Promise.resolve();
      await new Promise(r => setTimeout(r, 0)); // flush Promise chaining from showMessageDialog

      expect(msgSpy).toHaveBeenCalled();
   });

   // Bug: doConvert() subscribes to modelService.getModel().pipe().subscribe() without storing
   // the Subscription reference. After the component is destroyed the callback still fires and
   // mutates this.model. Fix: store the subscription in a field and unsubscribe in ngOnDestroy.
   it.fails("should not mutate model after component is destroyed (subscribe leak)", async () => {
      const { comp, fixture } = await renderComponent();
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      const subject = new Subject<{ model: any, hasMvs: boolean }>();
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(subject.asObservable());

      comp.convertToWorksheet();
      await Promise.resolve(); // flush confirm

      const originalSrc = comp.model.selectDataSourceDialogModel;

      fixture.destroy(); // no ngOnDestroy → subscription NOT cancelled

      const newSrc = { title: "WS", dataSource: { type: "WORKSHEET", description: "ws/new", alias: null } as any };
      subject.next({ model: newSrc, hasMvs: false });

      // With fix: model unchanged after destroy
      // Currently: FAILS — callback runs and mutates model
      expect(comp.model.selectDataSourceDialogModel).toBe(originalSrc);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit — form controls + alias/description override [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — ngOnInit", () => {
   it("should add maxRows form control on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["maxRows"]).toBeDefined();
   });

   it("should add alias form control on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["alias"]).toBeDefined();
   });

   it("should add touchInterval form control on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["touchInterval"]).toBeDefined();
   });

   it("should add snapGrid form control on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["snapGrid"]).toBeDefined();
   });

   // 🔁 Regression-sensitive (Bug #20438): when a dataSource has both description and alias,
   //    the last path segment of description must be replaced with the alias so the UI shows
   //    the user-friendly alias name instead of the technical path segment.
   it("should override the last segment of dataSource.description with alias (Bug #20438)", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: {
               type: AssetType.DATA_SOURCE,
               description: "folder/originalName",
               alias: "friendlyAlias",
            } as any,
         },
      } as any);

      expect(comp.dataSource.description).toBe("folder/friendlyAlias");
   });

   it("should NOT override description when alias is falsy", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: {
               type: AssetType.DATA_SOURCE,
               description: "folder/originalName",
               alias: null,
            } as any,
         },
      } as any);

      expect(comp.dataSource.description).toBe("folder/originalName");
   });

   it("should NOT override description when description has no slash", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: {
               type: AssetType.DATA_SOURCE,
               description: "noSlash",
               alias: "friendlyAlias",
            } as any,
         },
      } as any);

      // index == -1 so no replacement
      expect(comp.dataSource.description).toBe("noSlash");
   });
});

// ---------------------------------------------------------------------------
// Group 5: changeServerSideUpdate + isServerSideUpdate [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — changeServerSideUpdate + isServerSideUpdate", () => {
   it("should disable touchInterval when serverSideUpdate is false initially", async () => {
      const { form } = await renderComponent({ serverSideUpdate: false });
      expect(form.controls["touchInterval"].disabled).toBe(true);
   });

   it("should enable touchInterval when serverSideUpdate is true initially", async () => {
      const { form } = await renderComponent({ serverSideUpdate: true });
      expect(form.controls["touchInterval"].enabled).toBe(true);
   });

   it("should enable touchInterval after changeServerSideUpdate when serverSideUpdate becomes true", async () => {
      const { comp, form } = await renderComponent({ serverSideUpdate: false });
      comp.model.serverSideUpdate = true;
      comp.changeServerSideUpdate();
      expect(form.controls["touchInterval"].enabled).toBe(true);
   });

   it("should disable touchInterval after changeServerSideUpdate when serverSideUpdate becomes false", async () => {
      const { comp, form } = await renderComponent({ serverSideUpdate: true });
      comp.model.serverSideUpdate = false;
      comp.changeServerSideUpdate();
      expect(form.controls["touchInterval"].disabled).toBe(true);
   });

   it("should return true from isServerSideUpdate when model.serverSideUpdate is true", async () => {
      const { comp } = await renderComponent({ serverSideUpdate: true });
      expect(comp.isServerSideUpdate()).toBe(true);
   });

   it("should return false from isServerSideUpdate when model.serverSideUpdate is false", async () => {
      const { comp } = await renderComponent({ serverSideUpdate: false });
      expect(comp.isServerSideUpdate()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isLogicModelDataSource [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — isLogicModelDataSource", () => {
   it("should return false when dataSource is null", async () => {
      const { comp } = await renderComponent();
      expect(comp.isLogicModelDataSource()).toBe(false);
   });

   it("should return true when dataSource type is LOGIC_MODEL", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.LOGIC_MODEL, description: "lm/model", alias: null } as any,
         },
      } as any);
      expect(comp.isLogicModelDataSource()).toBe(true);
   });

   it("should return false when dataSource type is not LOGIC_MODEL", async () => {
      const { comp } = await renderComponent({
         selectDataSourceDialogModel: {
            title: "",
            dataSource: { type: AssetType.DATA_SOURCE, description: "ds/myds", alias: null } as any,
         },
      } as any);
      expect(comp.isLogicModelDataSource()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: showViewsheetParametersDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — showViewsheetParametersDialog", () => {
   it("should update model.viewsheetParametersDialogModel when modal result resolves", async () => {
      const { comp } = await renderComponent();
      const newParams = { enabledParameters: ["p1"], disabledParameters: [] };
      const fakeResult = Promise.resolve(newParams);
      MODAL_SERVICE_MOCK.open.mockReturnValue({ result: fakeResult });

      comp.showViewsheetParametersDialog();
      await fakeResult;

      expect(comp.model.viewsheetParametersDialogModel).toBe(newParams);
   });

   it("should NOT update model when modal is dismissed (reject)", async () => {
      const { comp } = await renderComponent();
      const originalParams = comp.model.viewsheetParametersDialogModel;
      const rejectResult = Promise.reject("dismissed");
      MODAL_SERVICE_MOCK.open.mockReturnValue({ result: rejectResult });

      comp.showViewsheetParametersDialog();
      await rejectResult.catch(() => {}); // suppress unhandled rejection

      expect(comp.model.viewsheetParametersDialogModel).toBe(originalParams);
   });
});

// ---------------------------------------------------------------------------
// Group 8: showSelectDataSourceDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — showSelectDataSourceDialog", () => {
   it("should update model.selectDataSourceDialogModel when modal result resolves", async () => {
      const { comp } = await renderComponent();
      const newSrc = { title: "New DS", dataSource: null };
      const fakeResult = Promise.resolve(newSrc);
      MODAL_SERVICE_MOCK.open.mockReturnValue({ result: fakeResult });

      comp.showSelectDataSourceDialog();
      await fakeResult;

      expect(comp.model.selectDataSourceDialogModel).toBe(newSrc);
   });

   it("should call changeSource after the modal resolves", async () => {
      const { comp } = await renderComponent();
      const newSrc = { title: "New DS", dataSource: null };
      const fakeResult = Promise.resolve(newSrc);
      MODAL_SERVICE_MOCK.open.mockReturnValue({ result: fakeResult });
      const changeSpy = vi.spyOn(comp, "changeSource");

      comp.showSelectDataSourceDialog();
      await fakeResult;

      expect(changeSpy).toHaveBeenCalled();
   });

   it("should NOT update model when modal is dismissed (reject)", async () => {
      const { comp } = await renderComponent();
      const originalSrc = comp.model.selectDataSourceDialogModel;
      const rejectResult = Promise.reject("dismissed");
      MODAL_SERVICE_MOCK.open.mockReturnValue({ result: rejectResult });

      comp.showSelectDataSourceDialog();
      await rejectResult.catch(() => {});

      expect(comp.model.selectDataSourceDialogModel).toBe(originalSrc);
   });
});

// ---------------------------------------------------------------------------
// Group 9: snapToGrid [Risk 1]
// ---------------------------------------------------------------------------

describe("ViewsheetOptionsPane — snapToGrid", () => {
   it("should return true when localStorage does not contain 'snap-to-grid'", async () => {
      vi.spyOn(LocalStorage, "getItem").mockReturnValue(null);
      const { comp } = await renderComponent();
      expect(comp.snapToGrid()).toBe(true);
   });

   it("should return false when localStorage snap-to-grid is 'false'", async () => {
      vi.spyOn(LocalStorage, "getItem").mockReturnValue("false");
      const { comp } = await renderComponent();
      expect(comp.snapToGrid()).toBe(false);
   });

   it("should return true when localStorage snap-to-grid is 'true'", async () => {
      vi.spyOn(LocalStorage, "getItem").mockReturnValue("true");
      const { comp } = await renderComponent();
      expect(comp.snapToGrid()).toBe(true);
   });
});
