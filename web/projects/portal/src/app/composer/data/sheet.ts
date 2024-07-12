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
import {from as observableFrom,  BehaviorSubject ,  Observable } from "rxjs";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { VSObjectTreeNode } from "./vs-object-tree-node";

export type SheetType = "worksheet" | "viewsheet";

export abstract class Sheet {
   public id: string;
   public localId: number;   // discriminator primarily for use by trackBy
   public embeddedId: string;
   public label: string;
   public type: SheetType;
   public runtimeId: string;
   public newSheet: boolean;
   public meta: boolean;
   public autoSaveFile: string;
   public socketConnection: ViewsheetClientService;
   public objectTree: VSObjectTreeNode;
   public points: number;
   public current: number;
   public currentTS: number = 0;
   public autoSaveTS: number;
   public savePoint: number;
   public annotationChanged: boolean = false;
   public closeOnServer: boolean = true;
   public closedOnServer: boolean = false;
   public messageLevels: string[];
   private _loading: boolean;
   public gettingStarted: boolean = false;
   public readonly loadingSubject = new BehaviorSubject<boolean>(true);
   private focusedSubject = new BehaviorSubject<boolean>(true);
   private _focused: boolean;
   private focusedAssembliesSubject = new BehaviorSubject<any[]>([]);
   private _focusedAssemblies: any[];

   public get loading(): boolean {
      return this._loading;
   }

   public set loading(flag: boolean) {
      this._loading = flag;
      this.loadingSubject.next(flag);
   }

   public get isFocused(): boolean {
      return this._focused;
   }

   public set isFocused(value: boolean) {
      this._focused = value;
      this.focusedSubject.next(value);
   }

   public get focused(): Observable<boolean> {
      return observableFrom(this.focusedSubject);
   }

   public get currentFocusedAssemblies(): any[] {
      return this.getCurrentFocusedAssemblies();
   }

   public set currentFocusedAssemblies(value: any[]) {
      this._focusedAssemblies = value ? value.filter(a => !!a) : value;
      this.focusedAssembliesChanged();
   }

   public get focusedAssemblies(): Observable<any> {
      return observableFrom(this.focusedAssembliesSubject);
   }

   protected constructor(sheet: Sheet = null) {
      this._focused = sheet ? sheet._focused : false;
      this.focusedSubject.next(this._focused);
      this._focusedAssemblies = sheet ? sheet._focusedAssemblies : [];
      this.focusedAssembliesChanged();

      if(sheet) {
         this.id = sheet.id;
         this.localId = sheet.localId;
         this.label = sheet.label;
         this.type = sheet.type;
         this.runtimeId = sheet.runtimeId;
         this.newSheet = sheet.newSheet;
         this.socketConnection = sheet.socketConnection;
         this.objectTree = sheet.objectTree;
         this.points = sheet.points;
         this.current = sheet.current;
         this.savePoint = sheet.savePoint;
         this.loading = sheet.loading;
         this.annotationChanged = sheet.annotationChanged;
      }
   }

   public selectAssembly(assembly: any): void {
      if(assembly && !this._focusedAssemblies.some(a => (
         this.type == "worksheet" ? a === assembly : a.absoluteName === assembly.absoluteName))) {
         assembly.interactionDisabled = false;
         this._focusedAssemblies.push(assembly);
         this.focusedAssembliesChanged();
      }
   }

   public selectOnlyAssembly(assembly: any): void {
      if(assembly) {
         this._focusedAssemblies = [assembly];
         this.focusedAssembliesChanged();
      }
   }

   public updateSelectedAssembly(assembly: any): boolean {
      const index = this.getFocusedAssemblyIndex(assembly);

      if(index >= 0) {
         this._focusedAssemblies[index] = assembly;
         return true;
      }

      return false;
   }

   public deselectAssembly(assembly: any): void {
      const index = this.getFocusedAssemblyIndex(assembly);

      if(index >= 0) {
         this._focusedAssemblies.splice(index, 1);
         this.focusedAssembliesChanged();
      }
   }

   public replaceFocusedAssembly(current: any, replacement: any): void {
      const index = this.getFocusedAssemblyIndex(current);

      if(index >= 0) {
         this._focusedAssemblies[index] = replacement;
         this.focusedAssembliesChanged();
      }
   }

   private getFocusedAssemblyIndex(assembly: any): number {
      return this._focusedAssemblies.findIndex((a) => !!a.absoluteName ?
         a.absoluteName === assembly.absoluteName : a.name === assembly.name);
   }

   public clearFocusedAssemblies(): void {
      this.currentFocusedAssemblies = [];
   }

   public focusedAssembliesChanged(): void {
      this.focusedAssembliesSubject.next(this._focusedAssemblies.concat([]));
   }

   public isAssemblyFocused(assembly: any): boolean {
      let result: boolean;

      if(typeof assembly === "string") {
         result = !!this._focusedAssemblies.find(a => a != null && a.absoluteName == assembly);
      }
      else {
         result = !!this._focusedAssemblies.find(a => a === assembly);
      }

      return result;
   }

   public isModified(): boolean {
      return this.annotationChanged || this.current != undefined &&
         this.savePoint != undefined && this.current !== this.savePoint;
   }

   /** Normal method getter allows for method overriding. */
   protected getCurrentFocusedAssemblies(): any[] {
      return this._focusedAssemblies ? this._focusedAssemblies : [];
   }
}
