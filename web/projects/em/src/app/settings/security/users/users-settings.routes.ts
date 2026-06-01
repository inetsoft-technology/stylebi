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
import { Routes } from "@angular/router";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { SecurityTreeService } from "./security-tree.service";
import { UsersSettingsPageComponent } from "./users-settings-page/users-settings-page.component";
import { usersSettingsSaveGuard } from "./users-settings-page/users-settings-save.guard";

export const USERS_SETTINGS_ROUTES: Routes = [
   {
      path: "",
      component: UsersSettingsPageComponent,
      canDeactivate: [usersSettingsSaveGuard],
      providers: [SecurityTreeService, ErrorHandlerService]
   }
];
