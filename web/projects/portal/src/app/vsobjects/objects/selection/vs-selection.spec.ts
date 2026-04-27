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
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { ElementRef, NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionBaseModel } from "../../model/vs-selection-base-model";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../../model/vs-selection-tree-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { MiniMenu } from "../mini-toolbar/mini-menu.component";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { TitleCell } from "../title-cell/title-cell.component";
import { SelectionListCell } from "./selection-list-cell.component";
import { VSSelection } from "./vs-selection.component";
import { GlobalSubmitService } from "../../util/global-submit.service";

let createMeasureFormats: () => Map<string, VSFormatModel> = () => {
   return new Map([
      ["Measure Text0", TestUtils.createMockVSFormatModel()],
      ["Measure Bar0", TestUtils.createMockVSFormatModel()],
      ["Measure Bar(-)0", TestUtils.createMockVSFormatModel()]
   ]);
};

let createBaseModel: () => VSSelectionBaseModel = () => {
   return Object.assign({
      measureFormats: createMeasureFormats(),
      dropdown: true,
      hidden: false,
      listHeight: 0,
      cellHeight: 18,
      titleRatio: 1,
      singleSelection: false,
      showText: false,
      showBar: false,
      submitOnChange: false,
      title: "",
      titleFormat: TestUtils.createMockVSFormatModel(),
      titleVisible: false,
   });
};

let createMockSelectionValues: () => SelectionValueModel[] = () => {
   return [{
      label: "",
      value: "testString",
      state: 9, // To indicate selected (SelectionListCell.DISPLAY_STATES & 9 = 1)
      level: 0,
      measureLabel: "",
      measureValue: 0,
      maxLines: 0,
      formatIndex: 0,
      others: false,
      more: false,
      excluded: false,
      parentNode: null,
      path: ""
   },
   {
      label: "",
      value: "testString2",
      state: 8, // To indicate unselected (SelectionListCell.DISPLAY_STATES & 8 = 0)
      level: 0,
      measureLabel: "",
      measureValue: 0,
      maxLines: 0,
      formatIndex: 0,
      others: false,
      more: false,
      excluded: false,
      parentNode: null,
      path: ""
   }];
};

let createListModel: () => VSSelectionListModel = () => {
   return Object.assign({
         selectionList: {
            selectionValues: createMockSelectionValues(),
            formats: {0: TestUtils.createMockVSFormatModel()},
            measureMin: 0,
            measureMax: 0
         },
         sortType: 0,
         supportRemoveChild: true,
         adhocFilter: false,
         numCols: 1,
      },
      createBaseModel(),
      TestUtils.createMockVSObjectModel("VSSelectionList", "VSSelectionList1"),
   );
};

let createTreeModel: () => VSSelectionTreeModel = () => {
   return Object.assign({
         root: null,
         mode: 1,
         sortType: 0,
         expandAll: false,
         levels: 1
      },
      createBaseModel(),
      TestUtils.createMockVSObjectModel("VSSelectionTree", "VSSelectionTree1"),
   );
};

describe("VSSelection Test", () => {
   let vsSelection: VSSelection;
   let fixture: ComponentFixture<VSSelection>;
   let viewsheetClientService: any = { sendEvent: jest.fn() };
   viewsheetClientService.commands = observableOf([]);
   let renderer: any = {};
   let formDataService: any = {};
   let elementRef: any = {};
   let interactService: any = {
      addInteractable: jest.fn(),
      removeInteractable: jest.fn(),
      notify: jest.fn()
   };
   let contextService: any = {};
   let dataTipService: any = { isDataTip: jest.fn() };
   let fixedDropdownService: any;
   let adhocFilterService: any = { showFilter: jest.fn(), adhocFilterShowing: false };
   let globalSubmitService: any = {};
   let popService: any = { isCurrentPopComponent: jest.fn() };
   const changeRef: any = { detectChanges: jest.fn() };
   const zone: any = { run: jest.fn(), runOutsideAngular: jest.fn() };
   const scaleService = { getScale: jest.fn(), setScale: jest.fn(), getCurrentScale: jest.fn() };
   scaleService.getScale.mockImplementation(() => observableOf(1));
   let httpClient: HttpClient;

   beforeEach(async(() => {
      fixedDropdownService = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [ NgbModule, FormsModule, HttpClientTestingModule ],
         schemas: [NO_ERRORS_SCHEMA],
         declarations: [
            VSSelection, MiniToolbar, TitleCell, SelectionListCell, DefaultFocusDirective,
            VSPopComponentDirective, InteractableDirective, MiniMenu, SafeFontDirective],
         providers: [
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: ElementRef, useValue: elementRef },
            { provide: Renderer2, useValue: renderer },
            { provide: InteractService, useValue: interactService },
            { provide: CheckFormDataService, useValue: formDataService },
            { provide: ContextProvider, useValue: contextService },
            { provide: ScaleService, useValue: scaleService },
            PopComponentService,
            { provide: DataTipService, useValue: dataTipService },
            { provide: FixedDropdownService, useValue: fixedDropdownService },
            { provide: AdhocFilterService, useValue: adhocFilterService },
            GlobalSubmitService
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSSelection);
      fixture.componentInstance.selectionValues = createMockSelectionValues();
      fixture.componentInstance.model = createListModel();
      fixture.detectChanges();
      const httpClient = fixture.debugElement.injector.get(HttpClient);
      vsSelection = new VSSelection(
         viewsheetClientService, formDataService, renderer, adhocFilterService, elementRef, changeRef,
         zone, scaleService, contextService, dataTipService, fixedDropdownService, globalSubmitService,
         popService, httpClient, null);
   }));

   // Bug #15994 selection bars not showing on selection trees
   it("should have a cell width", () => {
      let treeModel: VSSelectionTreeModel = createTreeModel();
      treeModel.objectFormat.width = 100;
      vsSelection.model = treeModel;

      expect(vsSelection.cellWidth).toBeDefined();
   });

   it("should add asterisks to the title when it's a dropdown and a cell is selected", () => {
      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.pendingSubmit).toBe(true);
      });
   });

   it("should create listSelectedString to include only mockLabelSelected", () => {
      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.listSelectedString).toBe("testString");
      });
   });

   it("should not have show-others icon and text if all values are excluded", () => {
      fixture.componentInstance.selectionValues
         .forEach(svalue => svalue.state = SelectionValue.STATE_EXCLUDED);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.controller.showOther).toBe(false);
      });
   });

   //Bug #18554 should show dropdown pane when click show button.
   it("check selection dropdown pane height and visible", () => {
      fixture.componentInstance.model.dropdown = true;
      fixture.componentInstance.model.hidden = false;
      fixture.componentInstance.model.listHeight = 6;
      fixture.componentInstance.selectionValues = [];
      fixture.componentInstance.updateTable();
      fixture.detectChanges();

      // selection-list-body should exist when hidden is false and have height equal to list
      // height multiplied by cell height
      let listBody = fixture.nativeElement.querySelector("div.selection-list-body");
      expect(listBody).not.toBeNull();
      expect(listBody.style["height"]).toEqual("108px");
   });

   //Bug #20791
   it("selection list border should show up", () => {
      let listModel = TestUtils.createMockVSSelectionListModel("Region");
      let cellFormat = TestUtils.createMockVSFormatModel();
      cellFormat.border = {
         bottom: "0px none #888888",
         left: "0px none #888888",
         right: "0px none #888888",
         top: "none"
      };
      listModel.selectionList.formats = {0: cellFormat};
      listModel.selectionList.selectionValues = createMockSelectionValues();
      listModel.title = "Region";
      listModel.titleFormat.border = {
         bottom: "1px none #888888",
         left: "1px none #888888",
         right: "1px none #888888",
         top: "1px none #888888"
      };
      listModel.objectFormat.border = {
         bottom: "1px solid #888888",
         left: "1px solid #888888",
         right: "1px solid #888888",
         top: "1px solid #888888"
      };
      vsSelection.model = listModel;

      expect(vsSelection.leftMargin).toEqual(0);
      expect(vsSelection.leftMarginTitle).toEqual(-1);
      expect(vsSelection.topMarginTitle).toEqual(-1);

      vsSelection.model.titleFormat.border = {
         bottom: "0px none #888888",
         left: "0px none #888888",
         right: "0px none #888888",
         top: "0px none #888888"
      };

      expect(vsSelection.leftMarginTitle).toEqual(-1);
   });

   it("should not shift non-dropdown selection in bottom-tab (backend already positions correctly)", () => {
      let listModel = createListModel();
      listModel.dropdown = false;
      listModel.objectFormat.top = 300;
      listModel.objectFormat.height = 200;
      listModel.titleFormat.height = 20;
      listModel.titleVisible = true;
      listModel.containerType = "VSTab";
      listModel.container = "Tab1";

      let tabModel = Object.assign(
         { bottomTabs: true },
         TestUtils.createMockVSObjectModel("VSTab", "Tab1")
      );

      contextService.viewer = true;
      fixture.componentInstance.model = listModel;
      fixture.componentInstance.vsInfo = { vsObjects: [tabModel] } as any;

      // non-dropdown: objectFormat.top already accounts for full component height
      expect(fixture.componentInstance.topPosition).toBe(300);
   });

   // Bug #74494 — dropdown selection in a bottom-tabs tab must derive its Y
   // from the parent tab's objectFormat.top, not its own (possibly stale)
   // objectFormat.top. Stale values occur when Tab.bottomTabs is toggled via
   // script and the child pixelOffset update doesn't reach the client.
   it("should anchor collapsed dropdown selection to parent tab top, ignoring stale objectFormat.top", () => {
      let listModel = createListModel();
      listModel.dropdown = true;
      listModel.hidden = true;                 // dropdown panel collapsed
      listModel.searchDisplayed = false;
      listModel.objectFormat.top = 9999;       // stale; must be ignored
      listModel.titleFormat.height = 20;
      listModel.containerType = "VSTab";
      listModel.container = "Tab1";

      let tabModel = Object.assign(
         { bottomTabs: true },
         TestUtils.createMockVSObjectModel("VSTab", "Tab1")
      );
      tabModel.objectFormat.top = 544;

      contextService.viewer = true;
      fixture.componentInstance.model = listModel;
      fixture.componentInstance.vsInfo = { vsObjects: [tabModel] } as any;

      // title flush with tab bar: 544 - titleHeight(20) = 524
      expect(fixture.componentInstance.topPosition).toBe(524);
   });

   it("should shift expanded dropdown selection above parent tab by body height", () => {
      let listModel = createListModel();
      listModel.dropdown = true;
      listModel.searchDisplayed = false;
      listModel.objectFormat.top = 9999;       // stale; must be ignored
      listModel.titleFormat.height = 20;
      listModel.cellHeight = 18;
      listModel.listHeight = 5;                // getBodyHeight → 18 * 5 = 90
      listModel.containerType = "VSTab";
      listModel.container = "Tab1";

      let tabModel = Object.assign(
         { bottomTabs: true },
         TestUtils.createMockVSObjectModel("VSTab", "Tab1")
      );
      tabModel.objectFormat.top = 544;

      contextService.viewer = true;
      fixture.componentInstance.model = listModel;
      // model setter forces hidden=true for new dropdown models; flip after assignment
      fixture.componentInstance.model.hidden = false;
      fixture.componentInstance.vsInfo = { vsObjects: [tabModel] } as any;

      // tabTop(544) - titleHeight(20) - bodyHeight(90) = 434
      expect(fixture.componentInstance.topPosition).toBe(434);
   });
});

// Direct unit tests of setQuickSwitchHover's positioning math, without TestBed.
// Mocks the renderer to capture setStyle calls and constructs minimal cell/list
// elements with controlled getBoundingClientRect / offsetWidth values.
describe("VSSelection - quick-switch overlay positioner", () => {
   let vsSelection: VSSelection;
   let setStyleCalls: Array<{ el: any; prop: string; value: string }>;
   let listEl: any;
   let btnEl: any;

   const makeRect = (left: number, top: number, width: number, height: number): DOMRect => ({
      left, top, width, height,
      right: left + width,
      bottom: top + height,
      x: left, y: top,
      toJSON: () => ({})
   } as DOMRect);

   // Pass labelRect to model a cell whose label has been laid out; omit it to model
   // the (template-impossible) case where .selection-list-cell-label is missing — used
   // only by the regression test that pins the fallback contract.
   const makeCell = (rect: DOMRect, hasMeasure: boolean, labelRect?: DOMRect): any => ({
      getBoundingClientRect: () => rect,
      querySelector: (sel: string) => {
         if(sel === ".selection-list-cell-content") {
            return hasMeasure ? {} : null;
         }

         if(sel === ".selection-list-cell-label") {
            return labelRect ? { getBoundingClientRect: () => labelRect } : null;
         }

         return null;
      }
   });

   // Reverse-find so the helpers return the LAST setStyle write for a given (el, prop) —
   // that is what the rendered DOM reflects.  find() would silently mask a future
   // reset-then-set sequence.
   const lastStyle = (el: any, prop: string) =>
      [...setStyleCalls].reverse().find(c => c.el === el && c.prop === prop)?.value;
   const btnLeft = () => lastStyle(btnEl, "left");
   const btnRight = () => lastStyle(btnEl, "right");
   const listWidth = () => lastStyle(listEl, "width");

   beforeEach(() => {
      setStyleCalls = [];
      listEl = {
         getBoundingClientRect: () => makeRect(0, 0, 200, 300),
         querySelector: jest.fn(() => null),
         querySelectorAll: jest.fn(() => [])
      };
      btnEl = { offsetWidth: 24 };

      const renderer: any = {
         setStyle: (el: any, prop: string, value: string) =>
            setStyleCalls.push({ el, prop, value }),
         removeStyle: jest.fn(),
         setAttribute: jest.fn(),
         addClass: jest.fn(),
         removeClass: jest.fn(),
         listen: jest.fn(() => () => {})
      };

      const elementRef: any = {
         nativeElement: {
            querySelector: (sel: string) => sel === ".selection-list" ? listEl : null
         }
      };

      const zone: any = { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() };

      const scaleService: any = {
         getScale: () => observableOf(1),
         setScale: jest.fn(),
         getCurrentScale: jest.fn()
      };

      vsSelection = new VSSelection(
         { sendEvent: jest.fn(), commands: observableOf([]) } as any,
         {} as any,
         renderer,
         { showFilter: jest.fn(), adhocFilterShowing: false } as any,
         elementRef,
         { detectChanges: jest.fn() } as any,
         zone,
         scaleService,
         {} as any,
         { isDataTip: jest.fn() } as any,
         { open: jest.fn() } as any,
         {} as any,
         { isCurrentPopComponent: jest.fn() } as any,
         {} as HttpClient,
         null
      );

      (vsSelection as any).quickSwitchOverlay = { nativeElement: btnEl };
      (vsSelection as any).quickSwitchOverlayIcon = { nativeElement: {} };
      (vsSelection as any).verticalScrollWrapper = { nativeElement: {} };
      (vsSelection as any).scale = 1;
      (vsSelection as any).scrollbarWidth = 16;
      // Override the showScroll getter (depends on internal model state); each test
      // can re-define it before calling setQuickSwitchHover.
      Object.defineProperty(vsSelection, "showScroll", { get: () => false, configurable: true });
   });

   it("with measure content: anchors button at labelEl.right (column-agnostic)", () => {
      const cell = makeCell(makeRect(0, 0, 100, 30), true, makeRect(0, 0, 70, 30));
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // labelRight(70) - listLeft(0) = 70
      expect(btnLeft()).toBe("70px");
      expect(btnRight()).toBe("auto");
      expect(listWidth()).toBe("200px");
   });

   it("non-last column without measure: anchors button at right edge of hovered cell", () => {
      const cell = makeCell(makeRect(0, 0, 100, 30), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // cellRight(100) - btn(24) = 76; list(200) - 0 - 24 = 176; min = 76
      expect(btnLeft()).toBe("76px");
      expect(btnRight()).toBe("auto");
   });

   it("last/single column without measure, with scrollbar: clamps to listRight - scrollbar - btn", () => {
      Object.defineProperty(vsSelection, "showScroll", { get: () => true, configurable: true });
      const cell = makeCell(makeRect(0, 0, 200, 30), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // cellRight(200) - btn(24) = 176; list(200) - scrollbar(16) - btn(24) = 160; min = 160
      expect(btnLeft()).toBe("160px");
   });

   it("clamps button left to 0 when cell is narrower than the button", () => {
      const cell = makeCell(makeRect(0, 0, 10, 30), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // cellRight(10) - btn(24) = -14 → clamped to 0 by Math.max(0, ...)
      expect(btnLeft()).toBe("0px");
   });

   it("does not expand body width or apply margin-left to adjacent columns", () => {
      const cell = makeCell(makeRect(0, 0, 100, 30), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // Required writes: button position + list width lock.
      expect(setStyleCalls.find(c => c.el === btnEl && c.prop === "left")).toBeDefined();
      expect(setStyleCalls.find(c => c.el === listEl && c.prop === "width")).toBeDefined();
      // Forbidden writes from the removed column-shift path: any margin-left, or any
      // width write on something other than the list element (body/row expansion).
      expect(setStyleCalls.find(c => c.prop === "margin-left")).toBeUndefined();
      expect(setStyleCalls.find(c => c.prop === "width" && c.el !== listEl)).toBeUndefined();
   });

   it("converts viewport coordinates to CSS px when scale != 1", () => {
      (vsSelection as any).scale = 2;
      // List at viewport (100, 0), 400x600 viewport px → 200x300 CSS px.
      listEl.getBoundingClientRect = () => makeRect(100, 0, 400, 600);
      // Cell at viewport (100, 0), 200x60 viewport px → 100x30 CSS px (non-last column).
      const cell = makeCell(makeRect(100, 0, 200, 60), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // (cellRight 300 - listLeft 100) / scale 2 = 100 CSS px → 100 - btn(24) = 76;
      // listWidth 200 - 0 - btn(24) = 176; min = 76.
      expect(btnLeft()).toBe("76px");
      expect(listWidth()).toBe("200px");
   });

   it("falls back to cell-rect math when measure content has no labelEl (defensive)", () => {
      // Template-impossible state: .selection-list-cell-content present, but no
      // .selection-list-cell-label.  Pins the fallback contract so a future template
      // change cannot silently regress this case.
      const cell = makeCell(makeRect(0, 0, 100, 30), true /* hasMeasure */ /* no labelRect */);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // Falls into the else branch: cellRight(100) - btn(24) = 76; min vs 176 = 76.
      expect(btnLeft()).toBe("76px");
   });

   it("returns early without writing position styles when the list element is missing", () => {
      // Stub elementRef to return no .selection-list child.
      (vsSelection as any).elementRef = {
         nativeElement: { querySelector: () => null }
      };
      const cell = makeCell(makeRect(0, 0, 100, 30), false);
      vsSelection.setQuickSwitchHover(cell, false, () => {});

      // No left/width writes — the early return fires before measurement.
      expect(btnLeft()).toBeUndefined();
      expect(listWidth()).toBeUndefined();
      // _currentHoverElement is still set by setQuickSwitchHover before the early return,
      // documenting the contract that the hover state is recorded even when the list
      // element cannot be located.
      expect((vsSelection as any)._currentHoverElement).toBe(cell);
   });
});
