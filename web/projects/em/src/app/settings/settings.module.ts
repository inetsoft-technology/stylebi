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
import { LayoutModule } from "@angular/cdk/layout";
import { ScrollingModule } from "@angular/cdk/scrolling";
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MAT_PAGINATOR_DEFAULT_OPTIONS } from "@angular/material/paginator";
import { PageHeaderModule } from "../page-header/page-header.module";
import { SearchModule } from "../search/search.module";
import { TopScrollModule } from "../top-scroll/top-scroll.module";
import { SettingsRoutingModule } from "./settings-routing.module";
import { SettingsSidenavComponent } from "./settings-sidenav/settings-sidenav.component";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      LayoutModule,
      MatButtonModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatSidenavModule,
      MatToolbarModule,
      ScrollingModule,
      PageHeaderModule,
      SettingsRoutingModule,
      SearchModule,
      TopScrollModule
   ],
   declarations: [SettingsSidenavComponent],
   providers: [
      {
       provide: MAT_PAGINATOR_DEFAULT_OPTIONS,
       useValue: {formFieldAppearance: "fill"},
      }
   ]
})
export class SettingsModule {
}
