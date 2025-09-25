/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { DataRef } from "../../../common/data/data-ref";

export function getAvailableFields(availableFields: DataRef[]): string {
   if(!availableFields) {
      return "";
   }

   let fields: Field[] = [];

   availableFields.forEach(fld => {
      let is_calc = false;
      let expression = "";

      if(fld.classType === "CalculateRef" && fld.expression && (<any> fld).dataRefModel?.exp) {
         is_calc = true;
         expression = (<any> fld).dataRefModel.exp;
      }

      fields.push({
         field_name: fld.name,
         data_type: fld.dataType,
         description: fld.description,
         is_calcfield: is_calc
      });
   });

   return JSON.stringify(fields);
}
