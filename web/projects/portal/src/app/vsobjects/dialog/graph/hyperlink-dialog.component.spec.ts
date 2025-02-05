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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService } from "../../../common/viewsheet-client";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { GenericSelectableList } from "../../../widget/generic-selectable-list/generic-selectable-list.component";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { DragService } from "../../../widget/services/drag.service";
import { ModelService } from "../../../widget/services/model.service";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { TooltipService } from "../../../widget/tooltip/tooltip.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { HyperlinkDialogModel } from "../../model/hyperlink-dialog-model";
import { VSTrapService } from "../../util/vs-trap.service";
import { HyperlinkDialog } from "./hyperlink-dialog.component";

@Component({
   selector: "input-parameter-dialog",
   template: "<div></div>"
})
class InputParameterDialog {
   @Input() model: any;
   @Input() fields: string[];
   @Input() selectEdit: boolean = true;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
}

@Component({
   selector: "repository-tree",
   template: "<div></div>"
})
class RepositoryTreeComponent {
   @Input() showRoot: false;
   @Input() permission: string = null;
   @Input() selector: number = null;
   @Input() detailType: string = null;
   @Input() disabled: boolean = false;
   @Input() showContextMenu: boolean = false;
   @Input() expandAll: boolean = false;
   @Input() showTooltip: boolean = false;
   @Input() searchEnabled: boolean = false;
   @Input() multiSelect: boolean = false;
   @Input() draggable: boolean = false;
   @Input() initExpanded: boolean = false;
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
   @Output() nodeClicked = new EventEmitter<TreeNodeModel>();
}

let createHyperlinkModel: () => HyperlinkDialogModel = () => {
   return {
      linkType: 9,
      webLink: null,
      assetLinkPath: null,
      assetLinkId: null,
      bookmark: null,
      targetFrame: null,
      self: true,
      tooltip: null,
      disableParameterPrompt: false,
      sendViewsheetParameters: true,
      sendSelectionsAsParameters: false,
      paramList: [],
      row: 2,
      col: 1,
      fields: [TestUtils.createMockDataRef("id")],
      colName: null,
      table: true,
      grayedOutFields: []
   };
};

describe("hyperlink dialog componnet unit case", () => {
   let trapService: any;
   let modalService: any;
   let repositoryTreeService: any;
   let modelService: any;
   let dragService: any;
   let stompClient: any;
   let fixture: ComponentFixture<HyperlinkDialog>;
   let hyperlinkDialog: HyperlinkDialog;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   let treeNode: TreeNodeModel = {
            children: [{
                  children: [],
                  data: {
                     classType: "RepletFolderEntry",
                     entry: null,
                     name: "My Reports",
                     label: "My Reports",
                     op: "N",
                     path: "My Reports",
                     type: 1
                  },
               }, {
                  children: [],
                  data: {
                     classType: "ViewsheetEntry",
                     entry: {
                        scope: 1,
                        type: "VIEWSHEET",
                        path: "vs",
                        identifier: "1^128^__NULL__^vs",
                     },
                     name: "vs",
                     op: "RCD",
                     type: 64
                  },
                  label: "vs",
                  leaf: true,
            }],
            data: {
               classType: "RepositoryEntry",
               htmlType: 0,
               path: "/",
               type: 1,
               op: "N",
               name: ""
            },
            label: "Repository",
            leaf: false
   };

   beforeEach(() => {
      trapService = { checkTrap: jest.fn() };
      modalService = { open: jest.fn() };
      repositoryTreeService = {
         getRootFolder: jest.fn(),
         getFolder: jest.fn(),
         getAliasedPath: jest.fn()
      };
      modelService = { getModel: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf(treeNode));
      dragService = { getDragData: jest.fn() };
      stompClient = { connect: jest.fn() };

      stompClient.connect.mockImplementation(() => observableOf(null));

      repositoryTreeService.getFolder.mockImplementation(() => observableOf({
         children: []
      }));

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule, HttpClientTestingModule],
         declarations: [HyperlinkDialog, InputParameterDialog, LargeFormFieldComponent, RepositoryTreeComponent, GenericSelectableList, EnterSubmitDirective, TooltipDirective, FixedDropdownDirective],
         providers: [TooltipService,
            {provide: VSTrapService, useValue: trapService},
            {provide: NgbModal, useValue: modalService},
            {provide: RepositoryTreeService, useValue: repositoryTreeService},
            {provide: ModelService, useValue: modelService},
            {provide: DragService, useValue: dragService},
            {provide: StompClientService, useValue: stompClient},
            ],
         schemas: [ NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(HyperlinkDialog);
      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   });

   //Bug #16115
   //Bug #19475
   //Bug #19550
   //Bug #20912
   it("check target Frame load and actions", () => {
      hyperlinkDialog = <HyperlinkDialog>fixture.componentInstance;
      hyperlinkDialog.model = createHyperlinkModel();
      fixture.detectChanges();
      hyperlinkDialog.model.linkType = 1;
      hyperlinkDialog.model.self = true;
      let selfCB = fixture.debugElement.query(By.css("input.self-check_id")).nativeElement;
      selfCB.checked = true;
      fixture.detectChanges();
      expect(selfCB.getAttribute("ng-reflect-model")).toBe("true");

      hyperlinkDialog.model.webLink = "www.baidu.com";
      hyperlinkDialog.model.self = false;
      selfCB.checked = false;
      hyperlinkDialog.model.targetFrame = "_blank";
      let targetInput: HTMLInputElement = fixture.debugElement.query(By.css("input.target-text_id")).nativeElement;
      targetInput.click();
      fixture.detectChanges();
      expect(targetInput.getAttribute("ng-reflect-model")).toBe("_blank");
      //Bug #19550
      //@TODO The implementation of this changed on Feature #40469.
      //expect(fixture.debugElement.query(By.css("a.dropdown-item")).nativeElement.textContent).toContain("hyperlink:id");

      //Bug #20912
      hyperlinkDialog.chooseLink(9);
      fixture.detectChanges();
      let paraListPane = fixture.debugElement.query(By.css("w-large-form-field generic-selectable-list")).nativeElement;
      expect(paraListPane.getAttribute("ng-reflect-disabled")).toBe("true");
   });

   //Bug #19600 unget bookmark for report asset link
   it("only vs can get bookmark", () => {
      hyperlinkDialog = <HyperlinkDialog>fixture.componentInstance;
      hyperlinkDialog.model = createHyperlinkModel();
      fixture.detectChanges();

      //report asset link
      hyperlinkDialog.model.linkType = 0;
      hyperlinkDialog.model.assetLinkId = "report1";
      hyperlinkDialog.model.assetLinkPath = "report1";
      hyperlinkDialog.ngOnInit();
      expect(hyperlinkDialog._bookmarks.length).toBe(0);

      //vs asset link
      hyperlinkDialog.model.linkType = 8;
      hyperlinkDialog.model.assetLinkId = "1^128^__NULL__^chart1";
      hyperlinkDialog.model.assetLinkPath = "chart1";
      hyperlinkDialog.ngOnInit();

      const request = httpTestingController.expectOne("../api/composer/vs/hyperlink-dialog-model/bookmarks/" + encodeURIComponent("1^128^__NULL__^chart1"));
      request.flush(["(Home)(anonymous)"]);

      expect(hyperlinkDialog._bookmarks.length).toBe(1);
      expect(hyperlinkDialog._bookmarks[0]).toBe("(Home)(anonymous)");
   });

   //Bug #20907 and Bug #20994
   it("should show right date type parameter", () => {
      let model = createHyperlinkModel();
      model.linkType = 1;
      model.webLink = "www.baidu.com";
      model.paramList = [{
         name: "para1",
         value: "2012-12-25",
         valueSource: "constant",
         type: "date"
      }, {
         name: "para2",
         value: "1",
         valueSource: "constant",
         type: "integer"
      }, {
         name: "para3",
         value: "State",
         valueSource: "field",
         type: "string"
      }, {
         name: "para3",
         value: "date",
         valueSource: "field",
         type: "date"
      }];
      hyperlinkDialog = <HyperlinkDialog>fixture.componentInstance;
      hyperlinkDialog.model = model;
      fixture.detectChanges();

      let paras = fixture.nativeElement.querySelectorAll("div.d-table");
      expect(paras[0].textContent.trim()).toBe("para1:{d '2012-12-25'}");
      expect(paras[1].textContent.trim()).toBe("para2:1");
      expect(paras[2].textContent.trim()).toBe("para3:[State]");
      expect(paras[3].textContent.trim()).toBe("para3:[date]");
   });

   //Bug #21035 and Bug #21048 check select a vs link
   it("should show right status when select a vs links", () => {
      let model = createHyperlinkModel();
      model.linkType = 8;
      model.assetLinkPath = "vs";
      model.assetLinkId = "1^128^__NULL__^vs";
      model.col = 1;
      model.self = false;
      model.targetFrame = "";
      hyperlinkDialog = <HyperlinkDialog>fixture.componentInstance;
      hyperlinkDialog.model = model;
      hyperlinkDialog.rootNode = treeNode;
      fixture.detectChanges();

      let targetFrame = fixture.debugElement.query(By.css("input.target-text_id")).nativeElement;
      let selfCB = fixture.debugElement.query(By.css("input.self-check_id")).nativeElement;
      expect(targetFrame.getAttribute("ng-reflect-is-disabled")).toBe("false");
      // 1. don't test implementation, test behavior
      // 2. absolutely don't test the implementation of third party software.
      // expect(selfCB.getAttribute("ng-reflect-is-disabled")).toBe("true");
   });

   //Bug #20979
   it("should not selected folder as asset link", () => {
      let model = createHyperlinkModel();
      model.linkType = 0;
      hyperlinkDialog = <HyperlinkDialog>fixture.componentInstance;
      hyperlinkDialog.model = model;
      hyperlinkDialog.rootNode = treeNode;

      hyperlinkDialog.nodeSelected(treeNode.children[0]);
      expect(hyperlinkDialog.model.assetLinkId).toBe("My Reports");
      expect(hyperlinkDialog.model.assetLinkPath).toBeNull();

      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css(".btn-primary")).nativeElement.disabled).toBeTruthy();
   });
});
