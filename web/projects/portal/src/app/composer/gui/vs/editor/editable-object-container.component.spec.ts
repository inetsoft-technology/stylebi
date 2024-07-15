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
import { EventEmitter, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { DropDownTestModule } from "../../../../common/test/test-module";
import { TestUtils } from "../../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { TextActions } from "../../../../vsobjects/action/text-actions";
import { ComposerContextProviderFactory } from "../../../../vsobjects/context-provider.service";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { AdhocFilterService } from "../../../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { MiniToolbarService } from "../../../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { SelectionContainerChildrenService } from "../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { ActionsContextmenuAnchorDirective } from "../../../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../../widget/interact/interactable.directive";
import { DragService } from "../../../../widget/services/drag.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { LineAnchorService } from "../../../services/line-anchor.service";
import { ComposerObjectService } from "../composer-object.service";
import { DragBorderType } from "../objects/selection/composer-selection-container-children.component";
import { EditableObjectContainer } from "./editable-object-container.component";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { Observable } from "rxjs";
import { ComposerVsSearchService } from "../composer-vs-search.service";

describe("EditableObjectContainer", () => {
   let selectionContainerChildrenService: any;
   let composerObjectService: any;
   let actionFactory: any;
   let interactService: any;
   let dropdownService: any;
   let viewsheet: Viewsheet;
   let model: any;
   let dragService: any;
   let dataTipService: any;
   let adhocFilterService: any;
   let dialogService: any;
   let lineAnchorService: any;
   let scaleService: any;
   let miniToolbarService: any;
   let composerVsSearchService: any;

   beforeEach(async(() => {
      selectionContainerChildrenService = { pushModel: jest.fn() };
      composerObjectService = {
         getNewObject: jest.fn(),
         getObjectType: jest.fn(),
         addKeyEventAdapter: jest.fn(),
         removeKeyEventAdapter: jest.fn(),
         addNewObject: jest.fn()
      };
      interactService = {
         notify: jest.fn(),
         addInteractable: jest.fn(),
         removeInteractable: jest.fn()
      };
      dropdownService = { open: jest.fn() };
      dragService = { reset: jest.fn(), put: jest.fn() };
      dataTipService = {};
      adhocFilterService = {};
      dialogService = { hasSlideout: jest.fn(), showSlideoutFor: jest.fn() };
      lineAnchorService = { addEditorName: jest.fn(), removeEditorName: jest.fn() };
      scaleService = { getScale: jest.fn(), getCurrentScale: jest.fn(), setScale: jest.fn() };
      miniToolbarService = { hasMiniToolbar: jest.fn(), isMiniToolbarVisible: jest.fn() };
      composerVsSearchService = {
         isVisible: jest.fn(),
         isSearchMode: jest.fn(),
         changeSearchMode: jest.fn(),
         assemblyVisible: jest.fn(),
         nextFocus: jest.fn(),
         previousFocus: jest.fn(),
         focusAssembly: jest.fn(),
         isFocusAssembly: jest.fn(),
         focusChange: jest.fn()
      };

      actionFactory = { createActions: jest.fn(), createCurrentSelectionActions: jest.fn() };
      actionFactory.createActions.mockImplementation(() => ({
         onAssemblyActionEvent: new EventEmitter<AssemblyActionEvent<VSObjectModel>>(),
         toolbarActions: [],
         menuActions: [],
         clickActions: null,
         scriptAction: null
      }));
      actionFactory.createCurrentSelectionActions.mockImplementation(() => ({
         onAssemblyActionEvent: new EventEmitter<AssemblyActionEvent<VSObjectModel>>(),
         toolbarActions: [],
         menuActions: [],
         clickActions: null,
         scriptAction: null
      }));

      viewsheet = new Viewsheet();
      viewsheet.localId = 1;
      viewsheet.label = "";
      viewsheet.id = "Viewsheet1";
      viewsheet.socketConnection = <any> { sendEvent: jest.fn() };

      model = Object.assign({
         text: "hello",
         shadow: false
      }, TestUtils.createMockVSObjectModel("VSText", "Text1"));

      TestBed.configureTestingModule({
         imports: [
            NgbModule,
            DropDownTestModule
         ],
         providers: [
            { provide: SelectionContainerChildrenService, useValue: selectionContainerChildrenService },
            { provide: ComposerObjectService, useValue: composerObjectService },
            { provide: ViewsheetClientService, useValue: null },
            { provide: AssemblyActionFactory, useValue: actionFactory },
            { provide: InteractService, useValue: interactService },
            { provide: FixedDropdownService, userValue: dropdownService },
            { provide: DragService, useValue: dragService },
            { provide: DataTipService, useValue: dataTipService},
            { provide: AdhocFilterService, useValue: adhocFilterService},
            { provide: DialogService, useValue: dialogService},
            { provide: LineAnchorService, useValue: lineAnchorService },
            { provide: MiniToolbarService, useValue: miniToolbarService },
            { provide: ScaleService, useValue: scaleService },
            { provide: ComposerVsSearchService, useValue: composerVsSearchService }
         ],
         declarations: [
            EditableObjectContainer, ActionsContextmenuAnchorDirective, InteractableDirective
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   it("should create editable object container component", () => {
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = model;
      fixture.detectChanges();
      expect(fixture).toBeTruthy();
   });

   it("should work without assembly actions", () => {
      // for Bug #9947
      actionFactory.createActions.mockImplementation(() => null);
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = model;
      fixture.detectChanges();
      expect(fixture).toBeTruthy();
   });

   // Bug #9817 ensure that when object is dropped the layout dialog is opened
   it("should drop onto object without error", () => {
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = model;
      fixture.componentInstance.dragOverBorder = DragBorderType.ALL;
      fixture.detectChanges();

      let mockEvent = {
         preventDefault: jest.fn(),
         stopPropagation: jest.fn(),
         dataTransfer: {
            getData: jest.fn(() => JSON.stringify({
               dragName: ["draggauge"]
            }))
         }
      };

      composerObjectService.getObjectType.mockImplementation(() => 1);

      let modalService = TestBed.inject(NgbModal);
      const oldOpen = modalService.open;
      modalService.open = <any> jest.fn(() => ({
         result: Promise.resolve(fixture.componentInstance.layoutOptionDialogModel)
      }));

      try {
         fixture.componentInstance.drop(mockEvent);
         expect(modalService.open).toHaveBeenCalled();
      }
      finally {
         modalService.open = oldOpen;
      }
   });

   // Bug #19077 drag detail calcfield to form object the layout dialog is open
   it("calcfield should drop to form object and pop up layout dialog", () => {
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.dragOverBorder = DragBorderType.ALL;
      fixture.componentInstance.vsObjectModel = TestUtils.createMockVSSliderModel("slider1");
      fixture.detectChanges();

      let mockEvent = {
         preventDefault: jest.fn(),
         stopPropagation: jest.fn(),
         dataTransfer: {
            getData: jest.fn(() => JSON.stringify({
               dragName: ["column"]
            }))
         }
      };

      composerObjectService.getObjectType.mockImplementation(() => 1);
      let modalService = TestBed.inject(NgbModal);
      const oldOpen = modalService.open;
      modalService.open = <any> jest.fn(() => ({
         result: Promise.resolve(fixture.componentInstance.layoutOptionDialogModel)
      }));

      try {
         fixture.componentInstance.drop(mockEvent);
         expect(modalService.open).toHaveBeenCalled();
      }
      finally {
         modalService.open = oldOpen;
      }
   });

   it("should not only show script icon when script is defined", () => {
      actionFactory.createActions.mockImplementation(() => new TextActions(model, ComposerContextProviderFactory()));
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = model;
      fixture.detectChanges();

      let element = fixture.debugElement.query(By.css(".javascript-icon"));
      expect(element).toBeFalsy();

      model.script = `var foo = "bar";`;
      fixture.detectChanges();

      element = fixture.debugElement.query(By.css(".javascript-icon"));
      expect(element).toBeTruthy();
   });

   // Bug #21218 should disable assembly when container is invisible in design
   it("should disable assembly when container is invisible in design", () => {
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = model;
      fixture.componentInstance.vsObject.active = false;
      fixture.detectChanges();

      let element = fixture.nativeElement.querySelector("div.object-editor");
      expect(element.classList).toContain("fade-assembly");
   });

   //Bug #21294, Bug #23392 should not hide selected assembly in tab when tab is invisible in design
   it("should not hide selected assembly in tab when tab is invisible in design", () => {
      const fixture: ComponentFixture<EditableObjectContainer> =
         TestBed.createComponent(EditableObjectContainer);
      let table1 = TestUtils.createMockVSTabModel("table1");
      let table2 = TestUtils.createMockVSTabModel("table2");
      let tab1 = TestUtils.createMockVSTabModel("tab1");
      table1.container = "tab1";
      table1.containerType = "VSTab";
      table2.container = "tab1";
      table2.containerType = "VSTab";
      tab1.visible = false;
      tab1.active = true;
      viewsheet.vsObjects = [table1, table2, tab1];
      fixture.componentInstance.viewsheet = viewsheet;
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.vsObjectModel = table1;
      fixture.detectChanges();

      let tabElem: HTMLElement = fixture.nativeElement.querySelector("div.object-editor");
      expect(tabElem.getAttribute("class")).toContain("fade-assembly");
   });
});
