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
import { ActionsContextmenuAnchorDirective } from "./actions-contextmenu-anchor.directive";
import { FixedDropdownContextmenuComponent } from "./fixed-dropdown-contextmenu.component";
import { FixedDropdownComponent } from "./fixed-dropdown.component";
import { FixedDropdownDirective } from "./fixed-dropdown.directive";
import { ActionsContextmenuComponent } from "./actions-contextmenu.component";
import { WidgetDirectivesModule } from "../directive/widget-directives.module";
import { FixedDropdownService } from "./fixed-dropdown.service";
import { DropdownStackService } from "./dropdown-stack.service";

@NgModule({
   imports: [
      CommonModule,
      WidgetDirectivesModule,
   ],
   declarations: [
      ActionsContextmenuAnchorDirective,
      ActionsContextmenuComponent,
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent,
      FixedDropdownDirective
   ],
   exports: [
      ActionsContextmenuAnchorDirective,
      ActionsContextmenuComponent,
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent,
      FixedDropdownDirective
   ],
   providers: [
      FixedDropdownService,
      DropdownStackService
   ],
})
export class FixedDropdownModule {
}
