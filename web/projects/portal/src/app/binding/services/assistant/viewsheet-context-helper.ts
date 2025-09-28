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

import { Viewsheet } from "../../../composer/data/vs/viewsheet";

export function getViewsheetScriptContext(vs: Viewsheet): string {
   if(!vs || !vs.vsObjects || vs.vsObjects.length === 0) {
      return "";
   }

   const contextMap = new Map<string, string>();

   vs.vsObjects.forEach(vsObject => {
      let objectType = vsObject.objectType.substring(2);
      objectType = objectType === "CalcTable" ? "Freehand table" : objectType;

      if(contextMap.has(objectType)) {
         contextMap.set(objectType, contextMap.get(objectType) + ", " + vsObject.absoluteName);
      }
      else {
         contextMap.set(objectType, vsObject.absoluteName);
      }
   });

   return Array.from(contextMap.entries())
      .map(entry => `${entry[0]}: ${entry[1]}`)
      .join("\n");
}