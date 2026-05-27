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
import { Component, forwardRef, HostBinding, Input, OnInit } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from "@angular/forms";
import { FileData } from "../../../../../../../shared/util/model/file-data";
import { FileChooserComponent } from "../file-chooser/file-chooser.component";
import { MatDivider } from "@angular/material/divider";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton, MatButton } from "@angular/material/button";
import { MatList, MatListItem } from "@angular/material/list";
import { NgIf, NgFor } from "@angular/common";
import { MatCard, MatCardHeader, MatCardContent, MatCardActions } from "@angular/material/card";

@Component({
    selector: "em-multi-file-chooser",
    templateUrl: "./multi-file-chooser.component.html",
    styleUrls: ["./multi-file-chooser.component.scss"],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => MultiFileChooserComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [MatCard, NgIf, MatCardHeader, MatCardContent, MatList, NgFor, MatListItem, MatIconButton, MatIcon, MatDivider, MatCardActions, FileChooserComponent, FormsModule, MatButton]
})
export class MultiFileChooserComponent implements OnInit, ControlValueAccessor {
   @HostBinding("class") hostClass = "em-multi-file-chooser";
   @Input() header: string;
   @Input() accept: string;
   @Input() disabled = false;
   @Input() selectButtonLabel = "_#(js:Select)";

   get value(): FileData[] {
      return this._value;
   }

   set value(files: FileData[]) {
      this._value = files;
   }

   get selectedFiles(): FileData[] {
      return this._selectedFiles;
   }

   set selectedFiles(files: FileData[]) {
      this._selectedFiles = files;
      this.mergeFiles(files);
   }

   private _value: FileData[] = [];
   private _selectedFiles: FileData[] = [];
   private onChange = (files: FileData[]) => {};
   private onTouched = () => {};

   constructor() {
   }

   ngOnInit() {
   }

   writeValue(files: FileData[]): void {
      this.value = files;
      this.onChange(this.value);
   }

   registerOnChange(fn: any): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.disabled = isDisabled;
   }

   removeFile(file: FileData) {
      this.value = this.value.filter(f => f.name !== file.name);
      this.onChange(this.value);
   }

   private mergeFiles(files: FileData[]): void {
      if(files && files.length) {
         const newFiles =
            files.filter(f => !this.value.find(i => i.name === f.name));

         if(newFiles.length > 0) {
            this.value = this.value.concat(newFiles);
            this.onChange(this.value);
         }
      }
   }
}
