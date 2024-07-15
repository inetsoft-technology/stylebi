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
import { ElementRef, Injectable } from "@angular/core";
import { Observable, BehaviorSubject } from "rxjs";

@Injectable()
export class LineAnchorService {

   private anchorMap: Map<Element, [string, boolean]> = new Map<Element, [string, boolean]>();
   private endAnchorIdMap: Map<string, string> = new Map<string, string>();
   private startAnchorIdMap: Map<string, string> = new Map<string, string>();
   private endAnchorLineToTypeMap: Map<string, number> = new Map<string, number>();
   private startAnchorLineToTypeMap: Map<string, number> = new Map<string, number>();
   private objectEditorNameMap: Map<Element, string> = new Map<Element, string>();

   registerLineAnchor(line: string, vsobject: Element, end: boolean, handle: Element) {

      if(!this.anchorMap.get(vsobject)) {
         this.anchorMap.set(vsobject, [line, end]);
         const vsObjectName = this.objectEditorNameMap.get(vsobject);
         const north = handle.classList.contains("top");
         const south = handle.classList.contains("bottom");
         const west = handle.classList.contains("left");
         const east = handle.classList.contains("right");
         let type = 0;
         type += north ? 1 : 0;
         type += south ? 4 : 0;
         type += west  ? 8 : 0;
         type += east  ? 2 : 0;

         if(end) {
            this.endAnchorLineToTypeMap.set(line, type);
            this.endAnchorIdMap.set(line, vsObjectName);
         }
         else {
            this.startAnchorLineToTypeMap.set(line, type);
            this.startAnchorIdMap.set(line, vsObjectName);
         }
      }
   }

   unregisterLineAnchor(vsobject: Element) {
      let line = this.anchorMap.get(vsobject);

      if(!!line) {
         this.anchorMap.delete(vsobject);
         const vsObjectName = this.objectEditorNameMap.get(vsobject);

         if(this.startAnchorIdMap.get(line[0]) == vsObjectName) {
            this.startAnchorIdMap.delete(line[0]);
            this.startAnchorLineToTypeMap.delete(line[0]);
         }

         if(this.endAnchorIdMap.get(line[0]) == vsObjectName) {
            this.endAnchorIdMap.delete(line[0]);
            this.endAnchorLineToTypeMap.delete(line[0]);
         }
      }
   }

   getLineInfo(vsLine: string): {startId: string, endId: string, startPos: number, endPos: number} {
      return {
         startId: this.startAnchorIdMap.get(vsLine),
         endId: this.endAnchorIdMap.get(vsLine),
         startPos: this.startAnchorLineToTypeMap.get(vsLine),
         endPos: this.endAnchorLineToTypeMap.get(vsLine)
      };
   }

   addEditorName(elem: ElementRef, name: string) {
      this.objectEditorNameMap.set(elem.nativeElement, name);
   }

   removeEditorName(elem: ElementRef) {
      this.objectEditorNameMap.delete(elem.nativeElement);
      const lineSide = this.anchorMap.get(elem.nativeElement);

      if(!!lineSide) {
         if(lineSide[1]) {
            this.endAnchorIdMap.delete(lineSide[0]);
         }
         else {
            this.startAnchorIdMap.delete(lineSide[0]);
         }
      }
   }
}
