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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { HighlightModel } from "./highlight-model";
import { FormValidators } from "../../../../../shared/util/form-validators";

const DEFAULT_HIGHLIGHT_PREFIX = "highlight";

@Component({
   selector: "add-highlight-dialog",
   templateUrl: "add-highlight-dialog.component.html",
   viewProviders: [FormValidators]
})
export class AddHighlightDialog implements OnInit {
   @Input() highlights: HighlightModel[] = [];
   @Input() renameIndex: number = -1;
   form: UntypedFormGroup;
   name: string = "";
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formValid = () => this.form && this.form.valid && !this.duplicateName();

   ngOnInit() {
      if(this.renameIndex == -1) {
         this.prepareDefaultName();
      }
      else {
         this.name = this.highlights[this.renameIndex].name;
      }

      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.name, [Validators.required,
            FormValidators.containsSpecialChars])
      });
   }

   duplicateName(): boolean {
      if(this.renameIndex == -1) {
         for(let x = 0; x < this.highlights.length; x++) {
            if(this.highlights[x].name == this.name) {
               return true;
            }
         }
      }
      else {
         for(let y = 0; y < this.highlights.length; y++) {
            if(y != this.renameIndex && this.highlights[y].name == this.name) {
               return true;
            }
         }
      }

      return false;
   }

   ok(): void {
      if (this.renameIndex != -1) {
         this.highlights[this.renameIndex].name = this.name;
      }

      this.onCommit.emit({
         "name": this.name,
         "renameIndex": this.renameIndex
      });
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   private prepareDefaultName(): void {
      let defaultNumber: number = 1;
      let defaultNumberArr: boolean[] = new Array(this.highlights.length);

      this.highlights.map(highlight => highlight.name)
         .forEach((name) => {
            let index = name.indexOf(DEFAULT_HIGHLIGHT_PREFIX);

            if(index < 0) {
               return;
            }

            let tail = name.substr(index + DEFAULT_HIGHLIGHT_PREFIX.length);
            let tailNum = !isNaN(+tail) ? parseInt(tail, 10) : NaN;

            if(!isNaN(tailNum) && tailNum > 0 && tailNum <= defaultNumberArr.length) {
               defaultNumberArr[tailNum - 1] = true;
            }
         });

      let nameIndex;

      for(nameIndex = 0; nameIndex < defaultNumberArr.length; nameIndex++) {
         if(!defaultNumberArr[nameIndex]) {
            defaultNumber = nameIndex + 1;
            break;
         }
      }

      if(nameIndex == defaultNumberArr.length) {
         defaultNumber = nameIndex + 1;
      }

      this.name = DEFAULT_HIGHLIGHT_PREFIX + defaultNumber;
   }
}
