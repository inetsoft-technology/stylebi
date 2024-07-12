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
import { Component, EventEmitter, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { ColumnOptionDialogModel } from "../model/column-option-dialog-model";
import { IntegerEditorModel } from "../model/integer-editor-model";
import { DateEditorModel } from "../model/date-editor-model";
import { TextEditorModel } from "../model/text-editor-model";
import { FloatEditorModel } from "../model/float-editor-model";
import { ColumnOptionType } from "../model/column-option-type";
import { EditorModel } from "../model/editor-model";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "column-option-dialog",
   templateUrl: "column-option-dialog.component.html",
})
export class ColumnOptionDialog {
   _model: ColumnOptionDialogModel;
   runtimeId: string;
   inputsTypes: any[] = [
      { label: "_#(js:Text)", value: ColumnOptionType.TEXT },
      { label: "_#(js:ComboBox)", value: ColumnOptionType.COMBOBOX },
      { label: "_#(js:Date)", value: ColumnOptionType.DATE },
      { label: "_#(js:Integer)", value: ColumnOptionType.INTEGER },
      { label: "_#(js:Float)", value: ColumnOptionType.FLOAT },
      { label: "_#(js:Boolean)", value: ColumnOptionType.BOOLEAN }];
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   ColumnOptionType = ColumnOptionType;
   initEditor: EditorModel;
   form: UntypedFormGroup = new UntypedFormGroup({});
   formValid = () => this.form && this.form.valid;

   get model(): ColumnOptionDialogModel {
      return this._model;
   }

   set model(value: ColumnOptionDialogModel) {
      this._model = value;
      this.initEditor = Tool.clone(value.editor);
   }

   choose(editorName: string): void {
      this.model.inputControl = editorName;

      if(this.initEditor && editorName == this.initEditor.type) {
         this.model.editor = Tool.clone(this.initEditor);
      }
      else if(editorName === ColumnOptionType.DATE) {
         this.model.editor = <DateEditorModel> {
            minimum: "",
            maximum: "",
            errorMessage: "",
            type: ColumnOptionType.DATE
         };
      }
      else if(editorName === ColumnOptionType.TEXT) {
         this.model.editor = <TextEditorModel> {
            pattern: "",
            errorMessage: "",
            type: ColumnOptionType.TEXT
         };
      }
      else if(editorName === ColumnOptionType.INTEGER) {
         this.model.editor = <IntegerEditorModel> {
            minimum: null,
            maximum: null,
            errorMessage: "",
            type: ColumnOptionType.INTEGER
         };
      }
      else if(editorName === ColumnOptionType.FLOAT) {
         this.model.editor = <FloatEditorModel> {
            minimum: null,
            maximum: null,
            errorMessage: "",
            type: ColumnOptionType.FLOAT
         };
      }
      else if(editorName === ColumnOptionType.COMBOBOX) {
         this.model.editor = this.model.comboBoxBlankEditor;
      }
   }

   ok(): void {
      this.onCommit.emit(this.model);
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.model});
   }

   close(): void {
      this.onCancel.emit("cancel");
   }
}
