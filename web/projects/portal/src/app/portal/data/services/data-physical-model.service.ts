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
import { Injectable, OnDestroy } from "@angular/core";
import { ValidatorFn, Validators } from "@angular/forms";
import { Observable, ReplaySubject, Subject } from "rxjs";
import { ValidatorMessageInfo } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { NotificationData } from "../../../widget/repository-tree/repository-tree.service";
import { PhysicalModelDefinition } from "../model/datasources/database/physical-model/physical-model-definition";
import { Tool } from "../../../../../../shared/util/tool";
import { HttpClient, HttpParams } from "@angular/common/http";
import { GetModelEvent } from "../model/datasources/database/events/get-model-event";
import { tap } from "rxjs/operators";
import { AddPhysicalModelEvent } from "../model/datasources/database/events/add-physical-model-event";
import { GraphModel } from "../model/datasources/database/physical-model/graph/graph-model";
import { FormValidators } from "../../../../../../shared/util/form-validators";

const PHYSICAL_MODEL_URI: string = "../api/data/physicalmodel/model";
const PHYSICAL_MODEL_CREATE_URI: string = "../api/data/physicalmodel/model/create";
const PHYSICAL_MODEL_REFRESH_URI: string = "../api/data/physicalmodel/model/refresh";

export interface HighlightInfo {
   sourceTable: string;
   targetTable: string;
}

@Injectable()
export class DataPhysicalModelService implements OnDestroy {
   readonly onFullScreen =  new ReplaySubject<boolean>(1);
   onNotification = new Subject<NotificationData>();
   onHighlightConnections = new Subject<HighlightInfo[]>();
   onRefreshWarning = new Subject<string>(); // runtimeId
   private _modelChange: Subject<boolean> = new Subject<boolean>();
   defaultModel: PhysicalModelDefinition = {
      name: null,
      folder: null,
      description: null,
      tables: [],
      id: null
   };
   physicalModel: PhysicalModelDefinition = Tool.clone(this.defaultModel);
   database: string;
   parent: string;
   aliasValidators: ValidatorFn[] = [
      Validators.required,
      FormValidators.invalidDataModelName,
      FormValidators.nameStartWithCharDigit
   ];
   aliasValidatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "_#(js:data.physicalmodel.tableAliasNameRequired)"},
      {validatorName: "invalidDataModelName", message: "_#(js:data.physicalmodel.tableAliasNameInvalid)"},
      {validatorName: "nameStartWithCharDigit", message: "_#(js:data.physicalmodel.tableAliasNameStartError)"}
   ];
   loadingModel: boolean = false;

   constructor(private httpClient: HttpClient) {
   }

   ngOnDestroy(): void {
      if(!!this.onNotification) {
         this.onNotification.unsubscribe();
         this.onNotification = null;
      }

      if(!!this._modelChange) {
         this._modelChange.unsubscribe();
         this._modelChange = null;
      }

      if(!!this.onFullScreen) {
         this.onFullScreen.unsubscribe();
      }

      if(!!this.onRefreshWarning) {
         this.onRefreshWarning.unsubscribe();
         this.onRefreshWarning = null;
      }

      if(!!this.onHighlightConnections) {
         this.onHighlightConnections.unsubscribe();
         this.onHighlightConnections = null;
      }
   }

   public notify: (data: NotificationData) => void
      = (data: NotificationData) => this.onNotification.next(data);

   public refreshWarning: (runtimeId: string) => void
      = (runtimeId: string) => this.onRefreshWarning.next(runtimeId);

   public highlightConnections: (infos: HighlightInfo[]) => void
      = (infos: HighlightInfo[]) => this.onHighlightConnections.next(infos);

   public refreshModel(): Promise<null> {
      let promise = Promise.resolve(null);

      let params: HttpParams = new HttpParams().set("id", this.physicalModel.id);

      promise = promise.then(() => {
         return this.httpClient.get<PhysicalModelDefinition>(PHYSICAL_MODEL_REFRESH_URI,
            { params: params}).toPromise().then(data =>
         {
            if(data) {
               this.physicalModel = data;
            }
         });
      });

      return promise;
   }

   /**
    * Send request to get the physical model definition for the given name and database.
    */
   public openPhysicalModel(databaseName: string, originalName: string, parent?: string): Observable<PhysicalModelDefinition> {
      let event = new GetModelEvent(databaseName, originalName, null, parent);
      this.loadingModel = true;

      return this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODEL_URI, event)
         .pipe(tap(data => {
            this.physicalModel = data;
            this.loadingModel = false;
         }));
   }

   /**
    * Create a new physical model.
    */
   public createPhysicalModel(parent?: string): Observable<PhysicalModelDefinition> {
      let event: AddPhysicalModelEvent  = new AddPhysicalModelEvent(this.database,
         this.physicalModel, parent);
      this.loadingModel = true;

      return this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODEL_CREATE_URI, event)
         .pipe(tap(data => {
            this.physicalModel = data;
            this.loadingModel = false;
         }));
   }

   public resetModel(): void {
      this.physicalModel = Tool.clone(this.defaultModel);
   }

   public emitModelChange(graphTrigger = false): void {
      this._modelChange.next(graphTrigger);
   }

   get modelChange(): Observable<boolean> {
      return this._modelChange.asObservable();
   }

   isAutoAliasNode(graph: GraphModel): boolean {
      return !!graph && graph.autoAlias;
   }

   getAutoAliasName(graph: GraphModel): string {
      if(graph.autoAlias || graph.autoAliasByOutgoing) {
         return graph.node.name;
      }

      return null;
   }

   getTableName(graph: GraphModel): string {
      if(graph.autoAlias) {
         return graph.node.aliasSource;
      }
      else if(graph.autoAliasByOutgoing) {
         return graph.node.outgoingAliasSource;
      }
      else {
         return graph.node.name;
      }
   }
}
