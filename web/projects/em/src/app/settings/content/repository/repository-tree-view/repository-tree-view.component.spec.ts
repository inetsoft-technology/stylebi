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
import { Component, NgZone, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatIconModule } from "@angular/material/icon";
import { MatLegacyButtonModule } from "@angular/material/legacy-button";
import { MatLegacyCheckboxModule } from "@angular/material/legacy-checkbox";
import { MatLegacyMenuModule } from "@angular/material/legacy-menu";
import { NEVER, of as observableOf } from "rxjs";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { RepositoryTreeDataSource } from "../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../repository-tree-node";
import { RepositoryTreeViewComponent } from "./repository-tree-view.component";

@Component({
   selector: "em-test-app",
   template: `<em-repository-tree-view [dataSource]="dataSource" [selectedNodes]="selectedNodes"></em-repository-tree-view>`
})

class TestApp {
   @ViewChild(RepositoryTreeViewComponent, {static: false}) treeView: RepositoryTreeViewComponent;
   selectedNodes: RepositoryFlatNode[] = [];
   dataSource: RepositoryTreeDataSource;
}

describe("RepositoryTreeViewComponent", () => {
   const fakeNode = (type: RepositoryEntryType, level: number = 0): RepositoryFlatNode => {
      const data = <RepositoryTreeNode> {type: type};
      return new RepositoryFlatNode("", level, false, data);
   };

   let tree: RepositoryTreeViewComponent;
   let stompClientService: any;
   let component: TestApp;
   let fixture: ComponentFixture<TestApp>;

   beforeEach(async(() => {
      const stompConnection = {
         subscribe: jest.fn(() => NEVER),
         disconnect: jest.fn()
      };
      stompClientService = {
         connect: jest.fn(() => observableOf(stompConnection))
      };
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            MatLegacyCheckboxModule,
            MatLegacyMenuModule,
            MatLegacyButtonModule,
            MatIconModule
         ],
         declarations: [
            TestApp,
            RepositoryTreeViewComponent
         ],
         providers: [
            { provide: StompClientService, useValue: stompClientService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TestApp);
      component = fixture.componentInstance;
      const http = fixture.debugElement.injector.get(HttpClient);
      const zone = fixture.debugElement.injector.get(NgZone);
      component.dataSource = new RepositoryTreeDataSource(http, stompClientService, zone);
      fixture.detectChanges();

      tree = component.treeView;
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   it("should disable all buttons when no nodes are selected", () => {
      component.selectedNodes = [];
      fixture.detectChanges();

      const allButtonsDisabled: boolean = tree.newFolderDisabled && tree.deleteDisabled;
      expect(allButtonsDisabled).toBeTruthy();
   });

   it("should disable all 'New' buttons when 2 or more nodes are selected", () => {
      component.selectedNodes = [fakeNode(RepositoryEntryType.ALL), fakeNode(RepositoryEntryType.ALL)];
      fixture.detectChanges();

      const newEntryBtnsDisabled: boolean = tree.newFolderDisabled;
      expect(newEntryBtnsDisabled).toBeTruthy();
   });

   it("should disable delete when a root folder is selected", () => {
      const node = fakeNode(RepositoryEntryType.FOLDER);
      component.selectedNodes = [node];
      fixture.detectChanges();

      expect(tree.deleteDisabled).toBeTruthy();
   });

   it("should disable delete when a user's report folder is selected", () => {

      component.selectedNodes = [fakeNode(RepositoryEntryType.USER_FOLDER)];
      fixture.detectChanges();

      expect(tree.deleteDisabled).toBeTruthy();
   });
});
