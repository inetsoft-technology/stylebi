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
import { Injectable } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../common/util/component-tool";
import { FontService } from "../../../widget/services/font.service";
import { RichTextDialog } from "./rich-text-dialog.component";
import { RichTextService } from "./rich-text.service";

@Injectable()
export class CKEditorRichTextService extends RichTextService {
   constructor(private fontService: FontService, private modalService: NgbModal) {
      super();
   }

   showAnnotationDialog(onCommit: (content: string) => void, bgColor: string = null): Observable<RichTextDialog> {
      return this.fontService.getAllFonts().pipe(map((fonts) => {
         let modalOptions: NgbModalOptions = {
            backdrop: "static",
            size: "lg"
         };

         const dialog: RichTextDialog = ComponentTool.showDialog(this.modalService, RichTextDialog, onCommit, modalOptions);
         dialog.dialogTitle = "_#(js:Annotation)";
         dialog.fonts = fonts;
         dialog.bgColor = bgColor;
         return dialog;
      }));
   }
}
