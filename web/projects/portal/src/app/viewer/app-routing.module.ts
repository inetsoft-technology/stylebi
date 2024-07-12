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
import { NgModule } from "@angular/core";
import { RouterModule, Routes, UrlMatchResult, UrlSegment } from "@angular/router";
import { CanComponentDeactivateService } from "../../../../shared/util/guard/can-component-deactivate.service";
import { GlobalParameterGuard } from "../../../../shared/util/guard/global-parameter-guard.service";
import { ViewDataResolver } from "./view-data-resolver.service";
import { ViewerRootComponent } from "./viewer-root.component";
import { ViewerViewComponent } from "./viewer-view/viewer-view.component";
import { PrincipalResolver } from "../common/services/principal-resolver.service";

export function VIEW_URL_MATCHER(url: UrlSegment[]): UrlMatchResult {
   let result: UrlMatchResult = null;

   if(url && url.length > 0 && url[0].path === "view") {
      const params: {[name: string]: UrlSegment} = {};
      result = {
         consumed: url,
         posParams: params
      };

      if(url.length > 1) {
         let assetScope: UrlSegment = null;
         let assetOwner: UrlSegment = null;
         let assetPath: string = null;
         let assetId: string = null;

         if(url[1].path === "global") {
            assetScope = url[1];

            if(url.length > 2) {
               assetPath = url.slice(2).map((s) => s.path).join("/");
               assetId = `1^128^__NULL__^${assetPath}`;
            }
         }
         else if(url[1].path === "user") {
            assetScope = url[1];

            if(url.length > 2) {
               assetOwner = url[2];

               if(url.length > 3) {
                  assetPath = url.slice(3).map((s) => s.path).join("/");
                  assetId = `4^128^${assetOwner.path}^${assetPath}`;
               }
            }
         }
         else {
            assetId = url.slice(1).map((s) => s.path).join("/");
            const match = /^([14])+\^128\^([^^]+)\^(.+)$/.exec(assetId);

            if(match) {
               if(match[1] == "1") {
                  assetScope = new UrlSegment("global", {});
               }
               else {
                  assetScope = new UrlSegment("user", {});
                  assetOwner = new UrlSegment(match[2], {});
               }

               assetPath = match[3];
            }
         }

         if(assetScope) {
            params.assetScope = assetScope;
         }

         if(assetOwner) {
            params.assetOwner = assetOwner;
         }

         if(assetPath) {
            params.assetPath = new UrlSegment(assetPath, {});
         }

         if(assetId) {
            params.assetId = new UrlSegment(assetId, {});
         }
      }
   }
   else if(url && url.length > 0 && url[0].path === "dashboard") {
      const params: {[name: string]: UrlSegment} = {};
      result = {
         consumed: url,
         posParams: params
      };

      if(url.length > 1) {
         params.dashboardName = url[1];
      }
   }

   return result;
}

const routes: Routes = [
   {
      path: "",
      component: ViewerRootComponent,
      children: [
         {
            component: ViewerViewComponent,
            canActivate: [GlobalParameterGuard],
            canDeactivate: [CanComponentDeactivateService],
            resolve: {
               viewData: ViewDataResolver,
               principalCommand: PrincipalResolver
            },
            matcher: VIEW_URL_MATCHER
         },
         {
            path: "edit",
            loadChildren: () => import("./viewer-edit/viewer-edit.module").then(m => m.ViewerEditModule),
            resolve: {
               viewData: ViewDataResolver
            }
         },
         {
            path: "**",
            redirectTo: "view"
         }
      ]
   },
   {
      path: "**",
      redirectTo: ""
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule],
   providers: [
      ViewDataResolver,
      PrincipalResolver,
      CanComponentDeactivateService,
      GlobalParameterGuard
   ]
})
export class ViewerAppRoutingModule {
}
