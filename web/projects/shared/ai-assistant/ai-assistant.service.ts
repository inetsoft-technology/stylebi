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
import {
   getCalcTableBindings,
   getCalcTableRetrievalScriptContext, getCalcTableScriptContext
} from "../../portal/src/app/binding/services/assistant/calc-table-context-helper";
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
   assistantBaseUrl: string = "";
   userId: string = "";
   calcTableCellBindings: { [key: string]: CellBindingInfo } = {};
   calcTableAggregates: string[] = [];
   private contextMap: Record<string, string> = {};

   constructor(private http: HttpClient,
               private modalService: NgbModal)
   {
      this.http.get("../api/assistant/get-assistant-base-url").subscribe((url: string) => {
         this.assistantBaseUrl = url || "";
      });
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
         this.setContextTypeFieldValue(ContextType.CHART);
      }
      else if(objectType === "VSCrosstab") {
         this.setContextTypeFieldValue(ContextType.CROSSTAB);
      }
      else if(objectType === "VSCalcTable") {
         this.setContextTypeFieldValue(ContextType.FREEHAND);
      }
      else if(objectType === "VSTable") {
         this.setContextTypeFieldValue(ContextType.TABLE);
      }
   }

   setContextTypeFieldValue(contextType: string): void {
      this.setContextField("contextType", contextType)
   }

   setCalcTableBindingContext(layout: CalcTableLayout): void {
      if(!layout?.tableRows?.length || !this.calcTableCellBindings) {
         return;
      }

      const bindingFields =
         getCalcTableBindings(layout, this.calcTableCellBindings, this.calcTableAggregates);
      this.setContextTypeFieldValue(ContextType.FREEHAND);
      this.setContextField("bindingContext", JSON.stringify(bindingFields));
   }

   setCalcTableRetrievalScriptContext(layout: CalcTableLayout): void {
      if(!layout?.tableRows?.length || !this.calcTableCellBindings) {
         this.removeContextField("groupCells");
         this.removeContextField("aggregateCells");
         return;
      }

      const retrievalScriptContext =
         getCalcTableRetrievalScriptContext(layout, this.calcTableCellBindings);

      if(retrievalScriptContext) {
         this.setContextField("groupCells", retrievalScriptContext.groupCells);
         this.setContextField("aggregateCells", retrievalScriptContext.aggregateCells);
      }
      else {
         this.removeContextField("groupCells");
         this.removeContextField("aggregateCells");
      }
   }

   setCalcTableScriptContext(layout: CalcTableLayout): void {
      if(!layout?.tableRows?.length) {
         return;
      }

      const scriptContext = getCalcTableScriptContext(
         layout, this.calcTableCellBindings, this.calcTableAggregates);

      if(scriptContext) {
         this.setContextField("scriptContext", scriptContext);
      }
      else {
         this.removeContextField("scriptContext");
      }
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

      let dataContext = getAvailableFields(bindingModel.tables, bindingModel.availableFields);

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

      this.setContextTypeFieldValue(contextType);
      this.setContextField("scriptContext", objectModel.script);
   }

   setWorksheetContext(ws: Worksheet): void {
      this.resetContextMap();
      this.setContextTypeFieldValue(ContextType.WORKSHEET);
      let context = getWorksheetContext(ws);
      this.setContextField("tableSchemas", context);
      this.setContextField("dataContext", context);
   }

   setWorksheetScriptContext(fields: TreeNodeModel[]): void {
      if(!fields || fields.length === 0) {
         this.setContextTypeFieldValue(ContextType.WORKSHEET);
         this.removeContextField("scriptContext");
         return;
      }

      this.setContextTypeFieldValue(ContextType.WORKSHEET_SCRIPT);
      this.setContextField("scriptContext", getWorksheetScriptContext(fields));
   }

   setViewsheetScriptContext(vs: Viewsheet): void {
      if(!vs || !vs.vsObjects || vs.vsObjects.length === 0) {
         return;
      }

      this.resetContextMap();
      this.setContextTypeFieldValue(ContextType.VIEWSHEET);
      this.setContextField("scriptContext", getViewsheetScriptContext(vs));
   }
}