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
import { Component, EventEmitter, Input, OnDestroy, OnInit, OnChanges, SimpleChanges,
         Output } from "@angular/core";
import { Subject } from "rxjs";
import { debounceTime, takeUntil } from "rxjs/operators";
import { RestParameter, RestParameters } from "../../common/data/tabular/rest-parameters";

@Component({
   selector: "tabular-rest-parameters-editor",
   templateUrl: "tabular-rest-parameters-editor.component.html",
   styleUrls: ["tabular-rest-parameters-editor.component.scss"]
})
export class TabularRestParametersEditorComponent implements OnInit, OnDestroy, OnChanges {
   @Input()
   get value(): RestParameters {
      return this._value;
   }

   set value(val: RestParameters) {
      this._value = TabularRestParametersEditorComponent.copyParameters(val);
   }

   @Input() enabled = true;
   @Input() required = true;
   @Output() valueChange = new EventEmitter<RestParameters>();
   @Output() validChange = new EventEmitter<boolean>();
   private changes$ = new Subject<void>();
   private destroy$ = new Subject<void>();
   private _value: RestParameters;

   ngOnInit(): void {
      if(!this.value) {
         this.value = {
            endpoint: null,
            parameters: []
         };

         setTimeout(() => this.valueChanged(), 0);
      }

      this.changes$
         .pipe(
            takeUntil(this.destroy$),
            debounceTime(500)
         )
         .subscribe(() => this.valueChanged());
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["value"]) {
         this.validChange.emit(this.isValid());
      }
   }

   parameterValueChanged() {
      this.changes$.next();
   }

   isParameterValid(parameter: RestParameter): boolean {
      return !parameter || !parameter.required || !!parameter.value;
   }

   private valueChanged(): void {
      this.validChange.emit(this.isValid());
      this.valueChange.emit(this.value);
   }

   private isValid(): boolean {
      if(!this.value || !this.value.parameters) {
         return true;
      }

      return this.value.parameters.reduce((previous, current) => {
         return previous && (!current.required || !!current.value);
      }, true);
   }

   private static copyParameters(parameters: RestParameters): RestParameters {
      if(!parameters) {
         return null;
      }

      return {
         endpoint: parameters.endpoint,
         parameters: TabularRestParametersEditorComponent.sortParameters(parameters.parameters)
      };
   }

   private static sortParameters(parameters: RestParameter[]): RestParameter[] {
      if(!parameters) {
         return [];
      }

      return parameters.sort((a, b) => {
         if(a.required && !b.required) {
            return -1;
         }

         if(b.required && !a.required) {
            return 1;
         }

         // don't sort the labels so the order can be controlled in endpoints.json.
         // this way relevant fields (e.g. From Date, To Date) can be arranged together.
         //a.label.localeCompare(b.label);
         return 0;
      });
   }
}
