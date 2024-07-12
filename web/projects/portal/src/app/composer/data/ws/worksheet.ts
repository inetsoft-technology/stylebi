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
import { Sheet } from "../sheet";
import { AbstractTableAssembly } from "./abstract-table-assembly";
import { CompositeTableAssembly } from "./composite-table-assembly";
import { WSAssembly } from "./ws-assembly";
import { WSCompositeViewInfo } from "./ws-composite-view-info";
import { WSGroupingAssembly } from "./ws-grouping-assembly";
import { WSVariableAssembly } from "./ws-variable-assembly";

export class Worksheet extends Sheet {
   public tables: AbstractTableAssembly[] = [];
   public variables: WSVariableAssembly[] = [];
   public groupings: WSGroupingAssembly[] = [];
   public init: boolean = false;
   public jspAssemblyGraph: JSPlumb.JSPlumbInstance;
   public jspSchemaGraph: JSPlumb.JSPlumbInstance;
   public compositeViewInfo: WSCompositeViewInfo;
   public hasVPMPrincipal: boolean = false;
   public autoSaveFile: string;
   public closeProhibited: boolean = false;
   public singleQuery: boolean;
   private _callback?: () => any;

   constructor(sheet: Worksheet = null) {
      super(sheet);
      this.type = "worksheet";
      this.compositeViewInfo = sheet ?
         sheet.compositeViewInfo : new WSCompositeViewInfo();

      if(sheet) {
         this.tables = sheet.tables;
         this.variables = sheet.variables;
         this.groupings = sheet.groupings;
         this.init = sheet.init;
         this.jspAssemblyGraph = sheet.jspAssemblyGraph;
         this.jspSchemaGraph = sheet.jspSchemaGraph;
         this.hasVPMPrincipal = sheet.hasVPMPrincipal;
         this.closeProhibited = sheet.closeProhibited;
         this.gettingStarted = sheet.gettingStarted;
         this.singleQuery = sheet.singleQuery;
      }
   }

   /** @inheritDoc */
   public getCurrentFocusedAssemblies(): WSAssembly[] {
      if(this.selectedCompositeTable != null) {
         return [];
      }
      else {
         return super.getCurrentFocusedAssemblies();
      }
   }

   public get selectedCompositeTable(): CompositeTableAssembly {
      return this.compositeViewInfo.selectedCompositeTable;
   }

   public set selectedCompositeTable(table: CompositeTableAssembly) {
      this.compositeViewInfo.selectedCompositeTable = table;
   }

   public get selectedSubtables(): AbstractTableAssembly[] {
      return this.compositeViewInfo.selectedSubtables;
   }

   public set selectedSubtables(subtables: AbstractTableAssembly[]) {
      this.compositeViewInfo.selectedSubtables = subtables;
   }

   public set callBackFun(func: () => any) {
      this._callback = func;
   }

   public get callBackFun(): () => any {
      return this._callback;
   }

   /** Returns true if worksheet is in composite view, false otherwise. */
   public isCompositeView(): boolean {
      return this.selectedCompositeTable != null;
   }

   public exitCompositeView() {
      this.selectedCompositeTable = null;
   }

   /**
    * Returns the assembly names of the worksheet. If name is specified, then that name
    * will filter out from the resultant array.
    *
    * @param name the name to exclude from the result.
    *
    * @return the assembly names of the worksheet.
    */
   public assemblyNames(name?: string): string[] {
      let names = this.assemblies().map((a) => a.name);

      if(name != undefined) {
         names = names.filter((n) => n !== name);
      }

      return names;
   }

   /** All the assemblies in the worksheet. */
   public assemblies(): WSAssembly[] {
      return [
         ...this.tables,
         ...this.groupings,
         ...this.variables
      ];
   }

   /**
    * Return the table with the given name in this worksheet if such a table exists.
    *
    * @param tableName the name of the table.
    * @returns the table if found, undefined otherwise.
    */
   public getTableFromName(tableName: string): AbstractTableAssembly {
      return this.tables.find((table) => table.name === tableName);
   }

   /** @inheritDoc */
   public deselectAssembly(assembly: WSAssembly): void {
      super.deselectAssembly(assembly);

      if(this.compositeViewInfo != null) {
         if(!!this.selectedCompositeTable && this.selectedCompositeTable.name === assembly.name) {
            this.exitCompositeView();
         }
         else if(!!this.selectedSubtables && this.selectedSubtables.length > 0) {
            const index = this.selectedSubtables.findIndex((a) => a.name === assembly.name);

            if(index >= 0) {
               this.selectedSubtables.splice(index, 1);
            }
         }
      }
   }

   /** @inheritDoc */
   public replaceFocusedAssembly(oldAssembly: WSAssembly, newAssembly: WSAssembly): void {
      super.replaceFocusedAssembly(oldAssembly, newAssembly);

      if(this.compositeViewInfo != null) {
         this.compositeViewInfo.updateAssembly(oldAssembly, newAssembly);
      }
   }

   /** @inheritDoc */
   public isAssemblyFocused(assembly: WSAssembly): boolean {
      let focused: boolean = super.isAssemblyFocused(assembly);

      if(!!this.compositeViewInfo && assembly instanceof AbstractTableAssembly) {
         focused = focused || this.compositeViewInfo.selectedCompositeTable === assembly;
         const subtables = this.compositeViewInfo.selectedSubtables;

         if(!!subtables && subtables.length > 0) {
            focused = focused || subtables.indexOf(assembly) >= 0;
         }
      }

      return focused;
   }

   /**
    * Updates secondary references to assemblies, e.g. focused assemblies, so that
    * they contain references to updated assemblies.
    */
   public updateSecondaryAssemblyReferences() {
      const assemblies = this.assemblies();

      if(!!this.compositeViewInfo) {
         this.compositeViewInfo.refreshAssemblyReferences(this.tables);
      }

      const currentFocusedAssemblies = super.getCurrentFocusedAssemblies();

      if(currentFocusedAssemblies.length > 0) {
         const updatedFocusAssemblies: WSAssembly[] = [];

         super.getCurrentFocusedAssemblies().forEach((oldAssembly: WSAssembly) => {
            const assembly = assemblies.find((newAssembly) => newAssembly.name === oldAssembly.name);

            if(!!assembly) {
               updatedFocusAssemblies.push(assembly);
            }
         });

         this.currentFocusedAssemblies = updatedFocusAssemblies;
      }

      if(!!this.selectedCompositeTable) {
         const newTable = this.tables.find((table) => table.name === this.selectedCompositeTable.name);

         if(!!newTable && newTable instanceof CompositeTableAssembly) {
            this.selectedCompositeTable = newTable;
         }
         else {
            this.selectedCompositeTable = null;
         }
      }
   }

   public get focusedTable(): AbstractTableAssembly {
      if(this.isCompositeView()) {
         if(!!this.selectedSubtables && this.selectedSubtables.length > 0) {
            return this.selectedSubtables[0];
         }

         if(!!this.selectedCompositeTable) {
            return this.selectedCompositeTable;
         }
      }

      if(this.currentFocusedAssemblies.length > 0) {
         const assembly = this.currentFocusedAssemblies[0];

         if(assembly instanceof AbstractTableAssembly) {
            return assembly;
         }
      }

      return null;
   }
}
