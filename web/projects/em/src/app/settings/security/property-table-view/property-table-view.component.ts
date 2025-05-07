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
import {
   AfterViewInit,
   Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild
} from "@angular/core";
import { SelectionModel } from "@angular/cdk/collections";
import { MatDialog } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { MatPaginator } from "@angular/material/paginator";
import { OrganizationPropertyDialogComponent } from
   "../organization-property-dialog/organization-property-dialog.component";
import { PropertyModel } from "./property-model";

@Component({
   selector: "em-property-table-view",
   templateUrl: "./property-table-view.component.html",
   styleUrls: ["./property-table-view.component.scss"]
})
export class PropertyTableViewComponent implements OnChanges, AfterViewInit {
   @Input() get dataSource(): PropertyModel[] {
      return this._dataSource;
   }

   set dataSource(value: PropertyModel[]) {
      const editable: PropertyModel[] = [];
      const uneditable: PropertyModel[] = [];

      for(let i = 0; i < value.length; i++) {
         if(this.isEditableProperty(value[i].name)) {
            editable.push(value[i]);
         }
         else {
            uneditable.push(value[i]);
         }
      }

      editable.sort((p1, p2) => p1.name.localeCompare(p2.name));
      uneditable.sort((p1, p2) => p1.name.localeCompare(p2.name));
      this.editableProperties = editable;
      this._dataSource = [...editable, ...uneditable];
   }

   _dataSource: PropertyModel[] = [];
   editableProperties = [];
   @Input() name: string;
   @Input() type: number;
   @Input() label: string;
   @Input() editable: boolean = true;
   @Output() addProperties = new EventEmitter<PropertyModel[]>();
   @Output() removeProperties = new EventEmitter<PropertyModel[]>();
   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
   matTableDataSource: MatTableDataSource<PropertyModel>;
   displayColumns: string[] = ["selected", "name", "value"];
   orgProperties: string[] = ["max.row.count", "max.col.count", "max.cell.size", "max.user.count"];
   selection = new SelectionModel<PropertyModel>(true, []);

   constructor(private dialog: MatDialog) { }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource) {
         if(this.dataSource != null) {
            this.matTableDataSource = new MatTableDataSource(this.dataSource);
            this.matTableDataSource.paginator = this.paginator;
            this.selection.clear();
         }
      }
   }

   ngAfterViewInit() {
      if(this.matTableDataSource) {
         this.matTableDataSource.paginator = this.paginator;
      }
   }

   getLabel(name: string): string {
      if(name == "max.row.count") {
         return "_#(js:Max Row Count)";
      }
      else if(name == "max.col.count") {
         return "_#(js:Max Column Count)";
      }
      else if(name == "max.user.count") {
         return "_#(js:Max User Count)";
      }
      else if(name == "max.cell.size") {
         return "_#(js:Max Cell Size)";
      }

      return name;
   }

   openAddDialog(isAdd: boolean) {
      this.dialog.open(OrganizationPropertyDialogComponent, {
         role: "dialog",
         width: "400px",
         data: this.selection.selected[0]
      }).afterClosed().subscribe(result => {
         if(result) {
            if(!isAdd) {
               this.removeProperties.emit([this.selection.selected[0]]);
            }

            this.addProperties.emit([result]);
         }
      });
   }

   getIcon(type: number): string {
      let icon: string;

      switch(type) {
      case IdentityType.USER:
         icon = "account-icon";
         break;
      case IdentityType.GROUP:
         icon = "user-group-icon";
         break;
      case IdentityType.ORGANIZATION:
         icon = "user-organizations-icon";
         break;
      default:
         icon = "user-roles-icon";
      }

      return icon;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.editableProperties.length;
   }

   toggleRow(row) {
      this.selection.toggle(row);
   }

   masterToggle() {
      this.isAllSelected() ?
         this.selection.clear() :
         this.matTableDataSource.data
            .filter(prop => this.isEditableProperty(prop.name))
            .forEach(row => this.selection.select(row));
   }

   toggleSelection(row :PropertyModel) {
      if(this.isEditableProperty(row.name)) {
         this.selection.toggle(row);
      }
   }

   isEditableProperty(property: string): boolean {
      return this.orgProperties.indexOf(property) != -1;
   }
}
