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
   Component, Output, EventEmitter, Input, OnInit
} from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { ImagePreviewPaneModel } from "../image-editor/image-preview-pane-model";

@Component({
   selector: "edit-image-dialog",
   templateUrl: "edit-image-dialog.component.html"
})
export class EditImageDialog implements OnInit {
   @Input() model: ImagePreviewPaneModel;
   @Input() runtimeId: string;
   @Input() animateGif: boolean = true;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;

   ngOnInit() {
      this.form = new UntypedFormGroup({});
   }

   ok(): void {
      this.onCommit.emit("ok");
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
