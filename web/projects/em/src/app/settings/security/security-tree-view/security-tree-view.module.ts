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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatTreeModule } from "@angular/material/tree";
import { SecurityTreeViewComponent } from "./security-tree-view.component";
import { ScrollingModule } from "@angular/cdk/scrolling";
import { MatProgressBarModule } from "@angular/material/progress-bar";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatTreeModule,
      MatButtonModule,
      ScrollingModule,
      MatProgressBarModule
   ],
   declarations: [
      SecurityTreeViewComponent
   ],
   exports: [
      SecurityTreeViewComponent
   ]
})
export class SecurityTreeViewModule {
}
