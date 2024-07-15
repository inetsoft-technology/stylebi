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
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { IdentityTreeComponent } from "../identity-tree/identity-tree.component";
import { DragService } from "../services/drag.service";
import { ModelService } from "../services/model.service";
import { ShuffleListComponent } from "../shuffle-list/shuffle-list.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { EmailAddrDialogModel } from "./email-addr-dialog-model";
import { EmailAddrDialog } from "./email-addr-dialog.component";
import { EmbeddedEmailPane } from "./embedded-email-pane.component";
import { QueryEmailPane } from "./query-email-pane.component";

let createModel: () => EmailAddrDialogModel = () => {
   return {
      rootTree: {
         children: [{
            children: [],
            label: "admin",
            expanded: false,
            leaf: true,
            type: "0"
         },
         {
            children: [],
            label: "guest",
            expanded: false,
            leaf: true,
            type: "0"
         }],
         label: "Users",
         expanded: true,
         leaf: false,
         type: "-2"
      }
   };
};

describe("Email Addr Dialog Unit Test", () => {
   let fixture: ComponentFixture<EmailAddrDialog>;
   let emailAddrDialog: EmailAddrDialog;

   let changeDetectorRef = { detectChanges: jest.fn() };
   let modelService = { getModel: jest.fn(), getCurrentOrganization: jest.fn() };
   let dragService = { reset: jest.fn(), put: jest.fn() };
   beforeEach(() => {
      modelService.getModel.mockImplementation(() => observableOf([]));
      modelService.getCurrentOrganization.mockImplementation(() => observableOf());
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            EmailAddrDialog, EmbeddedEmailPane, QueryEmailPane, ShuffleListComponent, IdentityTreeComponent, TreeComponent, TreeNodeComponent, TreeSearchPipe
         ],
         providers: [{
            provide: ChangeDetectorRef, useValue: changeDetectorRef
         },
         {
            provide: ModelService, useValue: modelService
         },
         {
            provide: DragService, useValue: dragService
         }],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   });

   //Bug #20490, Bug #20491 reset can not work
   it("reset can not work", () => {
      fixture = TestBed.createComponent(EmailAddrDialog);
      emailAddrDialog = <EmailAddrDialog>fixture.componentInstance;
      let model = createModel();
      emailAddrDialog.model = model;
      fixture.detectChanges();

      let emailPane: EmbeddedEmailPane = fixture.debugElement.query(By.directive(EmbeddedEmailPane)).componentInstance;
      let adminNode = [model.rootTree.children[0]];
      emailPane.nodeSelected(adminNode);
      emailPane.addIdentities();
      fixture.detectChanges();
      let addedUsers: any = fixture.nativeElement.querySelectorAll("div[shuffleRight] div.unhighlightable");
      expect(addedUsers.length).toEqual(1);

      //Bug #20491
      emailPane.nodeSelected(adminNode);
      emailPane.addIdentities();
      fixture.detectChanges();
      addedUsers = fixture.nativeElement.querySelectorAll("div[shuffleRight] div.unhighlightable");
      expect(addedUsers.length).toEqual(1);

      emailAddrDialog.reset();
      fixture.detectChanges();
      addedUsers = fixture.nativeElement.querySelectorAll("div[shuffleRight] div.unhighlightable");
      expect(addedUsers.length).toEqual(0);
   });

   //Bug #21249
   it("can not remove user from email pane", () => {
      let fixture1 = TestBed.createComponent(EmbeddedEmailPane);
      let emailPane = <EmbeddedEmailPane>fixture1.componentInstance;
      let model = createModel();
      model.rootTree.children[0].data = ["scheduleServer@inetsoft.com"];
      model.rootTree.children[1].data = ["scheduleServer@inetsoft.com"];
      emailPane.model = model;
      emailPane.addresses = "admin:scheduleServer@inetsoft.com,guest:scheduleServer@inetsoft.com";
      emailPane.embeddedOnly = false;
      fixture1.detectChanges();

      let addBtn: HTMLButtonElement = fixture1.nativeElement.querySelector("button.add_btn_id");
      let removeBtn: HTMLButtonElement = fixture1.nativeElement.querySelector("button.remove_btn_id");
      emailPane.nodeSelected(model.rootTree.children);
      addBtn.click();
      fixture1.detectChanges();

      let sendUsers = fixture1.nativeElement.querySelectorAll("div[shuffleRight] table tbody > tr");
      expect(sendUsers.length).toEqual(2);

      //select a user
      sendUsers[1].click();
      fixture1.detectChanges();
      removeBtn.click();
      fixture1.detectChanges();

      sendUsers = fixture1.nativeElement.querySelectorAll("div[shuffleRight] table tbody > tr");
      expect(sendUsers.length).toEqual(1);
   });
});