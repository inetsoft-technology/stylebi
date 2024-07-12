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
import { Injectable } from "@angular/core";
import { Observable ,  Subject } from "rxjs";

export interface SheetMessage {
   action: "show" | "hide" | "close";
   sheetId: string;
}

export interface ObjectMessage {
   action: "property" | "add" | "delete" | "rename";
   objectId: string;
   sheetId: string;
   title: string;
}

@Injectable({
    providedIn: "root"
})
export class UIContextService {
   private lastTabs: Map<string, string> = new Map();
   private sheetChange: Subject<SheetMessage> = new Subject<SheetMessage>();
   private objectChange: Subject<ObjectMessage> = new Subject<ObjectMessage>();
   private currentSheetId: string = null;

   isVS(): boolean {
      return true;
   }

   getDefaultTab(key: string, defaultTab: string): string {
      return this.lastTabs[key] || defaultTab;
   }

   setDefaultTab(key: string, defaultTab: string): void {
      this.lastTabs[key] = defaultTab;
   }

   getSheetChange(): Observable<SheetMessage> {
      return this.sheetChange.asObservable();
   }

   sheetShow(id: string) {
      this.sheetChange.next({action: "show", sheetId: id});
      this.currentSheetId = id;
   }

   sheetHide(id: string) {
      this.sheetChange.next({action: "hide", sheetId: id});

      if(this.currentSheetId == id) {
         this.currentSheetId = null;
      }
   }

   sheetClose(id: string) {
      this.sheetChange.next({action: "close", sheetId: id});

      if(this.currentSheetId == id) {
         this.currentSheetId = null;
      }
   }

   getCurrentSheetId(): string {
      return this.currentSheetId;
   }

   getObjectChange(): Observable<ObjectMessage> {
      return this.objectChange.asObservable();
   }

   objectPropertyChanged(objectId: string, title: string) {
      this.objectChange.next({action: "property", title: title,
                              objectId: objectId, sheetId: this.currentSheetId });
   }

   objectAdded(dragName: string) {
      this.objectChange.next({action: "add", title: null,
                              objectId: dragName, sheetId: this.currentSheetId });
   }

   objectDeleted(dragName: string) {
      this.objectChange.next({action: "delete", title: null,
                              objectId: dragName, sheetId: this.currentSheetId });
   }

   objectRenamed(objectId: string) {
      this.objectChange.next({action: "rename", title: null,
                              objectId: objectId, sheetId: this.currentSheetId });
   }
}
