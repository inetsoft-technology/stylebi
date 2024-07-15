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
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { EventEmitter, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf, Subject } from "rxjs";
import { ChatService } from "../../../common/chat/chat.service";
import { FileUploadService } from "../../../common/services/file-upload.service";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ToolbarGroup } from "../../../widget/toolbar/toolbar-group/toolbar-group.component";
import { Viewsheet } from "../../data/vs/viewsheet";
import { VSLayoutModel } from "../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../data/vs/vs-layout-object-model";
import { Worksheet } from "../../data/ws/worksheet";
import { ImportCSVDialog } from "../../dialog/ws/import-csv-dialog.component";
import { ComposerToolbarService } from "../composer-toolbar.service";
import { EventQueueService } from "../vs/event-queue.service";
import { ComposerToolbarComponent } from "./composer-toolbar.component";

enum LayoutAlignment {
   TOP,
   MIDDLE,
   BOTTOM,
   LEFT,
   CENTER,
   RIGHT,
   DIST_H,
   DIST_V,
   RESIZE_MIN_W,
   RESIZE_MAX_W,
   RESIZE_MIN_H,
   RESIZE_MAX_H
}

let createLayoutObjectModel: () => VSLayoutObjectModel = () => {
   return {
      editable: false,
      name: "object",
      left: 0,
      top: 0,
      width: 2,
      height: 2,
      tableLayout: 0,
      supportTableLayout: false,
      objectModel: {
         objectFormat: null,
         objectType: null,
         enabled: true,
         description: null,
         script: null,
         scriptEnabled: true,
         hasCondition: false,
         visible: true,
         absoluteName: null,
         dataTip: null,
         popComponent: null,
         popLocation: null,
         inEmbeddedViewsheet: false,
         assemblyAnnotationModels: [],
         dataAnnotationModels: [],
         actionNames: [],
         genTime: 0,
         adhocFilterEnabled: true,
         sheetMaxMode: false,
         hasDynamic: false
      },
      childModels: null
   };
};

let createVSLayoutModel: () => VSLayoutModel = () => {
   return Object.assign({
      name: "layout1",
      objects: null,
      selectedObjects: [],
      printLayout: false,
      guideType: 0,
      // Print Layout Info
      currentPrintSection: 1,
      unit: "",
      width: 1,
      height: 1,
      marginTop: 0,
      marginLeft: 0,
      marginRight: 0,
      marginBottom: 0,
      headerFromEdge: 0,
      footerFromEdge: 0,
      headerObjects: [],
      footerObjects: [],
      horizontal: false,
      runtimeID: "",

      socketConnection: null,
      focusedObjects: null,
      focusedObjectsSubject: null
   }, new VSLayoutModel());
};

describe("Composer toolbar", () => {
   let modelService: any;
   let fileUploadService: any;
   let httpService: any;
   let httpClient: any;
   let scaleService: any;
   let fixture: ComponentFixture<ComposerToolbarComponent>;
   let composerToolbarComponent: ComposerToolbarComponent;
   let socket: ViewsheetClientService;
   let eventQueueService: any;
   let toolbarService: any;
   let fullScreenService: any;

   beforeEach(() => {
      modelService = { getModel: jest.fn(() => observableOf([])) };
      fileUploadService = {
         getObserver: jest.fn(() => new Subject<number>()),
         upload: jest.fn()
      };
      httpService = { get: jest.fn() };
      scaleService = {
         getScale: jest.fn(() => observableOf(1)),
         setScale: jest.fn()
      };
      socket = <any> { sendEvent: jest.fn() };
      eventQueueService = { addResizeEvent: jest.fn() };
      toolbarService = { jdbcExists: true };
      httpClient = { get: jest.fn(() => new Subject()) };
      fullScreenService = {
         fullScreenChange: new EventEmitter<any>(),
         fullScreenMode: false,
         enterFullScreen: jest.fn(),
         exitFullScreen: jest.fn()
      };
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            HttpClientTestingModule
         ],
         providers: [
            { provide: ModelService, useValue: modelService },
            { provide: FileUploadService, useValue: fileUploadService },
            { provide: HttpClient, useValue: httpClient },
            { provide: ScaleService, useValue: scaleService},
            { provide: ViewsheetClientService, useValue: socket },
            { provide: EventQueueService, useValue: eventQueueService },
            { provide: ComposerToolbarService, useValue: toolbarService },
            { provide: ChatService, useValue: null }
         ],
         declarations: [ ComposerToolbarComponent, ToolbarGroup, ImportCSVDialog ],
         schemas: [ NO_ERRORS_SCHEMA ]
      })
      .overrideComponent(ComposerToolbarComponent, {
         set: {
            providers: [
               { provide: FullScreenService, useValue: fullScreenService }
            ]
         }
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ComposerToolbarComponent);
      composerToolbarComponent = <ComposerToolbarComponent>fixture.componentInstance;
      fixture.detectChanges();
   });

   //BUg #21103 should not show preview button on worksheet
   it("worksheet toolbar status check", () => {
      composerToolbarComponent.sheet = new Worksheet();
      composerToolbarComponent.sheet.runtimeId = "Worksheet1";
      (<Worksheet> composerToolbarComponent.sheet).init = true;

      fixture.detectChanges();
      let previewBtn = fixture.nativeElement.querySelector("button.preview-button");
      expect(previewBtn).toBeNull();
   });

   //Bug #17208 enable layout align when select multi object on layouts
   it("should enable layout align button when select multi object on layouts", () => {
      composerToolbarComponent.sheet = new Viewsheet();
      (<Viewsheet> composerToolbarComponent.sheet).type = "viewsheet";
      (<Viewsheet> composerToolbarComponent.sheet).currentLayout = createVSLayoutModel();
      (<Viewsheet> composerToolbarComponent.sheet).scale = 1;
      (<Viewsheet> composerToolbarComponent.sheet).currentLayout.focusedObjects = [
         createLayoutObjectModel(),
         createLayoutObjectModel()
      ];

      fixture.detectChanges();
      let layoutAlign: HTMLInputElement = fixture.nativeElement.querySelector("#editorLayoutAlignButton");
      expect(layoutAlign.disabled).toBeFalsy();
   });

   //Bug #16940 disbale move mode button on layout, move mode button have been removed

   //Bug #17173 should enable paste button when select multi assemblies
   it("should enable paste when select multi assemblies", () => {
      composerToolbarComponent.sheet = new Viewsheet();
      (<Viewsheet> composerToolbarComponent.sheet).type = "viewsheet";
      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = ["A", "B", "C"];
      (<Viewsheet> composerToolbarComponent.sheet).scale = 1;
      fixture.detectChanges();
      let pasteBtn: HTMLInputElement = fixture.nativeElement.querySelector("button.paste-button");
      expect(pasteBtn.disabled).toBeTruthy();
   });

   //Bug #18462 The snapping icon should be enabled in layout pane.
   //Bug #18596,  Bug #18602
   it("check button status on device or print layout", () => {
      composerToolbarComponent.sheet = new Viewsheet();
      (<Viewsheet> composerToolbarComponent.sheet).type = "viewsheet";
      (<Viewsheet> composerToolbarComponent.sheet).preview = true;

      fixture.detectChanges();
      let snap = fixture.nativeElement.querySelector("#editorSnapOptionsButton");
      let copy = document.querySelector(".copy-button");
      let cut = document.querySelector(".cut-button");
      let paste = document.querySelector(".paste-button");
      let formatPainter = fixture.nativeElement.querySelector(".format-painter-button");
      let guide = fixture.nativeElement.querySelector("#guidesButton");
      expect(snap.disabled).toBeTruthy();

      //device layout
      (<Viewsheet> composerToolbarComponent.sheet).preview = false;
      (<Viewsheet> composerToolbarComponent.sheet).currentLayout = createVSLayoutModel();
      (<Viewsheet> composerToolbarComponent.sheet).currentLayout.printLayout = false;
      (<Viewsheet> composerToolbarComponent.sheet).scale = 1;
      fixture.detectChanges();
      guide = fixture.nativeElement.querySelector("#guidesButton");
      expect(snap.disabled).toBeFalsy();
      expect(copy.hasAttribute("disabled")).toBeTruthy();
      expect(cut.hasAttribute("disabled")).toBeTruthy();
      expect(paste.hasAttribute("disabled")).toBeTruthy();
      expect(formatPainter.disabled).toBeTruthy();

      expect(guide.disabled).toBeFalsy();

      //device layout preview
      (<Viewsheet> composerToolbarComponent.sheet).preview = true;
      fixture.detectChanges();
      expect(guide.disabled).toBeTruthy();

      //print layout
      (<Viewsheet> composerToolbarComponent.sheet).preview = false;
      (<Viewsheet> composerToolbarComponent.sheet).currentLayout.printLayout = true;
      fixture.detectChanges();
      let editContent = fixture.nativeElement.querySelector("#editContentButton");
      expect(editContent.disabled).toBeFalsy();
   });

   //Bug 19212 disable distribute icon when selected object <= 2
   //Bug 19207 distribute group child
   //Bug #20796 should enable align and resize icon when select 2 object
   //Bug #20636 should not allow layout align/resize/distribute for locked objects
   it("check layout align icon status", () => {
      composerToolbarComponent.sheet = new Viewsheet();
      (<Viewsheet> composerToolbarComponent.sheet).type = "viewsheet";
      let list = TestUtils.createMockVSSelectionListModel("list1");
      let calendar1 = TestUtils.createMockVSCalendarModel("calendar1");
      let radio = TestUtils.createMockVSRadioButtonModel("radio1");
      let oval = TestUtils.createMockVSOvalModel("oval");
      let groupContainer = TestUtils.createMockVSGroupContainerModel("GroupContainer1");
      calendar1.container = "GroupContainer1";
      calendar1.containerType = "VSGroupContainer";
      radio.container = "GroupContainer1";
      radio.containerType = "VSGroupContainer";

      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = [list, groupContainer, radio, oval];
      expect(composerToolbarComponent.layoutDistributeEnabled).toBeTruthy();

      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = [list, groupContainer, radio];
      expect(composerToolbarComponent.layoutDistributeEnabled).toBeFalsy();

      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = [list, oval];
      expect(composerToolbarComponent.layoutAlignEnabled).toBeTruthy();
      expect(composerToolbarComponent.layoutResizeEnabled).toBeTruthy();

      oval.locked = true;
      expect(composerToolbarComponent.layoutAlignEnabled).toBeFalsy();
      expect(composerToolbarComponent.layoutResizeEnabled).toBeFalsy();

      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = [list, oval, groupContainer];
      expect(composerToolbarComponent.layoutDistributeEnabled).toBeFalsy();
   });

   //Bug #20112 Resize Max Width function
   it("check Resize Max Width", () => {
      composerToolbarComponent.sheet = new Viewsheet();
      composerToolbarComponent.sheet.socketConnection = socket;
      (<Viewsheet> composerToolbarComponent.sheet).type = "viewsheet";
      let list1 = TestUtils.createMockVSSelectionListModel("list1");
      let list2 = TestUtils.createMockVSSelectionListModel("list2");

      list1.objectFormat.top = 132;
      list1.objectFormat.left = 209;
      list1.objectFormat.width = 199;
      list1.objectFormat.height = 108;
      list2.objectFormat.top = 121;
      list2.objectFormat.left = 522;
      list2.objectFormat.width = 276;
      list2.objectFormat.height = 108;

      (<Viewsheet> composerToolbarComponent.sheet).currentFocusedAssemblies = [list1, list2];
      composerToolbarComponent.layoutResize(LayoutAlignment.RESIZE_MAX_W);

      expect(list1.objectFormat.width).toBe(276);
      expect(list2.objectFormat.width).toBe(276);
   });
});
