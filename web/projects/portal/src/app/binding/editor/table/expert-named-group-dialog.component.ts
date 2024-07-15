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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ConditionExpression } from "../../../common/data/condition/condition-expression";
import { DataRef } from "../../../common/data/data-ref";
import { ConditionList } from "../../../common/util/condition-list";
import { Tool } from "../../../../../../shared/util/tool";
import { NamedGroupInfo } from "../../data/named-group-info";
import { NameInputDialog } from "../name-input-dialog.component";
import { ComponentTool } from "../../../common/util/component-tool";
import { ConditionDialogService } from "../../../widget/condition/condition-dialog.service";
import { ConditionPane } from "../../../widget/condition/condition-pane.component";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionItemPaneProvider } from "../../../common/data/condition/condition-item-pane-provider";

@Component({
   selector: "expert-named-group-dialog",
   templateUrl: "expert-named-group-dialog.component.html",
   styleUrls: ["expert-named-group-dialog.component.scss"]
})
export class ExpertNamedGroupDialog implements OnInit {
   @Input() namedGroupInfo: NamedGroupInfo;
   @Input() provider: ConditionItemPaneProvider;
   @Input() table: string;
   @Input() field: DataRef;
   @Input() others: boolean;
   @Output() onCommit: EventEmitter<any> = new EventEmitter();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(ConditionPane) conditionPane: ConditionPane;

   groups: ConditionExpression[] = [];
   selectedGroup: ConditionExpression = null;
   editing: boolean = false;

   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private conditionService: ConditionDialogService)
   {
   }

   ngOnInit(): void {
      let ninfo: NamedGroupInfo = Tool.clone(this.namedGroupInfo);
      this.groups = ninfo.conditions;

      if(this.groups != null) {
         this.selectedGroup = this.groups[0];
      }
   }

   ok(evt: MouseEvent): void {
      evt.stopPropagation();
      this.namedGroupInfo.conditions = this.groups;
      this.namedGroupInfo.groups = [];

      this.onCommit.emit({
         others: this.others,
         namedGroupInfo: this.namedGroupInfo
      });
   }

   finishEditing() {
      const save = () => this.conditionPane.save();
      const isValid = () => this.conditionPane.isConditionValid();
      const saveOption = () => this.conditionPane.saveOption();

      this.conditionService.checkDirtyConditions(save, isValid, saveOption).then((success: boolean) => {
         if(success) {
            this.editing = false;
         }
      });
   }

   cancel(evt: MouseEvent): void {
     this.onCancel.emit("cancel");
   }

   selectGroup(group: ConditionExpression) {
      this.selectedGroup = group;
   }

   editGroup() {
      this.editing = true;
   }

   conditionChange(value: {selectedIndex: number, condition: Condition}) {
      this.conditionService.dirtyCondition = value;
   }

   conditionListChange(value: any[]) {
      for(let i = 0; i < this.groups.length; i++){
         if(this.groups[i].name == this.selectedGroup.name) {
            this.groups[i].list = value;
         }
      }
   }

   isSelected(gname: string) {
      if(this.selectedGroup == null) {
         return false;
      }

      return gname == this.selectedGroup.name;
   }

   get selectCondition(): ConditionList {
      if(this.selectedGroup == null) {
         return null;
      }

      return this.selectedGroup.list;
   }

   addGroup() {
      let dialog: NameInputDialog = ComponentTool.showDialog(this.modalService, NameInputDialog,
         (result: any) => {
            let newCond: ConditionExpression = <ConditionExpression> {
               name: result, list: []
            };

            this.groups.push(newCond);
            this.selectGroup(newCond);

         });

      dialog.title = "_#(js:Group Name)";
      dialog.existedNames = this.getGroupName();
   }

   deleteGroup() {
      if(this.inValid()) {
         return;
      }

      if(this.findGroupIndex() >= 0) {
         let index: number = this.findGroupIndex();
         let length: number = this.groups.length;
         let value: boolean = (index + 1) == length;
         this.groups.splice(this.findGroupIndex(), 1);

         if(value && length > 1) {
            this.selectedGroup = this.groups[index - 1];
         }
         else if(length > 1) {
            this.selectedGroup = this.groups[index];
         }
         else {
            this.selectedGroup = <ConditionExpression> {
               name: "", list: []
            };
         }
      }
   }

   renameGroup() {
      if(this.inValid()) {
         return;
      }

      let dialog: NameInputDialog = ComponentTool.showDialog(this.modalService, NameInputDialog,
         (result: any) => {
            if(this.findGroupIndex() >= 0) {
               this.selectedGroup.name = result;
            }
         });

      dialog.existedNames = this.getGroupName();
      dialog.inputName = this.selectedGroup.name;
   }

   clearAll() {
      if(this.groups == null) {
         return;
      }

      this.groups.splice(0, this.groups.length);
      this.selectedGroup = null;
   }

   moveUp() {
      if(this.inValid()) {
         return;
      }

      let i = this.findGroupIndex();

      if(i > 0) {
         // swap i-1 and i group
         this.groups[i - 1] = this.groups.splice(i, 1, this.groups[i - 1])[0];
      }
   }

   moveDown() {
      if(this.inValid()) {
         return;
      }

      let i = this.findGroupIndex();

      if(i < this.groups.length - 1) {
         // swap i and i+1 group
         this.groups[i] = this.groups.splice(i + 1, 1, this.groups[i])[0];
      }
   }

   canUp(): boolean {
      if(this.inValid()) {
         return false;
      }

      return this.findGroupIndex() > 0;
   }

   canDown(): boolean {
      if(this.inValid()) {
         return false;
      }

      let i = this.findGroupIndex();

      return i < this.groups.length - 1 && i >= 0;
   }

   private inValid(): boolean {
      if(this.selectedGroup == null || this.groups == null) {
         return true;
      }

      return false;
   }

   private findGroupIndex(): number {
      for(let i = 0; i < this.groups.length; i++) {
         if(this.groups[i].name == this.selectedGroup.name) {
            return i;
         }
      }

      return -1;
   }

   hasEmptyGroup(): boolean {
      return !!this.groups.find(g => !g.list || g.list.length == 0);
   }

   getGroupName(): string[] {
      let names: string[] = [];

      for(let i = 0; i < this.groups.length; i++) {
         if(this.selectedGroup.name != this.groups[i].name) {
            names.push(this.groups[i].name);
         }
      }

      return names;
   }
}
