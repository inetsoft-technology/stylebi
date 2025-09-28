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
import { CellBindingInfo } from "../../portal/src/app/binding/data/table/cell-binding-info";
import { CrosstabBindingModel } from "../../portal/src/app/binding/data/table/crosstab-binding-model";
import { getAvailableFields } from "../../portal/src/app/binding/services/assistant/available-fields-helper";
import { getCalcTableBindingContext } from "../../portal/src/app/binding/services/assistant/calc-table-context-helper";
import { getChartBindingContext } from "../../portal/src/app/binding/services/assistant/chart-context-helper";
import { getCrosstabBindingContext } from "../../portal/src/app/binding/services/assistant/crosstab-context-helper";
import { getViewsheetScriptContext } from "../../portal/src/app/binding/services/assistant/viewsheet-context-helper";
import {
   getWorksheetContext,
   getWorksheetScriptContext
} from "../../portal/src/app/binding/services/assistant/worksheet-context-helper";
import { CalcTableLayout } from "../../portal/src/app/common/data/tablelayout/calc-table-layout";
import { ComponentTool } from "../../portal/src/app/common/util/component-tool";
import { Viewsheet } from "../../portal/src/app/composer/data/vs/viewsheet";
import { Worksheet } from "../../portal/src/app/composer/data/ws/worksheet";
import { CurrentUser } from "../../portal/src/app/portal/current-user";
import { VSObjectModel } from "../../portal/src/app/vsobjects/model/vs-object-model";
import { TreeNodeModel } from "../../portal/src/app/widget/tree/tree-node-model";
import { AiAssistantDialogComponent } from "./ai-assistant-dialog.component";

const PORTAL_CURRENT_USER_URI: string = "../api/portal/get-current-user";
const EM_CURRENT_USER_URI: string = "../api/em/security/get-current-user";

export enum ContextType {
   VIEWSHEET = "viewsheet",
   WORKSHEET = "worksheet",
   FREEHAND = "freehand",
   CHART = "chart",
   CROSSTAB = "crosstab",
   TABLE = "table",
   PORTAL_DATA = "portal",
   EM = "em",
   VIEWSHEET_SCRIPT = "viewsheetScript",
   WORKSHEET_SCRIPT = "worksheetScript",
   CHART_SCRIPT = "chartScript",
   CROSSTAB_SCRIPT = "crosstabScript",
}

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

   resetContextMap(): void {
      this.contextMap = {};
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

   removeContextField(key: string) {
      delete this.contextMap[key];
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
      else if(objectType === "VSCalcTable") {
         this.setContextTypeFiledValue(ContextType.FREEHAND);
      }
      else if(objectType === "VSTable") {
         this.setContextTypeFiledValue(ContextType.TABLE);
      }
   }

   setContextTypeFiledValue(contextType: string): void {
      this.setContextField("contextType", contextType)
   }

   setCalcTableBindingContext(layout: CalcTableLayout,
                              cellBindings: { [key: string]: CellBindingInfo }): void
   {
      if(!layout?.tableRows?.length || !cellBindings) {
         return;
      }

      this.setContextTypeFiledValue(ContextType.FREEHAND);
      this.setContextField("bindingContext", getCalcTableBindingContext(layout, cellBindings));
   }

   setBindingContext(objectModel: BindingModel): void {
      if(!objectModel) {
         return;
      }

      let bindingContext = "";

      if(objectModel.type === "chart") {
         bindingContext = getChartBindingContext(objectModel as ChartBindingModel);
      }
      else if(objectModel.type === "crosstab") {
         bindingContext = getCrosstabBindingContext(objectModel as CrosstabBindingModel);
      }

      if(bindingContext) {
         this.setContextField("bindingContext", bindingContext);
      }
   }

   setDataContext(bindingModel: BindingModel): void {
      if(!bindingModel?.availableFields) {
         return;
      }

      let dataContext = getAvailableFields(bindingModel.availableFields);

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
         case "VSCalcTable":
            contextType = ContextType.FREEHAND;
            break;
         case "VSTable":
            contextType = ContextType.TABLE;
            break;
         default:
            contextType = ContextType.VIEWSHEET_SCRIPT;
      }

      this.setContextTypeFiledValue(contextType);
      this.setContextField("scriptContext", objectModel.script);
   }

   setWorksheetContext(ws: Worksheet): void {
      this.resetContextMap();
      this.setContextTypeFiledValue(ContextType.WORKSHEET);
      let context = getWorksheetContext(ws);
      this.setContextField("tableSchemas", context);
      this.setContextField("dataContext", context);
   }

   setWorksheetScriptContext(fields: TreeNodeModel[]): void {
      if(!fields || fields.length === 0) {
         this.setContextTypeFiledValue(ContextType.WORKSHEET);
         this.removeContextField("scriptContext");
         return;
      }

      this.setContextTypeFiledValue(ContextType.WORKSHEET_SCRIPT);
      this.setContextField("scriptContext", getWorksheetScriptContext(fields));
   }

   setViewsheetScriptContext(vs: Viewsheet): void {
      if(!vs || !vs.vsObjects || vs.vsObjects.length === 0) {
         return;
      }

      this.resetContextMap();
      this.setContextTypeFiledValue(ContextType.VIEWSHEET);
      this.setContextField("scriptContext", getViewsheetScriptContext(vs));
   }
}