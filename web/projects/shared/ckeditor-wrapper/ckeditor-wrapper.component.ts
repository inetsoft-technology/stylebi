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
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from "@angular/forms";
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


// @ts-ignore
import af from "ckeditor5/translations/af.js";
// @ts-ignore
import ar from "ckeditor5/translations/ar.js";
// @ts-ignore
import ast from "ckeditor5/translations/ast.js";
// @ts-ignore
import az from "ckeditor5/translations/az.js";
// @ts-ignore
import bg from "ckeditor5/translations/bg.js";
// @ts-ignore
import bs from "ckeditor5/translations/bs.js";
// @ts-ignore
import ca from "ckeditor5/translations/ca.js";
// @ts-ignore
import cs from "ckeditor5/translations/cs.js";
// @ts-ignore
import da from "ckeditor5/translations/da.js";
// @ts-ignore
import de from "ckeditor5/translations/de.js";
// @ts-ignore
import dech from "ckeditor5/translations/de-ch.js";
// @ts-ignore
import el from "ckeditor5/translations/el.js";
// @ts-ignore
import enau from "ckeditor5/translations/en-au.js";
// @ts-ignore
import engb from "ckeditor5/translations/en-gb.js";
// @ts-ignore
import eo from "ckeditor5/translations/eo.js";
// @ts-ignore
import es from "ckeditor5/translations/es.js";
// @ts-ignore
import et from "ckeditor5/translations/et.js";
// @ts-ignore
import eu from "ckeditor5/translations/eu.js";
// @ts-ignore
import fa from "ckeditor5/translations/fa.js";
// @ts-ignore
import fi from "ckeditor5/translations/fi.js";
// @ts-ignore
import fr from "ckeditor5/translations/fr.js";
// @ts-ignore
import gl from "ckeditor5/translations/gl.js";
// @ts-ignore
import gu from "ckeditor5/translations/gu.js";
// @ts-ignore
import he from "ckeditor5/translations/he.js";
// @ts-ignore
import hi from "ckeditor5/translations/hi.js";
// @ts-ignore
import hr from "ckeditor5/translations/hr.js";
// @ts-ignore
import hu from "ckeditor5/translations/hu.js";
// @ts-ignore
import id from "ckeditor5/translations/id.js";
// @ts-ignore
import it from "ckeditor5/translations/it.js";
// @ts-ignore
import ja from "ckeditor5/translations/ja.js";
// @ts-ignore
import jv from "ckeditor5/translations/jv.js";
// @ts-ignore
import kk from "ckeditor5/translations/kk.js";
// @ts-ignore
import km from "ckeditor5/translations/km.js";
// @ts-ignore
import kn from "ckeditor5/translations/kn.js";
// @ts-ignore
import ko from "ckeditor5/translations/ko.js";
// @ts-ignore
import ku from "ckeditor5/translations/ku.js";
// @ts-ignore
import lt from "ckeditor5/translations/lt.js";
// @ts-ignore
import lv from "ckeditor5/translations/lv.js";
// @ts-ignore
import ms from "ckeditor5/translations/ms.js";
// @ts-ignore
import nb from "ckeditor5/translations/nb.js";
// @ts-ignore
import ne from "ckeditor5/translations/ne.js";
// @ts-ignore
import nl from "ckeditor5/translations/nl.js";
// @ts-ignore
import no from "ckeditor5/translations/no.js";
// @ts-ignore
import oc from "ckeditor5/translations/oc.js";
// @ts-ignore
import pl from "ckeditor5/translations/pl.js";
// @ts-ignore
import pt from "ckeditor5/translations/pt.js";
// @ts-ignore
import ptbr from "ckeditor5/translations/pt-br.js";
// @ts-ignore
import ro from "ckeditor5/translations/ro.js";
// @ts-ignore
import ru from "ckeditor5/translations/ru.js";
// @ts-ignore
import si from "ckeditor5/translations/si.js";
// @ts-ignore
import sk from "ckeditor5/translations/sk.js";
// @ts-ignore
import sl from "ckeditor5/translations/sl.js";
// @ts-ignore
import sq from "ckeditor5/translations/sq.js";
// @ts-ignore
import sr from "ckeditor5/translations/sr.js";
// @ts-ignore
import srlatn from "ckeditor5/translations/sr-latn.js";
// @ts-ignore
import sv from "ckeditor5/translations/sv.js";
// @ts-ignore
import th from "ckeditor5/translations/th.js";
// @ts-ignore
import tk from "ckeditor5/translations/tk.js";
// @ts-ignore
import tr from "ckeditor5/translations/tr.js";
// @ts-ignore
import tt from "ckeditor5/translations/tt.js";
// @ts-ignore
import ug from "ckeditor5/translations/ug.js";
// @ts-ignore
import uk from "ckeditor5/translations/uk.js";
// @ts-ignore
import ur from "ckeditor5/translations/ur.js";
// @ts-ignore
import uz from "ckeditor5/translations/uz.js";
// @ts-ignore
import vi from "ckeditor5/translations/vi.js";
// @ts-ignore
import zh from "ckeditor5/translations/zh.js";
// @ts-ignore
import zhcn from "ckeditor5/translations/zh-cn.js";
import { CKEditorModule } from "@ckeditor/ckeditor5-angular";
import { NgIf } from "@angular/common";

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
    ],
    standalone: true,
    imports: [NgIf, CKEditorModule, FormsModule]
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
   private editorInstance: ClassicEditor;

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

      this.config.translations = [
         af, ar, ast, az, bg, bs, ca, cs, da, de, dech, el, enau, engb,
         eo, es, et, eu, fa, fi, fr, gl, gu, he, hi, hr, hu, id, it,
         ja, jv, kk, km, kn, ko, ku, lt, lv, ms, nb, ne, nl, no, oc,
         pl, pt, ptbr, ro, ru, si, sk, sl, sq, sr, srlatn, sv, th, tk,
         tr, tt, ug, uk, ur, uz, vi, zh, zhcn
      ];

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
      if((this._content ?? "") !== value) {
         this._content = value;
         this.onChange(this._content);
      }
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

   onEditorReady(editor: ClassicEditor) {
      this.editorInstance = editor;
      this.ready.emit(editor);
   }

   handleEnter(event: KeyboardEvent): void {
      if(event.key === "Enter") {
         event.preventDefault();

         if(this.editorInstance) {
            this.editorInstance.model.change((writer) => {
               this.editorInstance.execute('enter');
            });
         }
      }
   }

}