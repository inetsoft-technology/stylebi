/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf } from "rxjs";
import { DashboardModel } from "../../common/data/dashboard-model";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { StompClientService } from "../../common/viewsheet-client";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { DomService } from "../../widget/dom-service/dom.service";
import { RepositoryTreeComponent } from "../../widget/repository-tree/repository-tree.component";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { DragService } from "../../widget/services/drag.service";
import { ModelService } from "../../widget/services/model.service";
import { DialogButtonsDirective } from "../../widget/standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../../widget/standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../../widget/standard-dialog/standard-dialog.component";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { EditDashboardDialog } from "./edit-dashboard-dialog.component";

describe("Edit Dashboard Dialog Unit Test", () => {
   const createDashboardModel: () => DashboardModel = () => {
      return {
         name: "Construction__GLOBAL",
         label: "Construction",
         type: "g",
         description: "test",
         path: "Examples/Construction Dashboard",
         identifier: "1^128^__NULL__^Examples/Construction Dashboard",
         enabled: false
      };
   };

   const createTreeNodeModel: () => Observable<TreeNodeModel> = () => {
      return observableOf({
         expanded: true,
         label: "Repository",
         leaf: false,
         data: {name: "", type: 1, path: "/", label: ""},
         children: [
            {
               expanded: true,
               label: "Dashboards",
               leaf: false,
               data: {name: "Examples", type: 1, path: "Examples", label: "Dashboards"},
               children: [
                  {
                     expanded: false,
                     label: "Construction Dashboard",
                     leaf: true,
                     data: {name: "Construction Dashboard", type: 64, path:
                        "Examples/Construction Dashboard", label: "Construction Dashboard"}
                  },
                  {
                     expanded: false,
                     label: "Census",
                     leaf: true,
                     data: {name: "Census", type: 64, path: "Examples/Census", label: "Census"}
                  }
               ]
            }
         ]
      });
   };

   let changeRef = { detectChanges: jest.fn() };
   let ngbService = { open: jest.fn() };
   let modelService = {
      getModel: jest.fn(),
      putModel: jest.fn(),
      sendModel: jest.fn()
   };
   let repoTreeService = {
      getRootFolder: jest.fn(),
      getFolder: jest.fn(),
      getContentSource: jest.fn()
   };
   let dragService: any;
   let debounceService: any = { debounce: jest.fn() };

   let fixture: ComponentFixture<EditDashboardDialog>;
   let editDashboardDialog: EditDashboardDialog;

   repoTreeService.getFolder.mockImplementation(() => createTreeNodeModel());
   repoTreeService.getRootFolder.mockImplementation(() => createTreeNodeModel());
   repoTreeService.getContentSource.mockImplementation(() => "");

   let stompClient: any = TestUtils.createMockStompClientService();

   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(async(() => {
      dragService = { currentlyDragging: false };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            EditDashboardDialog, StandardDialogComponent, RepositoryTreeComponent,
            EnterSubmitDirective, DialogContentDirective, DialogButtonsDirective
         ],
         providers: [
            { provide: StompClientService, useValue: stompClient },
            { provide: ChangeDetectorRef, useValue: changeRef },
            { provide: NgbModal, useValue: ngbService },
            { provide: ModelService, useValue: modelService },
            { provide: RepositoryTreeService, useValue: repoTreeService },
            { provide: DebounceService, useValue: debounceService },
            { provide: DragService, useValue: dragService },
            { provide: DomService, useValue: null }

         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);

      fixture = TestBed.createComponent(EditDashboardDialog);
      editDashboardDialog = <EditDashboardDialog>fixture.componentInstance;
      editDashboardDialog.dashboard = createDashboardModel();
      editDashboardDialog.composerEnabled = false;
      fixture.detectChanges();
   }));

   //Bug #18620 Don't allow special characters in the name
   //Bug #21678 should allow & -+
   xit("check dashboard name", () => { // broken test
      let dashName = fixture.debugElement.query(By.css("input.dashboard_name_id")).nativeElement;
      dashName.value = "A&B";
      dashName.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      let message = fixture.debugElement.query(By.css(".invalid-feedback")).nativeElement;
      expect(okBtn.hasAttribute("disabled")).toBeFalsy();
      expect(message).toBeNull();

      dashName = fixture.debugElement.query(By.css("input.dashboard_name_id")).nativeElement;
      dashName.value = "A%~";
      dashName.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      okBtn = fixture.debugElement.query(By.css("button.btn.btn-primary")).nativeElement;
      message = fixture.debugElement.query(By.css(".invalid-feedback")).nativeElement;
      expect(okBtn.hasAttribute("disabled")).toBeTruthy();
      expect(TestUtils.toString(message.textContent)).toContain("repository.tree.SpecialChar");
   });
});