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
import { AbstractTableAssembly } from "./abstract-table-assembly";
import { CompositeTableAssembly } from "./composite-table-assembly";
import { WSAssembly } from "./ws-assembly";
import { WSCompositeBreadcrumb } from "./ws-composite-breadcrumb";

export class WSCompositeViewInfo {
   private _breadcrumbs: WSCompositeBreadcrumb[] = [];
   private selectedBreadcrumbIndex: number;

   public get selectedCompositeTable(): CompositeTableAssembly {
      return this.selectedBreadcrumb ? this.selectedBreadcrumb.compositeTable : null;
   }

   public set selectedCompositeTable(table: CompositeTableAssembly) {
      if(table == null) {
         this.clear();
         return;
      }

      const existingBreadcrumbIndex =
         this._breadcrumbs.findIndex((bc) => bc.compositeTable.name === table.name);

      if(existingBreadcrumbIndex >= 0 &&
         existingBreadcrumbIndex <= this.selectedBreadcrumbIndex + 1)
      {
         this.selectedBreadcrumbIndex = existingBreadcrumbIndex;
      }
      else {
         const newBreadcrumb: WSCompositeBreadcrumb = {
            compositeTable: table,
            selectedSubtables: []
         };

         this._breadcrumbs =
            [...this._breadcrumbs.slice(0, this.selectedBreadcrumbIndex + 1), newBreadcrumb];
         this.selectedBreadcrumbIndex = this.breadcrumbs.length - 1;
      }
   }

   public get selectedSubtables(): AbstractTableAssembly[] {
      return this.selectedBreadcrumb ? this.selectedBreadcrumb.selectedSubtables : [];
   }

   public set selectedSubtables(subtables: AbstractTableAssembly[]) {
      const breadcrumb = this.selectedBreadcrumb;

      if(!!breadcrumb) {
         breadcrumb.selectedSubtables = subtables;
      }
   }

   public get breadcrumbs(): WSCompositeBreadcrumb[] {
      return this._breadcrumbs;
   }

   public updateAssembly(oldAssembly: WSAssembly, newAssembly: WSAssembly) {
      if(!(oldAssembly instanceof AbstractTableAssembly &&
            newAssembly instanceof AbstractTableAssembly))
      {
         return;
      }

      for(let i = this._breadcrumbs.length - 1; i >= 0; i--) {
         const breadcrumb = this._breadcrumbs[i];

         if(breadcrumb.compositeTable === oldAssembly &&
            newAssembly instanceof CompositeTableAssembly)
         {
            breadcrumb.compositeTable = newAssembly;
            breadcrumb.selectedSubtables = breadcrumb.selectedSubtables
               .filter((subtable) => newAssembly.subtables.indexOf(subtable.name) !== -1);
         }
         else if(!!breadcrumb.selectedSubtables && breadcrumb.selectedSubtables.length > 0) {
            const index = breadcrumb.selectedSubtables.indexOf(oldAssembly);

            if(index >= 0) {
               breadcrumb.selectedSubtables[index] = newAssembly;
            }
         }
      }

      this._breadcrumbs = [...this._breadcrumbs];
   }

   public refreshAssemblyReferences(tables: AbstractTableAssembly[]) {
      for(const breadcrumb of this._breadcrumbs) {
         const compositeTable =
            tables.find((table) => table.name === breadcrumb.compositeTable.name);

         if(compositeTable instanceof CompositeTableAssembly) {
            breadcrumb.compositeTable = compositeTable;
         }

         if(!!breadcrumb.selectedSubtables && breadcrumb.selectedSubtables.length > 0) {
            breadcrumb.selectedSubtables =
               tables.filter((table) =>
                  breadcrumb.selectedSubtables.find(
                     (subtable) => subtable.name === table.name));
         }
      }

      this._breadcrumbs = [...this._breadcrumbs];
   }

   public clear() {
      this._breadcrumbs = [];
      this.selectedBreadcrumbIndex = -1;
   }

   public get selectedBreadcrumb(): WSCompositeBreadcrumb {
      return this._breadcrumbs[this.selectedBreadcrumbIndex];
   }

   public set selectedBreadcrumb(breadcrumb: WSCompositeBreadcrumb) {
      const index = this._breadcrumbs.indexOf(breadcrumb);

      if(index >= 0) {
         this.selectedBreadcrumbIndex = index;
      }
      else {
         console.error("Unknown breadcrumb selected.");
      }
   }
}