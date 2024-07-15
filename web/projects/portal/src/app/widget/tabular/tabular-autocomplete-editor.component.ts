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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { NgbTypeahead } from "@ng-bootstrap/ng-bootstrap";
import { merge, Observable, Subject } from "rxjs";
import { debounceTime, distinctUntilChanged, filter, map } from "rxjs/operators";

@Component({
   selector: "tabular-autocomplete-editor",
   templateUrl: "tabular-autocomplete-editor.component.html",
   styleUrls: ["tabular-autocomplete-editor.component.scss"]
})
export class TabularAutocompleteEditor implements OnInit, OnChanges {
   @Input() value: string;
   @Input() tags: Array<string>;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   valueControl: UntypedFormControl;

   @ViewChild("typeahead", {static: true}) typeahead: NgbTypeahead;
   focus = new Subject<string>();
   click = new Subject<string>();

   ngOnInit(): void {
      if(this.required) {
         this.valueControl = new UntypedFormControl(this.value, Validators.required);
      }
      else {
         this.valueControl = new UntypedFormControl();
      }

      if(!this.enabled) {
         this.valueControl.disable();
      }

      this.valueControl.valueChanges.pipe(debounceTime(1000))
         .subscribe((newValue: string) => {
            this.valueChanged();
         });
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.valueControl) {
         if(changes["enabled"]) {
            if(this.enabled) {
               this.valueControl.enable();
            }
            else {
               this.valueControl.disable();
            }
         }
      }
   }

   search = (text: Observable<string>) => {
      const debouncedText = text.pipe(debounceTime(200), distinctUntilChanged());
      const clicksWithClosedPopup = this.click.pipe(filter(() => !this.typeahead.isPopupOpen()));
      const inputFocus = this.focus;

      return merge(debouncedText, inputFocus, clicksWithClosedPopup).pipe(
         map(term => (term === "" ? this.tags
            : this.tags.filter(v => v.toLowerCase().indexOf(term.toLowerCase()) > -1)))
      );
   };

   valueChanged() {
      this.validChange.emit(this.valueControl.valid || !this.enabled);

      if(this.valueControl.dirty) {
         this.valueChange.emit(this.value);
      }
   }
}
