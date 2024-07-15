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
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AggregateInfo } from "../../../binding/data/aggregate-info";
import { ColumnRef } from "../../../binding/data/column-ref";
import { ColumnInfo } from "./column-info";
import { WSDependency } from "./ws-dependency";
import {
   TableAssemblyClass,
   WSTableAssembly,
   WSTableButton,
   WSTableMode
} from "./ws-table-assembly";
import { WSTableAssemblyInfo } from "./ws-table-assembly-info";

export abstract class AbstractTableAssembly implements WSTableAssembly {
   name: string;
   description: string;
   top: number;
   left: number;
   dependeds: WSDependency[];
   dependings: string[];
   primary: boolean;
   outerMirror: boolean;

   totalRows: number;
   rowsCompleted: boolean;
   columnTypeEnabled: boolean;
   hasMaxRow: boolean;
   crosstab: boolean;
   info: WSTableAssemblyInfo;
   colInfos: ColumnInfo[];
   exceededMaximum: string;
   aggregateInfo: AggregateInfo;
   classType: "TableAssembly";
   tableClassType: TableAssemblyClass;

   tableButtons: WSTableButton[] = [
      "export",
      "search-toggle",
      "wrap-column-headers",
      "group",
      "sort",
      "visible",
      "reorder-columns"];
   modes: WSTableMode[];
   mode: WSTableMode;

   canDropAssetColumns: boolean;
   readonly headers: string[];

   /** The column to focus the next time this ws details pane focuses this table */
   focusedColumnData: {column: ColumnInfo, offsetLeft: number};

   constructor(table: WSTableAssembly) {
      this.name = table.name;
      this.description = table.description;
      this.top = table.top;
      this.left = table.left;
      this.dependeds = table.dependeds;
      this.dependings = table.dependings;
      this.primary = table.primary;
      this.classType = table.classType as "TableAssembly";

      this.totalRows = table.totalRows;
      this.rowsCompleted = table.rowsCompleted;
      this.columnTypeEnabled = table.columnTypeEnabled;
      this.info = table.info;
      this.colInfos = table.colInfos;
      this.exceededMaximum = table.exceededMaximum;
      this.aggregateInfo = table.aggregateInfo;
      this.tableClassType = table.tableClassType;
      this.crosstab = table.crosstab;

      this.headers = this.colInfos.map(getHeader);
      this.populateModes();
      this.computeCurrentMode();
      this.addOptionalTableButtons();
   }

   public get colCount(): number{
      return this.colInfos.length;
   }

   public isValidToInsert(entries: AssetEntry[]): boolean {
      return false;
   }

   /**
    * Returns true if table would be a WSEmbeddedTable in previous version, false otherwise.
    * Primarily for backwards compatibility with regard to certain UI conditions.
    */
   public isWSEmbeddedTable(): boolean {
      return false;
   }

   public isEmbeddedTable(): boolean {
      return false;
   }

   public isMirrorTable(): boolean {
      return false;
   }

   public isSnapshotTable(): boolean {
      return false;
   }

   /**
    * Returns true if it is possible for this table's cell data to be edited.
    */
   protected isEditable(): boolean {
      return false;
   }

   public getPublicColumnSelection(): ColumnRef[] {
      return this.info.publicSelection;
   }

   public isCrosstabColumn(colInfo: ColumnInfo): boolean {
      if(!colInfo.crosstab) {
         return false;
      }

      const inPrivateSelection = this.info.privateSelection
         .find((ref) => ColumnRef.equal(ref, colInfo.ref));
      const inPublicSelection = this.getPublicColumnSelection()
         .find((ref) => ColumnRef.equal(ref, colInfo.ref));

      return inPublicSelection && !inPrivateSelection;
   }

   /**
    * Sets the focused attribute.
    *
    * @param outerAttribute the outer attribute name to focus
    * @param offsetLeft the outer attribute name to focus
    */
   public setFocusedAttribute(outerAttribute: string, offsetLeft: number) {
      const column = this.colInfos.find((colInfo) => {
         return colInfo.alias === outerAttribute ||
            (colInfo.alias == null && colInfo.ref.dataRefModel.attribute === outerAttribute);
      });

      this.focusedColumnData = {column, offsetLeft};
   }

   /**
    * Clears the focused attribute.
    */
   public clearFocusedAttribute() {
      this.focusedColumnData = null;
   }

   public isRuntime(): boolean {
      return this.info.runtime;
   }

   public isRuntimeSelected(): boolean {
      return this.info.runtimeSelected;
   }

   private getConditionButton(): WSTableButton {
      return this.info.hasCondition ? "has-condition" : "condition";
   }

   private populateModes() {
      this.modes = [];

      // don't show metadata for snapshot
      if(!this.isSnapshotTable() && !this.isEmbeddedTable()) {
         this.modes.push("default");
      }

      if(this.isEditable()) {
         this.modes.push("edit");
      }

      // don't show "Meta Detail View" for snapshot
      if(!this.isSnapshotTable() && !this.isEmbeddedTable()) {
         if(this.info.hasAggregate) {
            this.modes.push("full");
         }
      }

      this.modes.push("live");

      if(this.info.hasAggregate) {
         this.modes.push("detail");
      }
   }

   private computeCurrentMode() {
      if(this.info.editMode) {
         this.mode = "edit";
      }
      // composed not handled
      else if(!this.info.live && this.info.hasAggregate && !this.info.aggregate &&
         !this.info.runtime)
      {
         this.mode = "full";
      }
      else if(this.info.live && this.info.hasAggregate && !this.info.aggregate &&
         !this.info.runtime)
      {
         this.mode = "detail";
      }
      else if(this.info.live) {
         this.mode = "live";
      }
      else {
         this.mode = "default";
      }
   }

   private addOptionalTableButtons() {
      if(!this.info.aggregate) {
         this.tableButtons.push("expression");
      }

      const conditionButton = this.getConditionButton();

      if(conditionButton) {
         this.tableButtons.push(conditionButton);
      }

      if(this.mode == "live" && !this.isSnapshotTable() && !this.isEmbeddedTable()) {
         if(this.rowsCompleted) {
            this.tableButtons.push("run-query");
         }
         else {
            this.tableButtons.push("stop-query");
         }
      }
   }
}

export function getHeader(info: ColumnInfo): string {
   return info.alias ? info.alias : info.header ? info.header : info.ref.attribute;
}
