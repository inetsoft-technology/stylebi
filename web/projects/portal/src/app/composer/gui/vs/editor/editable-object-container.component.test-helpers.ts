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
 * Shared test helpers for EditableObjectContainer P1/P2/P3 spec files.
 *
 * Strategy: direct class instantiation.
 *   - AbstractActionComponent.constructor just stores actionFactory — no async work.
 *   - The vsObjectModel setter is the first real work: calls updateActions() which
 *     calls actionFactory.createActions(model) and subscribes to onAssemblyActionEvent.
 *   - ngOnInit() subscribes to viewsheet.focusedAssemblies (BehaviorSubject-based).
 *   - @ViewChild refs (objectEditor, objectComponent) are wired manually after construction.
 */

import { EventEmitter } from "@angular/core";
import { EMPTY } from "rxjs";
import { EditableObjectContainer } from "./editable-object-container.component";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Actions mock — minimal shape required by AbstractActionComponent.updateActions()
// ---------------------------------------------------------------------------

export function makeActionsMock() {
   return {
      onAssemblyActionEvent: new EventEmitter<any>(),
      toolbarActions: [],
      menuActions: [],
      clickActions: null,
      scriptAction: null,
   };
}

export function makeActionFactoryMock() {
   return {
      createActions: vi.fn().mockReturnValue(makeActionsMock()),
      createCurrentSelectionActions: vi.fn().mockReturnValue(makeActionsMock()),
   };
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeMiniToolbarService() {
   return {
      hasMiniToolbar: vi.fn().mockReturnValue(false),
      isMiniToolbarVisible: vi.fn().mockReturnValue(false),
      isMiniToolbarHidden: vi.fn().mockReturnValue(false),
      addContainerEvents: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
      handleMouseEnter: vi.fn(),
      getToolbarWidth: vi.fn().mockReturnValue(0),
      freeze: vi.fn(),
      hiddenUnfreeze: vi.fn(),
      unfreeze: vi.fn(),
   };
}

export function makeComposerObjectService() {
   return {
      addKeyEventAdapter: vi.fn(),
      removeKeyEventAdapter: vi.fn(),
      addNewObject: vi.fn(),
      moveObject: vi.fn(),
      copyObject: vi.fn(),
      resizeObject: vi.fn(),
      resizeObjectTitle: vi.fn(),
      adjustTitleHeight: vi.fn(),
      updateLine: vi.fn(),
      moveFromContainer: vi.fn(),
      getNewObject: vi.fn(),
      getObjectType: vi.fn(),
   };
}

export function makeDialogService() {
   return {
      hasSlideout: vi.fn().mockReturnValue(false),
      showSlideoutFor: vi.fn(),
      open: vi.fn(),
      setSheetId: vi.fn(),
      objectDelete: vi.fn(),
   };
}

export function makeScaleService() {
   return {
      getScale: vi.fn().mockReturnValue(1),
      getCurrentScale: vi.fn().mockReturnValue(1),
      setScale: vi.fn(),
   };
}

export function makeComposerVsSearchService() {
   return {
      isSearchMode: vi.fn().mockReturnValue(false),
      searchString: "",
      assemblyVisible: vi.fn().mockReturnValue(true),
      isFocusAssembly: vi.fn().mockReturnValue(false),
      matchName: vi.fn().mockReturnValue(false),
   };
}

export function makeLineAnchorService() {
   return {
      addEditorName: vi.fn(),
      removeEditorName: vi.fn(),
      registerLineAnchor: vi.fn(),
      unregisterLineAnchor: vi.fn(),
      getHandles: vi.fn().mockReturnValue([]),
   };
}

// ---------------------------------------------------------------------------
// VSObjectModel factory — produces the minimal shape used by the setter
// ---------------------------------------------------------------------------

export function makeVsObjectModel(objectType = "VSText", name = "Text1"): VSObjectModel {
   return {
      objectType,
      absoluteName: name,
      assemblyAnnotationModels: [],
      dataAnnotationModels: [],
      objectFormat: {
         top: 100, left: 100, width: 200, height: 50,
         zIndex: 42, background: null, foreground: null,
         border: { top: "", bottom: "", left: "", right: "" },
         font: null,
      },
      titleFormat: {
         top: 0, left: 100, width: 200, height: 20,
         zIndex: 0, background: null, foreground: null,
      },
      active: true,
      visible: true,
      editable: true,
      container: null,
      containerType: null,
      script: null,
      interactionDisabled: false,
      inEmbeddedViewsheet: false,
      actionNames: [],
   } as any;
}

// ---------------------------------------------------------------------------
// Component factory — direct instantiation
// ---------------------------------------------------------------------------

export interface EocMocks {
   actionFactory: ReturnType<typeof makeActionFactoryMock>;
   miniToolbarService: ReturnType<typeof makeMiniToolbarService>;
   composerObjectService: ReturnType<typeof makeComposerObjectService>;
   viewsheetClient: any;
   selectionContainerChildrenService: any;
   modalService: any;
   changeDetectorRef: any;
   renderer: any;
   dragService: any;
   dataTipService: any;
   adhocFilterService: any;
   dialogService: ReturnType<typeof makeDialogService>;
   lineAnchorService: ReturnType<typeof makeLineAnchorService>;
   scaleService: ReturnType<typeof makeScaleService>;
   composerVsSearchService: ReturnType<typeof makeComposerVsSearchService>;
   vs: Viewsheet;
   model: VSObjectModel;
}

export function makeComponent(overrides: Partial<EocMocks> = {}): {
   comp: EditableObjectContainer;
   mocks: EocMocks;
} {
   const actionFactory     = overrides.actionFactory     ?? makeActionFactoryMock();
   const miniToolbarService= overrides.miniToolbarService?? makeMiniToolbarService();
   const composerObjectService = overrides.composerObjectService ?? makeComposerObjectService();
   const viewsheetClient   = overrides.viewsheetClient   ?? { commands: EMPTY, sendEvent: vi.fn(), runtimeId: "rt1" };
   const selectionContainerChildrenService = overrides.selectionContainerChildrenService ?? {
      pushModel: vi.fn(),
      childDragModel: { dragging: false, fromIndex: 0, toIndex: -1, isCurrentSelection: false, container: null },
   };
   const modalService      = overrides.modalService      ?? { open: vi.fn() };
   const changeDetectorRef = overrides.changeDetectorRef ?? { detectChanges: vi.fn(), markForCheck: vi.fn() };
   const renderer          = overrides.renderer          ?? { addClass: vi.fn(), removeClass: vi.fn() };
   const dragService       = overrides.dragService       ?? { reset: vi.fn(), put: vi.fn(), get: vi.fn().mockReturnValue(null), getDragData: vi.fn().mockReturnValue({}), currentlyDragging: false };
   const dataTipService    = overrides.dataTipService    ?? { dataTipName: null };
   const adhocFilterService= overrides.adhocFilterService?? { adhocFilterShowing: false };
   const dialogService     = overrides.dialogService     ?? makeDialogService();
   const lineAnchorService = overrides.lineAnchorService ?? makeLineAnchorService();
   const scaleService      = overrides.scaleService      ?? makeScaleService();
   const composerVsSearchService = overrides.composerVsSearchService ?? makeComposerVsSearchService();

   const comp = new EditableObjectContainer(
      miniToolbarService as any,
      { nativeElement: document.createElement("div") } as any,
      composerObjectService as any,
      viewsheetClient as any,
      selectionContainerChildrenService as any,
      modalService as any,
      changeDetectorRef as any,
      renderer as any,
      dragService as any,
      dataTipService as any,
      adhocFilterService as any,
      dialogService as any,
      lineAnchorService as any,
      actionFactory as any,
      scaleService as any,
      composerVsSearchService as any,
   );

   // Wire @ViewChild refs that Angular does not set in direct instantiation
   const classList = { add: vi.fn(), remove: vi.fn(), contains: vi.fn().mockReturnValue(false) };
   (comp as any).objectEditor = { nativeElement: { classList } };
   (comp as any).objectComponent = { resized: vi.fn() };

   const vs = overrides.vs ?? new Viewsheet();
   const model = overrides.model ?? makeVsObjectModel();

   // Must set viewsheet BEFORE vsObjectModel because the setter calls viewsheet.getAssembly()
   comp.viewsheet = vs;
   comp.vsObjectModel = model;

   const mocks: EocMocks = {
      actionFactory, miniToolbarService, composerObjectService, viewsheetClient,
      selectionContainerChildrenService, modalService, changeDetectorRef, renderer,
      dragService, dataTipService, adhocFilterService, dialogService, lineAnchorService,
      scaleService, composerVsSearchService, vs, model,
   };

   return { comp, mocks };
}
