/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
         submitOnChange: false
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

   //Bug #18841 should apply border on selection tree
   xit("should apply border format on selection tree", () => { // broken
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
      expect(listCell.styles["border-width"]).toBe("2px");
      expect(listCell.styles["border-style"]).toBe("solid");
      expect(listCell.styles["border-color"]).toBe("rgb(255, 0, 255)");
   });
});
