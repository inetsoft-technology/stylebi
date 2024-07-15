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
import { Injectable } from "@angular/core";

import { CopyVSObjectEvent } from "./vs/event/copy-vs-objects-event";
import { Sheet } from "../data/sheet";
import { WSPasteAssembliesEvent } from "./ws/socket/ws-paste-assemblies-event";
import { Point } from "../../common/data/point";
import { WSRemoveAssembliesEvent } from "./ws/socket/ws-remove-assemblies-event";

const CONTROLLER_WORKSHEET_PASTE = "/events/composer/worksheet/paste-assemblies";
const CONTROLLER_VIEWSHEET_PASTE = "/events/composer/viewsheet/objects/paste";
const CONTROLLER_VIEWSHEET_COPY = "/events/composer/viewsheet/objects/copy";
const CONTROLLER_VIEWSHEET_CUT = "/events/composer/viewsheet/objects/cut";
const REMOVE_ASSEMBLIES_URI = "/events/composer/worksheet/remove-assemblies";

@Injectable()
export class ClipboardService {
   isClipboardEmpty: boolean = true;
   sourceSheet: Sheet;
   objects: string[] = [];
   isCut: boolean;

   get clipboardEmpty(): boolean {
      return this.isClipboardEmpty;
   }

   addToClipboard(sheet: Sheet, isCut: boolean): void {
      let objectList: string[] = [];
      this.isCut = isCut;

      for(let object of sheet.currentFocusedAssemblies) {
         objectList.push(object.absoluteName ? object.absoluteName : object.name);
      }

      this.objects = objectList;

      if(objectList.length < 1) {
         return;
      }

      this.isClipboardEmpty = false;
      this.sourceSheet = sheet;

      if(this.sourceSheet.type == "viewsheet") {
         let vsEvent: CopyVSObjectEvent = new CopyVSObjectEvent(objectList, isCut);

         if(isCut) {
            sheet.socketConnection.sendEvent(CONTROLLER_VIEWSHEET_CUT, vsEvent);
         }
         else {
            sheet.socketConnection.sendEvent(CONTROLLER_VIEWSHEET_COPY, vsEvent);
         }
      }
      else { // Worksheet event
         // (nop)
      }

      if(isCut) {
         sheet.clearFocusedAssemblies();
      }
   }

   pasteWithCutFinish(sheet: Sheet, cutAssemblies: string[]) {
      if(sheet?.type != "worksheet") {
         return;
      }

      let event = new WSRemoveAssembliesEvent();
      event.setAssemblyNames(cutAssemblies);
      sheet.socketConnection.sendEvent(REMOVE_ASSEMBLIES_URI, event);
   }

   pasteObjects(sheet: Sheet, pos?: Point): void {
      if(sheet.type == "viewsheet") {
         if(!pos) {
            pos = {x: 0, y: 0};
         }

         sheet.socketConnection.sendEvent(CONTROLLER_VIEWSHEET_PASTE + "/" +
                                          Math.round(pos.x) + "/" + Math.round(pos.y),
                                          window.event);
      }
      else if(sheet.type === "worksheet" && this.sourceSheet &&
              this.sourceSheet.type === "worksheet" && this.objects.length > 0)
      {
         // Worksheet event
         let event = new WSPasteAssembliesEvent();
         event.setAssemblies(this.objects);
         event.setSourceRuntimeId(this.sourceSheet.runtimeId);
         event.setCut(this.isCut);

         if(pos) {
            event.setTop(pos.y);
            event.setLeft(pos.x);
         }

         sheet.socketConnection.sendEvent(CONTROLLER_WORKSHEET_PASTE, event);

         if(this.isCut) {
            this.objects = [];
            this.isClipboardEmpty = true;
         }
      }
   }

   checkRemovedAssembly(assemblyName: string) {
      this.objects = this.objects.filter(el => el !== assemblyName);
      this.isClipboardEmpty = !this.objects.length;
   }

   checkRenamedAssembly(oldName: string, newName: string) {
      if(oldName !== newName) {
         const index = this.objects.indexOf(oldName);

         if(index !== -1) {
            this.objects[index] = newName;
         }
      }
   }

   sheetClosed(runtimeId: string) {
      if(this.sourceSheet && this.sourceSheet.runtimeId === runtimeId) {
         this.isClipboardEmpty = true;
         this.sourceSheet = null;
         this.objects = [];
         this.isCut = false;
      }
   }
}
