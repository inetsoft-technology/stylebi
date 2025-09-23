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
import { Injectable, Injector } from "@angular/core";
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
import { VSCrosstabModel } from "../../portal/src/app/vsobjects/model/vs-crosstab-model";

const PORTAL_CURRENT_USER_URI: string = "../api/portal/get-current-user";
const EM_CURRENT_USER_URI: string = "../api/em/security/get-current-user";

export enum ContextType {
   VIEWSHEET = "viewsheet",
   WORKSHEET = "worksheet",
   FREEHAND = "freehand",
   CHART = "chart",
   CROSSTAB = "crosstab",
   PORTAL_DATA = "portal",
   EM = "em",
   VIEWSHEET_SCRIPT = "viewsheetScript",
   WORKSHEET_SCRIPT = "worksheetScript",
   CHART_SCRIPT = "chartScript",
   CROSSTAB_SCRIPT = "crosstabScript",
}

const chartFieldMappings: Record<string, string> = {
   xfields: "X fields",
   yfields: "Y fields",
   groupFields: "Group fields",
   geoFields: "Geo fields",

   colorField: "Color field",
   shapeField: "Shape field",
   sizeField: "Size field",
   textField: "Text field",

   openField: "Open field",
   closeField: "Close field",
   highField: "High field",
   lowField: "Low field",
   pathField: "Path field",
   sourceField: "Source field",
   targetField: "Target field",
   startField: "Start field",
   endField: "End field",
   milestoneField: "Milestone field",

   nodeColorField: "Node color field",
   nodeSizeField: "Node size field"
};

@Injectable({
   providedIn: "root"
})
export class AiAssistantService {
   userId: string = "";
   private contextMap: Record<string, string> = {};

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
      const injector = Injector.create({
         providers: [
            { provide: AiAssistantService, useValue: this }
         ],
      });

      ComponentTool.showDialog(this.modalService, AiAssistantDialogComponent, () => {}, {
         backdrop: true,
         windowClass: "ai-assistant-container",
         injector: injector
      });
   }

   setContextField(key: string, value: string) {
      this.contextMap[key] = value;
   }

   getContextField(key: string): string {
      return this.contextMap[key] || "";
   }

   getFullContext(): string {
      return JSON.stringify(this.contextMap);
   }

   setContextType(objectType: string): void {
      if(objectType === "VSChart") {
         this.setContextTypeFiledValue(ContextType.CHART);
      }
      else if(objectType === "VSCrosstab") {
         this.setContextTypeFiledValue(ContextType.CROSSTAB);
      }
   }

   setContextTypeFiledValue(contextType: string): void {
      this.setContextField("contextType", contextType)
   }

   setBindingContext(objectModel: BindingModel): void {
      if(!objectModel) {
         return;
      }

      // Add a comment line at the top to indicate format for model
      let bindingContext = "# Format: DataSource/TableName : FieldName (Type)\n";

      if(objectModel.type === "chart") {
         const model: ChartBindingModel = objectModel as ChartBindingModel;

         for(const [fieldKey, label] of Object.entries(chartFieldMappings)) {
            const fields = this.getFields(model, fieldKey);

            if(fields.length > 0) {
               bindingContext +=
                  `${label}: ${fields.map(f => f.fullName + "(" + f.dataType + ")").join(",")}\n`;
            }
         }
      }

      this.setContextField("bindingContext", bindingContext);
   }

   getFields(objectModel: ChartBindingModel, fieldKey: string): ChartRef[] {
      const value: any = (objectModel as any)[fieldKey];

      if(!value) {
         return [];
      }

      if(Array.isArray(value)) {
         return value;
      }

      if("dataInfo" in value) {
         return value.dataInfo ? [value.dataInfo] : [];
      }

      return [value];
   }

   setDataContext(bindingModel: BindingModel): void {
      if(!bindingModel?.availableFields) {
         return;
      }

      let dataContext = "# Format: DataSource/TableName : FieldName (expression if any): FieldType\n";

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

      if(dataContext) {
         this.setContextField("dataContext", dataContext);
      }
   }

   setDateComparisonContext(objectModel: VSObjectModel): void {
      if(!objectModel ||
         !(objectModel.objectType === "VSChart" || objectModel.objectType === "VSCrosstab"))
      {
         return;
      }

      const model = objectModel as any;

      if(!model.dateComparisonEnabled || !model.dateComparisonDescription) {
         return;
      }

      const dcDesc: string = model.dateComparisonDescription.replace(/<\/?b>/g, '');
      this.setContextField("dateComparisonContext", dcDesc);
   }

   setScriptContext(objectModel: VSObjectModel) {
      if(!objectModel || !objectModel.scriptEnabled || !objectModel.script) {
         return;
      }

      let contextType = "";

      switch(objectModel.objectType) {
         case "VSChart":
            contextType = ContextType.CHART_SCRIPT;
            break;
         case "VSCrosstab":
            contextType = ContextType.CROSSTAB_SCRIPT;
            break;
         default:
            contextType = ContextType.VIEWSHEET_SCRIPT;
      }

      this.setContextField("contextType", contextType);
      this.setContextField("scriptContext", objectModel.script);
   }
}