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
import { NgModule, NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule, MAT_FORM_FIELD_DEFAULT_OPTIONS } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { LoadingSpinnerModule } from "../../../common/util/loading-spinner/loading-spinner.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { ContentMaterializedViewsViewComponent } from "../content-materialized-views-view/content-materialized-views-view.component";
import { MaterializedViewsRoutingModule } from "./materialized-views-routing.module";
import { MvManagementViewComponent } from "./mv-management-view/mv-management-view.component";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      LoadingSpinnerModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatDialogModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatSelectModule,
      MatSortModule,
      MatTableModule,
      MessageDialogModule,
      ReactiveFormsModule,
      MaterializedViewsRoutingModule,
      TableViewModule,
      EditorPanelModule,
   ],
   declarations: [
      ContentMaterializedViewsViewComponent,
      MvManagementViewComponent
   ],
   providers: [
      {provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: {float: "always"}}
   ],
   schemas: [ NO_ERRORS_SCHEMA ]
})
export class MaterializedViewsModule {
}
