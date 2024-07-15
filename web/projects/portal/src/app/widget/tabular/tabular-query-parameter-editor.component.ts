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
import { QueryParameter } from "../../common/data/tabular/query-parameter";

@Component({
   selector: "tabular-query-parameter-editor",
   templateUrl: "tabular-query-parameter-editor.component.html",
})
export class TabularQueryParameterEditor implements OnInit {
   @Input() value: QueryParameter;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<QueryParameter> = new EventEmitter<QueryParameter>();

   ngOnInit(): void {
      if(this.value == null) {
         this.value = <QueryParameter>{
            name: null,
            type: "STRING",
            variable: false,
            value: null
         };

         setTimeout(() => this.valueChanged(), 0);
      }
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
   }
}
