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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { ClassicEditor } from "ckeditor5";
import { GuiTool } from "../../../common/util/gui-tool";

@Component({
   selector: "rich-text-dialog",
   templateUrl: "./rich-text-dialog.component.html",
   styleUrls: ["./rich-text-dialog.component.scss"]
})
export class RichTextDialog implements OnInit {
   @Input() dialogTitle: string;
   @Input() fonts: string[] = [];
   @Input() language: string;
   @Input() bgColor: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   @Input()
   get initialContent(): string {
      return this._initialContent;
   }

   set initialContent(value: string) {
      value = value || "";
      this._initialContent = value;
   }

   isIE = GuiTool.isIE();
   content = "";
   private _initialContent: string;

   constructor() {
   }

   ngOnInit() {
      if(this._initialContent) {
         this.content = this._initialContent;
      }
   }

   onEditorReady(editor: ClassicEditor): void {
      if(!this._initialContent) {
         // make sure the default color is set to black, because the default background is white
         editor.execute("fontColor", { value: "hsl(0, 0%, 0%)" });
      }
   }

   ok() {
      this.onCommit.emit(this.content);
   }

   cancel() {
      this.onCancel.emit();
   }
}
