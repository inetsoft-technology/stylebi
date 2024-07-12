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
import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {
   AddRepositoryFolderDialog
} from "./add-repository-folder-dialog.component";
import {RepositoryListComponent} from "./repository-list.component";
import {RepositoryTreeComponent} from "./repository-tree.component";
import {RepositoryTreeService} from "./repository-tree.service";
import {TreeModule} from "../tree/tree.module";
import {StandardDialogModule} from "../standard-dialog/standard-dialog.module";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {WidgetDirectivesModule} from "../directive/widget-directives.module";

@NgModule({
   imports: [
      CommonModule,
      TreeModule,
      StandardDialogModule,
      ReactiveFormsModule,
      FormsModule,
      WidgetDirectivesModule,
   ],
   declarations: [
      AddRepositoryFolderDialog,
      RepositoryListComponent,
      RepositoryTreeComponent
   ],
   exports: [
      AddRepositoryFolderDialog,
      RepositoryListComponent,
      RepositoryTreeComponent
   ],
   providers: [
      RepositoryTreeService
   ],
})
export class RepositoryTreeModule {
}
