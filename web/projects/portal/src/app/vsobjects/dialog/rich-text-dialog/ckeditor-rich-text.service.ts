/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { combineLatest, Observable } from "rxjs";
import { map } from "rxjs/operators";
import { CkeditorLanguageService } from "../../../../../../shared/ckeditor/ckeditor-language.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { FontService } from "../../../widget/services/font.service";
import { RichTextDialog } from "./rich-text-dialog.component";
import { RichTextService } from "./rich-text.service";

@Injectable()
export class CKEditorRichTextService extends RichTextService {
   constructor(private fontService: FontService, private languageService: CkeditorLanguageService,
               private modalService: NgbModal)
   {
      super();
   }

   showAnnotationDialog(onCommit: (content: string) => void, bgColor: string = null): Observable<RichTextDialog> {
      const fonts$ = this.fontService.getAllFonts();
      const language$ = this.languageService.getLanguage();
      return combineLatest([fonts$, language$]).pipe(map(([fonts, language]) => {
         let modalOptions: NgbModalOptions = {
            backdrop: "static",
            size: "lg"
         };

         const dialog: RichTextDialog = ComponentTool.showDialog(this.modalService, RichTextDialog, onCommit, modalOptions);
         dialog.dialogTitle = "_#(js:Annotation)";
         dialog.fonts = fonts;
         dialog.language = language;
         dialog.bgColor = bgColor;
         return dialog;
      }));
   }
}
