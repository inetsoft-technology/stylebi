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
 * ComposerSelectionContainerChildren — Pass 1: interaction tests
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit subscription lifecycle + ngOnDestroy memory leak
 *   Group 2 [Risk 3] — isDragAcceptable(): selection-type drag returns true; invalid JSON returns false
 *   Group 3 [Risk 2] — isShape(): true for VSOval/VSRectangle/VSLine; false for VSSelectionContainer
 *   Group 4 [Risk 2] — select(): clears focused assemblies only without ctrlKey; always emits onRefreshFormat
 *   Group 5 [Risk 2] — droppedOnChild(): stops propagation only for DragBorderType.ALL
 *   Group 6 [Risk 2] — getObjectTop(), getBodyWidth(), childWithBorder getter
 *   Group 7 [Risk 1] — childChanged, resizeAssembly, isSelected, zIndex getter
 *
 * Fixed bugs:
 *   Bug #75599 (fixed) — onChildUpdate subscription leak (Group 1): ngOnInit subscribed to
 *     selectionContainerChildrenService.onChildUpdate (source line 174) without adding the
 *     subscription to this.subscriptions. After ngOnDestroy, the callback still fired and called
 *     setChildrenHeight() on the dead component. Fixed by adding this subscription to the
 *     existing this.subscriptions container.
 *
 * Out of scope:
 *   onEnter, onLeave, onContainerDragOver — require selectionContainerRef (@Input EditableObjectContainer)
 *     which is a live component reference; cannot be injected meaningfully in unit tests.
 *   onDragOver — uses event.path (DOM path traversal) not available in jsdom.
 *   drop — 200-line complex switch driven by drag data shape, trapService.checkTrap async flow,
 *     and composerObjectService.addNewObject; integration-level coverage required.
 *   openLayoutOptionDialog — private method.
 *   processChangeVSSelectionTitleCommand — private method.
 *   vsObject setter with outerSelections — requires per-outerSelection AssemblyActionFactory setup.
 *   setChildrenHeight — requires full VSSelectionListModel with dropdown/containerType fields;
 *     covered transitively by vsObject setter test.
 *   getPaddingHeight — calls parent getBodyHeight which requires full model hierarchy; Risk 2.
 *   getInnerWidth — calls Tool.getMarginSize with border CSS strings; Risk 2.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComposerSelectionContainerChildren, DragBorderType } from "./composer-selection-container-children.component";
import { ViewsheetClientService } from "../../../../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../../../../vsobjects/action/assembly-action-factory.service";
import { SelectionContainerChildrenService } from "../../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { ContextProvider } from "../../../../../vsobjects/context-provider.service";
import { VSTrapService } from "../../../../../vsobjects/util/vs-trap.service";
import { ComposerObjectService } from "../../composer-object.service";
import { DomService } from "../../../../../widget/dom-service/dom.service";
import { DragService } from "../../../../../widget/services/drag.service";
import { ComposerVsSearchService } from "../../composer-vs-search.service";
import { EditableObjectContainer } from "../../editor/editable-object-container.component";
import { AssemblyType } from "../../assembly-type";

// --- Shared Subjects for subscription tests ---
let dragSubject: Subject<any>;
let onChildUpdateSubject: Subject<any>;
let focusChangeSubject: Subject<any>;

// --- Service mocks ---
let selectionChildServiceMock: any;
let composerVsSearchServiceMock: any;
let composerObjectServiceMock: any;
let dragServiceMock: any;
let actionFactoryMock: any;
let viewsheetMock: any;

function createMocks() {
   dragSubject = new Subject<any>();
   onChildUpdateSubject = new Subject<any>();
   focusChangeSubject = new Subject<any>();

   selectionChildServiceMock = {
      dragModelSubject: dragSubject,
      onChildUpdate: onChildUpdateSubject,
      onChildModelUpdate: new Subject<any>(),
      pushModel: vi.fn(),
      get childWithBorder() { return 3; },
      childDragModel: { dragging: false },
   };
   composerVsSearchServiceMock = {
      focusChange: vi.fn().mockReturnValue(focusChangeSubject),
   };
   composerObjectServiceMock = {
      getObjectType: vi.fn().mockReturnValue(0),
      addNewObject: vi.fn(),
      getDataSource: vi.fn().mockReturnValue(null),
   };
   dragServiceMock = { getDragData: vi.fn().mockReturnValue({}) };
   actionFactoryMock = {
      createActions: vi.fn().mockReturnValue([]),
      createCurrentSelectionActions: vi.fn().mockReturnValue([]),
   };
   viewsheetMock = {
      clearFocusedAssemblies: vi.fn(),
      selectAssembly: vi.fn(),
      isAssemblyFocused: vi.fn().mockReturnValue(false),
   };
}

function makeVsObject(overrides: any = {}) {
   return {
      absoluteName: "Container1",
      objectType: "VSSelectionContainer",
      objectFormat: {
         top: 10, width: 200, height: 150, left: 0,
         border: { top: null, bottom: null, left: null, right: null },
      },
      titleFormat: { height: 30 },
      outerSelections: [],
      childObjects: [],
      dataRowHeight: 20,
      childrenNames: [],
      ...overrides,
   } as any;
}

async function renderComponent(extraProps: any = {}) {
   // Spy on parent-owned method before instance creation so vsObject setter doesn't throw.
   vi.spyOn(ComposerSelectionContainerChildren.prototype as any, "getBodyHeight").mockReturnValue(120);

   const { fixture } = await render(ComposerSelectionContainerChildren, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: {
            sendEvent: vi.fn(), runtimeId: "vs-test",
            addMessageListener: vi.fn(), removeMessageListener: vi.fn(),
         }},
         { provide: AssemblyActionFactory, useValue: actionFactoryMock },
         { provide: SelectionContainerChildrenService, useValue: selectionChildServiceMock },
         { provide: ContextProvider, useValue: { viewer: false, preview: false } },
         { provide: VSTrapService, useValue: { checkTrap: vi.fn() } },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: ComposerObjectService, useValue: composerObjectServiceMock },
         { provide: DomService, useValue: {} },
         { provide: DragService, useValue: dragServiceMock },
         { provide: ComposerVsSearchService, useValue: composerVsSearchServiceMock },
      ],
      componentProperties: {
         viewsheet: viewsheetMock,
         // vsObject must be provided here: the template binds vsObject.objectFormat.left (etc.)
         // unconditionally on line 20 of the HTML. Without it, initial change detection crashes.
         vsObject: makeVsObject(),
         ...extraProps,
      },
   });
   const comp = fixture.componentInstance as ComposerSelectionContainerChildren;
   return { comp, fixture };
}

beforeEach(() => createMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit + ngOnDestroy memory leak [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — ngOnInit subscription lifecycle", () => {
   // 🔁 Regression-sensitive: subscriptions to dragModelSubject must be cleaned up on destroy;
   //    if the guard is removed, destroyed components keep mutating their state after navigation.
   it("should update childDragModel when dragSubject emits with a matching container name", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({ absoluteName: "Container1" });

      const dragModel = {
         container: "Container1", dragging: true, isContainerChild: false,
      } as any;
      dragSubject.next(dragModel);

      expect((comp as any).childDragModel).toBe(dragModel);
   });

   it("should NOT update childDragModel after ngOnDestroy (dragModelSubject cleaned up)", async () => {
      const { comp, fixture } = await renderComponent();
      comp.vsObject = makeVsObject({ absoluteName: "Container1" });

      const first = { container: "Container1", dragging: true, isContainerChild: false } as any;
      dragSubject.next(first);
      expect((comp as any).childDragModel).toBe(first);

      fixture.destroy(); // triggers ngOnDestroy -> subscriptions.unsubscribe()

      const second = { container: "Container1", dragging: false, isContainerChild: false } as any;
      dragSubject.next(second);

      // dragModelSubject subscription was cleaned up; childDragModel still points to first
      expect((comp as any).childDragModel).toBe(first);
   });

   it("should call subscriptions.unsubscribe() on ngOnDestroy", async () => {
      const { comp, fixture } = await renderComponent();
      const unsubSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");
      fixture.destroy();
      expect(unsubSpy).toHaveBeenCalledOnce();
   });

   // Bug #75599 (fixed): onChildUpdate.subscribe() on line 174 of source was NOT added to
   // this.subscriptions. After ngOnDestroy, the subscription still fired — calling
   // setChildrenHeight() on a dead component. Fixed by adding this subscription to the
   // existing this.subscriptions container.
   it("should not call setChildrenHeight after ngOnDestroy via onChildUpdate (leaked subscription)", async () => {
      const { comp, fixture } = await renderComponent();
      comp.vsObject = makeVsObject();
      const spy = vi.spyOn(comp, "setChildrenHeight");

      fixture.destroy(); // calls ngOnDestroy -> subscriptions.unsubscribe()
      onChildUpdateSubject.next(0); // subscription is cleaned up, so this is a no-op

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: isDragAcceptable [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — isDragAcceptable", () => {
   // 🔁 Regression-sensitive: incorrect acceptance of drag data causes drop events that crash;
   //    incorrect rejection prevents valid selections from being added to the container.
   it("should return false when dragData has no keys", async () => {
      const { comp } = await renderComponent();
      expect(comp.isDragAcceptable({})).toBe(false);
   });

   it("should return true when the drag name maps to SELECTION_LIST_ASSET type", async () => {
      const { comp } = await renderComponent();
      composerObjectServiceMock.getObjectType.mockReturnValue(AssemblyType.SELECTION_LIST_ASSET);
      expect(comp.isDragAcceptable({ "VSSelectionList": "anything" })).toBe(true);
   });

   it("should return true when the drag name maps to TIME_SLIDER_ASSET type", async () => {
      const { comp } = await renderComponent();
      composerObjectServiceMock.getObjectType.mockReturnValue(AssemblyType.TIME_SLIDER_ASSET);
      expect(comp.isDragAcceptable({ "VSRangeSlider": "anything" })).toBe(true);
   });

   it("should return false when the drag data value is not valid JSON", async () => {
      const { comp } = await renderComponent();
      composerObjectServiceMock.getObjectType.mockReturnValue(0); // not a selection type
      expect(comp.isDragAcceptable({ "unknown": "not-valid-json{{" })).toBe(false);
   });

   it("should return false when the parsed drag data array is empty", async () => {
      const { comp } = await renderComponent();
      composerObjectServiceMock.getObjectType.mockReturnValue(0);
      expect(comp.isDragAcceptable({ "name": "[]" })).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: isShape [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — isShape", () => {
   it("should return true when objectType is VSOval", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({ objectType: "VSOval" });
      expect(comp.isShape()).toBe(true);
   });

   it("should return true when objectType is VSRectangle", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({ objectType: "VSRectangle" });
      expect(comp.isShape()).toBe(true);
   });

   it("should return true when objectType is VSLine", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({ objectType: "VSLine" });
      expect(comp.isShape()).toBe(true);
   });

   it("should return false when objectType is VSSelectionContainer", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({ objectType: "VSSelectionContainer" });
      expect(comp.isShape()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: select() [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — select", () => {
   // 🔁 Regression-sensitive: clearFocusedAssemblies must be called for single-click (no ctrlKey)
   //    but not for multi-select (ctrlKey). Without this, single-click breaks multi-selection state.
   it("should call clearFocusedAssemblies when ctrlKey is not held", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();

      comp.select(new MouseEvent("click", { ctrlKey: false }));

      expect(viewsheetMock.clearFocusedAssemblies).toHaveBeenCalledOnce();
   });

   it("should NOT call clearFocusedAssemblies when ctrlKey is held", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();

      comp.select(new MouseEvent("click", { ctrlKey: true }));

      expect(viewsheetMock.clearFocusedAssemblies).not.toHaveBeenCalled();
   });

   it("should always call selectAssembly with vsObject", async () => {
      const { comp } = await renderComponent();
      const vsObj = makeVsObject();
      comp.vsObject = vsObj;

      comp.select(new MouseEvent("click"));

      expect(viewsheetMock.selectAssembly).toHaveBeenCalledWith(vsObj);
   });

   it("should emit onRefreshFormat with the event and vsObject", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();
      const emitted: any[] = [];
      comp.onRefreshFormat.subscribe(v => emitted.push(v));

      const evt = new MouseEvent("click");
      comp.select(evt);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].event).toBe(evt);
      expect(emitted[0].vsobject).toBe(comp.vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 5: droppedOnChild [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — droppedOnChild", () => {
   it("should set isContainerDragover to false", async () => {
      const { comp } = await renderComponent();
      comp.isContainerDragover = true;

      comp.droppedOnChild({} as any);

      expect(comp.isContainerDragover).toBe(false);
   });

   it("should call event.stopPropagation() when dragOverBorder is DragBorderType.ALL", async () => {
      const { comp } = await renderComponent();
      comp.dragOverBorder = DragBorderType.ALL;
      const event = { stopPropagation: vi.fn() } as any;

      comp.droppedOnChild(event);

      expect(event.stopPropagation).toHaveBeenCalledOnce();
   });

   it("should NOT call event.stopPropagation() when dragOverBorder is not ALL", async () => {
      const { comp } = await renderComponent();
      comp.dragOverBorder = DragBorderType.ABOVE;
      const event = { stopPropagation: vi.fn() } as any;

      comp.droppedOnChild(event);

      expect(event.stopPropagation).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: getObjectTop, getBodyWidth, childWithBorder [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — dimension accessors", () => {
   it("should return objectFormat.top + titleFormat.height from getObjectTop", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({
         objectFormat: { top: 20, width: 200, height: 150, left: 0, border: {} },
         titleFormat: { height: 40 },
      });
      expect(comp.getObjectTop()).toBe(60);
   });

   it("should return objectFormat.width from getBodyWidth", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject({
         objectFormat: { top: 10, width: 350, height: 150, left: 0, border: {} },
      });
      expect(comp.getBodyWidth()).toBe(350);
   });

   it("should return childWithBorder from selectionContainerChildrenService", async () => {
      const { comp } = await renderComponent();
      expect(comp.childWithBorder).toBe(3);
   });
});

// ---------------------------------------------------------------------------
// Group 7: childChanged, resizeAssembly, isSelected, zIndex [Risk 1]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — delegation methods", () => {
   it("should emit objectChanged via childChanged", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.objectChanged.subscribe(v => emitted.push(v));

      comp.childChanged(true);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(true);
   });

   it("should emit onResize via resizeAssembly", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onResize.subscribe(v => emitted.push(v));
      const payload = { event: {}, model: {} as any };

      comp.resizeAssembly(payload);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(payload);
   });

   it("should return false from isSelected when viewsheet.isAssemblyFocused returns false", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();
      viewsheetMock.isAssemblyFocused.mockReturnValue(false);

      expect(comp.isSelected()).toBe(false);
   });

   it("should return true from isSelected when viewsheet.isAssemblyFocused returns true", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();
      viewsheetMock.isAssemblyFocused.mockReturnValue(true);

      expect(comp.isSelected()).toBe(true);
   });

   it("should return the value from EditableObjectContainer.calculateZIndex for the zIndex getter", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = makeVsObject();
      vi.spyOn(EditableObjectContainer, "calculateZIndex").mockReturnValue(7);

      expect(comp.zIndex).toBe(7);
      expect(EditableObjectContainer.calculateZIndex).toHaveBeenCalledWith(comp.vsObject, viewsheetMock);
   });
});
