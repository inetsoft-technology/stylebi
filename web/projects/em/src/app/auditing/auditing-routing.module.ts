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
import { inject, NgModule } from "@angular/core";
import { CanActivateFn, Router, RouterModule, Routes, UrlTree } from "@angular/router";
import { Observable, of } from "rxjs";
import { map, switchMap } from "rxjs/operators";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { AuthorizationGuard } from "../authorization/authorization-guard.service";
import { LogViewLinks } from "../monitoring/log/log-view-links";
import { AuditingSidenavComponent } from "./auditing-sidenav/auditing-sidenav.component";
import { AuditingResolverService } from "./auditing-resolver.service";

export const canActivateAuditViewer: CanActivateFn = (): Observable<boolean | UrlTree> => {
   const http = inject(HttpClient);
   const appInfoService = inject(AppInfoService);
   const router = inject(Router);

   return appInfoService.isEnterprise().pipe(
      switchMap((enterprise) => {
         if(!enterprise) {
            router.navigate(["/"]);

            return of(false);
         }

         return http.get<LogViewLinks>("../api/em/monitoring/audit/links").pipe(
            map(links => {
               if(!links.fluentdLogging || !links.auditViewUrl) {
                  return true;
               }

               window.open(links.auditViewUrl, "_blank");
               return false;
            })
         );
      }));
};

const routes: Routes = [
   {
      path: "",
      component: AuditingSidenavComponent,
      canActivate: [ canActivateAuditViewer ],
      children: [
         {
            path: "inactive-resource",
            loadChildren: () => import("./audit-inactive-resource/audit-inactive-resource.module").then(m => m.AuditInactiveResourceModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "inactive-resource"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "inactive-user",
            loadChildren: () => import("./audit-inactive-user/audit-inactive-user.module").then(m => m.AuditInactiveUserModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "inactive-user"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "identity-info",
            loadChildren: () => import("./audit-identity-info/audit-identity-info.module").then(m => m.AuditIdentityInfoModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "identity-info"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "logon-error",
            loadChildren: () => import("./audit-logon-error/audit-logon-error.module").then(m => m.AuditLogonErrorModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "logon-error"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "logon-history",
            loadChildren: () => import("./audit-logon-history/audit-logon-history.module").then(m => m.AuditLogonHistoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "logon-history"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "modification-history",
            loadChildren: () => import("./audit-modification-history/audit-modification-history.module").then(m => m.AuditModificationHistoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "modification-history"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "user-session",
            loadChildren: () => import("./audit-user-session/audit-user-session.module").then(m => m.AuditUserSessionModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "user-session"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "dependent-assets",
            loadChildren: () => import("./audit-dependent-assets/audit-dependent-assets.module").then(m => m.AuditDependentAssetsModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "dependent-assets"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "required-assets",
            loadChildren: () => import("./audit-required-assets/audit-required-assets.module").then(m => m.AuditRequiredAssetsModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "required-assets"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "export-history",
            loadChildren: () => import("./audit-export-history/audit-export-history.module").then(m => m.AuditExportHistoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "export-history"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "schedule-history",
            loadChildren: () => import("./audit-schedule-history/audit-schedule-history.module").then(m => m.AuditScheduleHistoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "schedule-history"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "bookmark-history",
            loadChildren: () => import("./audit-bookmark-history/audit-bookmark-history.module").then(m => m.AuditBookmarkHistoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "auditing",
               permissionChild: "bookmark-history"
            },
            resolve: {
               model: AuditingResolverService
            }
         },
         {
            path: "**",
            redirectTo: "inactive-resource"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule],
   providers: [AuditingResolverService]
})
export class AuditingRoutingModule {
}
