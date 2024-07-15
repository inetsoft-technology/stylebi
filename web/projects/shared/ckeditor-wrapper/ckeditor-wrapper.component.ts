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
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   EventEmitter,
   forwardRef,
   Input,
   OnInit,
   Output
} from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import {
   Alignment,
   Autosave,
   Base64UploadAdapter,
   Bold,
   ClassicEditor,
   EditorConfig,
   Essentials,
   FontBackgroundColor,
   FontColor,
   FontFamily,
   FontSize,
   Heading,
   ImageBlock,
   ImageInsert,
   ImageInsertViaUrl,
   ImageResize,
   ImageToolbar,
   ImageUpload,
   Indent,
   Italic,
   Link,
   List,
   Paragraph,
   Strikethrough,
   Subscript,
   Superscript,
   TextPartLanguage,
   Underline
} from "ckeditor5";

@Component({
   selector: "ckeditor-wrapper",
   templateUrl: "./ckeditor-wrapper.component.html",
   styleUrls: ["./ckeditor-wrapper.component.scss"],
   providers: [
      {
         provide: NG_VALUE_ACCESSOR,
         useExisting: forwardRef(() => CkeditorWrapperComponent),
         multi: true
      }
   ]
})
export class CkeditorWrapperComponent implements OnInit, AfterViewInit, ControlValueAccessor {
   @Input() advanced = false;
   @Input() fonts: string[];
   @Input() language: string;
   @Output() ready = new EventEmitter<ClassicEditor>();
   isLayoutReady = false;
   Editor = ClassicEditor;
   config: EditorConfig = {};
   disabled = false;
   private _content: string;

   onChange = (content: string) => {};
   onTouched = () => {};

   get content(): string {
      return this._content;
   }

   set content(content: string) {
      this.writeValue(content);
   }

   constructor(private changeDetector: ChangeDetectorRef) {
   }

   ngOnInit(): void {
   }

   ngAfterViewInit(): void {
      if(this.advanced) {
         this.config = {
            toolbar: {
               items: [
                  "textPartLanguage", "|",
                  "heading", "|",
                  "bold", "italic", "underline", "strikethrough", "|",
                  "bulletedList", "numberedList", "|",
                  "superscript", "subscript", "|",
                  "outdent", "indent", "|",
                  "fontFamily", "fontSize", "fontColor", "fontBackgroundColor", "|",
                  "alignment", "|",
                  "link", "insertImage"
               ],
               shouldNotGroupWhenFull: false
            },
            plugins: [
               Alignment, Autosave, Base64UploadAdapter, Bold, Essentials, FontBackgroundColor, FontColor,
               FontFamily, FontSize, Heading, ImageBlock, ImageInsert, ImageInsertViaUrl,
               ImageResize, ImageToolbar, ImageUpload, Indent, Italic, Link, List,
               Paragraph, Strikethrough, Subscript, Superscript, TextPartLanguage, Underline
            ],
            fontFamily: {
               supportAllValues: true
            },
            fontSize: {
               options: [ "default", 9, 10, 12, 14, 16, 18, 20, 24 ],
               supportAllValues: true
            },
            heading: {
               options: [
                  { model: "paragraph", title: "Paragraph", class: "ck-heading_paragraph" },
                  { model: "heading1", view: "h1", title: "Heading 1", class: "ck-heading_heading1" },
                  { model: "heading2", view: "h2", title: "Heading 2", class: "ck-heading_heading2" },
                  { model: "heading3", view: "h3", title: "Heading 3", class: "ck-heading_heading3" },
                  { model: "heading4", view: "h4", title: "Heading 4", class: "ck-heading_heading4" },
                  { model: "heading5", view: "h5", title: "Heading 5", class: "ck-heading_heading5" },
                  { model: "heading6", view: "h6", title: "Heading 6", class: "ck-heading_heading6" }
               ]
            },
            image: {
               toolbar: [ "imageTextAlternative" ]
            },
            link: {
               addTargetToExternalLinks: true,
               defaultProtocol: "https://",
               decorators: {
                  toggleDownloadable: {
                     mode: "manual",
                     label: "Downloadable",
                     attributes: {
                        download: "file"
                     }
                  }
               }
            }
         };
      }
      else {
         this.config = {
            toolbar: {
               items: [
                  "textPartLanguage", "|",
                  "heading", "|",
                  "bold", "italic", "underline", "strikethrough", "|",
                  "superscript", "subscript", "|",
                  "fontFamily", "fontSize", "fontColor", "fontBackgroundColor", "|",
                  "alignment"
               ],
               shouldNotGroupWhenFull: false
            },
            plugins: [
               Alignment, Autosave, Bold, Essentials, FontBackgroundColor, FontColor, FontFamily, FontSize,
               Heading, Italic, Paragraph, Strikethrough, Subscript, Superscript, TextPartLanguage,
               Underline
            ],
            fontFamily: {
               supportAllValues: true
            },
            fontSize: {
               options: [ "default", 9, 10, 12, 14, 16, 18, 20, 24 ],
               supportAllValues: true
            },
            heading: {
               options: [
                  { model: "paragraph", title: "Paragraph", class: "ck-heading_paragraph" },
                  { model: "heading1", view: "h1", title: "Heading 1", class: "ck-heading_heading1" },
                  { model: "heading2", view: "h2", title: "Heading 2", class: "ck-heading_heading2" },
                  { model: "heading3", view: "h3", title: "Heading 3", class: "ck-heading_heading3" },
                  { model: "heading4", view: "h4", title: "Heading 4", class: "ck-heading_heading4" },
                  { model: "heading5", view: "h5", title: "Heading 5", class: "ck-heading_heading5" },
                  { model: "heading6", view: "h6", title: "Heading 6", class: "ck-heading_heading6" }
               ]
            }
         };
      }

      if(!!this.fonts) {
         this.config.fontFamily = {
            options: ["default"].concat(this.fonts),
            supportAllValues: false
         }
      }

      if(!!this.language) {
         this.config.language = this.language;
      }

      this.isLayoutReady = true;
      this.changeDetector.detectChanges();
   }

   writeValue(value: string) {
      this._content = value;
      this.onChange(this._content);
   }

   registerOnChange(fn: (value: string) => void): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: () => void): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.disabled = isDisabled;
   }
}