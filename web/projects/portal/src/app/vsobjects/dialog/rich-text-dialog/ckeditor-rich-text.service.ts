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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, combineLatest, Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../common/util/component-tool";
import { CurrentUser } from "../../../portal/current-user";
import { FontService } from "../../../widget/services/font.service";
import { RichTextDialog } from "./rich-text-dialog.component";
import { RichTextService } from "./rich-text.service";

@Injectable()
export class CKEditorRichTextService extends RichTextService {
   private readonly languages = [
      "af", "ar", "ast", "az", "bg", "bs", "ca", "cs", "da", "de", "de-ch", "el", "en-au", "en-gb",
      "eo", "es", "et", "eu", "fa", "fi", "fr", "gl", "gu", "he", "hi", "hr", "hu", "id", "it",
      "ja", "jv", "kk", "km", "kn", "ko", "ku", "lt", "lv", "ms", "nb", "ne", "nl", "no", "oc",
      "pl", "pt", "pt-br", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sr-latn", "sv", "th", "tk",
      "tr", "tt", "ug", "uk", "ur", "uz", "vi", "zh", "zh-cn"
   ];

   private language$: BehaviorSubject<string> = new BehaviorSubject<string>(null);

   constructor(private fontService: FontService, private modalService: NgbModal, http: HttpClient) {
      super();

      http.get<CurrentUser>("../api/portal/get-current-user")
         .pipe(map(user => this.getUserLanguage(user)))
         .subscribe(language => this.language$.next(language));
   }

   showAnnotationDialog(onCommit: (content: string) => void, bgColor: string = null): Observable<RichTextDialog> {
      const fonts$ = this.fontService.getAllFonts();
      return combineLatest([fonts$, this.language$]).pipe(map(([fonts, language]) => {
         let modalOptions: NgbModalOptions = {
            backdrop: "static",
            size: "lg"
         };

         const dialog: RichTextDialog = ComponentTool.showDialog(this.modalService, RichTextDialog, onCommit, modalOptions);
         dialog.dialogTitle = "_#(js:Annotation)";
         dialog.fonts = fonts;
         dialog.bgColor = bgColor;
         dialog.language = language;
         return dialog;
      }));
   }

   private getUserLanguage(user: CurrentUser): string {
      const { localeLanguage, localeCountry } = user;
      let language: string;

      if(!!localeLanguage) {
         if(!!localeCountry) {
            const test = `${localeLanguage.toLowerCase()}-${localeCountry.toLowerCase()}`;
            language = this.languages.find(l => l === test);
         }

         if(!language) {
            const test = localeLanguage.toLowerCase();
            language = this.languages.find(l => l === test);
         }
      }

      return language;
   }
}
