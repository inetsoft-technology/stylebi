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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { DataRef } from "../../common/data/data-ref";
import { UIContextService } from "../../common/services/ui-context.service";
import { Tool } from "../../../../../shared/util/tool";
import { BindingModel } from "../data/binding-model";
import { EventService } from "./event.service";

@Injectable()
export abstract class BindingService extends EventService {
   _headers: HttpHeaders;
   _bindingModel: BindingModel;
   _grayedOutFields: DataRef[];
   private _runtimeId: string;
   private _assemblyName: string;  // assembly type
   private _objectType: string;
   private _variableValues: string[];  // variables

   constructor(public http: HttpClient,
               protected uiContextService: UIContextService)
   {
      super();

      this._headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   public loadGrayedOutFields() {
   }

   public clear(): void {
      this._bindingModel = null;
      this._runtimeId = null;
      this._assemblyName = null;
      this._objectType = null;
      this._variableValues = null;
   }

   set runtimeId(runtiemId: string) {
      this._runtimeId = runtiemId;
   }

   get runtimeId(): string {
      return this._runtimeId;
   }

   set assemblyName(assemblyName: string) {
      this._assemblyName = assemblyName;
   }

   get assemblyName(): string {
      return this._assemblyName;
   }

   set objectType(objectType: string) {
      this._objectType = objectType;
   }

   get objectType(): string {
      return this._objectType;
   }

   set variableValues(variableValues: string[]) {
      this._variableValues = variableValues;
   }

   get variableValues(): string[] {
      return this._variableValues;
   }

   set bindingModel(bindingModel: BindingModel) {
      this._bindingModel = bindingModel;
   }

   get bindingModel(): BindingModel {
      return this._bindingModel;
   }

   public getURLParams(): HttpParams {
      let parameters = new HttpParams()
         .set("vsId", this.runtimeId)
         .set("assemblyName", this.assemblyName);

      return parameters;
   }

   handleError<T>(error: any): Observable<T> {
      let errMsg = (error.message) ? error.message :
         error.status ? `${error.status} - ${error.statusText}` : "Server error";
      console.error(errMsg); // log to console instead
      return throwError(errMsg);
   }

   getGrayedOutFields(): DataRef[] {
      return this._grayedOutFields;
   }

   setGrayedOutFields(fields: DataRef[]): void {
      this._grayedOutFields = fields;
   }

   getBindingModel(): BindingModel {
      return this._bindingModel;
   }

   resetBindingModel(binding: BindingModel): void {
      this._bindingModel = binding;
   }

   isGrayedOutField(name: string) {
      if(name == null || this._bindingModel == null || this._bindingModel.source == null ||
         this._bindingModel.source.source == null)
      {
         return false;
      }

      let field = Tool.replaceStr(name, "\\^", ".");
      field = Tool.replaceStr(field, ":", ".");
      let source = this._bindingModel.source.source;
      let grayedOutFields: DataRef[] = this.getGrayedOutFields();

      for(let i = 0; grayedOutFields && i < grayedOutFields.length; i++) {
         if(field === grayedOutFields[i].name) {
            return true;
         }

         if(field === grayedOutFields[i].attribute && source === grayedOutFields[i].entity) {
            return true;
         }
      }

      return false;
   }

   getModel(url: string, params?: string): Observable<any> {
      return this.http.get(url + this.getRequestParams() + (params ? "&" + params : "")).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   setModel<T>(url: string, vals: any, params?: string): Observable<T> {
      return this.http.put<T>(url + this.getRequestParams() + (params ? "&" + params : ""),
         vals, { headers: this._headers })
         .pipe(
            catchError((error) => this.handleError<T>(error))
         );
   }

   abstract getRequestParams(): string;
   abstract loadBindingModel(callback?: Function): void;
   abstract setBindingModel(model?: any, callback?: Function, params?: any): any | Observable<any>;
   abstract getFormulaFields(oname?: string, tableName?: string): Observable<any>;
}
