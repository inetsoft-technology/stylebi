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
import { HttpClient } from "@angular/common/http";
import { inject } from "@angular/core";
import {
   ActivatedRouteSnapshot,
   CanActivateFn,
   ParamMap,
   Params,
   RouterStateSnapshot,
   UrlTree
} from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of, Subject } from "rxjs";
import { mergeMap } from "rxjs/operators";
import { ComponentTool } from "../../../portal/src/app/common/util/component-tool";
import { ShowHyperlinkService } from "../../../portal/src/app/vsobjects/show-hyperlink.service";
import { ParameterDialogComponent } from "../../../portal/src/app/widget/parameter/parameter-dialog/parameter-dialog.component";
import { ParameterPageModel } from "../../../portal/src/app/widget/parameter/parameter-page-model";
import { RepletParameterModel } from "../../../portal/src/app/widget/parameter/replet-parameter-model";

interface GlobalParameterModel {
   required: boolean;
   model?: ParameterPageModel;
}

export const globalParameterGuard: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> => {
   const http = inject(HttpClient);
   const modal = inject(NgbModal);

   const routeType = next.url[0].path;

   if(routeType !== "report" && routeType !== "view") {
      return of(true);
   }

   const assetType = routeType == "view" ? "viewsheet" : routeType;
   const paramMap = ShowHyperlinkService.getQueryParams(next.queryParamMap);
   const path = next.url.slice(1).map(s => decodeURIComponent(s.path)).join("/");

   if(paramMap.has("__inetsoftGlobalParamsSet") &&
      paramMap.get("__inetsoftGlobalParamsSet") === "true")
   {
      return of(true);
   }

   return getGlobalParameters(path, assetType, http).pipe(mergeMap(paramModel => {
      if(!paramModel.required) {
         return of(true);
      }

      let allParamsSet = true;

      for(let param of paramModel.model.params) {
         if(paramMap.has(param.name)) {
            param.value = paramMap.get(param.name);
         }
         else {
            allParamsSet = false;
         }
      }

      if(allParamsSet) {
         return of(true);
      }

      const subject = new Subject<boolean | UrlTree>();
      const dialog = ComponentTool.showDialog<ParameterDialogComponent>(
         modal, ParameterDialogComponent,
         (params) => appendParameters(params, paramMap, next, subject),
         {backdrop: "static", size: "lg"},
         () => rejectRoute(subject));
      dialog.model = paramModel.model;
      return subject.asObservable();
   }));
};

function getGlobalParameters(path: string, type: string, http: HttpClient): Observable<GlobalParameterModel> {
   path = encodeURI(path);
   const url = `../api/portal/global-parameters/${type}/${path}`;
   return http.get<GlobalParameterModel>(url);
}

function appendParameters(appendParams: RepletParameterModel[], inputParams: ParamMap, next: ActivatedRouteSnapshot, subject: Subject<boolean | UrlTree>): void {
   const url = next.pathFromRoot.reduce((previous, current) => {
      if(current.url.length > 0) {
         return previous + "/" + current.url.map(s => s.path).join("/");
      }

      return previous;
   }, "");

   const params: Params = {
      "__inetsoftGlobalParamsSet": "true"
   };

   for(let k of inputParams.keys) {
      params[k] = inputParams.get(k);
   }

   for(let param of appendParams) {
      params[param.name] = param.value;
   }

   const tree = this.router.parseUrl(url);
   tree.queryParams = params;
   subject.next(tree);
   subject.complete();
}

function rejectRoute(subject: Subject<boolean | UrlTree>): void {
   subject.next(false);
   subject.complete();
}