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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatPaginator } from "@angular/material/paginator";
import { MatTableDataSource } from "@angular/material/table";
import { Observable } from "rxjs";
import {
   EditLicenseKeyDialogData,
   LicenseKeyType
} from "../edit-license-key-dialog/edit-license-key-dialog-data";
import { EditLicenseKeyDialogComponent } from "../edit-license-key-dialog/edit-license-key-dialog.component";
import { LicenseKeyModel } from "../license-key-settings-model";

@Component({
   selector: "em-api-key",
   templateUrl: "./api-key.component.html",
   styleUrls: ["./api-key.component.scss"]
})
export class ApiKeyComponent implements OnInit {
   @Input() title: string;
   @Input() scheduler = false;
   @Input() isEnterpise: boolean;
   @Output() keysChange = new EventEmitter<LicenseKeyModel[]>();
   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;

   @Input()
   get keys(): LicenseKeyModel[] {
      return this._keys;
   }

   set keys(value: LicenseKeyModel[]) {
      this._keys = value || [];
      this.dataSource.data = this._keys;
      this.updateSelection();
   }

   get hasKey(): boolean {
      return !!(this._keys?.length && this._keys[0]?.key?.trim());
   }

   getKey() {
      return this._keys[0];
   }

   private get keyType(): LicenseKeyType {
      return LicenseKeyType.SERVER;
   }

   selection = new SelectionModel<LicenseKeyModel>(true, []);
   dataSource = new MatTableDataSource<LicenseKeyModel>();
   displayedColumns = [ "select", "key" ];
   private _keys: LicenseKeyModel[] = [];

   constructor(private dialog: MatDialog) {
   }

   ngOnInit() {
      this.dataSource.paginator = this.paginator;
   }

   updateSelection() {
      let licenseKeyModels = this.selection.selected.filter(row => this.dataSource.data.includes(row));
      this.selection.clear();

      licenseKeyModels.forEach(km => {
         this.selection.select(km);
      });
   }

   editKey(): void {
      const key = this.selection.selected[0];
      const index = this.keys.findIndex(k => k.key == key.key);
      this.openKeyDialog(this.keyType, key).subscribe(result => {
         if(result) {
            const oldKey = this.keys[index];
            const selected = this.selection.selected.slice();
            const selectionIndex = selected.findIndex(k => k.key === oldKey.key);

            const values = this.keys;
            values[index] = result;
            this.keys = values;
            this.selection.clear();

            if(selectionIndex >= 0) {
               selected[selectionIndex] = result;
               this.selection.select(...selected);
            }

            this.keysChange.emit(values);
         }
      });
   }

   addKey() {
      this.openKeyDialog(this.keyType).subscribe(result => {
         if(result) {
            const values = [result];
            this.keys = values;
            this.keysChange.emit(values);
         }
      });
   }

   deleteKey() {
      const values = this.keys;
      this.selection.selected.forEach((key) => {
         const index = values.findIndex(k => k.key === key.key);

         if(index >= 0) {
            values.splice(index, 1);
         }
      });
      this.keys = values;
      this.selection.clear();
      this.keysChange.emit(values);
   }

   private openKeyDialog(type: LicenseKeyType, key?: LicenseKeyModel): Observable<LicenseKeyModel> {
      return this.dialog.open(EditLicenseKeyDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: new EditLicenseKeyDialogData(key, type, this.keys, this.isEnterpise),
      }).afterClosed();
   }
}
