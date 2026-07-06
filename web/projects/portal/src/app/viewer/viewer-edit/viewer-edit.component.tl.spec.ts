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
 * ViewerEditComponent — single pass (+memory-leak)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit: tableModel branch populates bindingPaneModel;
 *                        chartModel branch populates bindingPaneModel; cubeType guard;
 *                        runtimeId set on client; connect() called
 *   Group 2  [baseline] — ngOnDestroy: routeSubscription is properly unsubscribed
 *   Group 3  [Risk 2] — aiAssistantPermission: true/false directions
 *   Group 4  [Risk 2] — assemblyName: tableModel and chartModel branches
 *   Group 5  [Risk 2] — objectType: tableModel and chartModel branches
 *   Group 6  [Risk 2] — isEmbedded: dot present → true; no dot → false
 *   Group 7  [Risk 2] — wizardChart: all four guard conditions
 *   Group 8  [Risk 2] — displayChartWizard: DEFAULT / CHART_WIZARD / BINDING_EDITOR mode
 *   Group 9  [baseline] — wizardModel: lazy initialization and caching
 *   Group 10 [Risk 2] — openWizardPane: truthy evt → CHART_WIZARD; falsy → no-op
 *   Group 11 [Risk 3] — closeWizardPane: fromWizard+cancel, full-editor path,
 *                        cancel VIEWSHEET_PANE, cancel non-VIEWSHEET_PANE, save=true
 *   Group 12 [baseline] — goToFullEditor: delegates to openBindingPane
 *   Group 13 [baseline] — openBindingPane: updates bindingPaneModel + BINDING_EDITOR
 *   Group 14 [Risk 2] — closeEditor: portal+dashboard, portal+report, !portal navigation
 *
 * ViewsheetClientService is declared in the component's providers:[] and must be
 * overridden via componentProviders in ATL (not providers).
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { render } from "@testing-library/angular";
import { of } from "rxjs";

import { EditorMode, ViewerEditComponent } from "./viewer-edit.component";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { HideNavService } from "../../portal/services/hide-nav.service";
import { VSBindingPane } from "../../vsview/edit/vs-binding-pane.component";
import { VsWizardComponent } from "../../vs-wizard/gui/vs-wizard.component";
import { ViewData } from "../view-data";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VSWizardConstants } from "../../vs-wizard/model/vs-wizard-constants";

// ---------------------------------------------------------------------------
// Stubs for child components declared in ViewerEditComponent.imports[]
// ---------------------------------------------------------------------------

@Component({ selector: "vs-binding-pane", template: "", standalone: true })
class VSBindingPaneStub {
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() objectType: string;
   @Output() onCloseBindingPane = new EventEmitter<void>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
}

@Component({ selector: "vs-wizard", template: "", standalone: true })
class VsWizardStub {
   @Input() model: any;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onFullEditor = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const VS_CLIENT_MOCK: any = {
   runtimeId: null as string | null,
   connect: vi.fn(),
};

const ROUTER_MOCK = {
   navigate: vi.fn().mockResolvedValue(true),
};

const HIDE_NAV_MOCK = {
   appendParameter: vi.fn().mockImplementation((params: any) => params),
};

function makeTableViewData(overrides: Partial<ViewData> = {}): ViewData {
   return {
      runtimeId: "rt-001",
      assetId: "1^128^__NULL__^Sales",
      queryParameters: new Map<string, string[]>(),
      tableModel: {
         absoluteName: "Table1",
         objectType: "VSTable",
         cubeType: null,
         editedByWizard: false,
      } as any,
      chartModel: null,
      ...overrides,
   };
}

function makeChartViewData(overrides: Partial<ViewData> = {}): ViewData {
   return {
      runtimeId: "rt-002",
      assetId: "1^128^__NULL__^Sales",
      queryParameters: new Map<string, string[]>(),
      tableModel: null,
      chartModel: {
         absoluteName: "Chart1",
         objectType: "VSChart",
         cubeType: null,
         editedByWizard: false,
      } as any,
      ...overrides,
   };
}

async function renderComponent(viewData: ViewData) {
   VS_CLIENT_MOCK.runtimeId = null;
   VS_CLIENT_MOCK.connect.mockClear();
   ROUTER_MOCK.navigate.mockClear();
   HIDE_NAV_MOCK.appendParameter.mockClear().mockImplementation((p: any) => p);

   const result = await render(ViewerEditComponent, {
      // ViewsheetClientService is in component providers:[] — must use componentProviders
      componentProviders: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
      ],
      providers: [
         { provide: ActivatedRoute, useValue: { data: of({ viewData }) } },
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: HideNavService, useValue: HIDE_NAV_MOCK },
      ],
      importOverrides: [
         { replace: VSBindingPane, with: VSBindingPaneStub },
         { replace: VsWizardComponent, with: VsWizardStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return result.fixture.componentInstance;
}

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — ngOnInit()", () => {
   // 🔁 Regression-sensitive: bindingPaneModel drives the vs-binding-pane template
   // inputs — wrong runtimeId or assemblyName silently shows stale data.
   it("should populate bindingPaneModel from tableModel on init", async () => {
      const comp = await renderComponent(makeTableViewData());
      expect(comp.bindingPaneModel.runtimeId).toBe("rt-001");
      expect(comp.bindingPaneModel.absoluteName).toBe("Table1");
      expect(comp.bindingPaneModel.objectType).toBe("VSTable");
   });

   it("should populate bindingPaneModel from chartModel on init", async () => {
      const comp = await renderComponent(makeChartViewData());
      expect(comp.bindingPaneModel.runtimeId).toBe("rt-002");
      expect(comp.bindingPaneModel.absoluteName).toBe("Chart1");
      expect(comp.bindingPaneModel.objectType).toBe("VSChart");
   });

   it("should set isCube=true when tableModel has a non-empty cubeType", async () => {
      const comp = await renderComponent(makeTableViewData({
         tableModel: { absoluteName: "T", objectType: "VSTable", cubeType: "XMLA", editedByWizard: false } as any,
      }));
      expect(comp.isCube).toBe(true);
   });

   it("should set isCube=false when tableModel cubeType is null", async () => {
      const comp = await renderComponent(makeTableViewData());
      expect(comp.isCube).toBe(false);
   });

   it("should assign runtimeId on viewsheetClient during init", async () => {
      await renderComponent(makeTableViewData());
      expect(VS_CLIENT_MOCK.runtimeId).toBe("rt-001");
   });

   it("should call viewsheetClient.connect() during init", async () => {
      await renderComponent(makeTableViewData());
      expect(VS_CLIENT_MOCK.connect).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — ngOnDestroy()", () => {
   it("should unsubscribe the route data subscription on destroy", async () => {
      const comp = await renderComponent(makeTableViewData());
      // Bypass: routeSubscription is private with no public getter; direct access required to spy.
      const sub = (comp as any).routeSubscription;
      expect(sub).toBeTruthy();
      const spy = vi.spyOn(sub, "unsubscribe");
      try {
         comp.ngOnDestroy();
         expect(spy).toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3 — aiAssistantPermission
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — aiAssistantPermission", () => {
   it("should return true when viewData.aiAssistantPermission is true", async () => {
      const comp = await renderComponent(makeTableViewData({ aiAssistantPermission: true }));
      expect(comp.aiAssistantPermission).toBe(true);
   });

   it("should return false when viewData.aiAssistantPermission is false", async () => {
      const comp = await renderComponent(makeTableViewData({ aiAssistantPermission: false }));
      expect(comp.aiAssistantPermission).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — assemblyName
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — assemblyName", () => {
   it("should return tableModel.absoluteName when tableModel is present", async () => {
      const comp = await renderComponent(makeTableViewData());
      expect(comp.assemblyName).toBe("Table1");
   });

   it("should return chartModel.absoluteName when only chartModel is present", async () => {
      const comp = await renderComponent(makeChartViewData());
      expect(comp.assemblyName).toBe("Chart1");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — objectType
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — objectType", () => {
   it("should return tableModel.objectType when tableModel is present", async () => {
      const comp = await renderComponent(makeTableViewData());
      expect(comp.objectType).toBe("VSTable");
   });

   it("should return chartModel.objectType when only chartModel is present", async () => {
      const comp = await renderComponent(makeChartViewData());
      expect(comp.objectType).toBe("VSChart");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — isEmbedded
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — isEmbedded", () => {
   it("should return true when assemblyName contains a dot (embedded assembly)", async () => {
      const comp = await renderComponent(makeTableViewData({
         tableModel: { absoluteName: "Tab1.Table1", objectType: "VSTable", cubeType: null, editedByWizard: false } as any,
      }));
      expect(comp.isEmbedded).toBe(true);
   });

   it("should return false when assemblyName has no dot (top-level assembly)", async () => {
      const comp = await renderComponent(makeTableViewData());
      expect(comp.isEmbedded).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — wizardChart
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — wizardChart", () => {
   it("should return true when not metadata, not embedded, and editedByWizard=true", async () => {
      const comp = await renderComponent(makeChartViewData({
         chartModel: { absoluteName: "Chart1", objectType: "VSChart", cubeType: null, editedByWizard: true } as any,
      }));
      expect(comp.wizardChart).toBe(true);
   });

   it("should return false when isMetadata=true", async () => {
      const comp = await renderComponent(makeChartViewData({
         isMetadata: true,
         chartModel: { absoluteName: "Chart1", objectType: "VSChart", cubeType: null, editedByWizard: true } as any,
      }));
      expect(comp.wizardChart).toBe(false);
   });

   it("should return false when assemblyName contains a dot (embedded)", async () => {
      const comp = await renderComponent(makeChartViewData({
         chartModel: { absoluteName: "Tab.Chart1", objectType: "VSChart", cubeType: null, editedByWizard: true } as any,
      }));
      expect(comp.wizardChart).toBe(false);
   });

   it("should return false when editedByWizard=false", async () => {
      const comp = await renderComponent(makeChartViewData());
      expect(comp.wizardChart).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — displayChartWizard
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — displayChartWizard", () => {
   it("should follow wizardChart in DEFAULT mode (true when editedByWizard=true)", async () => {
      const comp = await renderComponent(makeChartViewData({
         chartModel: { absoluteName: "Chart1", objectType: "VSChart", cubeType: null, editedByWizard: true } as any,
      }));
      // editorMode is DEFAULT after init — displayChartWizard delegates to wizardChart in this mode
      expect(comp.displayChartWizard).toBe(true);
   });

   it("should return true when editorMode is CHART_WIZARD regardless of wizardChart", async () => {
      const comp = await renderComponent(makeTableViewData()); // tableModel → wizardChart=false
      comp.editorMode = EditorMode.CHART_WIZARD;
      expect(comp.displayChartWizard).toBe(true);
   });

   it("should return false when editorMode is BINDING_EDITOR", async () => {
      const comp = await renderComponent(makeChartViewData({
         chartModel: { absoluteName: "Chart1", objectType: "VSChart", cubeType: null, editedByWizard: true } as any,
      }));
      comp.editorMode = EditorMode.BINDING_EDITOR;
      expect(comp.displayChartWizard).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — wizardModel
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — wizardModel", () => {
   it("should build wizardModel from viewData on first access", async () => {
      const comp = await renderComponent(makeChartViewData());
      const model = comp.wizardModel;
      expect(model.runtimeId).toBe("rt-002");
      expect(model.viewer).toBe(true);
      expect(model.oinfo.editMode).toBe(VsWizardEditModes.VIEWSHEET_PANE);
      expect(model.oinfo.absoluteName).toBe("Chart1");
   });

   it("should return the same object on repeated accesses (lazy cache)", async () => {
      const comp = await renderComponent(makeChartViewData());
      expect(comp.wizardModel).toBe(comp.wizardModel);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — openWizardPane
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — openWizardPane()", () => {
   it("should assign wizardModel and switch to CHART_WIZARD when evt is truthy", async () => {
      const comp = await renderComponent(makeChartViewData());
      const customModel: any = { runtimeId: "rt-wizard", viewer: true, oinfo: {} };
      comp.openWizardPane(customModel);
      expect(comp.editorMode).toBe(EditorMode.CHART_WIZARD);
      expect(comp.wizardModel).toBe(customModel);
   });

   it("should be a no-op when evt is falsy", async () => {
      const comp = await renderComponent(makeChartViewData());
      comp.openWizardPane(null as any);
      expect(comp.editorMode).toBe(EditorMode.DEFAULT);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — closeWizardPane
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — closeWizardPane()", () => {
   // 🔁 Regression-sensitive: if fromWizard is true and save=false the temp assembly
   // is already destroyed by the wizard — navigating to full editor would throw because
   // the assembly no longer exists. Must close the editor instead.
   it("should close editor when cancelling out of a temp-assembly wizard", async () => {
      const comp = await renderComponent(makeChartViewData());
      // Set absoluteName to one starting with VSWizardConstants.TEMP_ASSEMBLY ("Recommender")
      comp.bindingPaneModel = {
         runtimeId: "rt-002",
         objectType: "VSChart",
         absoluteName: VSWizardConstants.TEMP_ASSEMBLY_PREFIX + "Chart",
         wizardOriginalInfo: null,
      };
      comp.closeWizardPane({
         save: false,
         model: { oinfo: { editMode: VsWizardEditModes.VIEWSHEET_PANE } } as any,
      });
      expect(ROUTER_MOCK.navigate).toHaveBeenCalled();
   });

   it("should delegate to goToFullEditor when evt.model.editMode is FULL_EDITOR", async () => {
      const comp = await renderComponent(makeChartViewData());
      // bindingPaneModel must NOT start with "Recommender" or the fromWizard branch fires first
      comp.bindingPaneModel = {
         runtimeId: "rt-002",
         objectType: "VSChart",
         absoluteName: "Chart1",
         wizardOriginalInfo: null,
      };
      comp.closeWizardPane({
         save: false,
         model: {
            // editMode at the top level of VsWizardModel is what closeWizardPane checks
            editMode: VsWizardEditModes.FULL_EDITOR,
            runtimeId: "rt-002",
            objectModel: { objectType: "VSChart", absoluteName: "ChartA" },
            oinfo: { runtimeId: "rt-002", editMode: VsWizardEditModes.FULL_EDITOR },
         } as any,
      });
      expect(comp.editorMode).toBe(EditorMode.BINDING_EDITOR);
      expect(comp.bindingPaneModel.absoluteName).toBe("ChartA");
   });

   it("should close editor on cancel when oinfo.editMode is VIEWSHEET_PANE", async () => {
      const comp = await renderComponent(makeChartViewData());
      comp.bindingPaneModel = {
         runtimeId: "rt",
         objectType: "VSChart",
         absoluteName: "Chart1",
         wizardOriginalInfo: null,
      };
      comp.closeWizardPane({
         save: false,
         model: { oinfo: { editMode: VsWizardEditModes.VIEWSHEET_PANE } } as any,
      });
      expect(ROUTER_MOCK.navigate).toHaveBeenCalled();
   });

   it("should open binding pane on cancel when oinfo.editMode is WIZARD_DASHBOARD", async () => {
      const comp = await renderComponent(makeChartViewData());
      comp.bindingPaneModel = {
         runtimeId: "rt",
         objectType: "VSChart",
         absoluteName: "Chart1",
         wizardOriginalInfo: null,
      };
      const oinfo: any = {
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
         objectType: "VSChart",
         absoluteName: "Chart1",
      };
      comp.closeWizardPane({
         save: false,
         model: { runtimeId: "rt", oinfo } as any,
      });
      expect(comp.editorMode).toBe(EditorMode.BINDING_EDITOR);
   });

   it("should close editor when save=true", async () => {
      const comp = await renderComponent(makeChartViewData());
      comp.bindingPaneModel = {
         runtimeId: "rt",
         objectType: "VSChart",
         absoluteName: "Chart1",
         wizardOriginalInfo: null,
      };
      comp.closeWizardPane({
         save: true,
         model: { oinfo: { editMode: VsWizardEditModes.VIEWSHEET_PANE } } as any,
      });
      expect(ROUTER_MOCK.navigate).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 12 — goToFullEditor
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — goToFullEditor()", () => {
   it("should open binding pane using objectModel data from the close event", async () => {
      const comp = await renderComponent(makeChartViewData());
      comp.goToFullEditor({
         save: false,
         model: {
            runtimeId: "rt-full",
            objectModel: { objectType: "VSChart", absoluteName: "ChartA" },
            oinfo: { runtimeId: "rt-full", editMode: VsWizardEditModes.FULL_EDITOR },
         } as any,
      });
      expect(comp.bindingPaneModel.absoluteName).toBe("ChartA");
      expect(comp.bindingPaneModel.objectType).toBe("VSChart");
      expect(comp.editorMode).toBe(EditorMode.BINDING_EDITOR);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — openBindingPane
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — openBindingPane()", () => {
   it("should update bindingPaneModel and set BINDING_EDITOR mode", async () => {
      const comp = await renderComponent(makeChartViewData());
      const oinfo: any = { runtimeId: "rt-new", editMode: VsWizardEditModes.FULL_EDITOR };
      comp.openBindingPane("rt-new", "VSTable", "Table2", oinfo);
      expect(comp.bindingPaneModel).toEqual({
         runtimeId: "rt-new",
         objectType: "VSTable",
         absoluteName: "Table2",
         wizardOriginalInfo: oinfo,
      });
      expect(comp.editorMode).toBe(EditorMode.BINDING_EDITOR);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — closeEditor
// ---------------------------------------------------------------------------

describe("ViewerEditComponent — closeEditor()", () => {
   // 🔁 Regression-sensitive: wrong navigation path prevents returning to the dashboard.
   it("should navigate to portal dashboard route when portal=true and dashboard=true", async () => {
      const comp = await renderComponent(makeChartViewData({
         portal: true,
         dashboard: true,
         assetId: "1^128^__NULL__^SalesDash",
         fullScreen: false,
         hasBaseEntry: false,
      }));
      comp.closeEditor();
      const [commands] = ROUTER_MOCK.navigate.mock.calls[0];
      expect(commands[0]).toContain("/portal/tab/dashboard/vs/view/");
   });

   it("should navigate to portal report route when portal=true and dashboard=false", async () => {
      const comp = await renderComponent(makeChartViewData({
         portal: true,
         dashboard: false,
         assetId: "1^128^__NULL__^SalesReport",
         fullScreen: false,
         hasBaseEntry: false,
      }));
      comp.closeEditor();
      const [commands] = ROUTER_MOCK.navigate.mock.calls[0];
      expect(commands[0]).toContain("/portal/tab/report/vs/view/");
   });

   it("should navigate to /viewer/view/ route when not in portal", async () => {
      const comp = await renderComponent(makeChartViewData({
         portal: false,
         assetId: "1^128^__NULL__^MyVS",
         fullScreen: false,
         hasBaseEntry: false,
      }));
      comp.closeEditor();
      const [commands] = ROUTER_MOCK.navigate.mock.calls[0];
      expect(commands[0]).toContain("/viewer/view/");
   });
});
