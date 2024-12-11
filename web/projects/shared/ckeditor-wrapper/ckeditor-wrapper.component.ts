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


// @ts-expect-error
import af from "ckeditor5/translations/af.js";
// @ts-expect-error
import ar from "ckeditor5/translations/ar.js";
// @ts-expect-error
import ast from "ckeditor5/translations/ast.js";
// @ts-expect-error
import az from "ckeditor5/translations/az.js";
// @ts-expect-error
import bg from "ckeditor5/translations/bg.js";
// @ts-expect-error
import bs from "ckeditor5/translations/bs.js";
// @ts-expect-error
import ca from "ckeditor5/translations/ca.js";
// @ts-expect-error
import cs from "ckeditor5/translations/cs.js";
// @ts-expect-error
import da from "ckeditor5/translations/da.js";
// @ts-expect-error
import de from "ckeditor5/translations/de.js";
// @ts-expect-error
import dech from "ckeditor5/translations/de-ch.js";
// @ts-expect-error
import el from "ckeditor5/translations/el.js";
// @ts-expect-error
import enau from "ckeditor5/translations/en-au.js";
// @ts-expect-error
import engb from "ckeditor5/translations/en-gb.js";
// @ts-expect-error
import eo from "ckeditor5/translations/eo.js";
// @ts-expect-error
import es from "ckeditor5/translations/es.js";
// @ts-expect-error
import et from "ckeditor5/translations/et.js";
// @ts-expect-error
import eu from "ckeditor5/translations/eu.js";
// @ts-expect-error
import fa from "ckeditor5/translations/fa.js";
// @ts-expect-error
import fi from "ckeditor5/translations/fi.js";
// @ts-expect-error
import fr from "ckeditor5/translations/fr.js";
// @ts-expect-error
import gl from "ckeditor5/translations/gl.js";
// @ts-expect-error
import gu from "ckeditor5/translations/gu.js";
// @ts-expect-error
import he from "ckeditor5/translations/he.js";
// @ts-expect-error
import hi from "ckeditor5/translations/hi.js";
// @ts-expect-error
import hr from "ckeditor5/translations/hr.js";
// @ts-expect-error
import hu from "ckeditor5/translations/hu.js";
// @ts-expect-error
import id from "ckeditor5/translations/id.js";
// @ts-expect-error
import it from "ckeditor5/translations/it.js";
// @ts-expect-error
import ja from "ckeditor5/translations/ja.js";
// @ts-expect-error
import jv from "ckeditor5/translations/jv.js";
// @ts-expect-error
import kk from "ckeditor5/translations/kk.js";
// @ts-expect-error
import km from "ckeditor5/translations/km.js";
// @ts-expect-error
import kn from "ckeditor5/translations/kn.js";
// @ts-expect-error
import ko from "ckeditor5/translations/ko.js";
// @ts-expect-error
import ku from "ckeditor5/translations/ku.js";
// @ts-expect-error
import lt from "ckeditor5/translations/lt.js";
// @ts-expect-error
import lv from "ckeditor5/translations/lv.js";
// @ts-expect-error
import ms from "ckeditor5/translations/ms.js";
// @ts-expect-error
import nb from "ckeditor5/translations/nb.js";
// @ts-expect-error
import ne from "ckeditor5/translations/ne.js";
// @ts-expect-error
import nl from "ckeditor5/translations/nl.js";
// @ts-expect-error
import no from "ckeditor5/translations/no.js";
// @ts-expect-error
import oc from "ckeditor5/translations/oc.js";
// @ts-expect-error
import pl from "ckeditor5/translations/pl.js";
// @ts-expect-error
import pt from "ckeditor5/translations/pt.js";
// @ts-expect-error
import ptbr from "ckeditor5/translations/pt-br.js";
// @ts-expect-error
import ro from "ckeditor5/translations/ro.js";
// @ts-expect-error
import ru from "ckeditor5/translations/ru.js";
// @ts-expect-error
import si from "ckeditor5/translations/si.js";
// @ts-expect-error
import sk from "ckeditor5/translations/sk.js";
// @ts-expect-error
import sl from "ckeditor5/translations/sl.js";
// @ts-expect-error
import sq from "ckeditor5/translations/sq.js";
// @ts-expect-error
import sr from "ckeditor5/translations/sr.js";
// @ts-expect-error
import srlatn from "ckeditor5/translations/sr-latn.js";
// @ts-expect-error
import sv from "ckeditor5/translations/sv.js";
// @ts-expect-error
import th from "ckeditor5/translations/th.js";
// @ts-expect-error
import tk from "ckeditor5/translations/tk.js";
// @ts-expect-error
import tr from "ckeditor5/translations/tr.js";
// @ts-expect-error
import tt from "ckeditor5/translations/tt.js";
// @ts-expect-error
import ug from "ckeditor5/translations/ug.js";
// @ts-expect-error
import uk from "ckeditor5/translations/uk.js";
// @ts-expect-error
import ur from "ckeditor5/translations/ur.js";
// @ts-expect-error
import uz from "ckeditor5/translations/uz.js";
// @ts-expect-error
import vi from "ckeditor5/translations/vi.js";
// @ts-expect-error
import zh from "ckeditor5/translations/zh.js";
// @ts-expect-error
import zhcn from "ckeditor5/translations/zh-cn.js";

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