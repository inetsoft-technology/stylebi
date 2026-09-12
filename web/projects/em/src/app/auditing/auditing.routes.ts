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
import { inject } from "@angular/core";
import { CanActivateFn, Router, Routes, UrlTree } from "@angular/router";
import { Observable, of } from "rxjs";
import { switchMap } from "rxjs/operators";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { authorizationGuard } from "../authorization/authorization-guard.service";
import { auditingResolver } from "./auditing-resolver.service";
import { AuditingSidenavComponent } from "./auditing-sidenav/auditing-sidenav.component";
import { ErrorHandlerService } from "../common/util/error/error-handler.service";

export const canActivateAuditViewer: CanActivateFn = (): Observable<boolean | UrlTree> => {
   const appInfoService = inject(AppInfoService);
   const router = inject(Router);

   return appInfoService.isEnterprise().pipe(
      switchMap((enterprise) => {
         if(!enterprise) {
            router.navigate(["/"]);
            return of(false);
         }

         return of(true);
      }));
};

export const AUDITING_ROUTES: Routes = [
   {
      path: "",
      component: AuditingSidenavComponent,
      canActivate: [canActivateAuditViewer],
      providers: [ErrorHandlerService],
      children: [
         {
            path: "inactive-resource",
            loadChildren: () => import("./audit-inactive-resource/audit-inactive-resource.routes").then(m => m.AUDIT_INACTIVE_RESOURCE_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "inactive-resource"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "inactive-user",
            loadChildren: () => import("./audit-inactive-user/audit-inactive-user.routes").then(m => m.AUDIT_INACTIVE_USER_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "inactive-user"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "identity-info",
            loadChildren: () => import("./audit-identity-info/audit-identity-info.routes").then(m => m.AUDIT_IDENTITY_INFO_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "identity-info"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "logon-error",
            loadChildren: () => import("./audit-logon-error/audit-logon-error.routes").then(m => m.AUDIT_LOGON_ERROR_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "logon-error"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "logon-history",
            loadChildren: () => import("./audit-logon-history/audit-logon-history.routes").then(m => m.AUDIT_LOGON_HISTORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "logon-history"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "modification-history",
            loadChildren: () => import("./audit-modification-history/audit-modification-history.routes").then(m => m.AUDIT_MODIFICATION_HISTORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "modification-history"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "user-session",
            loadChildren: () => import("./audit-user-session/audit-user-session.routes").then(m => m.AUDIT_USER_SESSION_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "user-session"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "dependent-assets",
            loadChildren: () => import("./audit-dependent-assets/audit-dependent-assets.routes").then(m => m.AUDIT_DEPENDENT_ASSETS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "dependent-assets"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "required-assets",
            loadChildren: () => import("./audit-required-assets/audit-required-assets.routes").then(m => m.AUDIT_REQUIRED_ASSETS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "required-assets"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "export-history",
            loadChildren: () => import("./audit-export-history/audit-export-history.routes").then(m => m.AUDIT_EXPORT_HISTORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "export-history"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "schedule-history",
            loadChildren: () => import("./audit-schedule-history/audit-schedule-history.routes").then(m => m.AUDIT_SCHEDULE_HISTORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "schedule-history"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "bookmark-history",
            loadChildren: () => import("./audit-bookmark-history/audit-bookmark-history.routes").then(m => m.AUDIT_BOOKMARK_HISTORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "bookmark-history"
            },
            resolve: {
               model: auditingResolver
            }
         },
         {
            path: "**",
            redirectTo: "inactive-resource"
         }
      ]
   }
];
