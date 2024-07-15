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
import { Component, forwardRef, Input, OnInit } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { FileData } from "../../../../../../../shared/util/model/file-data";

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
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "em-multi-file-chooser"
   }
})
export class MultiFileChooserComponent implements OnInit, ControlValueAccessor {
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
