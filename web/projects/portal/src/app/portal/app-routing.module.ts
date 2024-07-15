/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NgModule } from "@angular/core";
import { RouterModule, Routes, UrlMatchResult, UrlSegment } from "@angular/router";
import { CanDeactivateGuard } from "../common/services/can-deactivate-guard.service";
import { PortalAppComponent } from "./app.component";
import { CustomTabComponent } from "./custom/custom-tab.component";
import { DashboardTabComponent } from "./dashboard/dashboard-tab.component";
import { DashboardLandingComponent } from "./dashboard/landing/dashboard-landing.component";
import { DatabasePhysicalModelComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/database-physical-model.component";
import { DataDatasourceBrowserComponent } from "./data/data-datasource-browser/data-datasource-browser.component";
import { DatasourceSelectionViewComponent } from "./data/data-datasource-browser/datasource-selection/datasource-selection-view.component";
import { DatasourcesDatabaseComponent } from "./data/data-datasource-browser/datasources-database/datasources-database.component";
import { DatasourcesDatasourceComponent } from "./data/data-datasource-browser/datasources-datasource/datasources-datasource.component";
import { DataFolderBrowserComponent } from "./data/data-folder-browser/data-folder-browser.component";
import { DataTabComponent } from "./data/data-tab.component";
import { PortalRedirectComponent } from "./portal-redirect.component";
import { ReportTabComponent } from "./report/report-tab.component";
import { PortalReportComponent } from "./report/report/portal-report.component";
import { WelcomePageComponent } from "./report/welcome/welcome-page.component";
import { ScheduleSaveGuard } from "./schedule/schedule-save.guard";
import { ScheduleTabComponent } from "./schedule/schedule-tab.component";
import { ScheduleTaskEditorComponent } from "./schedule/schedule-task-editor/schedule-task-editor.component";
import { ScheduleTaskListComponent } from "./schedule/schedule-task-list/schedule-task-list.component";
import { CanTabActivateService } from "./services/can-tab-activate.service";
import { DashboardTabResolver } from "./services/dashboard-tab-resolver.service";
import { ReportTabResolver } from "./services/report-tab-resolver.service";
import { RouteEntryResolver } from "./services/route-entry-resolver.service";
import { RouteSourceResolver } from "./services/route-source-resolver.service";
import { LogicalModelComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/logical-model.component";
import { DatabaseVPMComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/database-vpm.component";
import { DatabaseVPMBrowserComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/database-vpm-browser/database-vpm-browser.component";
import { CanDatabaseModelActivateService } from "./services/can-database-model-activate.service";
import { DatabaseDataModelBrowserComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/database-data-model-browser.component";
import { CanDatabaseCreateActivateService } from "./services/can-database-create-activate.service";
import { PrincipalResolver } from "../common/services/principal-resolver.service";
import { DatasourcesXmlaComponent } from "./data/data-datasource-browser/datasources-xmla/datasources-xmla.component";

export function REPORT_URL_MATCHER(url: UrlSegment[]): UrlMatchResult {
   let result: any = null;

   if(url && url.length > 1) {
      if(url[0].path === "report" || url[0].path === "archive") {
         const params: { [name: string]: UrlSegment } = {};
         params.reportType = url[0];

         for(let i = 1; i < url.length; i++) {
            params[`pathComponent${i}`] = url[i];
         }

         result = {
            consumed: url,
            posParams: params
         };
      }
   }

   return result;
}

const appRoutes: Routes = [
   {
      path: "",
      component: PortalAppComponent,
      children: [
         {
            path: "tab/dashboard",
            component: DashboardTabComponent,
            canActivate: [CanTabActivateService],
            resolve: {
               dashboardTabModel: DashboardTabResolver
            },
            children: [
               {
                  path: "",
                  component: DashboardLandingComponent,
                  resolve: {
                     dashboardTabModel: DashboardTabResolver
                  }
               },
               {
                  path: "vs",
                  loadChildren: () => import("../viewer/viewer-app.module").then(m => m.ViewerAppModule),
                  data: {
                     inPortal: true,
                     inDashboard: true
                  }
               },
               {
                  path: "**",
                  redirectTo: ""
               }
            ]
         },
         {
            path: "tab/report",
            component: ReportTabComponent,
            canActivate: [CanTabActivateService],
            resolve: {
               reportTabModel: ReportTabResolver
            },
            children: [
               {
                  path: "",
                  component: WelcomePageComponent
               },
               {
                  component: PortalReportComponent,
                  matcher: REPORT_URL_MATCHER,
                  resolve: {
                     repositoryEntry: RouteEntryResolver,
                     contentSource: RouteSourceResolver
                  },
               },
               {
                  path: "vs",
                  loadChildren: () => import("../viewer/viewer-app.module").then(m => m.ViewerAppModule),
                  data: {inPortal: true}
               },
               {
                  path: "**",
                  redirectTo: ""
               }
            ]
         },
         {
            path: "tab/schedule",
            component: ScheduleTabComponent,
            canActivate: [CanTabActivateService],
            children: [
               {
                  path: "tasks",
                  component: ScheduleTaskListComponent
               },
               {
                  path: "tasks/:task",
                  component: ScheduleTaskEditorComponent,
                  canDeactivate: [ScheduleSaveGuard]
               },
               {
                  path: "**",
                  redirectTo: "tasks"
               }
            ]
         },
         {
            path: "tab/data",
            component: DataTabComponent,
            resolve: {
               principalCommand: PrincipalResolver
            },
            canActivate: [CanTabActivateService],
            children: [
               {
                  path: "",
                  children: [
                     {
                        path: "",
                        redirectTo: "folder",
                        pathMatch: "full"
                     },
                     {
                        path: "folder",
                        component: DataFolderBrowserComponent
                     },
                     {
                        path: "datasources",
                        component: DataDatasourceBrowserComponent
                     },
                     {
                        // route for new datasource with parameter for type
                        path: "datasources/datasource/new/:datasourceType/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatasourceComponent
                     },
                     {
                        // route for a new data source from a listing
                        path: "datasources/datasource/listing/:listingName/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatasourceComponent
                     },
                     {
                        path: "datasources/datasource/:datasourcePath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatasourceComponent
                     },
                     {
                        // route for new database with no parameter
                        path: "datasources/database/new/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatabaseComponent
                     },
                     {
                        // route for a new database from a listing
                        path: "datasources/database/listing/:listingName/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatabaseComponent
                     },
                     {
                        path: "datasources/database/:databasePath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesDatabaseComponent
                     },
                     {
                        path: "datasources/datasource/xmla/new/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesXmlaComponent
                     },
                     {
                        path: "datasources/datasource/xmla/edit/:datasourcePath",
                        canDeactivate: [CanDeactivateGuard],
                        component: DatasourcesXmlaComponent
                     },
                     {
                        path: "datasources/listing/:parentPath",
                        canDeactivate: [CanDeactivateGuard],
                        canActivate: [CanDatabaseCreateActivateService],
                        component: DatasourceSelectionViewComponent
                     },
                     {
                        path: "datasources/listing",
                        redirectTo: "datasources/listing/"
                     },
                     {
                        path: "datasources/database/:databasePath/physicalModel/:physicalName",
                        canDeactivate: [CanDeactivateGuard],
                        canActivate: [CanDatabaseModelActivateService],
                        component: DatabasePhysicalModelComponent
                     },
                     {
                        path: "datasources/database/:databasePath/physicalModel/:physicalModelName/logicalModel/:logicalModelName",
                        canDeactivate: [CanDeactivateGuard],
                        canActivate: [CanDatabaseModelActivateService],
                        component: LogicalModelComponent
                     },
                     {
                        path: "datasources/database/vpms/:databaseName",
                        component: DatabaseVPMBrowserComponent
                     },
                     {
                        path: "datasources/database/vpm/:vpmPath",
                        canDeactivate: [CanDeactivateGuard],
                        canActivate: [CanDatabaseModelActivateService],
                        component: DatabaseVPMComponent
                     },
                     {
                        path: "datasources/databaseModels",
                        component: DatabaseDataModelBrowserComponent,
                     },
                     // end user-managed drivers is dangerous, disabling for now until we figure
                     // out a safe way to do this
                     // {
                     //    path: "drivers",
                     //    component: DatasourcesDriversComponent
                     // }
                  ]
               }
            ]
         },
         {
            path: "tab/custom/:name",
            component: CustomTabComponent
         },
         {
            path: "**",
            component: PortalRedirectComponent
         }
      ]
   }
];

@NgModule({
   imports: [
      RouterModule.forChild(appRoutes)
   ],
   exports: [
      RouterModule
   ],
   providers: [ PrincipalResolver ]
})
export class PortalAppRoutingModule {
}
