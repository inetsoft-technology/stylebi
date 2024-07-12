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
import {
   Component, Input, Output, EventEmitter, OnChanges,
   SimpleChanges, AfterViewInit, OnInit, Renderer2, ViewChild, ElementRef, OnDestroy
} from "@angular/core";
import { Observable } from "rxjs";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "variable-editor",
   templateUrl: "variable-editor.component.html",
   styleUrls: ["variable-editor.component.scss"]
})
export class VariableEditor implements OnInit, OnChanges, AfterViewInit, OnDestroy {
   @Input() value: string;
   @Input() showUseList: boolean = false;
   @Input() variablesFunction: () => Observable<any[]>;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() updateChoiceQuery: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("variableSelect") variableSelect: ElementRef;
   variableName: string;
   variableList: string[];
   _useList: boolean = false;
   vform: UntypedFormGroup = new UntypedFormGroup({"name": new UntypedFormControl()});
   useListId: string = "useList-" + Tool.generateRandomUUID();
   clickListener = null;

   constructor(private renderer: Renderer2) {
   }

   ngOnInit(): void {
      this.clickListener = this.renderer.listen("document", "click", (event: MouseEvent) => {
         if(!!this.variableSelect && event.target == this.variableSelect.nativeElement) {
            event.preventDefault();
         }
      });
   }

   ngAfterViewInit(): void {
      //fix a problem for IE
      //in IE if a select in form only has one option, it will Selected it by default
      //but we needn't.
      if(this.variableName == "" && this.variableList.length == 1) {
         this.vform.controls["name"].setValue(this.variableName);
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.value != null && this.value.indexOf("$(") == 0 &&
         this.value.lastIndexOf(")") == (this.value.length - 1))
      {
         this.variableName = this.value.substring(2, this.value.length - 1);
      }

      if(this.variablesFunction) {
         let obs = this.variablesFunction();

         if(obs === null) {
            this.variableList = null;
         }
         else {
            obs.subscribe((list) => {
               this.variableList = list;
            });
         }
      }
   }

   get useList(): boolean {
      return this._useList;
   }

   @Input() set useList(useList: boolean) {
      this._useList = useList;
      this.updateChoiceQuery.emit(useList);
   }

   variableNameChanged(value: string): void {
      this.variableName = value == undefined ? "" : value;
      this.value = "$(" + this.variableName + ")";
      this.valueChange.emit(this.value);
   }


   ngOnDestroy() {
      this.clickListener = null;
   }
}
