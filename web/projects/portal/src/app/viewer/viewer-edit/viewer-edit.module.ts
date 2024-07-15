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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { BindingModule } from "../../binding/binding.module";
import { SERVICE_PROVIDERS } from "../../composer/services.provider";
import { VSViewModule } from "../../vsview/vs-view.module";
import { ViewerEditRoutingModule } from "./viewer-edit-routing.module";
import { ViewerEditComponent } from "./viewer-edit.component";
import { VsWizardModule } from "../../vs-wizard/vs-wizard.module";

@NgModule({
   imports: [
      CommonModule,
      BindingModule.forRoot(SERVICE_PROVIDERS),
      VSViewModule,
      VsWizardModule,
      ViewerEditRoutingModule
   ],
   declarations: [
      ViewerEditComponent,
   ]
})
export class ViewerEditModule {
}
