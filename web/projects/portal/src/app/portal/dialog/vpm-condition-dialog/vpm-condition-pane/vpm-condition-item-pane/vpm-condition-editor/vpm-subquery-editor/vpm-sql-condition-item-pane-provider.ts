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
import { SQLConditionItemPaneProvider } from "../../../../../../../composer/dialog/ws/sql-condition-item-pane-provider";
import { Condition } from "../../../../../../../common/data/condition/condition";
import { Observable } from "rxjs";
import { BrowseDataModel } from "../../../../../../../common/data/browse-data-model";
import { ColumnRef } from "../../../../../../../binding/data/column-ref";
import { XSchema } from "../../../../../../../common/data/xschema";
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";

const CONTROLLER_BROWSE_DATA: string = "../api/data/vpm/sql-query-dialog/browse-data";

export class VpmSqlConditionItemPaneProvider extends SQLConditionItemPaneProvider {
   constructor(http: HttpClient) {
      super(http);
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      let ref: ColumnRef = {
         classType: "ColumnRef",
         dataRefModel: condition.field,
         visible: true,
         width: 1,
         sql: true,
         alias: null,
         valid: true,
         dataType: XSchema.STRING,
         description: ""
      };

      const headers = new HttpHeaders({
         "Content-Type": "application/json"
      });

      const params = new HttpParams()
         .set("dataSource", this.dataSource);

      return this.http.post<BrowseDataModel>(CONTROLLER_BROWSE_DATA, ref, {headers, params});
   }
}

