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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { AuthorizationGuard } from "../../authorization/authorization-guard.service";
import { ScheduleConfigurationPageComponent } from "./schedule-configuration-page/schedule-configuration-page.component";
import { ScheduleCycleEditorPageComponent } from "./schedule-cycle-editor-page/schedule-cycle-editor-page.component";
import { ScheduleCycleListPageComponent } from "./schedule-cycle-list-page/schedule-cycle-list-page.component";
import { ScheduleSettingsPageComponent } from "./schedule-settings-page/schedule-settings-page.component";
import { ScheduleStatusPageComponent } from "./schedule-status-page/schedule-status-page.component";
import { ScheduleSaveGuard } from "./schedule-task-editor-page/schedule-save.guard";
import { ScheduleTaskEditorPageComponent } from "./schedule-task-editor-page/schedule-task-editor-page.component";
import { ScheduleTaskListComponent } from "./schedule-task-list/schedule-task-list.component";
import { ScheduleCycleSaveGuard } from "./schedule-cycle-editor-page/schedule-cycle-save.guard";
import { ScheduleConfigSaveGuard } from "./schedule-configuration-page/schedule-config-save.guard";

const routes: Routes = [
   {
      path: "",
      component: ScheduleSettingsPageComponent,
      children: [
         {
            path: "tasks",
            component: ScheduleTaskListComponent,
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/schedule",
               permissionChild: "tasks"
            }
         },
         {
            path: "tasks/:task",
            component: ScheduleTaskEditorPageComponent,
            canDeactivate: [ScheduleSaveGuard]
         },
         {
            path: "cycles",
            component: ScheduleCycleListPageComponent,
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/schedule",
               permissionChild: "cycles"
            }
         },
         {
            path: "cycles/:cycle",
            component: ScheduleCycleEditorPageComponent,
            canDeactivate: [ScheduleCycleSaveGuard]
         },
         {
            path: "settings",
            component: ScheduleConfigurationPageComponent,
            canDeactivate: [ScheduleConfigSaveGuard],
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/schedule",
               permissionChild: "settings"
            }
         },
         {
            path: "status",
            component: ScheduleStatusPageComponent,
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/schedule",
               permissionChild: "status"
            }
         },
         {
            path: "**",
            redirectTo: "tasks"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule]
})
export class ScheduleRoutingModule {
}
