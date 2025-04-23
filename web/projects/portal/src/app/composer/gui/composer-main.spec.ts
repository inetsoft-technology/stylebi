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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf, Subject } from "rxjs";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { TestUtils } from "../../common/test/test-utils";
import { StompClientService } from "../../common/viewsheet-client";
import { ViewDataService } from "../../viewer/services/view-data.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { FontService } from "../../widget/services/font.service";
import { ModelService } from "../../widget/services/model.service";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { ClipboardService } from "./clipboard.service";
import { ComposerMainComponent } from "./composer-main.component";
import { ResizeHandlerService } from "./resize-handler.service";
import { ComposerObjectService } from "./vs/composer-object.service";
import { HttpClient } from "@angular/common/http";
import { AssetTreeService } from "../../widget/asset-tree/asset-tree.service";
import { ComposerRecentService } from "./composer-recent.service";
import { ScriptService } from "./script/script.service";

describe("ComposerMain Unit Tests", () => {
   const oldBroadcastChannel = window.BroadcastChannel;
   let composerObjectService: any;
   let resizeHandlerService: any;
   let clipboardService: any;
   let modelService: any;
   let uiContextService: any;
   let scriptService: any;
   let stompClientService: any;
   let viewsheet: Viewsheet;
   let worksheet: Worksheet;
   let router: any;
   let route: any;
   let httpService: any;
   let composerRecentService: any;
   let appInfoService: any;
   let fontService: any;

   beforeEach(async(() => {
      composerObjectService = { getNewIndex: jest.fn() };
      resizeHandlerService = { onVerticalDragEnd: jest.fn() };
      clipboardService = { clipboardEmpty: false, sheetClosed: jest.fn() };
      modelService = { getModel: jest.fn(() => observableOf({})) };
      scriptService = { setClickedNode: jest.fn(), getClickedNode: jest.fn()};
      uiContextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(() => false),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         sheetHide: jest.fn(),
         sheetClose: jest.fn(),
         sheetShow: jest.fn()
      };
      router = {
         navigate: jest.fn(),
         events: new Subject<any>()
      };

      route = {
         snapshot: {
            _routerState: {
               url: jest.fn().mockRejectedValue("")
            }
         }
      };

      const stompClientConnection = {
         subscribe: jest.fn(),
         send: jest.fn(),
         disconnect: jest.fn()
      };

      composerRecentService = {
         recentlyViewedChange: jest.fn(),
         removeRecentlyViewed: jest.fn(),
         addRecentlyViewed: jest.fn(),
         removeNonExistItems: jest.fn()
      };

      stompClientService = { connect: jest.fn(() => observableOf(stompClientConnection)) };

      httpService = { get: jest.fn(), post: jest.fn() };

      appInfoService = {
         getCurrentOrgInfo: jest.fn(() => observableOf({})),
      };

      fontService = {
         defaultFont: "Roboto"
      };

      viewsheet = new Viewsheet();
      viewsheet.localId = 1;
      viewsheet.label = "vs1";
      viewsheet.id = "Viewsheet1";
      viewsheet.socketConnection = <any> { sendEvent: jest.fn() };

      worksheet = new Worksheet();
      worksheet.label = "ws1";
      worksheet.id = "Worksheet1";
      window.BroadcastChannel = jest.fn().mockImplementation(() => ({onmessage: () => {}}));

      TestBed.configureTestingModule({
         imports: [
            NgbModule
         ],
         providers: [
            { provide: ComposerObjectService, useValue: composerObjectService },
            { provide: ClipboardService, useValue: clipboardService },
            { provide: ResizeHandlerService, useValue: resizeHandlerService },
            { provide: ModelService, useValue: modelService },
            { provide: UIContextService, useValue: uiContextService },
            { provide: ScriptService, useValue: scriptService },
            { provide: Router, useValue: router },
            { provide: ActivatedRoute, useValue: route},
            DataTipService,
            PopComponentService,
            NgbModal,
            FixedDropdownService,
            ShowHyperlinkService,
            AssetTreeService,
            ViewDataService,
            { provide: StompClientService, useValue: stompClientService },
            { provide: HttpClient, useValue: httpService },
            { provide: ComposerRecentService, useValue: composerRecentService },
            { provide: AppInfoService, useValue: appInfoService },
            { provide: FontService, useValue: fontService }
         ],
         declarations: [
            ComposerMainComponent
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   afterEach(async(() => {
      window.BroadcastChannel = oldBroadcastChannel;
   }));

   // Bug #16301 set embeddedId set if opening an embedded vs
   it("should have embeddedId when opening an embedded vs", () => {
      const fixture: ComponentFixture<ComposerMainComponent> = TestBed.createComponent(ComposerMainComponent);
      fixture.componentInstance.touchDevice = false;
      viewsheet.runtimeId = "foobar";
      fixture.componentInstance.sheets.push(viewsheet);
      fixture.componentInstance.focusedSheet = viewsheet;

      fixture.componentInstance.openViewsheet("fooId1", true);
      expect(fixture.componentInstance.sheets[1].embeddedId).toBe(viewsheet.runtimeId);
      fixture.componentInstance.openViewsheet("fooId2", false);
      expect(fixture.componentInstance.sheets[2].embeddedId).toBeNull();
   });

   it("should have a disabled components tab when worksheet is selected", () => {
      const fixture: ComponentFixture<ComposerMainComponent> = TestBed.createComponent(ComposerMainComponent);
      fixture.componentInstance.openNewWorksheet();
      expect(fixture.componentInstance.componentsPaneDisabled).toBe(true);
   });

   //Bug #18803 should disable format pane when select device layout
   it("should disabled format pane when forcus device layout", () => {
      const fixture: ComponentFixture<ComposerMainComponent> = TestBed.createComponent(ComposerMainComponent);
      let layout1 = TestUtils.createMockVSLayoutModel("layout1");
      layout1.printLayout = false;
      viewsheet.currentLayout = layout1;
      fixture.componentInstance.sheets.push(viewsheet);
      fixture.componentInstance.focusedSheet = viewsheet;
      fixture.componentInstance.onSheetUpdated(viewsheet);
      expect(fixture.componentInstance.formatPaneDisabled).toBeTruthy();
   });

   //Bug #18805 should enable format pane when select  print layout pane
   it("should enable format pane when forcus on print layout", () => {
      const fixture: ComponentFixture<ComposerMainComponent> = TestBed.createComponent(ComposerMainComponent);
      let layout2 = TestUtils.createMockVSLayoutModel("layout2");
      layout2.printLayout = true;
      viewsheet.currentLayout = layout2;
      fixture.componentInstance.sheets.push(viewsheet);
      fixture.componentInstance.focusedSheet = viewsheet;
      fixture.detectChanges();
      fixture.componentInstance.onSheetUpdated(viewsheet);
      expect(fixture.componentInstance.formatPaneDisabled).toBeFalsy();
   });

   //Bug #19832 should display correct label when preview layout
   //Bug #20165 should not change preview tab name for print layout
   it("should display correct label when preview layout", () => {
      const fixture: ComponentFixture<ComposerMainComponent> =
         TestBed.createComponent(ComposerMainComponent);
      let layout1 = TestUtils.createMockVSLayoutModel("layout1");
      let layout2 = TestUtils.createMockVSLayoutModel("layout2");
      layout2.printLayout = true;
      viewsheet.layouts = ["layout1", "layout2"];
      viewsheet.currentLayout = layout1;
      fixture.componentInstance.sheets.push(viewsheet);
      fixture.componentInstance.focusedSheet = viewsheet;
      fixture.componentInstance.previewViewsheet(viewsheet);

      expect(fixture.componentInstance.sheets[0].label).toBe("vs1");
      expect(fixture.componentInstance.sheets[1].label).toBe("_#(js:Preview) vs1 (layout1)");

      viewsheet.currentLayout = layout2;
      fixture.componentInstance.previewViewsheet(viewsheet);

      expect(fixture.componentInstance.sheets[0].label).toBe("vs1");
      expect(fixture.componentInstance.sheets[1].label).toBe("_#(js:Preview) vs1 (layout1)");
   });

    //Bug #19612 should back to viewsheet after close its preview tab
   it("should back to viewsheet after close its preview tab", () => {
      const fixture: ComponentFixture<ComposerMainComponent> =
         TestBed.createComponent(ComposerMainComponent);
      viewsheet.runtimeId = "vs1";
      worksheet.runtimeId = "ws1";
      fixture.componentInstance.sheets.push(viewsheet);
      fixture.componentInstance.sheets.push(worksheet);
      fixture.componentInstance.focusedSheet = viewsheet;
      fixture.componentInstance.previewViewsheet(viewsheet);

      expect(fixture.componentInstance.sheets.length).toBe(3);
      expect(fixture.componentInstance.sheets[0].label).toBe("vs1");
      expect(fixture.componentInstance.sheets[1].label).toBe("ws1");
      expect(fixture.componentInstance.sheets[2].label).toBe("_#(js:Preview) vs1");
      expect(fixture.componentInstance.focusedSheet.label).toBe("_#(js:Preview) vs1");

      fixture.componentInstance.sheets[2].runtimeId = "preview vs1";
      fixture.componentInstance.closePreview(2);
      expect(fixture.componentInstance.sheets.length).toBe(2);
      expect(fixture.componentInstance.sheets[0].label).toBe("vs1");
      expect(fixture.componentInstance.sheets[1].label).toBe("ws1");
      expect(fixture.componentInstance.focusedSheet.label).toBe("vs1");
   });
});
