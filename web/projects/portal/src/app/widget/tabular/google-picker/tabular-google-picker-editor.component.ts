/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
   SimpleChanges
} from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { GooglePicker } from "../../../common/data/tabular/google-picker";
import { GooglePickerService } from "./google-picker.service";

@Component({
   selector: "tabular-google-picker-editor",
   templateUrl: "tabular-google-picker-editor.component.html",
   styleUrls: ["tabular-google-picker-editor.component.scss"]
})
export class TabularGooglePickerEditor implements OnInit, OnChanges {
   @Input() value: GooglePicker;
   @Input() editorPropertyNames: string[];
   @Input() editorPropertyValues: string[];
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange = new EventEmitter<GooglePicker>();
   @Output() validChange = new EventEmitter<boolean>();
   valueControl: UntypedFormControl;

   constructor(private googlePickerService: GooglePickerService) {
   }

   ngOnInit(): void {
      if(this.required) {
         this.valueControl = new UntypedFormControl(this.value?.selectedFile?.name, Validators.required);
      }
      else {
         this.valueControl = new UntypedFormControl();
      }

      if(!this.enabled) {
         this.valueControl.disable();
      }
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

   valueChanged() {
      this.validChange.emit(this.valueControl.valid || !this.enabled);
      this.valueChange.emit(this.value);
   }

   openPicker() {
      this.googlePickerService.openPicker(this.value.oauthToken, (result) => {
         this.value.selectedFile = result;
         this.valueChanged();
      });
   }
}
