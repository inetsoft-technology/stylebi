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
import { Routes, UrlMatchResult, UrlSegment } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import { DefaultCodemirrorService } from "../../../../shared/util/codemirror/default-codemirror.service";
import { canComponentDeactivate } from "../../../../shared/util/guard/can-component-deactivate.service";
import { globalParameterGuard } from "../../../../shared/util/guard/global-parameter-guard.service";
import { UIContextService } from "../common/services/ui-context.service";
import {
   principalResolver,
   PrincipalResolverService
} from "../common/services/principal-resolver.service";
import { HideNavService } from "../portal/services/hide-nav.service";
import { VSTrapService } from "../vsobjects/util/vs-trap.service";
import { viewDataResolver } from "./view-data-resolver.service";
import { ViewerRootComponent } from "./viewer-root.component";
import { ViewerViewComponent } from "./viewer-view/viewer-view.component";

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

         if(url[1].path === "global" || url[1].path === "shared_global") {
            assetScope = url[1];
            let sharedGlobal: boolean = url[1].path === "shared_global";

            if(url.length > 2) {
               assetPath = url.slice(2).map((s) => s.path).join("/");
               assetId = `1^128^__NULL__^${assetPath}`;
            }

            if(sharedGlobal && !!assetId) {
               assetId = assetId + "^host-org";
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

export const viewerRoutes: Routes = [
   {
      path: "",
      component: ViewerRootComponent,
      providers: [
         VSTrapService,
         UIContextService,
         HideNavService,
         NgbModal,
         { provide: CodemirrorService, useClass: DefaultCodemirrorService }
      ],
      children: [
         {
            component: ViewerViewComponent,
            providers: [PrincipalResolverService],
            canActivate: [globalParameterGuard],
            canDeactivate: [canComponentDeactivate],
            resolve: {
               viewData: viewDataResolver,
               principalCommand: principalResolver
            },
            matcher: VIEW_URL_MATCHER
         },
         {
            path: "edit",
            loadChildren: () => import("./viewer-edit/viewer-edit.routes").then(m => m.viewerEditRoutes),
            resolve: {
               viewData: viewDataResolver
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
