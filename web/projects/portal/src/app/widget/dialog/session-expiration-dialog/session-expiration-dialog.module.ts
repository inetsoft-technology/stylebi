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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { WidgetDirectivesModule } from "../../directive/widget-directives.module";
import { StandardDialogModule } from "../../standard-dialog/standard-dialog.module";
import { SessionExpirationDialog } from "./session-expiration-dialog.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      StandardDialogModule,
      WidgetDirectivesModule,
   ],
   declarations: [
      SessionExpirationDialog
   ],
   exports: [
      SessionExpirationDialog
   ],
   providers: [],
})
export class SessionExpirationDialogModule {
}
