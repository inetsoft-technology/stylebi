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
import { Observable, Subject } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AlignmentInfo } from "../../../common/data/format-info-model";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { GuideBounds } from "../../../vsobjects/model/layout/guide-bounds";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { Sheet } from "../sheet";
import { VSLayoutModel } from "./vs-layout-model";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";

export class Viewsheet extends Sheet {
   baseEntry: AssetEntry;
   vsObjects: VSObjectModel[] = [];
   variableNames: string[] = [];
   preview: boolean = false;
   linkview: boolean = false;
   currentFormat: VSObjectFormatInfoModel;
   origFormat: VSObjectFormatInfoModel;
   formatPainterMode: boolean;
   painterFormat: VSObjectFormatInfoModel;
   layouts: string[];
   currentLayout: VSLayoutModel;
   currentLayoutGuides: GuideBounds = GuideBounds.GUIDES_NONE;
   scale: number;
   parentSheet: Sheet;
   bindingEditMode: boolean;
   statusText: string;
   linkUri: string;
   bindingTreeInitialLoad: boolean;
   layoutPoint: number;
   layoutPoints: number;
   snapGrid: number;
   metadata: boolean;
   newGroup: boolean;

   private removeAssemblySubject: Subject<string> = new Subject<string>();
   private layoutChangeSubject: Subject<any> = new Subject<any>();
   private messageCommandsSubject = new Subject<MessageCommand>();

   constructor(sheet: Viewsheet = null) {
      super(sheet);
      this.type = "viewsheet";
      this.currentFormat = <VSObjectFormatInfoModel> {
         type: "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel",
         font: {
            fontFamily: "Roboto",
            fontStyle: "normal",
            fontUnderline: "normal",
            fontStrikethrough: "normal",
            fontSize: "12",
            fontWeight: "normal"
         },
         borderTopWidth: "0",
         borderBottomWidth: "0",
         borderRightWidth: "0",
         borderLeftWidth: "0",
         color: "#000000",
         backgroundColor: "#ffffff",
         align: {
            halign: AlignmentInfo.LEFT,
            valign: ""
         },
         format: ""
      };
      this.bindingTreeInitialLoad = true;
      this.layoutPoint = -1;
      this.layoutPoints = 0;

      if(sheet) {
         this.baseEntry = sheet.baseEntry;
         this.vsObjects = sheet.vsObjects;
         this.variableNames = sheet.variableNames;
         this.preview = sheet.preview;
         this.currentFormat = sheet.currentFormat;
         this.scale = 1;
      }
   }

   public get layoutChange(): Observable<any> {
      return this.layoutChangeSubject.asObservable();
   }

   public notifyLayoutChange(isGuideChanged: boolean): void {
      this.layoutChangeSubject.next(isGuideChanged);
   }

   public getAssembly(name: string): VSObjectModel {
      return this.vsObjects.find(v => v.absoluteName == name);
   }

   public removeAssembly(objectName: string, fireEvent: boolean) {
      let vsObjects = this.vsObjects;

      if(!vsObjects) {
         return;
      }

      let changed = false;

      for(let i in vsObjects) {
         if(vsObjects[i].absoluteName === objectName) {
            vsObjects.splice(parseInt(i, 10), 1);
            changed = true;
            break;
         }
      }

      if(changed && fireEvent) {
         this.removeAssemblySubject.next(objectName);
      }
   }


   public get objectRemoved(): Observable<string> {
      return this.removeAssemblySubject.asObservable();
   }

   public isParentAssemblyFocused(assembly: any): boolean {
      return !!assembly && !!assembly.container &&
         (this.isAssemblyFocused(assembly.container) ||
          this.isParentAssemblyFocused(this.getAssembly(assembly.container)));
   }

   public isChildAssemblyFocused(assembly: any): boolean {
      return !!assembly && !!assembly.childrenNames &&
         !!assembly.childrenNames.find((c: any) => this.isAssemblyFocused(c));
   }

   public isModified(): boolean {
      if(this.currentLayout) {
         return this.layoutPoint >= 0 || super.isModified();
      }

      return super.isModified();
   }

   public onSave(): void {
      this.layoutPoint = -1;
      this.layoutPoints = 0;
   }

   public getMessageCommandObservable(): Observable<MessageCommand> {
      return this.messageCommandsSubject.asObservable();
   }

   public sendMessageCommand(command: MessageCommand): void {
      this.messageCommandsSubject.next(command);
   }
}
