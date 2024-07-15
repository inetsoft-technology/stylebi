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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { TableInfo } from "../../../../common/util/table/table-info";
import { TableModel } from "../../../../common/util/table/table-model";
import { RepositoryRecycleBinTableModel } from "../repository-folder-recycle-bin-page/repository-folder-recycle-bin-table-model";

@Component({
   selector: "em-repository-recycle-bin-settings-view",
   templateUrl: "./repository-recycle-bin-settings-view.component.html",
   styleUrls: ["./repository-recycle-bin-settings-view.component.scss"]
})
export class RepositoryRecycleBinSettingsViewComponent implements OnInit {
   @Input() model: RepositoryRecycleBinTableModel[];
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() restoreAssets = new EventEmitter<RepositoryRecycleBinTableModel[]>();
   @Output() removeAssets = new EventEmitter<RepositoryRecycleBinTableModel[]>();
   @Output() overwriteChanged = new EventEmitter<boolean>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   @Input() reportsTableInfo: TableInfo;

   @Input()
   get overwrite(): boolean {
      return this._overwrite;
   }

   set overwrite(value: boolean) {
      this._overwrite = !!value;
      this.form.get("overwrite").setValue(this._overwrite);
   }

   reports: TableModel[] = [];
   form: UntypedFormGroup;

   private _overwrite = false;

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         overwrite: [false, []]
      });
   }

   ngOnInit() {
      this.form.valueChanges.subscribe(() => {
         const value = !!this.form.get("overwrite").value;

         if(value !== this._overwrite) {
            this._overwrite = value;
            this.overwriteChanged.emit(this._overwrite);
         }
      });
   }

   restoreReports(): void {
      this.restoreAssets.emit(this.reports as RepositoryRecycleBinTableModel[]);
   }

   removeReports(): void {
      this.removeAssets.emit(this.reports as RepositoryRecycleBinTableModel[]);
   }
}
