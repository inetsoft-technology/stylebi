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
 * Shared test helpers for VSSelection multi-pass TL specs.
 */

import { Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../../model/vs-selection-tree-model";
import { SelectionValueModel } from "../../model/selection-value-model";
import { SelectionListModel } from "../../model/selection-list-model";
import { CompositeSelectionValueModel } from "../../model/composite-selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { ExpandTreeNodesCommand } from "../../command/expand-tree-nodes-command";
import { VSSelection } from "./vs-selection.component";

const createMeasureFormats = (): Map<string, VSFormatModel> => new Map([
   ["Measure Text0", TestUtils.createMockVSFormatModel()],
   ["Measure Bar0", TestUtils.createMockVSFormatModel()],
   ["Measure Bar(-)0", TestUtils.createMockVSFormatModel()],
]);

export function makeSelectionValue(overrides: Partial<SelectionValueModel> = {}): SelectionValueModel {
   return {
      label: "",
      value: "test",
      state: 8,
      level: 0,
      measureLabel: "",
      measureValue: 0,
      maxLines: 0,
      formatIndex: 0,
      others: false,
      more: false,
      excluded: false,
      parentNode: null,
      path: "",
      ...overrides,
   };
}

export function makeMockSelectionValues(): SelectionValueModel[] {
   return [
      makeSelectionValue({ label: "Item 1", value: "item1", state: 9 }),
      makeSelectionValue({ label: "Item 2", value: "item2", state: 8 }),
   ];
}

export function makeEmptySelectionList(): SelectionListModel {
   return {
      selectionValues: [],
      formats: { 0: TestUtils.createMockVSFormatModel() },
      measureMin: 0,
      measureMax: 0,
   };
}

export function makeCompositeRoot(value = "root"): CompositeSelectionValueModel {
   return {
      ...makeSelectionValue({ value }),
      selectionList: makeEmptySelectionList(),
   };
}

export function makeMockListModel(overrides: Partial<VSSelectionListModel> = {}): VSSelectionListModel {
   const objectFormat = TestUtils.createMockVSFormatModel();
   objectFormat.width = 200;
   objectFormat.height = 150;

   return Object.assign(
      TestUtils.createMockVSSelectionListModel("TestSelectionList"),
      {
         objectFormat,
         selectionList: {
            selectionValues: makeMockSelectionValues(),
            formats: { 0: TestUtils.createMockVSFormatModel() },
            measureMin: 0,
            measureMax: 0,
         },
         measureFormats: createMeasureFormats(),
         title: "Test Selection",
         submitOnChange: true,
         numCols: 1,
         listHeight: 5,
         cellHeight: 18,
      },
      overrides,
   );
}

export function makeMockTreeModel(overrides: Partial<VSSelectionTreeModel> = {}): VSSelectionTreeModel {
   return Object.assign(
      TestUtils.createMockVSSelectionTreeModel("TestSelectionTree"),
      {
         measureFormats: createMeasureFormats(),
         title: "Test Selection Tree",
         submitOnChange: true,
         singleSelectionLevels: [],
      },
      overrides,
   );
}

export function createMockController(model?: VSSelectionListModel | VSSelectionTreeModel | any): any {
   const unappliedSubject = new Subject<boolean>();
   const updateViewSubject = new Subject<void>();
   return {
      model: model ?? makeMockListModel(),
      unappliedSubject,
      updateViewSubject,
      unappliedSelections: [],
      applySelections: vi.fn(),
      searchSelections: vi.fn(),
      sortSelections: vi.fn(),
      reverseSelections: vi.fn(),
      clearSelections: vi.fn(),
      hideSelf: vi.fn(),
      showSelf: vi.fn(),
      hideChild: vi.fn(),
      showChild: vi.fn(),
      visibleValues: makeMockSelectionValues(),
      getCellFormat: vi.fn(() => TestUtils.createMockVSFormatModel()),
      selectionStateUpdated: vi.fn(),
      updateStatusByValues: vi.fn(),
      hideExcludedValues: vi.fn(),
   };
}

export function createMockActions(): any {
   const onAssemblyActionEvent = new Subject<any>();
   return {
      onAssemblyActionEvent,
      toolbarActions: [],
      menuActions: [],
      getMoreActions: vi.fn(() => []),
   };
}

export function createMockGlobalSubmitService(): any {
   const globalSubmitSubject = new Subject<string>();
   const updateSelectionsSubject = new Subject<Map<string, any[]>>();
   return {
      globalSubmit: vi.fn(() => globalSubmitSubject.asObservable()),
      updateSelections: vi.fn(() => updateSelectionsSubject.asObservable()),
      updateState: vi.fn(),
   };
}

export function setMockController(comp: VSSelection, controller: any): void {
   comp["_controller"] = controller;
}

/** Wire controller through the setter so subscriptions are established. */
export function assignController(comp: VSSelection, controller: any): void {
   comp.controller = controller;
}

/** Sync selection values on the component and its list model (used by updateListSelectedString). */
export function setSelectionValuesForTest(comp: VSSelection, values: SelectionValueModel[]): void {
   comp.selectionValues = values;

   const listModel = comp.model as VSSelectionListModel;
   if(listModel?.selectionList) {
      listModel.selectionList.selectionValues = values;
   }

   const controllerModel = comp.controller?.model as VSSelectionListModel;
   if(controllerModel?.selectionList) {
      controllerModel.selectionList.selectionValues = values;
   }
}

export function createCapturingRenderer(contains = false): any {
   const renderer: any = {
      listen: vi.fn((_target: string, event: string, handler: Function) => {
         if(event === "mousedown") {
            renderer.mousedownHandler = handler;
         }
         return vi.fn();
      }),
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
   };
   renderer.elementRef = {
      nativeElement: {
         querySelector: vi.fn(() => null),
         contains: vi.fn(() => contains),
      },
   };
   return renderer;
}

export function makeExpandTreeNodesCommand(
   overrides: Partial<ExpandTreeNodesCommand> = {},
): ExpandTreeNodesCommand {
   return {
      scriptChanged: false,
      expand: false,
      assembly: "TestSelectionTree",
      ...overrides,
   };
}

export async function renderSelectionComponent(overrides: any = {}) {
   const viewsheetClient = overrides.viewsheetClient ?? {
      sendEvent: vi.fn(),
      commands: new Subject<any>().asObservable(),
   };
   const scaleSubject = overrides.scaleSubject ?? new Subject<number>();
   const scaleService = overrides.scaleService ?? {
      getScale: vi.fn(() => scaleSubject.asObservable()),
   };
   const context = overrides.context ?? { viewer: true, preview: false };
   const dataTipService = overrides.dataTipService ?? { isDataTip: vi.fn(() => false) };
   const dropdownService = overrides.dropdownService ?? { open: vi.fn() };
   const globalSubmitService = overrides.globalSubmitService ?? createMockGlobalSubmitService();
   const popService = overrides.popService ?? { isCurrentPopComponent: vi.fn(() => false) };
   const adhocFilterService = overrides.adhocFilterService ?? { showFilter: vi.fn(() => () => {}) };
   const formDataService = {};
   const selectionMobileSubject = overrides.selectionMobileSubject ?? new Subject<{ obj: any; max: boolean }>();
   const selectionMobileService = overrides.selectionMobileService ?? {
      maxSelectionChanged: vi.fn(() => selectionMobileSubject.asObservable()),
   };
   const renderer = {
      listen: vi.fn(() => () => {}),
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
   };
   const elementRef = {
      nativeElement: {
         querySelector: vi.fn(() => null),
         contains: vi.fn(() => false),
      },
   };
   const changeDetectorRef = { detectChanges: vi.fn() };
   const zone = { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() };

   const comp = new VSSelection(
      viewsheetClient,
      formDataService as any,
      renderer as any,
      adhocFilterService as any,
      elementRef as any,
      changeDetectorRef as any,
      zone as any,
      scaleService as any,
      context as any,
      dataTipService as any,
      dropdownService as any,
      globalSubmitService as any,
      popService as any,
      {} as any,
      selectionMobileService as any,
   );
   comp.selectionValues = makeMockSelectionValues();
   comp.model = overrides.model || makeMockListModel();

   return {
      comp,
      fixture: null,
      globalSubmitService,
      scaleSubject,
      selectionMobileSubject,
      viewsheetClient,
      renderer,
      elementRef,
      changeDetectorRef,
   };
}
