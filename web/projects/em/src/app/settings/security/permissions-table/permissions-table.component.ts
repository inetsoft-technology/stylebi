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
import { SelectionModel } from "@angular/cdk/collections";
import {
   AfterViewInit,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort, Sort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { ResourcePermissionTableModel } from "../resource-permission/resource-permission-table-model";
import { SelectionTransfer } from "../resource-permission/selection-transfer";
import {convertToKey, IdentityId} from "../users/identity-id";

@Component({
   selector: "em-permissions-table",
   templateUrl: "./permissions-table.component.html",
   styleUrls: ["./permissions-table.component.scss"],
})
export class PermissionsTableComponent implements OnChanges, AfterViewInit, SelectionTransfer<ResourcePermissionTableModel> {
   @ViewChild(MatSort, { static: true }) sort: MatSort;
   @ViewChild(MatPaginator) paginator: MatPaginator;

   @Input() dataSource: ResourcePermissionTableModel[] = [];
   @Input() displayActions: ResourceAction[];
   @Output() tableDataChanged = new EventEmitter<ResourcePermissionTableModel[]>();
   @Output() tableSelectionChanged = new EventEmitter<ResourcePermissionTableModel[]>();

   matTableDataSource: MatTableDataSource<ResourcePermissionTableModel>;
   selection = new SelectionModel<ResourcePermissionTableModel>(true, []);

   private _displayColumns = ["selected", "type", "name"];
   private displayAdminAction: boolean;
   ResourceAction = ResourceAction;

   get displayColumns(): string[] {
      return this._displayColumns.concat(this.displayActions.map(action => "" + action));
   }

   getResourceActionLabel(raction: ResourceAction): String {
      let result = "";

      switch(raction){
      case ResourceAction.ACCESS:
         result = "_#(js:ACCESS)";
         break;
      case ResourceAction.ADMIN:
         result = "_#(js:ADMIN)";
         break;
      case ResourceAction.ASSIGN:
         result = "_#(js:ASSIGN)";
         break;
      case ResourceAction.DELETE:
         result = "_#(js:DELETE)";
         break;
      case ResourceAction.READ:
         result = "_#(js:READ)";
         break;
      case ResourceAction.WRITE:
         result = "_#(js:WRITE)";
         break;
      case ResourceAction.SHARE:
         result = "_#(js:SHARE)";
         break;
      }

      return result;
   }

   sendSelection(): ResourcePermissionTableModel[] {
      this.tableDataChanged.emit(this.dataSource.filter((row) => !this.selection.isSelected(row)));
      let selected: ResourcePermissionTableModel[] = this.selection.selected;
      this.selection.clear();
      this.tableSelectionChanged.emit(this.selection.selected);
      return selected;
   }

   receiveSelection(selection: ResourcePermissionTableModel[]): void {
      this.addPermission(selection);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource && this.dataSource != null) {
         const visibleRows = this.dataSource.filter(r => this.rowVisible(r));
         this.matTableDataSource = new MatTableDataSource(visibleRows);
         this.matTableDataSource.sort = this.sort;
         this.matTableDataSource.paginator = this.paginator;
         this.sortData(this.sort);
         this.selection.clear();
      }

      if(changes.displayActions && this.displayActions) {
         this.displayAdminAction = this.displayActions.includes(ResourceAction.ADMIN);
      }
   }

   ngAfterViewInit(): void {
      if(this.matTableDataSource) {
         this.matTableDataSource.paginator = this.paginator;
      }
   }

   private rowVisible(row: ResourcePermissionTableModel): boolean {
      return row.actions.some(a => this.displayActions.includes(a));
   }

   actionDisabled(row: ResourcePermissionTableModel, action: ResourceAction): boolean {
      return this.displayAdminAction && row.actions.includes(ResourceAction.ADMIN) && action !== ResourceAction.ADMIN;
   }

   actionChecked(row: ResourcePermissionTableModel, action: ResourceAction): boolean {
      return row.actions.includes(action);
   }

   updateAction(row: ResourcePermissionTableModel, action: ResourceAction) {
      // Remove action
      if(row.actions.includes(action)) {
         const index = row.actions.indexOf(action);
         row.actions.splice(index, 1);
      }
      // Add action
      else {
         if(action === ResourceAction.ADMIN) {
            row.actions = this.displayActions.slice(0);
         }
         else {
            row.actions.push(action);
         }
      }

      this.tableDataChanged.emit(this.dataSource);
   }

   addPermission(permissions: ResourcePermissionTableModel[]): void {
      const permissionMap = new Map<string, ResourcePermissionTableModel>();

      this.dataSource.forEach((p) => {
         permissionMap.set(this.getPermissionKey(p), p);
      });

      const newPermissions = [];
      permissions.filter(p => {
         const oldPerm = permissionMap.get(this.getPermissionKey(p));

         if(!oldPerm) {
            p.actions = this.displayActions.slice(0);
            newPermissions.push(p);
         }
         else if(!this.rowVisible(oldPerm)) {
            oldPerm.actions.splice(0, 0, ...this.displayActions.slice(0));
         }
         return !!oldPerm;
      });

      this.tableDataChanged.emit(this.dataSource.concat(newPermissions));
   }

   getIcon(type: IdentityType): string {
      let icon: string;

      switch(type) {
      case IdentityType.GROUP:
         icon = "user-group-icon";
         break;
      case IdentityType.ROLE:
         icon = "user-roles-icon";
         break;
      case IdentityType.USER:
         icon = "account-icon";
         break;
      case IdentityType.ORGANIZATION:
         icon = "user-organizations-icon";
         break;
      default:
         console.error("Unsupported identity type: " + type);
      }

      return icon;
   }

   selectAll(selectAll: boolean): void {
      if(selectAll) {
         this.masterToggle();
      }

      this.tableSelectionChanged.emit(this.selection.selected);
   }

   selectRow(selected: boolean, row: ResourcePermissionTableModel): void {
      if(selected) {
         this.toggleRow(row);
      }

      this.tableSelectionChanged.emit(this.selection.selected);
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.dataSource.length;
   }

   toggleRow(row: ResourcePermissionTableModel): void {
      this.selection.toggle(row);
   }

   /** Selects all rows if they are not all selected; otherwise clear selection. */
   masterToggle(): void {
      this.isAllSelected() ?
         this.selection.clear() :
         this.dataSource.forEach(row => this.selection.select(row));
   }

   compare(a: string, b: string, isAsc: boolean) {
      return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
   }

   sortData(sort: Sort) {
      const data = this.matTableDataSource.data.slice();
      this.matTableDataSource.data = data.sort((a, b) => {
         const isAsc = sort.direction === "asc" || sort.direction === "";
         return this.compare(a.identityID.name.toLowerCase(), b.identityID.name.toLowerCase(), isAsc);
      });
   }

   private getPermissionKey(p: ResourcePermissionTableModel): string {
      let tempID: IdentityId = {name: p.identityID.name, organization: p.identityID.organization};
      return convertToKey(tempID) + "," + p.type;
   }
}
