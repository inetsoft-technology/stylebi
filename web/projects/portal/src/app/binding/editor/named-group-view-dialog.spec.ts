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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DomService } from "../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { GroupCondition } from "../data/named-group-info";
import { NameInputDialog } from "./name-input-dialog.component";
import { NamedGroupPane } from "./named-group-pane.component";
import { NamedGroupView } from "./named-group-view-dialog.component";

describe("Named Group Pane Unit Test", () => {
   let createMockTreeNodeModel: (name: string) => TreeNodeModel = (name: string) => {
      return {
         label: name,
         data: name,
         expandedIcon: "folder-collapsed-icon",
         collapsedIcon: "folder-collapsed-icon",
         leaf: false,
         children: [],
         expanded: false,
      };
   };

   let fixture: ComponentFixture<NamedGroupView>;
   let namedGroupView: NamedGroupView;

   let groups: GroupCondition[] = [{
      name: "A1",
      value: []
   }];

   let modalService = { open: jest.fn() };
   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            NamedGroupView, NamedGroupPane, NameInputDialog
         ],
         providers: [
            {
               provide: NgbModal, useValue: modalService
            },
            {
               provide: DomService, useValue: null
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   //for Bug #18077, Delete button status is error in the Named Group Dialog
   it("test delete button status on named group pane", () => {
      fixture = TestBed.createComponent(NamedGroupView);
      namedGroupView = <NamedGroupView>fixture.componentInstance;
      namedGroupView.groups = groups;

      namedGroupView.property.selectNodes = [{
         label: "Others",
         data: "Others"
      }];
      fixture.detectChanges();

      let delButton: Element = fixture.nativeElement.querySelectorAll(".named-group-buttons > button")[1];
      expect(delButton.attributes["disabled"].value).toBe("");

      namedGroupView.property.selectNode([{
         label: "A1",
         data: "A1"
      }]);
      fixture.detectChanges();
      delButton = fixture.nativeElement.querySelectorAll(".named-group-buttons > button")[1];
      expect(delButton.hasAttribute("disabled")).toBeFalsy();
   });

   //for Bug #17906, name group not added on named group view
   it("test ok button on named group view dialog", () => {
      fixture = TestBed.createComponent(NamedGroupView);
      namedGroupView = <NamedGroupView>fixture.componentInstance;
      let onCommit: any = { emit: jest.fn() };
      namedGroupView.onCommit = onCommit;
      let condGroup: GroupCondition = {
         name: "A1",
         value: ["AK", "CA", "PA"]
      };
      let condGroups: GroupCondition[] = [];
      condGroups.push(condGroup);
      namedGroupView.groups = condGroups;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         namedGroupView.okClicked(new MouseEvent("click"));
         expect(onCommit.emit).toHaveBeenCalledWith(condGroups);
      });
   });

});
