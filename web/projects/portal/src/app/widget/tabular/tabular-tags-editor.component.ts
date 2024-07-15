/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges, ViewChild, ElementRef
} from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "tabular-tags-editor",
   templateUrl: "tabular-tags-editor.component.html",
   styleUrls: ["tabular-tags-editor.component.scss"]
})
export class TabularTagsEditor implements OnInit, OnChanges {
   @Input() value: string;
   @Input() tags: Array<string>;
   @Input() labels: Array<string>;
   @Input() enabled = true;
   @Input() required = false;
   @Input() editorPropertyNames: string[];
   @Input() editorPropertyValues: string[];
   @Output() valueChange = new EventEmitter<string>();
   @Output() validChange = new EventEmitter<boolean>();
   @ViewChild("searchControl") searchControl: ElementRef;
   editable = false;
   valueControl: UntypedFormControl;
   searching = false;
   searchTerm: string;
   searchResults: {tag: string, label: string}[] = [];

   ngOnInit(): void {
      if((this.tags == null || this.tags.length == 0) &&
         (this.labels == null || this.labels.length == 0))
      {
         this.editable = true;
      }

      if(this.required) {
         this.valueControl = new UntypedFormControl(this.value, Validators.required);
      }
      else {
         this.valueControl = new UntypedFormControl();
      }

      if(!this.enabled) {
         this.valueControl.disable();
      }

      this.valueControl.valueChanges
         .subscribe((newValue: string) => {
            this.validChange.emit(this.valueControl.valid || !this.enabled);

            if(this.valueControl.dirty) {
               this.valueChange.emit(newValue);
            }
         });

      if(!this.editable) {
         if(this.labels == null || this.labels.length != this.tags.length) {
            this.labels = this.tags;
         }

         let index = this.tags.indexOf(this.value);

         if(index < 0) {
            setTimeout(() => {
               this.value = this.tags[0];
               this.valueChange.emit(this.value);
            });
         }
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.valueControl) {
         if(changes["labels"] || changes["tags"]) {
            this.editable = (this.tags == null || this.tags.length == 0) &&
               (this.labels == null || this.labels.length == 0);

            if(!this.editable) {
               if(this.labels == null || this.labels.length != this.tags.length) {
                  this.labels = this.tags;
               }

               let index = this.tags.indexOf(this.value);

               if(index < 0) {
                  setTimeout(() => {
                     this.value = this.tags[0];
                     this.valueChange.emit(this.value);
                  });
               }
            }
         }

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

   get searchable(): boolean {
      if(this.editorPropertyNames && this.editorPropertyValues) {
         for(let i = 0; i < this.editorPropertyNames.length; i++) {
            if(this.editorPropertyNames[i] === "searchable") {
               return this.editorPropertyValues[i] === "true";
            }
         }
      }

      return false;
   }

   toggleSearch(): void {
      this.searching = !this.searching;

      if(this.searching) {
         setTimeout(() => {
            if(this.searchControl && this.searchControl.nativeElement) {
               this.searchControl.nativeElement.focus();
            }
         }, 0);
      }
   }

   onSearchChanged(): void {
      if(this.searchTerm) {
         const values = this.tags.map((tag, index) => ({ tag, label: this.labels[index] }));
         this.searchResults = Tool.findMatches(
            this.searchTerm, values, 10, 0.01, false, value => value.label,
            Tool.getSubstringMatchDistance);
      }
      else {
         this.searchResults = [];
      }
   }

   selectSearchResult(value: string): void {
      this.value = value;
      this.searching = false;
      this.valueChange.emit(value);
   }
}
