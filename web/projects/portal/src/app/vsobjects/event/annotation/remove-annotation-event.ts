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
      const names: string[] = [];

      if(selectedAssemblies) {
         for(let index of selectedAssemblies) {
            const current = vsObjects[index];

            if(current && current.objectType === "VSAnnotation" &&
               names.indexOf(current.absoluteName) === -1)
            {
               names.push(current.absoluteName);
            }
         }
      }

      // chart data point annotations are selected via a separate overlay
      // (see chart-annotation-overlay in vs-object-container.component.html) that doesn't add
      // the chart itself to selectedAssemblies, so selectedAnnotations must be checked on every
      // vsObject rather than only ones already in selectedAssemblies
      if(vsObjects) {
         for(let vsObject of vsObjects) {
            if(vsObject && vsObject.selectedAnnotations) {
               for(let name of vsObject.selectedAnnotations) {
                  if(names.indexOf(name) === -1) {
                     names.push(name);
                  }
               }
            }
         }
      }

      return names.length > 0 ? new RemoveAnnotationEvent(names) : null;
   }
}
