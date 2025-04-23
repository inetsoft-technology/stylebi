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
import { BehaviorSubject ,  Observable } from "rxjs";
import { AlignmentInfo } from "../../../common/data/format-info-model";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GuideBounds } from "../../../vsobjects/model/layout/guide-bounds";
import { PrintLayoutSection } from "../../../vsobjects/model/layout/print-layout-section";
import { FontService } from "../../../widget/services/font.service";
import { VSLayoutObjectModel } from "./vs-layout-object-model";

export enum PrintLayoutMeasures {
   INCH_POINT = 72,
   INCH_MM = 25.4
}

export class VSLayoutModel {
   name: string;
   objects: VSLayoutObjectModel[];
   selectedObjects: string[];
   printLayout: boolean;
   guideType: GuideBounds;

   // Print Layout Info
   currentPrintSection: PrintLayoutSection;
   unit: string;
   width: number;
   height: number;
   marginTop: number;
   marginLeft: number;
   marginRight: number;
   marginBottom: number;
   headerFromEdge: number;
   footerFromEdge: number;
   headerObjects: VSLayoutObjectModel[];
   footerObjects: VSLayoutObjectModel[];
   horizontal: boolean;
   runtimeID: string;
   currentFormat: VSObjectFormatInfoModel;
   origFormat: VSObjectFormatInfoModel;

   public socketConnection: ViewsheetClientService;
   private focusedObjectsSubject: BehaviorSubject<VSLayoutObjectModel[]> =
      new BehaviorSubject<VSLayoutObjectModel[]>([]);
   public focusedObjects: VSLayoutObjectModel[] = [];

   constructor(fontService: FontService = null, layout: VSLayoutModel = null) {
      this.focusedObjectsChanged();
      this.guideType = GuideBounds.GUIDES_NONE;
      this.currentPrintSection = PrintLayoutSection.CONTENT;
      this.currentFormat = <VSObjectFormatInfoModel> {
         type: "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel",
         font: {
            fontFamily: fontService ? fontService.defaultFont : "Roboto",
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

      if(layout) {
         this.name = layout.name;
         this.objects = layout.objects;
         this.printLayout = layout.printLayout;
         this.unit = layout.unit;
         this.width = layout.width;
         this.height = layout.height;
         this.marginTop = layout.marginTop;
         this.marginLeft = layout.marginLeft;
         this.marginRight = layout.marginRight;
         this.marginBottom = layout.marginBottom;
         this.headerFromEdge = layout.headerFromEdge;
         this.footerFromEdge = layout.footerFromEdge;
         this.headerObjects = layout.headerObjects;
         this.footerObjects = layout.footerObjects;
         this.horizontal = layout.horizontal;
         this.runtimeID = layout.runtimeID;
         this.guideType = layout.guideType || GuideBounds.GUIDES_NONE;
      }
   }

   public get focused(): Observable<VSLayoutObjectModel[]> {
      return this.focusedObjectsSubject.asObservable();
   }

   public selectObject(object: VSLayoutObjectModel): void {
      if(!this.isObjectFocused(object)) {
         this.focusedObjects.push(object);
      }

      this.focusedObjectsChanged();
   }

   public selectAllObjects(layoutSection: string): void {
      switch(layoutSection) {
      case "HEADER":
         this.focusedObjects = this.headerObjects.concat([]);
         break;
      case "CONTENT":
         this.focusedObjects = this.objects.filter(obj => obj.objectModel.objectType != "VSPageBreak").concat([]);
         break;
      case "FOOTER":
         this.focusedObjects = this.footerObjects.concat([]);
         break;
      }

      this.focusedObjectsChanged();
   }

   public deselectAssembly(object: any): void {
      const index = this.getFocusedObjectIndex(object);

      if(index >= 0) {
         this.focusedObjects.splice(index, 1);
         this.focusedObjectsChanged();
      }
   }

   private getFocusedObjectIndex(object: VSLayoutObjectModel): number {
      return this.focusedObjects.findIndex((a) => a.name === object.name);
   }

   public clearFocusedObjects(): void {
      this.focusedObjects = [];
      this.focusedObjectsChanged();
   }

   public updateFocusedObjects(object: VSLayoutObjectModel): void {
      const index: number = this.getFocusedObjectIndex(object);

      if(index >= 0) {
         this.focusedObjects[index] = object;
         this.focusedObjectsChanged();
      }
   }

   public focusedObjectsChanged(): void {
      this.focusedObjectsSubject.next(this.focusedObjects);
   }

   public isObjectFocused(object: VSLayoutObjectModel): boolean {
      return !!this.focusedObjects.find((a) => a.name === object.name);
   }

   public getLayoutSection(): string {
      if(this.printLayout) {
         switch(this.currentPrintSection) {
         case PrintLayoutSection.HEADER: return "HEADER";
         case PrintLayoutSection.CONTENT: return "CONTENT";
         case PrintLayoutSection.FOOTER: return "FOOTER";
         }
      }

      return "CONTENT";
   }
}
