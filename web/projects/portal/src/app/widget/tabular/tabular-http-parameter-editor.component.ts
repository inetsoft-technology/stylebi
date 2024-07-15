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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { HttpParameter } from "../../common/data/tabular/http-parameter";

@Component({
   selector: "tabular-http-parameter-editor",
   templateUrl: "tabular-http-parameter-editor.component.html"
})
export class TabularHttpParameterEditorComponent implements OnInit {
   @Input() value: HttpParameter;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Input() editorPropertyNames: string[];
   @Input() editorPropertyValues: string[];
   @Output() valueChange: EventEmitter<HttpParameter> = new EventEmitter<HttpParameter>();

   ngOnInit(): void {
      if(this.value == null) {
         this.value = {
            name: "",
            type: this.queryOnly ? "QUERY" : "HEADER",
            value: ""
         };

         setTimeout(() => this.valueChanged(), 0);
      }
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
   }

   get queryOnly(): boolean {
      return this.isPropertySet("queryOnly");
   }

   get headerOnly(): boolean {
      return this.isPropertySet("headerOnly");
   }

   private isPropertySet(name: string): boolean {
      if(this.editorPropertyNames && this.editorPropertyValues) {
         for(let i = 0; i < this.editorPropertyNames.length; i++) {
            if(this.editorPropertyNames[i] === name) {
               return this.editorPropertyValues[i] === "true";
            }
         }
      }

      return false;
   }
}
