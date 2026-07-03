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
 * VSViewsheet — single pass
 *
 * Risk-first coverage:
 *   Group 2  [Risk 3] — ngOnChanges: visible/vsInfo0/href computation, selectedAssemblies reset
 *   Group 5  [Risk 3] — processAddVSObjectCommand: add/update/sort/registration/emit
 *   Group 7  [Risk 3] — processRefreshVSObjectCommand / applyRefreshObject: position fix, maxMode, sync
 *   Group 15 [Risk 3] — selectViewsheet: modifier-key guard, viewer toggle, emit contract
 *   Group 4  [Risk 2] — getZIndex(): dataTip/popComponent source branches
 *   Group 6  [Risk 2] — processRefreshEmbeddedVSCommand: prune children not in command
 *   Group 8  [Risk 2] — processRemoveVSObjectCommand: splice matching entry
 *   Group 9  [Risk 2] — processUpdateZIndexesCommand: per-name zIndex + action recreation
 *   Group 10 [Risk 2] — openViewsheet(): emit guarded by href
 *   Group 11 [Risk 2] — iconHeight getter: viewer/top boundary math
 *   Group 12 [Risk 2] — showContextMenu(): dropdown open + field wiring
 *   Group 13 [Risk 2] — getVariableValues (variableValuesFunction): external + own-object variables
 *   Group 14 [Risk 2] — showIconContainer / isChildDropdownExpanded: bottom-tab overlap math
 *   Group 16 [Risk 2] — removeSelectedAnnotations(): event creation guard
 *   Group 1  [Risk 1] — constructor: preview/composer captured from ContextProvider
 *   Group 3  [Risk 1] — ngOnDestroy: base subscription cleanup
 *   Group 17 [Risk 1] — onMaxModeChanged(): field set + emit
 *   Group 18 [Risk 1] — navigate(): SPACE key delegates to openViewsheet
 *   Group 19 [Risk 1] — getEmbeddedVSBounds(): delegates to Rectangle.fromClientRect
 *
 * Confirmed bugs (it.fails):
 *   Bug — ngOnChanges null hyperlinkModel crash (Group 2): HyperlinkViewModel.fromHyperlinkModel()
 *     dereferences `hyperlink.disablePrompting` with no null guard. VSViewsheetModel.hyperlinkModel
 *     is typed as required, but TestUtils.createMockVSViewsheetModel() — the shared fixture factory
 *     used across the codebase — defaults it to `null`. Any consumer that forgets to override
 *     hyperlinkModel and triggers ngOnChanges while vsInfo is bound throws a TypeError.
 *
 * Out of scope this pass:
 *   getAssemblyName() / trackByIdx() / resized() — inherited from AbstractVSObject, not overridden.
 *   clearNavSelection() — empty no-op override required by the NavigationComponent abstract
 *     contract; zero observable behavior.
 *   applyRefreshObject() private helper — no independent entry point; exercised via
 *     processAddVSObjectCommand (Group 5) and processRefreshVSObjectCommand (Group 7).
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { VSViewsheet } from "./vs-viewsheet.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../action/assembly-action-factory.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { SelectionMobileService } from "../selection/services/selection-mobile.service";
import { DateTipHelper } from "../data-tip/date-tip-helper";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { RemoveAnnotationEvent } from "../../event/annotation/remove-annotation-event";
import { AddVsObjectMode } from "../../command/add-vs-object-command";
import { LinkType } from "../../../common/data/hyperlink-model";
import { TestUtils } from "../../../common/test/test-utils";
import { VSViewsheetModel } from "../../model/vs-viewsheet-model";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { VSTabModel } from "../../model/vs-tab-model";
import { NavigationKeys } from "../navigation-keys";

const commandsSubject = new Subject<any>();
const clientServiceMock = {
   sendEvent: vi.fn(),
   runtimeId: "vs-test",
   commands: commandsSubject,
};
const actionFactoryMock = {
   createActions: vi.fn().mockImplementation((model: VSObjectModel) => makeActions(model)),
};
const dropdownServiceMock = {
   open: vi.fn().mockReturnValue({ componentInstance: {} }),
};
const dataTipServiceMock = {
   hasDataTipShowing: vi.fn().mockReturnValue(false),
   dataTipName: null as string,
   clearDataTips: vi.fn(),
   registerDataTip: vi.fn(),
   registerDataTipVisible: vi.fn(),
};
const popComponentServiceMock = {
   hasPopUpComponentShowing: vi.fn().mockReturnValue(false),
   getPopComponent: vi.fn().mockReturnValue(null),
   registerPopComponent: vi.fn(),
   registerPopComponentVisible: vi.fn(),
};
const selectionMobileServiceMock = {
   toggleSelectionMaxMode: vi.fn(),
};

function makeContextProvider(overrides: Partial<Record<string, boolean>> = {}) {
   return { viewer: true, preview: false, composer: false, binding: false, ...overrides };
}

// Fake AbstractVSActions — components under test only read getModel()/menuActions/updateModel().
function makeActions(model: VSObjectModel) {
   return {
      _model: model,
      getModel: vi.fn(function(this: any) { return this._model; }),
      updateModel: vi.fn(function(this: any, m: VSObjectModel) { this._model = m; }),
      menuActions: [],
   };
}

function makeModel(overrides: Partial<VSViewsheetModel> = {}): VSViewsheetModel {
   const model = TestUtils.createMockVSViewsheetModel("Viewsheet1");
   // Non-null, resolvable-link hyperlinkModel by default so ngOnChanges() doesn't hit the
   // null-hyperlinkModel crash documented in Group 2 — that crash is exercised deliberately there.
   model.hyperlinkModel = {
      name: "", label: "", link: "www.example.com", query: null, wsIdentifier: null, targetFrame: null,
      tooltip: null, bookmarkName: null, bookmarkUser: null, parameterValues: [],
      sendReportParameters: false, sendSelectionParameters: false, disablePrompting: false,
      linkType: LinkType.WEB_LINK
   } as any;
   return Object.assign(model, overrides);
}

function makeChildObject(objectType: string, absoluteName: string,
                          overrides: Partial<VSObjectModel> = {}): VSObjectModel
{
   return Object.assign(TestUtils.createMockVSObjectModel(objectType as any, absoluteName), overrides);
}

async function renderComponent(props: Partial<VSViewsheet> = {}, contextOverrides: Record<string, boolean> = {}) {
   // Two ATL quirks worked around here — do not "simplify" back to componentProperties:
   //
   // 1. `model` is an @Input() accessor inherited from AbstractVSObject (not redeclared on
   //    VSViewsheet). ATL's setComponentProperties() looks up the descriptor via
   //    Object.getOwnPropertyDescriptor(fixture.componentInstance.constructor.prototype, key),
   //    which only sees VSViewsheet's OWN prototype and misses inherited accessors. It then
   //    shadows the property with a closure-backed getter/setter instead of calling the real
   //    setter, so `comp.model` reads back correctly but the real backing field `this._model`
   //    is never set. iconHeight() reads `this._model` directly and crashes on first render.
   // 2. Passing ANY componentProperties makes ATL auto-invoke ngOnChanges() internally, before
   //    we get a chance to set `model` — crashing on `this.model.visible`.
   // Fix: skip componentProperties entirely; assign every property directly on the instance
   // after render(), while `model` is still the real (unshadowed) inherited accessor.
   //
   // 3. ChangeDetectorRef/ElementRef/Renderer2/etc. are resolved via the component's own node
   //    injector for the root test component — neither `providers` nor `componentProviders`
   //    (TestBed.overrideProvider) intercepts that resolution. Spy on the REAL instance instead.
   const { fixture } = await render(VSViewsheet, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: clientServiceMock },
         { provide: AssemblyActionFactory, useValue: actionFactoryMock },
         { provide: FixedDropdownService, useValue: dropdownServiceMock },
         { provide: ContextProvider, useValue: makeContextProvider(contextOverrides) },
         { provide: DataTipService, useValue: dataTipServiceMock },
         { provide: PopComponentService, useValue: popComponentServiceMock },
         { provide: SelectionMobileService, useValue: selectionMobileServiceMock },
      ],
   });
   const comp = fixture.componentInstance as VSViewsheet;
   Object.assign(comp, { model: makeModel(), ...props });
   // Spy on the exact object the class holds internally (`this.changeDetector`) rather than a
   // fresh `injector.get(ChangeDetectorRef)` lookup — the two are not guaranteed to be the same
   // reference for the fixture's own root component.
   const detectChangesSpy = vi.spyOn((comp as any).changeDetector, "detectChanges")
      .mockImplementation(() => {});
   return { comp, fixture, detectChangesSpy };
}

beforeEach(() => {
   vi.clearAllMocks();
   actionFactoryMock.createActions.mockImplementation((model: VSObjectModel) => makeActions(model));
   dropdownServiceMock.open.mockReturnValue({ componentInstance: {} });
   dataTipServiceMock.hasDataTipShowing.mockReturnValue(false);
   dataTipServiceMock.dataTipName = null;
   popComponentServiceMock.hasPopUpComponentShowing.mockReturnValue(false);
   popComponentServiceMock.getPopComponent.mockReturnValue(null);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: constructor [Risk 1]
// ---------------------------------------------------------------------------

describe("VSViewsheet — constructor", () => {
   it("should capture preview/composer from ContextProvider when preview is true", async () => {
      const { comp } = await renderComponent({}, { preview: true, composer: false });
      expect(comp.preview).toBe(true);
      expect(comp.composer).toBe(false);
   });

   it("should capture preview/composer from ContextProvider when composer is true", async () => {
      const { comp } = await renderComponent({}, { preview: false, composer: true });
      expect(comp.preview).toBe(false);
      expect(comp.composer).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnChanges [Risk 3]
// ---------------------------------------------------------------------------

describe("VSViewsheet — ngOnChanges visible/vsInfo0/href", () => {
   it("should set visible=false when contextProvider.viewer is true and model.visible is false", async () => {
      const { comp } = await renderComponent({ model: makeModel({ visible: false }) }, { viewer: true });
      comp.ngOnChanges({});
      expect(comp.visible).toBe(false);
   });

   it("should set visible=true regardless of model.visible when contextProvider.viewer is false", async () => {
      const { comp } = await renderComponent({ model: makeModel({ visible: false }) }, { viewer: false });
      comp.ngOnChanges({});
      expect(comp.visible).toBe(true);
   });

   it("should build vsInfo0 from vsObjects + vsInfo linkUri/metadata/runtimeId", async () => {
      const { comp } = await renderComponent();
      comp.vsInfo = { vsObjects: [comp.model], linkUri: "/uri/", metadata: true, runtimeId: "rt1" } as any;
      comp.ngOnChanges({});
      expect(comp.vsInfo0.vsObjects).toBe(comp.vsObjects);
      expect(comp.vsInfo0.linkUri).toBe("/uri/");
      expect(comp.vsInfo0.metadata).toBe(true);
      expect(comp.vsInfo0.runtimeId).toBe("rt1");
   });

   it("should set href to the resolved link URL when viewer=true and preview=false", async () => {
      const model = makeModel({
         hyperlinkModel: { ...makeModel().hyperlinkModel, linkType: LinkType.WEB_LINK, link: "www.example.com", parameterValues: [] } as any
      });
      const { comp } = await renderComponent({ model }, { viewer: true, preview: false });
      comp.vsInfo = { vsObjects: [model], linkUri: "/uri/" } as any;
      comp.ngOnChanges({});
      // GuiTool.resolveUrl() resolves through a real DOM <a> element against the jsdom document
      // base (environment-dependent absolute URL) — assert the meaningful part only.
      expect(comp.href).toContain("www.example.com");
   });

   it("should set href to undefined when preview is true even though viewer is true", async () => {
      const model = makeModel({
         hyperlinkModel: { ...makeModel().hyperlinkModel, linkType: LinkType.WEB_LINK, link: "www.example.com", parameterValues: [] } as any
      });
      const { comp } = await renderComponent({ model }, { viewer: true, preview: true });
      comp.vsInfo = { vsObjects: [model], linkUri: "/uri/" } as any;
      comp.ngOnChanges({});
      expect(comp.href).toBeUndefined();
   });

   it("should reset mySelectedAssemblies when selectedAssemblies changes and no longer includes own index", async () => {
      const { comp } = await renderComponent();
      comp.vsInfo = { vsObjects: [comp.model], linkUri: "/uri/" } as any;
      comp.mySelectedAssemblies = ["Child1"];
      comp.selectedAssemblies = [];
      comp.ngOnChanges({ selectedAssemblies: new SimpleChange([0], [], false) });
      expect(comp.mySelectedAssemblies).toEqual([]);
   });

   it("should NOT reset mySelectedAssemblies when own index is still included", async () => {
      const { comp } = await renderComponent();
      comp.vsInfo = { vsObjects: [comp.model], linkUri: "/uri/" } as any;
      comp.mySelectedAssemblies = ["Child1"];
      comp.selectedAssemblies = [0];
      comp.ngOnChanges({ selectedAssemblies: new SimpleChange([], [0], false) });
      expect(comp.mySelectedAssemblies).toEqual(["Child1"]);
   });

   // `!this.selectedAssemblies || !...includes(myIndex)` — the first operand's own falsy path
   // (selectedAssemblies itself null/undefined, as opposed to an empty array) is a distinct
   // runtime state from `[]` and must short-circuit the `.includes()` call rather than throwing.
   it("should reset mySelectedAssemblies when selectedAssemblies is undefined", async () => {
      const { comp } = await renderComponent();
      comp.vsInfo = { vsObjects: [comp.model], linkUri: "/uri/" } as any;
      comp.mySelectedAssemblies = ["Child1"];
      comp.selectedAssemblies = undefined;
      comp.ngOnChanges({ selectedAssemblies: new SimpleChange([0], undefined, false) });
      expect(comp.mySelectedAssemblies).toEqual([]);
   });

   // Bug: model.hyperlinkModel is null by default in TestUtils.createMockVSViewsheetModel, and
   // HyperlinkViewModel.fromHyperlinkModel() dereferences hyperlink.disablePrompting unconditionally.
   // Expected failure: `expect(() => comp.ngOnChanges({})).not.toThrow()` fails because
   // ngOnChanges() throws a TypeError reading `disablePrompting` off the null hyperlinkModel.
   // If this instead fails during `renderComponent()`/setup (e.g. a fixture-configuration error)
   // rather than inside the `expect(...).not.toThrow()` call itself, that is NOT this bug —
   // investigate the setup change instead of assuming the known issue.
   it.fails("should not throw when model.hyperlinkModel is null (matches shared test-fixture default)", async () => {
      const model = makeModel({ hyperlinkModel: null });
      const { comp } = await renderComponent({ model }, { viewer: true, preview: false });
      comp.vsInfo = { vsObjects: [model], linkUri: "/uri/" } as any;
      expect(() => comp.ngOnChanges({})).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnDestroy [Risk 1]
// ---------------------------------------------------------------------------

describe("VSViewsheet — ngOnDestroy", () => {
   it("should unsubscribe from the command stream when destroyed", async () => {
      const { comp, fixture } = await renderComponent();
      const subscription = (comp as any).subscription;
      expect(subscription).toBeTruthy();
      const unsubscribeSpy = vi.spyOn(subscription, "unsubscribe");
      fixture.destroy();
      expect(unsubscribeSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: getZIndex() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — getZIndex", () => {
   it("should return model.objectFormat.zIndex when no data tip/pop component is showing", async () => {
      const model = makeModel({ absoluteName: "Viewsheet1" });
      model.objectFormat.zIndex = 42;
      const { comp } = await renderComponent({ model });
      expect((comp as any).getZIndex()).toBe(42);
   });

   it("should return the pop-up source z-index when this assembly is the data tip source", async () => {
      const model = makeModel({ absoluteName: "Viewsheet1" });
      const { comp } = await renderComponent({ model });
      dataTipServiceMock.hasDataTipShowing.mockReturnValue(true);
      dataTipServiceMock.dataTipName = "Viewsheet1.Child1";
      expect((comp as any).getZIndex()).toBe(DateTipHelper.getPopUpSourceZIndex());
   });

   it("should return the pop-up source z-index when this assembly is the pop component source", async () => {
      const model = makeModel({ absoluteName: "Viewsheet1" });
      const { comp } = await renderComponent({ model });
      popComponentServiceMock.hasPopUpComponentShowing.mockReturnValue(true);
      popComponentServiceMock.getPopComponent.mockReturnValue("Viewsheet1.Popup1");
      expect((comp as any).getZIndex()).toBe(DateTipHelper.getPopUpSourceZIndex());
   });

   // Fall-through half of the `A && B` operands: hasDataTipShowing() alone is not enough —
   // the shown data tip must also belong to THIS assembly, otherwise fall back to the plain
   // zIndex (a different assembly's data tip showing must not boost this one's z-index).
   it("should fall back to model.objectFormat.zIndex when a data tip is showing for a different assembly", async () => {
      const model = makeModel({ absoluteName: "Viewsheet1" });
      model.objectFormat.zIndex = 42;
      const { comp } = await renderComponent({ model });
      dataTipServiceMock.hasDataTipShowing.mockReturnValue(true);
      dataTipServiceMock.dataTipName = "OtherAssembly.Child1";
      expect((comp as any).getZIndex()).toBe(42);
   });

   it("should fall back to model.objectFormat.zIndex when a pop component is showing for a different assembly", async () => {
      const model = makeModel({ absoluteName: "Viewsheet1" });
      model.objectFormat.zIndex = 42;
      const { comp } = await renderComponent({ model });
      popComponentServiceMock.hasPopUpComponentShowing.mockReturnValue(true);
      popComponentServiceMock.getPopComponent.mockReturnValue("OtherAssembly.Popup1");
      expect((comp as any).getZIndex()).toBe(42);
   });
});

// ---------------------------------------------------------------------------
// Group 5: processAddVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VSViewsheet — processAddVSObjectCommand", () => {
   function addCommand(model: VSObjectModel, name = model.absoluteName): any {
      return { name, mode: AddVsObjectMode.RUNTIME_MODE, model, parent: null };
   }

   it("should push a new non-group-container object and create its actions", async () => {
      const { comp } = await renderComponent();
      const child = makeChildObject("VSText", "Text1");
      comp.processAddVSObjectCommand(addCommand(child));
      expect(comp.vsObjects.map(o => o.absoluteName)).toContain("Text1");
      expect(actionFactoryMock.createActions).toHaveBeenCalled();
   });

   it("should unshift a VSGroupContainer object to the front instead of pushing", async () => {
      const { comp } = await renderComponent();
      // Both objects share the same default zIndex (0) so the final stable sort preserves
      // the unshift-vs-push placement rather than being driven by a zIndex tiebreak.
      const text = makeChildObject("VSText", "Text1");
      comp.processAddVSObjectCommand(addCommand(text));
      const group = makeChildObject("VSGroupContainer", "Group1");
      comp.processAddVSObjectCommand(addCommand(group));
      expect(comp.vsObjects[0].absoluteName).toBe("Group1");
   });

   it("should update the existing object in place instead of duplicating it when the name already exists", async () => {
      const { comp } = await renderComponent();
      const text = makeChildObject("VSText", "Text1");
      comp.processAddVSObjectCommand(addCommand(text));
      const updated = makeChildObject("VSText", "Text1", { description: "updated" });
      comp.processAddVSObjectCommand(addCommand(updated));
      expect(comp.vsObjects.filter(o => o.absoluteName === "Text1")).toHaveLength(1);
      expect(comp.vsObjects.find(o => o.absoluteName === "Text1").description).toBe("updated");
   });

   it("should propagate sheetMaxMode to all vsObjects and sort by zIndex", async () => {
      const { comp } = await renderComponent();
      const low = makeChildObject("VSText", "Low", { objectFormat: Object.assign(TestUtils.createMockVSFormatModel(), { zIndex: 5 }) });
      comp.processAddVSObjectCommand(addCommand(low));
      const high = makeChildObject("VSText", "High", {
         objectFormat: Object.assign(TestUtils.createMockVSFormatModel(), { zIndex: 1 }),
         sheetMaxMode: true
      });
      comp.processAddVSObjectCommand(addCommand(high));
      expect(comp.vsObjects.map(o => o.absoluteName)).toEqual(["High", "Low"]);
      expect(comp.vsObjects.every(o => o.sheetMaxMode === true)).toBe(true);
   });

   it("should register the data tip and pop component with correct positional args", async () => {
      const { comp } = await renderComponent();
      const child = makeChildObject("VSText", "Text1", {
         dataTip: "Text1.Tip", popComponent: "Text1.Pop", container: "Tab1"
      });
      child.objectFormat.top = 10;
      child.objectFormat.left = 20;
      child.objectFormat.width = 30;
      child.objectFormat.height = 40;
      comp.processAddVSObjectCommand(addCommand(child));

      expect(dataTipServiceMock.clearDataTips).toHaveBeenCalledWith("Text1");
      expect(dataTipServiceMock.registerDataTip).toHaveBeenCalledWith("Text1.Tip", "Text1");
      expect(dataTipServiceMock.registerDataTipVisible).toHaveBeenCalledWith("Text1.Tip", true);
      expect(popComponentServiceMock.registerPopComponent).toHaveBeenCalledWith(
         "Text1.Pop", "Text1", 10, 20, 30, 40, "Text1", child, "Tab1"
      );
      expect(popComponentServiceMock.registerPopComponentVisible).toHaveBeenCalledWith("Text1.Pop", true);
   });

   it("should emit onSelectedAssemblyChanged when the added object is already selected", async () => {
      const { comp, detectChangesSpy } = await renderComponent();
      comp.vsInfo = { vsObjects: [comp.model], linkUri: "/uri/" } as any;
      comp.mySelectedAssemblies = ["Text1"];
      const emitted: any[] = [];
      comp.onSelectedAssemblyChanged.subscribe(v => emitted.push(v));

      const child = makeChildObject("VSText", "Text1");
      comp.processAddVSObjectCommand(addCommand(child));

      expect(emitted).toHaveLength(1);
      expect(emitted[0][0]).toBe(0);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   // E6: prove the STOMP command actually routes to this handler (assembly-name match +
   // "process" + message.type method-name convention), not just that the handler works in isolation.
   it("should route AddVSObjectCommand via commandsSubject to the handler", async () => {
      const { comp } = await renderComponent();
      const child = makeChildObject("VSText", "Text1");
      commandsSubject.next({
         assembly: comp.model.absoluteName,
         type: "AddVSObjectCommand",
         command: addCommand(child),
      });
      expect(comp.vsObjects.map(o => o.absoluteName)).toContain("Text1");
   });
});

// ---------------------------------------------------------------------------
// Group 6: processRefreshEmbeddedVSCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — processRefreshEmbeddedVSCommand", () => {
   it("should remove vsObjects whose absoluteName is not in command.assemblies", async () => {
      const { comp, detectChangesSpy } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1"), makeChildObject("VSText", "Text2")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any, makeActions(comp.vsObjects[1]) as any];

      (comp as any).processRefreshEmbeddedVSCommand({ assemblies: ["Text1"] });

      expect(comp.vsObjects.map(o => o.absoluteName)).toEqual(["Text1"]);
      expect(comp.vsObjectActions).toHaveLength(1);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should keep vsObjects whose absoluteName is still listed in command.assemblies", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];

      (comp as any).processRefreshEmbeddedVSCommand({ assemblies: ["Text1"] });

      expect(comp.vsObjects).toHaveLength(1);
   });

   it("should remove all vsObjects when command.assemblies is empty", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1"), makeChildObject("VSText", "Text2")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any, makeActions(comp.vsObjects[1]) as any];

      (comp as any).processRefreshEmbeddedVSCommand({ assemblies: [] });

      expect(comp.vsObjects).toHaveLength(0);
      expect(comp.vsObjectActions).toHaveLength(0);
   });

   it("should route RefreshEmbeddedVSCommand via commandsSubject to the handler", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1"), makeChildObject("VSText", "Text2")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any, makeActions(comp.vsObjects[1]) as any];

      commandsSubject.next({
         assembly: comp.model.absoluteName,
         type: "RefreshEmbeddedVSCommand",
         command: { assemblies: ["Text1"] },
      });

      expect(comp.vsObjects.map(o => o.absoluteName)).toEqual(["Text1"]);
   });
});

// ---------------------------------------------------------------------------
// Group 7: processRefreshVSObjectCommand / applyRefreshObject [Risk 3]
// ---------------------------------------------------------------------------

describe("VSViewsheet — processRefreshVSObjectCommand", () => {
   function seedChild(comp: VSViewsheet, name: string, overrides: Partial<VSObjectModel> = {}) {
      const child = makeChildObject("VSGroupContainer", name, overrides);
      comp.vsObjects = [child];
      comp.vsObjectActions = [makeActions(child) as any];
      return child;
   }

   it("should fix the refreshed object's position relative to the viewsheet's bounds offset", async () => {
      const model = makeModel({ bounds: { x: 100, y: 50, width: 0, height: 0 } as any });
      const { comp, detectChangesSpy } = await renderComponent({ model });
      seedChild(comp, "Group1");

      const refreshed = makeChildObject("VSGroupContainer", "Group1");
      refreshed.objectFormat.left = 150;
      refreshed.objectFormat.top = 80;
      comp.processRefreshVSObjectCommand({ info: refreshed, force: false });

      const updated = comp.vsObjects.find(o => o.absoluteName === "Group1");
      expect(updated.objectFormat.left).toBe(50);
      expect(updated.objectFormat.top).toBe(30);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should zero the position offset when the refreshed object is in max mode", async () => {
      const model = makeModel({ bounds: { x: 100, y: 50, width: 0, height: 0 } as any });
      const { comp } = await renderComponent({ model });
      seedChild(comp, "Group1");

      const refreshed = makeChildObject("VSGroupContainer", "Group1", { maxMode: true } as any);
      refreshed.objectFormat.left = 150;
      refreshed.objectFormat.top = 80;
      comp.processRefreshVSObjectCommand({ info: refreshed, force: false });

      const updated = comp.vsObjects.find(o => o.absoluteName === "Group1");
      expect(updated.objectFormat.left).toBe(150);
      expect(updated.objectFormat.top).toBe(80);
   });

   it("should call updateModel on the matching action with the merged model", async () => {
      const { comp } = await renderComponent();
      const child = seedChild(comp, "Group1");
      const actions = comp.vsObjectActions[0] as any;

      const refreshed = makeChildObject("VSGroupContainer", "Group1", { description: "new-desc" });
      comp.processRefreshVSObjectCommand({ info: refreshed, force: false });

      expect(actions.updateModel).toHaveBeenCalled();
      expect(comp.vsObjects[0].description).toBe("new-desc");
   });

   it("should sync the open context menu actions when it targets the refreshed assembly", async () => {
      const { comp } = await renderComponent();
      seedChild(comp, "Group1");
      const actions = comp.vsObjectActions[0] as any;
      actions.menuActions = ["refreshed-menu"];
      (comp as any).contextMenu = { assemblyName: "Group1", actions: [] };

      const refreshed = makeChildObject("VSGroupContainer", "Group1");
      comp.processRefreshVSObjectCommand({ info: refreshed, force: false });

      expect((comp as any).contextMenu.actions).toBe(actions.menuActions);
   });

   it("should leave vsObjects unchanged when the refreshed name does not match any existing object", async () => {
      const { comp } = await renderComponent();
      seedChild(comp, "Group1");
      const before = comp.vsObjects.slice();

      const refreshed = makeChildObject("VSGroupContainer", "Unknown");
      comp.processRefreshVSObjectCommand({ info: refreshed, force: false });

      expect(comp.vsObjects).toEqual(before);
   });

   it("should route RefreshVSObjectCommand via commandsSubject to the handler", async () => {
      const { comp } = await renderComponent();
      seedChild(comp, "Group1");

      const refreshed = makeChildObject("VSGroupContainer", "Group1", { description: "routed-desc" });
      commandsSubject.next({
         assembly: comp.model.absoluteName,
         type: "RefreshVSObjectCommand",
         command: { info: refreshed, force: false },
      });

      expect(comp.vsObjects[0].description).toBe("routed-desc");
   });
});

// ---------------------------------------------------------------------------
// Group 8: processRemoveVSObjectCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — processRemoveVSObjectCommand", () => {
   it("should splice the matching object and its actions out of the arrays", async () => {
      const { comp, detectChangesSpy } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1"), makeChildObject("VSText", "Text2")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any, makeActions(comp.vsObjects[1]) as any];

      (comp as any).processRemoveVSObjectCommand({ name: "Text1" });

      expect(comp.vsObjects.map(o => o.absoluteName)).toEqual(["Text2"]);
      expect(comp.vsObjectActions).toHaveLength(1);
      expect(detectChangesSpy).toHaveBeenCalled();
   });

   it("should leave the arrays unchanged when the name does not match any object", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];

      (comp as any).processRemoveVSObjectCommand({ name: "Unknown" });

      expect(comp.vsObjects).toHaveLength(1);
   });

   it("should route RemoveVSObjectCommand via commandsSubject to the handler", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];

      commandsSubject.next({
         assembly: comp.model.absoluteName,
         type: "RemoveVSObjectCommand",
         command: { name: "Text1" },
      });

      expect(comp.vsObjects).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 9: processUpdateZIndexesCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — processUpdateZIndexesCommand", () => {
   it("should update zIndex and recreate actions for a matched assembly name", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];
      actionFactoryMock.createActions.mockClear();

      (comp as any).processUpdateZIndexesCommand({ assemblies: ["Text1"], zIndexes: [99] });

      expect(comp.vsObjects[0].objectFormat.zIndex).toBe(99);
      expect(actionFactoryMock.createActions).toHaveBeenCalledWith(comp.vsObjects[0]);
   });

   it("should ignore assembly names that do not match any existing object", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];
      const originalActions = comp.vsObjectActions[0];

      (comp as any).processUpdateZIndexesCommand({ assemblies: ["Unknown"], zIndexes: [99] });

      expect(comp.vsObjects[0].objectFormat.zIndex).not.toBe(99);
      expect(comp.vsObjectActions[0]).toBe(originalActions);
   });

   it("should sync the open context menu actions when it targets the updated assembly", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];
      (comp as any).contextMenu = { assemblyName: "Text1", actions: [] };
      actionFactoryMock.createActions.mockImplementation((m: VSObjectModel) => {
         const a = makeActions(m);
         (a as any).menuActions = ["new-menu"];
         return a;
      });

      (comp as any).processUpdateZIndexesCommand({ assemblies: ["Text1"], zIndexes: [7] });

      expect((comp as any).contextMenu.actions).toEqual(["new-menu"]);
   });

   it("should route UpdateZIndexesCommand via commandsSubject to the handler", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeChildObject("VSText", "Text1")];
      comp.vsObjectActions = [makeActions(comp.vsObjects[0]) as any];

      commandsSubject.next({
         assembly: comp.model.absoluteName,
         type: "UpdateZIndexesCommand",
         command: { assemblies: ["Text1"], zIndexes: [42] },
      });

      expect(comp.vsObjects[0].objectFormat.zIndex).toBe(42);
   });
});

// ---------------------------------------------------------------------------
// Group 10: openViewsheet() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — openViewsheet", () => {
   it("should emit onOpenViewsheet with the model id when href is falsy", async () => {
      const model = makeModel({ id: "vs-123" });
      const { comp } = await renderComponent({ model });
      comp.href = undefined;
      const emitted: string[] = [];
      comp.onOpenViewsheet.subscribe(v => emitted.push(v));

      comp.openViewsheet();

      expect(emitted).toEqual(["vs-123"]);
   });

   it("should NOT emit onOpenViewsheet when href is already set", async () => {
      const { comp } = await renderComponent();
      comp.href = "/some/link";
      const emitted: string[] = [];
      comp.onOpenViewsheet.subscribe(v => emitted.push(v));

      comp.openViewsheet();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 11: iconHeight getter [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — iconHeight", () => {
   it("should subtract objectFormat.top from iconHeight when viewer and iconHeight >= top", async () => {
      const model = makeModel({ iconHeight: 50 });
      model.objectFormat.top = 20;
      const { comp } = await renderComponent({ model }, { viewer: true });
      expect(comp.iconHeight).toBe(30);
   });

   it("should return the raw iconHeight when iconHeight < objectFormat.top", async () => {
      const model = makeModel({ iconHeight: 10 });
      model.objectFormat.top = 20;
      const { comp } = await renderComponent({ model }, { viewer: true });
      expect(comp.iconHeight).toBe(10);
   });

   it("should return the raw iconHeight when not a viewer, even if iconHeight >= top", async () => {
      const model = makeModel({ iconHeight: 50 });
      model.objectFormat.top = 20;
      const { comp } = await renderComponent({ model }, { viewer: false, preview: false });
      expect(comp.iconHeight).toBe(50);
   });
});

// ---------------------------------------------------------------------------
// Group 12: showContextMenu() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — showContextMenu", () => {
   it("should open the dropdown at the event position and wire the context menu fields", async () => {
      const { comp } = await renderComponent();
      const contextMenuInstance: any = {};
      dropdownServiceMock.open.mockReturnValue({ componentInstance: contextMenuInstance });
      const actions: any = makeActions(comp.model);
      actions.menuActions = ["a1"];
      const event = new MouseEvent("contextmenu", { clientX: 12, clientY: 34 });
      const preventDefaultSpy = vi.spyOn(event, "preventDefault");

      comp.showContextMenu({ actions, event });

      expect(dropdownServiceMock.open).toHaveBeenCalledWith(
         ActionsContextmenuComponent,
         expect.objectContaining({ position: { x: 12, y: 34 }, contextmenu: true })
      );
      expect(contextMenuInstance.sourceEvent).toBe(event);
      expect(contextMenuInstance.actions).toEqual(["a1"]);
      expect(contextMenuInstance.assemblyName).toBe(comp.model.absoluteName);
      expect(preventDefaultSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 13: getVariableValues (variableValuesFunction) [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — variableValuesFunction", () => {
   it("should concatenate the externally-supplied variable values with the embedded object's own variables", async () => {
      const inputChild = makeChildObject("VSTextInput", "Input1");
      const { comp } = await renderComponent({ variableValues: (name: string) => ["$(external)"] });
      comp.vsObjects = [inputChild];

      const result = comp.variableValuesFunction("SomeAssembly");

      expect(result).toEqual(["$(external)", "$(Input1)"]);
   });

   it("should exclude the target object itself from the embedded variable list", async () => {
      const inputChild = makeChildObject("VSTextInput", "Input1");
      const { comp } = await renderComponent({ variableValues: (name: string) => [] });
      comp.vsObjects = [inputChild];

      const result = comp.variableValuesFunction("Input1");

      expect(result).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 14: showIconContainer / isChildDropdownExpanded [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — showIconContainer with child dropdown expansion", () => {
   function createSelectionInBottomTab(name: string, overrides: Partial<VSSelectionListModel> = {}): VSSelectionListModel {
      const sel = TestUtils.createMockVSSelectionListModel(name);
      sel.container = "Tab1";
      sel.containerType = "VSTab";
      sel.dropdown = true;
      sel.hidden = false;
      sel.cellHeight = 18;
      sel.listHeight = 10;
      sel.objectFormat.top = 200;
      sel.objectFormat.left = 0;
      sel.objectFormat.width = 150;
      return Object.assign(sel, overrides);
   }

   function createBottomTab(name: string): VSTabModel {
      const tab = TestUtils.createMockVSTabModel(name);
      tab.absoluteName = name;
      tab.bottomTabs = true;
      return tab;
   }

   function createCalendarInBottomTab(name: string, overrides: Partial<VSCalendarModel> = {}): VSCalendarModel {
      const cal = TestUtils.createMockVSCalendarModel(name);
      cal.container = "Tab1";
      cal.containerType = "VSTab";
      cal.dropdownCalendar = true;
      cal.calendarsShown = true;
      cal.objectFormat.top = 200;
      cal.objectFormat.left = 0;
      cal.objectFormat.width = 150;
      return Object.assign(cal, overrides);
   }

   it("should show icon when selection dropdown is not expanded (hidden=true)", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { hidden: true });
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should show icon when selection is not a dropdown", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { dropdown: false });
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should hide icon when expanded dropdown overlaps vertically", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { listHeight: 12 });
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeFalsy();
   });

   it("should show icon when expanded dropdown does not reach icon vertically", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { listHeight: 10 });
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should show icon when expanded dropdown is to the right of icon", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { listHeight: 12 });
      sel.objectFormat.left = 100;
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should show icon when selection is not in a bottom tab container", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = TestUtils.createMockVSTabModel("Tab1");
      tab.absoluteName = "Tab1";
      tab.bottomTabs = false;
      const sel = createSelectionInBottomTab("Sel1", { listHeight: 12 });
      comp.vsObjects = [tab, sel];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should hide icon when calendar dropdown overlaps", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const cal = createCalendarInBottomTab("Cal1");
      cal.objectFormat.top = 100;
      comp.vsObjects = [tab, cal];
      expect(comp.showIconContainer).toBeFalsy();
   });

   it("should show icon when calendar dropdown is not shown", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const cal = createCalendarInBottomTab("Cal1", { calendarsShown: false });
      comp.vsObjects = [tab, cal];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should show icon when no child objects exist", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      comp.vsObjects = [];
      expect(comp.showIconContainer).toBeTruthy();
   });

   it("should account for mobile cell height", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      const tab = createBottomTab("Tab1");
      const sel = createSelectionInBottomTab("Sel1", { cellHeight: 18, listHeight: 10 });
      comp.vsObjects = [tab, sel];

      (comp as any).mobileDevice = false;
      expect(comp.showIconContainer).toBeTruthy();

      (comp as any).mobileDevice = true;
      expect(comp.showIconContainer).toBeFalsy();
   });

   it("should hide the icon container when deployed is true", async () => {
      const { comp } = await renderComponent({ deployed: true }, { viewer: true });
      comp.vsObjects = [];
      expect(comp.showIconContainer).toBeFalsy();
   });

   it("should hide the icon container when maxMode is true", async () => {
      const { comp } = await renderComponent({}, { viewer: true });
      comp.vsObjects = [];
      comp.maxMode = true;
      expect(comp.showIconContainer).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 15: selectViewsheet() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSViewsheet — selectViewsheet", () => {
   function setup(childType = "VSText") {
      return async (contextOverrides: Record<string, boolean> = { viewer: true }) => {
         const { comp } = await renderComponent({}, contextOverrides);
         const child = makeChildObject(childType, "Child1", { selectedAnnotations: ["ann1"] });
         comp.vsObjects = [child];
         comp.vsObjectActions = [makeActions(child) as any];
         comp.vsInfo = {
            vsObjects: [comp.model], linkUri: "/uri/",
            selectAssembly: vi.fn(), clearFocusedAssemblies: vi.fn()
         } as any;
         return { comp, child };
      };
   }

   it("should clear sibling selectedAnnotations and set mySelectedAssemblies on a plain click", async () => {
      const { comp, child } = await setup()();
      const otherChild = makeChildObject("VSText", "Child2", { selectedAnnotations: ["ann2"] });
      comp.vsObjects = [child, otherChild];
      const event = new MouseEvent("click");
      const emitted: any[] = [];
      comp.onSelectedAssemblyChanged.subscribe(v => emitted.push(v));

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(otherChild.selectedAnnotations).toEqual([]);
      expect(comp.mySelectedAssemblies).toEqual(["Child1"]);
      expect(selectionMobileServiceMock.toggleSelectionMaxMode).toHaveBeenCalledWith(child);
      expect(comp.vsInfo.selectAssembly).toHaveBeenCalledWith(comp.model);
      expect(comp.model.selectedAnnotations).toEqual(["ann1"]);
      expect(emitted).toHaveLength(1);
      expect(emitted[0][0]).toBe(0);
   });

   it("should NOT clear sibling selectedAnnotations when ctrlKey is held", async () => {
      const { comp, child } = await setup()();
      const otherChild = makeChildObject("VSText", "Child2", { selectedAnnotations: ["ann2"] });
      comp.vsObjects = [child, otherChild];
      const event = new MouseEvent("click", { ctrlKey: true });

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(otherChild.selectedAnnotations).toEqual(["ann2"]);
   });

   // metaKey and shiftKey are separate `&&` operands from ctrlKey in the modifier guard
   // (!ctrlKey && !metaKey && !shiftKey) — each must be verified independently since they are
   // copy-pasted-looking conditions on different event fields.
   it("should NOT clear sibling selectedAnnotations when metaKey is held", async () => {
      const { comp, child } = await setup()();
      const otherChild = makeChildObject("VSText", "Child2", { selectedAnnotations: ["ann2"] });
      comp.vsObjects = [child, otherChild];
      const event = new MouseEvent("click", { metaKey: true });

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(otherChild.selectedAnnotations).toEqual(["ann2"]);
   });

   it("should NOT clear sibling selectedAnnotations when shiftKey is held", async () => {
      const { comp, child } = await setup()();
      const otherChild = makeChildObject("VSText", "Child2", { selectedAnnotations: ["ann2"] });
      comp.vsObjects = [child, otherChild];
      const event = new MouseEvent("click", { shiftKey: true });

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(otherChild.selectedAnnotations).toEqual(["ann2"]);
   });

   it("should skip mySelectedAssemblies/toggleSelectionMaxMode when the selected child is itself a VSViewsheet", async () => {
      const { comp } = await setup("VSViewsheet")();
      comp.mySelectedAssemblies = [];
      const event = new MouseEvent("click");

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(comp.mySelectedAssemblies).toEqual([]);
      expect(selectionMobileServiceMock.toggleSelectionMaxMode).not.toHaveBeenCalled();
   });

   it("should skip mySelectedAssemblies assignment when not in viewer or preview context", async () => {
      const { comp } = await setup()({ viewer: false, preview: false });
      comp.mySelectedAssemblies = [];
      const event = new MouseEvent("click");

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(comp.mySelectedAssemblies).toEqual([]);
      expect(comp.vsInfo.selectAssembly).toHaveBeenCalledWith(comp.model);
   });

   // `this.viewer || this.preview` guards this branch. Note `this.viewer` (base getter) is
   // itself `context.viewer || context.preview`, so context.preview=true always makes
   // `this.viewer` true too — there is no reachable state where `this.viewer` is false while
   // `this.preview` is true. This test still has regression value: it locks in that a
   // preview-only context (viewer=false, preview=true) takes the branch, which would silently
   // break if `this.viewer`'s definition were ever narrowed to drop the `|| context.preview` term.
   it("should still assign mySelectedAssemblies in a preview-only context (viewer=false, preview=true)", async () => {
      const { comp, child } = await setup()({ viewer: false, preview: true });
      comp.mySelectedAssemblies = [];
      const event = new MouseEvent("click");

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(comp.mySelectedAssemblies).toEqual(["Child1"]);
      expect(selectionMobileServiceMock.toggleSelectionMaxMode).toHaveBeenCalledWith(child);
   });

   it("should NOT emit onSelectedAssemblyChanged when this viewsheet is not found in vsInfo.vsObjects", async () => {
      const { comp } = await setup()();
      comp.vsInfo = { vsObjects: [], linkUri: "/uri/", selectAssembly: vi.fn(), clearFocusedAssemblies: vi.fn() } as any;
      const emitted: any[] = [];
      comp.onSelectedAssemblyChanged.subscribe(v => emitted.push(v));
      const event = new MouseEvent("click");

      comp.selectViewsheet([0, comp.vsObjectActions[0], event]);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 16: removeSelectedAnnotations() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSViewsheet — removeSelectedAnnotations", () => {
   it("should send a RemoveAnnotationEvent when a selected assembly has selected annotations", async () => {
      const { comp } = await renderComponent();
      const child = makeChildObject("VSText", "Child1", { selectedAnnotations: ["ann1"] });
      comp.vsObjects = [child];
      comp.mySelectedAssemblies = ["Child1"];

      comp.removeSelectedAnnotations();

      expect(clientServiceMock.sendEvent).toHaveBeenCalledWith(
         RemoveAnnotationEvent.REMOVE_ANNOTATION_URI, expect.anything()
      );
   });

   it("should NOT send an event when no selected assembly has annotations", async () => {
      const { comp } = await renderComponent();
      const child = makeChildObject("VSText", "Child1", { selectedAnnotations: [] });
      comp.vsObjects = [child];
      comp.mySelectedAssemblies = ["Child1"];

      comp.removeSelectedAnnotations();

      expect(clientServiceMock.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 17: onMaxModeChanged() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSViewsheet — onMaxModeChanged", () => {
   it("should set maxMode and emit maxModeChange with the given payload", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.maxModeChange.subscribe(v => emitted.push(v));

      comp.onMaxModeChanged({ assembly: "Child1", maxMode: true });

      expect(comp.maxMode).toBe(true);
      expect(emitted).toEqual([{ assembly: "Child1", maxMode: true }]);
   });
});

// ---------------------------------------------------------------------------
// Group 18: navigate() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSViewsheet — navigate", () => {
   it("should call openViewsheet() when the SPACE key is pressed", async () => {
      const { comp } = await renderComponent();
      comp.href = undefined;
      const openSpy = vi.spyOn(comp, "openViewsheet");

      (comp as any).navigate(NavigationKeys.SPACE);

      expect(openSpy).toHaveBeenCalled();
   });

   it("should do nothing for non-SPACE keys", async () => {
      const { comp } = await renderComponent();
      const openSpy = vi.spyOn(comp, "openViewsheet");

      (comp as any).navigate(NavigationKeys.LEFT);

      expect(openSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 19: getEmbeddedVSBounds() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSViewsheet — getEmbeddedVSBounds", () => {
   it("should build a Rectangle from the model's objectFormat", async () => {
      const model = makeModel();
      model.objectFormat.left = 5;
      model.objectFormat.top = 6;
      model.objectFormat.width = 7;
      model.objectFormat.height = 8;
      const { comp } = await renderComponent({ model });

      const rect = comp.getEmbeddedVSBounds();

      expect(rect.x).toBe(5);
      expect(rect.y).toBe(6);
      expect(rect.width).toBe(7);
      expect(rect.height).toBe(8);
   });
});
