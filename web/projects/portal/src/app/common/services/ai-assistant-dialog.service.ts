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
import { Injectable, Injector } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AiAssistantService, ContextType } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogComponent } from "../../../../../shared/ai-assistant/ai-assistant-dialog.component";
import { getViewsheetScriptContext } from "../../binding/services/assistant/viewsheet-context-helper";
import { getWorksheetContext, getWorksheetScriptContext } from "../../binding/services/assistant/worksheet-context-helper";
import { Viewsheet } from "../../composer/data/vs/viewsheet";
import { Worksheet } from "../../composer/data/ws/worksheet";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { ComponentTool } from "../util/component-tool";

@Injectable({
   providedIn: "root"
})
export class AiAssistantDialogService {

   constructor(public aiAssistantService: AiAssistantService,
               private modalService: NgbModal)
   {
   }

   get aiAssistantVisible(): boolean {
      return this.aiAssistantService.aiAssistantVisible;
   }

   openAiAssistantDialog(): void {
      const injector = Injector.create({
         providers: [
            { provide: AiAssistantService, useValue: this.aiAssistantService }
         ],
      });

      ComponentTool.showDialog(this.modalService, AiAssistantDialogComponent, () => {}, {
         backdrop: true,
         windowClass: "ai-assistant-container",
         injector: injector
      });
   }

   setViewsheetScriptContext(vs: Viewsheet): void {
      if(!vs || !vs.vsObjects || vs.vsObjects.length === 0) {
         return;
      }

      this.aiAssistantService.resetContextMap();
      this.aiAssistantService.setContextTypeFieldValue(ContextType.VIEWSHEET);
      this.aiAssistantService.setContextField("scriptContext", getViewsheetScriptContext(vs));
   }

   setWorksheetContext(ws: Worksheet): void {
      this.aiAssistantService.resetContextMap();
      this.aiAssistantService.setContextTypeFieldValue(ContextType.WORKSHEET);
      let context = getWorksheetContext(ws);
      this.aiAssistantService.setContextField("tableSchemas", context);
   }

   setWorksheetScriptContext(fields: TreeNodeModel[]): void {
      if(!fields || fields.length === 0) {
         this.aiAssistantService.setContextTypeFieldValue(ContextType.WORKSHEET);
         this.aiAssistantService.removeContextField("scriptContext");
         return;
      }

      this.aiAssistantService.setContextTypeFieldValue(ContextType.WORKSHEET_SCRIPT);
      this.aiAssistantService.setContextField("scriptContext", getWorksheetScriptContext(fields));
   }
}
