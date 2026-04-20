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
import { BehaviorSubject, Observable, of, Subject } from "rxjs";
import { catchError, map, take, timeout } from "rxjs/operators";
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
import { CalcTableLayout } from "../../portal/src/app/common/data/tablelayout/calc-table-layout";
import { VSObjectModel } from "../../portal/src/app/vsobjects/model/vs-object-model";
import { CurrentUserService } from "../util/current-user.service";

export enum ContextType {
   VIEWSHEET = "dashboard",
   WORKSHEET = "worksheet",
   FREEHAND = "freehand",
   CHART = "chart",
   CROSSTAB = "crosstab",
   TABLE = "table",
   PORTAL_DATA = "portal",
   EM = "em",
   WORKSHEET_SCRIPT = "worksheetScript",
   DASHBOARD_PORTAL = "dashboardPortal",
   SCHEDULE_TASK = "scheduleTask"
}

@Injectable({
   providedIn: "root"
})
export class AiAssistantService {
   chatAppServerUrl: string = "";
   styleBIUrl: string = "";
   chatAppTitle: string | null = null;
   chatAppVendorName: string | null = null;
   chatAppLogoUrl: string | null = null;
   private _panelOpen$ = new BehaviorSubject<boolean>(false);
   readonly panelOpen$ = this._panelOpen$.asObservable();
   private _contextChange$ = new Subject<void>();
   readonly contextChange$ = this._contextChange$.asObservable();
   get panelOpen(): boolean { return this._panelOpen$.value; }
   set panelOpen(v: boolean) { this._panelOpen$.next(v); }
   aiAssistantVisible: boolean = false;
   userId: string = "";
   email: string = "";
   private webComponentScriptPromise: Promise<void> | null = null;
   calcTableCellBindings: { [key: string]: CellBindingInfo } = {};
   calcTableAggregates: string[] = [];
   private contextMap: Record<string, string> = {};
   private _lastBindingObject: string = "";
   private _newChatFromBinding: boolean = false;

   // Caches the server-URL fetch so loadWebComponentScript() can await it even when
   // the Angular app has not yet processed the HTTP response before the component
   // tree is ready.
   private readonly _serverUrlLoaded: Promise<void>;

   constructor(private http: HttpClient, private currentUserService: CurrentUserService) {
      // TODO: replace .toPromise() with firstValueFrom() when upgrading to RxJS 7+
      this._serverUrlLoaded = this.http.get<string>("../api/assistant/get-chat-app-server-url").pipe(
         catchError(() => of("")),
         take(1)
      ).toPromise().then((url: string) => {
         this.chatAppServerUrl = url || "";
      });

      this.http.get("../api/assistant/get-stylebi-url").pipe(
         catchError(() => of(""))
      ).subscribe((url: string) => {
         this.styleBIUrl = url || "";
      });

   }

   refreshBranding(): Promise<void> {
      // TODO: replace .toPromise() with firstValueFrom() when upgrading to RxJS 7+
      return this.http.get<{title: string, vendorName: string, logoUrl: string}>(
         "../api/assistant/get-branding").pipe(
         catchError(() => of(null)),
         take(1)
      ).toPromise().then(branding => {
         if(branding) {
            this.chatAppTitle = branding.title || null;
            this.chatAppVendorName = branding.vendorName || null;
            this.chatAppLogoUrl = branding.logoUrl || null;
         }
      });
   }

   /**
    * Reads the key StyleBI CSS custom properties from the document root and returns
    * them as a JSON string suitable for passing to the ai-assistant web component's
    * theme attribute so the component can apply them inside its shadow DOM.
    */
   getThemeConfig(): string | null {
      const style = getComputedStyle(document.documentElement);
      const vars = [
         "--inet-primary-color",
         "--inet-text-color",
         "--inet-navbar-bg-color",
         "--inet-navbar-text-color",
         "--inet-main-panel-bg-color",
      ];
      const theme: Record<string, string> = {};

      for(const v of vars) {
         const val = style.getPropertyValue(v).trim();

         if(val) {
            theme[v] = val;
         }
      }

      return Object.keys(theme).length > 0 ? JSON.stringify(theme) : null;
   }

   /**
    * Dynamically loads the AI assistant web component script. Safe to call multiple times —
    * concurrent calls share the same in-flight promise. Returns a promise that resolves when
    * the script loads or rejects on error. The cached promise is cleared on error to allow
    * a retry on the next panel open.
    */
   loadWebComponentScript(): Promise<void> {
      // If the element was already registered (e.g. by the @inetsoft-technology/ai-assistant
      // npm package imported elsewhere in the app), skip loading the external UMD bundle to
      // avoid a double-registration NotSupportedError.
      if(customElements.get("ai-assistant")) {
         return Promise.resolve();
      }

      if(this.webComponentScriptPromise) {
         return this.webComponentScriptPromise;
      }

      this.webComponentScriptPromise = this._serverUrlLoaded.then(() => {
         const base = this.chatAppServerUrl ? this.chatAppServerUrl.replace(/\/$/, "") : "";

         if(!base) {
            return Promise.reject(new Error("AI assistant URL not configured"));
         }

         return new Promise<void>((resolve, reject) => {
            const script = document.createElement("script");
            script.src = base + "/web-component/ai-assistant.umd.js";
            script.onload = () => resolve();
            script.onerror = () => {
               document.head.removeChild(script); // remove so a retry appends a fresh element
               reject(new Error("Failed to load AI assistant web component"));
            };
            document.head.appendChild(script);
         });
      }).catch(err => {
         this.webComponentScriptPromise = null;
         return Promise.reject(err);
      });

      return this.webComponentScriptPromise;
   }

   checkHealth(): Observable<boolean> {
      return this.http.get<boolean>("../api/assistant/health").pipe(
         timeout(5000),
         map(online => online === true),
         catchError(() => of(false))
      );
   }

   set lastBindingObject(value: string) {
      this._newChatFromBinding = value !== this._lastBindingObject;
      this._lastBindingObject = value;
   }

   get createNewChat(): boolean {
      return this._newChatFromBinding;
   }

   resetNewChat(): void {
      this._newChatFromBinding = false;
   }

   resetContextMap(): void {
      this.contextMap = {};
      this._contextChange$.next();
   }

   loadCurrentUser(em: boolean = false): void {
      const user$ = em
         ? this.currentUserService.getEmCurrentUser()
         : this.currentUserService.getPortalCurrentUser();

      user$.subscribe(model => {
         this.userId = convertToKey(model.name);
         this.email = model.email?.length > 0 ? model.email[0] : "";
      });
   }

   setContextField(key: string, value: string) {
      if(this.contextMap[key] === value) {
         return;
      }

      this.contextMap[key] = value;
      this._contextChange$.next();
   }

   getContextField(key: string): string {
      return this.contextMap[key] || "";
   }

   removeContextField(key: string) {
      delete this.contextMap[key];
      this._contextChange$.next();
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
         let noBinding: boolean = objectModel.source == null || objectModel.source.source == null
            || objectModel.source.source.length == 0;
         this.setContextField("noBinding", noBinding ? "true": "false");
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

      this.setContextField("scriptContext", objectModel.script);
   }

}
