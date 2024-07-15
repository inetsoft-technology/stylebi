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
import { CommonModule } from "@angular/common";
import { TopScrollModule } from "../../top-scroll/top-scroll.module";
import { PropertiesRoutingModule } from "./properties-routing.module";
import { PropertySettingsViewComponent } from "./property-settings-view/property-settings-view.component";
import { CdkTableModule } from "@angular/cdk/table";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatPaginatorIntl, MatPaginatorModule } from "@angular/material/paginator";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { MatTooltipModule } from "@angular/material/tooltip";
import { PropertySettingsDatasourceService } from "./property-settings-services/property-settings-datasource.service";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { LocalizedMatPaginator } from "../../../../../shared/util/localized-mat-paginator";

@NgModule({
   imports: [
      CommonModule,
      CdkTableModule,
      FormsModule,
      MatAutocompleteModule,
      MatButtonModule,
      MatInputModule,
      MatTableModule,
      MatPaginatorModule,
      MatSortModule,
      MatIconModule,
      MatTooltipModule,
      PropertiesRoutingModule,
      ReactiveFormsModule,
      TopScrollModule
   ],
   providers: [
      PropertySettingsDatasourceService,
      {provide: MatPaginatorIntl, useClass: LocalizedMatPaginator }
   ],
   declarations: [PropertySettingsViewComponent]
})
export class PropertiesModule {
}
