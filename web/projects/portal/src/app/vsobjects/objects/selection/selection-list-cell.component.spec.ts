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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { BrowserModule, By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { CellRegion } from "./cell-region";
import { SelectionListCell } from "./selection-list-cell.component";
import { VSSelection } from "./vs-selection.component";
import { ComposerContextProviderFactory, ContextProvider } from "../../context-provider.service";

describe("Selection List Cell Test", () => {
   const createModel: () => SelectionValueModel = () => {
      return {
         formatIndex: 0,
         label: "AMG Logistics",
         level: 0,
         measureLabel: "19",
         measureValue: 0.6785714285714286,
         maxLines: 0,
         state: 8,
         value: "AMG Logistics",
         others: false,
         more: false,
         excluded: false,
         parentNode: null,
         path: null
      };
   };
   const createFormat: () => VSFormatModel = () => {
      return {
         alpha: 1,
         background: "",
         border: {
            bottom: null,
            left: null,
            right: null,
            top: null
         },
         decoration: "",
         font: "10px Roboto, Arial, verdana, arial, helvetica, sans-serif",
         foreground: "#2b2b2b",
         hAlign: "left",
         height: 0,
         left: 0,
         top: 0,
         vAlign: "top",
         justifyContent: "flex-start",
         alignItems: "stretch",
         width: 0,
         wrapping: {
            overflow: null,
            whiteSpace: "nowrap",
            wordWrap: null
         },
         zIndex: 0,
         bringToFrontEnabled: false,
         sendToBackEnabled: false,
         position: ""
      };
   };
   const createMeasureFormats: () => Map<string, VSFormatModel> = () => {
      return new Map([
         ["Measure Text0", TestUtils.createMockVSFormatModel()],
         ["Measure Bar0", TestUtils.createMockVSFormatModel()],
         ["Measure Bar(-)0", TestUtils.createMockVSFormatModel()]
      ]);
   };
   const createSelectionListModel: () => VSSelectionListModel = () => {
      return Object.assign({
         measureFormats: createMeasureFormats(),
         dropdown: true,
         hidden: true,
         listHeight: 3,
         cellHeight: 18,
         selectionList: null,
         sortType: 0,
         supportRemoveChild: true,
         adhocFilter: false,
         numCols: 1,
         titleRatio: 1,
         singleSelection: false,
         title: "title",
         titleVisible: true,
         titleFormat: TestUtils.createMockVSFormatModel(),
         showText: true,
         showBar: true,
         measure: "",
         barWidth: 0,
         textWidth: 0,
         showSubmitOnChange: false,
         submitOnChange: false,
         quickSwitchAllowed: false
      }, TestUtils.createMockVSObjectModel("VSSelectionList", "VSSelectionList1"));
   };
   let selectionListCell: SelectionListCell;
   let vsSelectionComponent: any;
   let selectionListController: any;
   let selectionTreeController: any;
   let interactService: any;
   let renderer: any;
   let cell: any;
   let fixture: ComponentFixture<SelectionListCell>;

   beforeEach(async(() => {
      vsSelectionComponent = { getMarginSize: jest.fn() };
      selectionListController = { getCellFormat: jest.fn() };
      selectionTreeController = { getCellFormat: jest.fn() };
      interactService = {
         notify: jest.fn(),
         addInteractable: jest.fn(),
         removeInteractable: jest.fn()
      };
      renderer = { listen: jest.fn(() => ({})) };

      vsSelectionComponent.model = createSelectionListModel();
      vsSelectionComponent.model.measureFormats["Measure Text0"] = TestUtils.createMockVSFormatModel();
      vsSelectionComponent.model.measureFormats["Measure Bar0"] = TestUtils.createMockVSFormatModel();
      vsSelectionComponent.model.measureFormats["Measure Bar(-)0"] = TestUtils.createMockVSFormatModel();
      selectionListController.getCellFormat.mockImplementation(() => createFormat());
      vsSelectionComponent.controller = selectionListController;
      vsSelectionComponent.getMarginSize.mockImplementation(() => 0);

      TestBed.configureTestingModule({
         imports: [ BrowserModule, HttpClientTestingModule, FormsModule, NgbModule ],
         declarations: [ SelectionListCell, InteractableDirective ],
         providers: [
            { provide: VSSelection, useValue: vsSelectionComponent },
            { provide: Renderer2, useValue: renderer },
            { provide: ContextProvider, useFactory: ComposerContextProviderFactory },
            { provide: InteractService, useValue: interactService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
      fixture = TestBed.createComponent(SelectionListCell);
      fixture.whenStable().then(() => {
         selectionListCell = <SelectionListCell>fixture.componentInstance;
         selectionListCell.selectionValue = createModel();
         fixture.detectChanges();
      });
   }));

   // Bug #10494 make sure selectionlist show text value properly.
   it("should show text value when 'Text' is set to true", (done) => {
      selectionListCell.showText = true;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         cell = fixture.debugElement.query(By.css("div.selection-list-measure-text"));
         expect(cell).not.toBe(null);
         done();
      });
   });

   it("should not show text value when 'Text' is set to false", (done) => {
      selectionListCell.showText = false;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         cell = fixture.debugElement.query(By.css("div.selection-list-measure-text"));
         expect(cell).toBe(null);
         done();
      });
   });

   // Bug #10050 make sure selectionlist bar is displayed properly.
   it("should show bar when 'Bar' is set to true", (done) => {
      selectionListCell.showBar = true;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         cell = fixture.debugElement.query(By.css("div.selection-list-bar-outer"));
         expect(cell).not.toBe(null);
         done();
      });
   });

   it("should not show bar when 'Bar' is set to false", (done) => {
      selectionListCell.showBar = false;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         cell = fixture.debugElement.query(By.css("div.selection-list-bar-outer"));
         expect(cell).toBe(null);
         done();
      });
   });

   // Bug #16317 use measure text format
   it("should use measure text format", (done) => {
      vsSelectionComponent.model.showText = true;
      vsSelectionComponent.model.measureFormats["Measure Text0"].decoration = "underline line-through";
      selectionListCell.ngOnInit();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         cell = fixture.nativeElement.querySelector("div.selection-list-measure-text");
         expect(cell.style["text-decoration"]).toEqual("underline line-through");
         done();
      });
   });

   //Bug #18047 should apply backgroud color on selection measure bar and text
   it("should set backgound color on selection measure bar and text", () => {
      vsSelectionComponent.model.showText = true;
      vsSelectionComponent.model.showBar = true;
      let vsformatModel = TestUtils.createMockVSFormatModel();
      vsformatModel.background = "rgba(255,255,0,1.0)";
      vsSelectionComponent.model.measureFormats["Measure Text0"] = vsformatModel;
      vsSelectionComponent.model.measureFormats["Measure Bar0"] = vsformatModel;
      selectionListCell.ngOnInit();
      fixture.detectChanges();

      let cellText = fixture.nativeElement.querySelector("div.selection-list-measure-text");
      let cellBar = fixture.nativeElement.querySelector("div.selection-list-bar-outer");
      expect(cellText.style["background-color"]).toBe("rgb(255, 255, 0)");
      expect(cellBar.style["background-color"]).toBe("rgb(255, 255, 0)");
   });

   describe("Quick-switch behavior", () => {
      const viewerContext = () =>
         new ContextProvider(true, false, false, false, false, false, false, false, false, false, false);

      // click() — switching path (quick-switch button or long-press)
      it("should emit { toggle: true, toggleAll: false } on switching click for a list cell", () => {
         selectionListCell.contextProvider = viewerContext();
         let emitted: any;
         selectionListCell.selectionStateChanged.subscribe(v => emitted = v);

         selectionListCell.click(new MouseEvent("click"), true);

         expect(emitted).toEqual({ toggle: true, toggleAll: false });
      });

      it("should emit { toggle: false, toggleAll: true } on switching click for a parent-ID tree cell", () => {
         selectionListCell.contextProvider = viewerContext();
         selectionListCell.isParentIDTree = true;
         let emitted: any;
         selectionListCell.selectionStateChanged.subscribe(v => emitted = v);

         selectionListCell.click(new MouseEvent("click"), true);

         expect(emitted).toEqual({ toggle: false, toggleAll: true });
      });

      // quickSwitchAllowed field — computed in updateModelInfo()
      it("should set quickSwitchAllowed to true in viewer context for a list assembly", () => {
         vsSelectionComponent.model.quickSwitchAllowed = true;
         selectionListCell.contextProvider = viewerContext();
         selectionListCell.ngOnInit();

         expect(selectionListCell.quickSwitchAllowed).toBe(true);
      });

      it("should set quickSwitchAllowed to false when not in viewer or preview", () => {
         vsSelectionComponent.model.quickSwitchAllowed = true;
         // Default context from ComposerContextProviderFactory: viewer=false, preview=false
         selectionListCell.ngOnInit();

         expect(selectionListCell.quickSwitchAllowed).toBe(false);
      });

      it("should set quickSwitchAllowed to false on mobile even in viewer context", () => {
         vsSelectionComponent.model.quickSwitchAllowed = true;
         selectionListCell.contextProvider = viewerContext();
         selectionListCell.mobile = true;
         selectionListCell.ngOnInit();

         expect(selectionListCell.quickSwitchAllowed).toBe(false);
      });

      // Long-press timer cancellation
      it("should cancel the long-press timer on touchmove", () => {
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         expect((selectionListCell as any).touchTimeout).not.toBeNull();

         selectionListCell.onTouchMove();

         expect((selectionListCell as any).touchTimeout).toBeNull();
      });

      it("should cancel the long-press timer on touchcancel", () => {
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         expect((selectionListCell as any).touchTimeout).not.toBeNull();

         selectionListCell.onTouchCancel();

         expect((selectionListCell as any).touchTimeout).toBeNull();
      });

      // Post-long-press click suppression
      it("should suppress the real browser click that follows a long-press", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");

         // Long-press: start timer, let it fire
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         jest.runAllTimers(); // fires → switching emit

         expect(emitSpy).toHaveBeenCalledWith({ toggle: true, toggleAll: false });
         emitSpy.mockClear();

         // Real browser click arrives when the finger lifts — should be suppressed
         selectionListCell.click(new MouseEvent("click"));

         expect(emitSpy).not.toHaveBeenCalled();
         jest.useRealTimers();
      });

      it("should not suppress a normal click that follows a regular tap", () => {
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");

         // Normal tap: start timer, cancel before it fires
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         selectionListCell.onTouchEnd();

         selectionListCell.click(new MouseEvent("click"));

         expect(emitSpy).toHaveBeenCalled();
      });

      it("should not consume the suppression flag on a right-click after a long-press", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");

         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         jest.runAllTimers(); // fires → switching emit
         emitSpy.mockClear();

         // Right-click arrives — must not consume the suppression flag
         selectionListCell.click(new MouseEvent("click", { button: 2 }));

         // The following left-click must still be suppressed
         selectionListCell.click(new MouseEvent("click"));
         expect(emitSpy).not.toHaveBeenCalled();
         jest.useRealTimers();
      });

      it("should reset the suppression flag when touchcancel fires after a long-press", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");

         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         jest.runAllTimers(); // fires → switching emit
         emitSpy.mockClear();

         // OS intercepts (e.g. context menu) — touchcancel fires, browser click never arrives
         selectionListCell.onTouchCancel();

         // The next click (e.g. from mouse) must not be suppressed
         selectionListCell.click(new MouseEvent("click"));
         expect(emitSpy).toHaveBeenCalled();
         jest.useRealTimers();
      });

      // Tree-icon long-press: stale suppression flag cleanup
      it("should not toggle folder and should reset the suppression flag when a long-press fires on the tree icon", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");
         vsSelectionComponent.controller.toggleNode = jest.fn();
         vsSelectionComponent.folderToggled = jest.fn();

         // Long-press fires
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         jest.runAllTimers(); // fires → switching emit
         emitSpy.mockClear();

         // Browser synthesizes click on tree icon — toggleFolder is called
         selectionListCell.toggleFolder(new MouseEvent("click"));

         // Folder must not have toggled (long-press already acted)
         expect(vsSelectionComponent.controller.toggleNode).not.toHaveBeenCalled();

         // The suppression flag must be cleared — next mouse click must NOT be suppressed
         selectionListCell.click(new MouseEvent("click"));
         expect(emitSpy).toHaveBeenCalled();
         jest.useRealTimers();
      });

      // Tree-icon long-press: synthesized click consumed by click() leaves stale longPressFired
      it("should toggle folder on a mouse click even when longPressFired is stale after a non-folder long-press", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const emitSpy = jest.spyOn(selectionListCell.selectionStateChanged, "emit");
         vsSelectionComponent.controller.toggleNode = jest.fn();
         vsSelectionComponent.folderToggled = jest.fn();

         // Long-press fires on a non-folder cell (e.g. the selection icon)
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockEvent);
         jest.runAllTimers(); // fires → switching emit
         emitSpy.mockClear();

         // Synthesized click lands on the selection icon, not the tree icon — click() consumes
         // suppressNextClick but does NOT (before the fix) clear longPressFired
         selectionListCell.click(new MouseEvent("click"));
         expect(emitSpy).not.toHaveBeenCalled(); // suppressed as expected

         // Subsequent mouse click on the folder toggle must NOT be blocked by stale longPressFired
         selectionListCell.toggleFolder(new MouseEvent("click"));
         expect(vsSelectionComponent.controller.toggleNode).toHaveBeenCalled();

         jest.useRealTimers();
      });

      // Bubbling path: click() stopPropagation prevents label-container selectRegion call
      it("should stop event propagation when suppressing the click after a long-press", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();

         const mockTouchEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockTouchEvent);
         jest.runAllTimers(); // fires long-press

         const clickEvent = new MouseEvent("click");
         const stopPropSpy = jest.spyOn(clickEvent, "stopPropagation");

         selectionListCell.click(clickEvent);

         expect(stopPropSpy).toHaveBeenCalled();
         jest.useRealTimers();
      });

      // Direct-hit path: selectRegion() guard suppresses a synthesized click landing on the
      // label-container or measure areas directly, without click() or toggleFolder() clearing
      // the flag first.
      it("should not emit regionClicked when a long-press synthesized click lands directly on a selectRegion target", () => {
         jest.useFakeTimers();
         selectionListCell.contextProvider = viewerContext();
         const regionSpy = jest.spyOn(selectionListCell.regionClicked, "emit");

         const mockTouchEvent = { touches: [{}] } as unknown as TouchEvent;
         selectionListCell.onTouchStart(mockTouchEvent);
         jest.runAllTimers(); // fires long-press

         // Synthesized click lands directly on the label-container (no child handler fires first)
         selectionListCell.selectRegion(new MouseEvent("click"), CellRegion.LABEL);

         expect(regionSpy).not.toHaveBeenCalled();

         // Flags must be cleared — next real mouse click must go through normally
         selectionListCell.selectRegion(new MouseEvent("click"), CellRegion.LABEL);
         expect(regionSpy).toHaveBeenCalled();

         jest.useRealTimers();
      });

      // Mobile non-max-mode guard
      it("should not start the long-press timer in non-max-mode on mobile", () => {
         selectionListCell.mobile = true;
         selectionListCell.maxMode = false;
         const mockEvent = { touches: [{}] } as unknown as TouchEvent;

         selectionListCell.onTouchStart(mockEvent);

         expect((selectionListCell as any).touchTimeout).toBeNull();
      });
   });

   //Bug #18841 should apply border on selection tree
   it("should apply border format on selection tree", () => { // broken
      let vsformats = TestUtils.createMockVSFormatModel();
      vsformats.border = {
         top: "2px solid #ff00ff",
         left: "2px solid #ff00ff",
         right: "2px solid #ff00ff",
         bottom: "2px solid #ff00ff"
      };

      selectionTreeController.getCellFormat.mockImplementation(() => vsformats);
      vsSelectionComponent.controller = selectionTreeController;
      vsSelectionComponent.getMarginSize.mockImplementation(() => 0);

      vsSelectionComponent.model = TestUtils.createMockVSSelectionTreeModel("trees");
      vsSelectionComponent.model.root = {
         formatIndex: -1,
         lavel: -1,
         state: 0,
         label: null,
         value: null,
         selectionList: {
            formats: [vsformats],
            selectionValues: [{
               label: "USA East",
               level: 0,
               path: "null/USA East",
               value: "USA East",
               selectionList: {
                  formats: null,
                  selectionValues: [{
                     label: "CA",
                     level: 1,
                     path: "null/USA East/CA",
                     value: "CA"
                  }]
               }
            }]
         }
      };
      selectionListCell.cellFormat = vsformats;
      fixture.detectChanges();

      let listCell = fixture.debugElement.queryAll(By.css("div.selection-list-cell"))[0];
      // expect(listCell.styles).toBeFalsy();
      expect(listCell.styles["border-bottom-width"]).toBe("2px");
      expect(listCell.styles["border-bottom-style"]).toBe("solid");
      expect(listCell.styles["border-bottom-color"]).toBe("#ff00ff");
   });
});
