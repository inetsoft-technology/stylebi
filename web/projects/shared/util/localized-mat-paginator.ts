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
import { MatLegacyPaginatorIntl as MatPaginatorIntl } from "@angular/material/legacy-paginator";
import { Injectable } from "@angular/core";
import { Tool } from "./tool";

@Injectable()
export class LocalizedMatPaginator extends MatPaginatorIntl {
   lastPageLabel = "_#(js:Last Page)";
   firstPageLabel = "_#(js:First Page)";
   itemsPerPageLabel = "_#(js:Items per Page):";
   nextPageLabel = "_#(js:Next Page)";
   previousPageLabel = "_#(js:Previous Page)";

   getRangeLabel = function(page: number, pageSize: number, length: number) {
      if(length == 0 || pageSize == 0) {
         return Tool.formatCatalogString("_#(js:nOfTotal)", [0, length]);
      }

      length = Math.max(length, 0);

      const startIndex = page * pageSize;

      // If the start index exceeds the list length, do not try and fix the end index to the end.
      const endIndex = startIndex < length ?
         Math.min(startIndex + pageSize, length) :
         startIndex + pageSize;

      return (startIndex + 1) + " - " +
        Tool.formatCatalogString("_#(js:nOfTotal)", [endIndex, length]);
   };
}
