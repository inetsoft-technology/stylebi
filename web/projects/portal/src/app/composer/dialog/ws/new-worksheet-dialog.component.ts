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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { DatabaseDataSource } from "../../../../../../shared/util/model/database-data-source";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";

export enum WSObjectType {
   EMBEDDED_TABLE,
   UPLOAD_FILE,
   DATABASE_QUERY,
   VARIABLE,
   GROUPING,
   TABULAR,
   MASHUP
}

@Component({
   selector: "new-worksheet-dialog",
   templateUrl: "new-worksheet-dialog.component.html",
   styleUrls: ["new-worksheet-dialog.component.scss"]
})
export class NewWorksheetDialog {
   @Input() tabularDataSourceTypes: TabularDataSourceTypeModel[];
   @Input() databaseDataSources: DatabaseDataSource[];
   @Input() sqlEnabled: boolean;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   WSObjectType = WSObjectType;
   submitOnEnter = () => false;

   selectObjectType(type: WSObjectType): void {
      this.onCommit.emit({objectType: type});
   }

   selectDatabaseQuery(dataSource: string): void {
      this.onCommit.emit({objectType: WSObjectType.DATABASE_QUERY, dataSource});
   }

   selectTabularQuery(tabularType: TabularDataSourceTypeModel): void {
      this.onCommit.emit({objectType: WSObjectType.TABULAR, tabularType });
   }

   ok() {
      this.onCommit.emit({objectType: null, tabularType: null});
   }

   cancel() {
      this.onCancel.emit("cancel");
   }
}
