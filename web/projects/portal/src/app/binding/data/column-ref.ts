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
import { AbstractDataRef } from "../../common/data/abstract-data-ref";
import { DataRefWrapper } from "../../common/data/data-ref-wrapper";
import { DataRef } from "../../common/data/data-ref";
import { AttributeRef } from "../../common/data/attribute-ref";
import { DateRangeRef } from "../../common/data/date-range-ref";
import { Tool } from "../../../../../shared/util/tool";
import { NumericRangeRef } from "../../common/data/numeric-range-ref";
import { GroupRef } from "../../common/data/group-ref";
import { AggregateRef } from "../../common/data/aggregate-ref";

export class ColumnRef extends AbstractDataRef implements DataRefWrapper {
   dataRefModel: DataRef;
   alias: string;
   width: number;
   visible: boolean;
   valid: boolean;
   sql: boolean;
   description: string;
   order?: number;

   public static equal(ref1: ColumnRef, ref2: ColumnRef): boolean {
      if(ref1 === ref2) {
         return true;
      }

      if(ref1 == undefined || ref2 == undefined) {
         return false;
      }

      return ref1.entity === ref2.entity &&
         ref1.attribute === ref2.attribute &&
         ref1.alias === ref2.alias &&
         ref1.width === ref2.width &&
         ref1.visible === ref2.visible &&
         ref1.valid === ref2.valid &&
         ref1.sql === ref2.sql &&
         ref1.dataType === ref2.dataType &&
         ref1.description === ref2.description;
   }

   // compare name only. columnRef many not contain all same attributes when they are
   // created from different point (e.g. join op vs. table info). make sure we can find
   // the column since join is done by table.column instead of full attributes
   public static equalName(ref1: ColumnRef, ref2: ColumnRef): boolean {
      if(ref1 === ref2) {
         return true;
      }

      if(ref1 == undefined || ref2 == undefined) {
         return false;
      }

      return ref1.name == ref2.name;
   }

   /**
    * Get the caption of a column ref.
    *
    * @param ref the ref to get the caption of
    * @returns the ref's caption
    */
   public static getCaption(ref: ColumnRef): string {
      let caption: string;

      if(ref.dataRefModel && (<AttributeRef> ref.dataRefModel).caption) {
         caption = (<AttributeRef> ref.dataRefModel).caption;
      }
      else {
         const entity: string = ref.entity;
         const attr: string = ref.attribute;
         // Bug #23023
         caption = entity == null ? attr : entity.replace(/(OUTER_|_\d+)/g, "") + "." + attr;
      }

      return caption;
   }

   public static getTooltip(_ref: DataRef): string {
      let tooltip: string = "";
      let dataRef: DataRef = ColumnRef.getColumnRef(_ref)?.dataRefModel;
      let att: AttributeRef = this.getAttributeRef(dataRef);

      if(att == null || dataRef == null) {
         return dataRef != null && dataRef.classType == "ExpressionRef" ?
            ColumnRef.getColumnRef(_ref)?.description : tooltip;
      }

      if(dataRef.classType == "NumericRangeRef" || dataRef.classType == "DateRangeRef") {
         tooltip = _ref.view + " (" + att.view + ")" + (!Tool.isEmpty(_ref.description) ?
            "\n" + _ref.description : "");
      }
      else {
         tooltip = "Alias: " + _ref.view + " (" + att.view + ")" + (!Tool.isEmpty(_ref.description) ?
            "\nDescription: " + _ref.description : "");
      }

      return tooltip;
   }

   public static getColumnRef(ref: DataRef): ColumnRef {
      if(ref.classType == "GroupRef") {
         let group: GroupRef = <GroupRef> ref;
         return ColumnRef.getColumnRef(group.ref);
      }
      else if(ref.classType == "AggregateRef") {
         let group: AggregateRef = <AggregateRef> ref;
         return ColumnRef.getColumnRef(group.ref);
      }
      else if(ref.classType == "ColumnRef"){
         return <ColumnRef> ref;
      }

      return null;
   }

   public static getAttributeRef(ref: DataRef): AttributeRef {
      if(!!!ref) {
         return null;
      }

      if(ref.classType == "AttributeRef") {
         return <AttributeRef> ref;
      }
      else if(ref.classType == "DateRangeRef") {
         return <AttributeRef> (<DateRangeRef> ref).ref;
      }
      else if(ref.classType == "NumericRangeRef") {
         return <AttributeRef> (<NumericRangeRef> ref).ref;
      }

      return null;
   }
}
