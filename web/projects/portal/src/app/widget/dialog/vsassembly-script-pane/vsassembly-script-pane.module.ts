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
import {VSAssemblyScriptPane} from "./vsassembly-script-pane.component";
import {ScriptPaneModule} from "../script-pane/script-pane.module";
import {FormsModule} from "@angular/forms";

@NgModule({
   imports: [
      CommonModule,
      ScriptPaneModule,
      FormsModule,
   ],
   declarations: [
      VSAssemblyScriptPane
   ],
   exports: [
      VSAssemblyScriptPane
   ],
   providers: [],
})
export class VSAssemblyScriptPaneModule {
}
