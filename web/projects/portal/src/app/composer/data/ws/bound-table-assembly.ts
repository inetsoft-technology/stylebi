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
import { SourceInfo } from "../../../binding/data/source-info";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AbstractTableAssembly } from "./abstract-table-assembly";
import { BoundTableAssemblyInfo } from "./bound-table-assembly-info";
import { WSTableAssembly } from "./ws-table-assembly";

export class BoundTableAssembly extends AbstractTableAssembly {
   info: BoundTableAssemblyInfo;

   constructor(table: WSTableAssembly) {
      super(table);
   }

   public isValidToInsert(entries: AssetEntry[]): boolean {
      if(!entries || entries.length === 0) {
         return false;
      }

      // var columns:ColumnSelection = tinfo.getPrivateColumnSelection() as ColumnSelection;
      let source: SourceInfo = (<BoundTableAssemblyInfo> this.info).sourceInfo;

      for(let i = 0; i < entries.length; i++) {
         let type = entries[i].type;

         if(type != AssetType.COLUMN && type != AssetType.PHYSICAL_COLUMN) {
            return false;
         }

         let parent: string = entries[i].properties["source"];
         let entity: string = entries[i].properties["entity"];
         let attr: string = entries[i].properties["attribute"];
         let caption: string = entries[i].properties["caption"];
         let rtype: string = entries[i].properties["refType"];
         let refType: number = rtype == null ? 0 : +rtype;
         // var ref:AttributeRef = new AttributeRef(entity, attr);
         // ref.setRefType(refType);
         //
         // if(caption != null) {
         //    ref.setCaption(caption);
         // }
         //
         // var column:ColumnRef = new ColumnRef(ref);

         if(source != null && source.source != null &&
            source.source != parent && attr != null /*||
            columns.containsAttribute(column)*/)
         {
            return false;
         }
      }

      return true;
   }
}
