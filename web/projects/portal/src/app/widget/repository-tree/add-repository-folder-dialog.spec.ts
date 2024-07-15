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
import { HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { EnterSubmitDirective } from "../directive/enter-submit.directive";
import { ModalHeaderComponent } from "../modal-header/modal-header.component";
import { ModelService } from "../services/model.service";
import { DialogButtonsDirective } from "../standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../standard-dialog/standard-dialog.component";
import { AddRepositoryFolderDialog } from "./add-repository-folder-dialog.component";

describe("Add Repository Folder  Dialog Unit Test", () => {

   let ngbService = { open: jest.fn() };
   let modelService = {
      getModel: jest.fn(() => observableOf({})),
      putModel: jest.fn(() => observableOf(new HttpResponse({body: null}))),
      sendModel: jest.fn(() => observableOf(new HttpResponse({body: null})))
   };

   let fixture: ComponentFixture<AddRepositoryFolderDialog>;
   let addRepoFolderDialog: AddRepositoryFolderDialog;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            AddRepositoryFolderDialog, StandardDialogComponent,
            EnterSubmitDirective, DialogContentDirective, DialogButtonsDirective, ModalHeaderComponent
         ],
         providers: [
            {
               provide: NgbModal, useValue: ngbService
            },
            {
               provide: ModelService, useValue: modelService
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(AddRepositoryFolderDialog);
      addRepoFolderDialog = <AddRepositoryFolderDialog>fixture.componentInstance;
      fixture.detectChanges();
   }));

   //Bug #18923 should disable ok when folder name is empty
   it("should disable ok when folder name is empty", () => {
      let okBtn = fixture.nativeElement.querySelector("button.btn.btn-primary");
      expect(okBtn.hasAttribute("disabled")).toBeTruthy();
   });
});