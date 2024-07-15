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
import { ViewsheetEvent } from "../../../common/viewsheet-client";
import { VSObjectModel } from "../../model/vs-object-model";

export class RemoveAnnotationEvent implements ViewsheetEvent {
   public static readonly REMOVE_ANNOTATION_URI: string = "/events/annotation/remove-annotation";
   // The names of the annotations to remove
   private names: string[];

   private constructor(names: string[]) {
      this.names = names;
   }

   /**
    * Creates a RemoveAnnotationEvent that contains the names of the annotations to be removed
    *
    * @param vsObjects          the objects to check for annotations
    * @param selectedAssemblies the selected objects
    */
   public static create(vsObjects: VSObjectModel[],
                        selectedAssemblies: number[]): RemoveAnnotationEvent
   {
      if(selectedAssemblies) {
         const names = selectedAssemblies
            .map(index => vsObjects[index])
            .reduce((selected, current) => {
               if(current.objectType === "VSAnnotation") {
                  return selected.concat(current.absoluteName);
               }
               else if(current.selectedAnnotations) {
                  return selected.concat(current.selectedAnnotations);
               }
               else {
                  return selected;
               }
            }, []);

         if(names.length > 0) {
            return new RemoveAnnotationEvent(names);
         }
      }

      return null;
   }
}
