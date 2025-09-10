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

import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { convertToKey } from "../../em/src/app/settings/security/users/identity-id";
import { BindingModel } from "../../portal/src/app/binding/data/binding-model";
import { ChartBindingModel } from "../../portal/src/app/binding/data/chart/chart-binding-model";
import { ChartRef } from "../../portal/src/app/common/data/chart-ref";
import { ComponentTool } from "../../portal/src/app/common/util/component-tool";
import { CurrentUser } from "../../portal/src/app/portal/current-user";
import { VSChartModel } from "../../portal/src/app/vsobjects/model/vs-chart-model";
import { VSObjectModel } from "../../portal/src/app/vsobjects/model/vs-object-model";
import { AiAssistantDialogComponent } from "./ai-assistant-dialog.component";

const PORTAL_CURRENT_USER_URI: string = "../api/portal/get-current-user";
const EM_CURRENT_USER_URI: string = "../api/em/security/get-current-user";

export enum ContextType {
   VIEWSHEET = "viewsheet",
   WORKSHEET = "worksheet",
   FREEHAND = "freehand",
   CHART = "chart",
   PORTAL_DATA = "portal",
   EM = "em",
   VIEWSHEET_SCRIPT = "viewsheetScript",
   WORKSHEET_SCRIPT = "worksheetScript",
   CHART_SCRIPT = "chartScript",
}

@Injectable({
   providedIn: "root"
})
export class AiAssistantService {
   userId: string = "";
   contextType: string = "chart";
   bindingContext: string = "";
   dataContext: string = "";
   scriptContext: string = "";

   constructor(private http: HttpClient,
               private modalService: NgbModal)
   {
   }

   loadCurrentUser(em: boolean = false): void {
      const uri = em ? EM_CURRENT_USER_URI : PORTAL_CURRENT_USER_URI;
      this.http.get(uri).subscribe((model: CurrentUser) => {
         this.userId = convertToKey(model.name);
      });
   }

   openAiAssistantDialog(): void {
      ComponentTool.showDialog(this.modalService, AiAssistantDialogComponent, () => {},
         {
            backdrop: true,
            windowClass: "ai-assistant-container"
         }
      );
   }

   getFullContext(): string {
      return JSON.stringify({
         contextType: this.contextType,
         bindingContext: this.bindingContext,
         dataContext: this.dataContext,
         scriptContext: this.scriptContext
      });
   }

   setContextType(objectType: string): void {
      if(objectType === "VSChart") {
         this.contextType = ContextType.CHART;
      }
   }

   setBindingContext(objectModel: BindingModel): void {
      let bindingContext: string = "";

      if(objectModel.type === "chart") {
         const model: ChartBindingModel = objectModel as ChartBindingModel;

         if(model.xfields && model.xfields.length > 0) {
            bindingContext += "X fields: " + this.getFieldsString(model, "xfields") + "\n";
         }

         if(model.yfields && model.yfields.length > 0) {
            bindingContext += "Y fields: " + this.getFieldsString(model, "yfields") + "\n";
         }

         if(model.groupFields && model.groupFields.length > 0) {
            bindingContext += "Group fields: " + this.getFieldsString(model, "groupFields") + "\n";
         }

         if(model.geoFields && model.geoFields.length > 0) {
            bindingContext += "Geo fields: " + this.getFieldsString(model, "geoFields") + "\n";
         }

         if(model.colorField) {
            bindingContext += "Color field: " + this.getFieldsString(model, "colorField") + "\n";
         }

         if(model.shapeField) {
            bindingContext += "Shape field: " + this.getFieldsString(model, "shapeField") + "\n";
         }

         if(model.sizeField) {
            bindingContext += "Size field: " + this.getFieldsString(model, "sizeField") + "\n";
         }

         if(model.textField) {
            bindingContext += "Text field: " + this.getFieldsString(model, "textField") + "\n";
         }
      }

      this.bindingContext = bindingContext;
   }

   getFieldsString(objectModel: ChartBindingModel, fieldType: string): string {
      return this.getFields(objectModel, fieldType)
         .map(field => field.fullName + "(" + field.dataType + ")").join(",");
   }

   getFields(objectModel: ChartBindingModel, fieldType: string): ChartRef[] {
      switch(fieldType) {
         case "xfields":
            return objectModel.xfields ? objectModel.xfields : [];
         case "yfields":
            return objectModel.yfields ? objectModel.yfields : [];
         case "groupFields":
            return objectModel.groupFields ? objectModel.groupFields : [];
         case "geoFields":
            return objectModel.geoFields ? objectModel.geoFields : [];
         case "colorField":
            return objectModel.colorField ? [objectModel.colorField.dataInfo] : [];
         case "shapeField":
            return objectModel.shapeField ? [objectModel.shapeField.dataInfo] : [];
         case "sizeField":
            return objectModel.sizeField ? [objectModel.sizeField.dataInfo] : [];
         case "textField":
            return objectModel.textField ? [objectModel.textField.dataInfo] : [];
         default:
            return [];
      }
   }

   setDataContext(bindingModel: BindingModel): void {
      let dataContext: string = "";

      if(bindingModel.availableFields) {
         bindingModel.availableFields.forEach(field => {
            if(field.classType === "CalculateRef" && field.expression &&
               (<any> field).dataRefModel?.exp)
            {
               dataContext +=
                  field.name + "(" + (<any> field).dataRefModel.exp + "): " + field.dataType + "\n";
            }
            else {
               dataContext += field.name + ": " + field.dataType + "\n";
            }
         });
      }

      this.dataContext = dataContext;
   }

   setDateComparisonToBindingContext(objectModel: VSObjectModel): void {
      if(objectModel.objectType === "VSChart") {
         const model: VSChartModel = objectModel as VSChartModel;

         if(model.dateComparisonDefined && !this.bindingContext.includes("Date comparison")) {
            this.bindingContext += "\nDate comparison: \n" + model.dateComparisonDescription + "\n";
         }
      }
   }
}