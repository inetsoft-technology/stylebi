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
import { GuideBounds } from "../model/layout/guide-bounds";
import { VSObjectModel } from "../model/vs-object-model";
import { VSSelectionBaseModel } from "../model/vs-selection-base-model";
import { VSCompositeModel } from "../model/vs-composite-model";

export namespace LayoutUtil {
   // eslint-disable-next-line no-inner-declarations
   function useTitle(obj: VSObjectModel ): boolean {
      return (obj.objectType == "VSSelectionList" ||
         obj.objectType == "VSSelectionTree") &&
         (<VSSelectionBaseModel> obj).dropdown;
   }

   /**
    * Get the display height of the assembly.
    */
   export function getHeight(obj: VSObjectModel): number {
      return useTitle(obj) ? (<VSCompositeModel> obj).titleFormat.height : obj.objectFormat.height;
   }

   /**
    * Set the display height of the assembly.
    */
   export function setHeight(obj: VSObjectModel, height: number): void {
      if(useTitle(obj)) {
         (<VSCompositeModel> obj).titleFormat.height = height;
      }
      else {
         obj.objectFormat.height = height;
      }
   }

   export function getWidth(obj: VSObjectModel): number {
      return obj.objectFormat.width;
   }

   export function setWidth(obj: VSObjectModel, value: number): void {
      obj.objectFormat.width = value;
   }
}
