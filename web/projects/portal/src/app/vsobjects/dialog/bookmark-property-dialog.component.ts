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
import {
   Component,
   Output,
   OnInit,
   EventEmitter,
   Input
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { VSBookmarkInfoModel, VSBookmarkType } from "../model/vs-bookmark-info-model";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
  selector: "bookmark-property-dialog",
  templateUrl: "bookmark-property-dialog.component.html",
})
export class BookmarkPropertyDialog implements OnInit {
   @Input() model: VSBookmarkInfoModel;
   @Input() runtimeId: string;
   @Input() shareToAllDisabled: boolean;
   @Output() onCommit: EventEmitter<VSBookmarkInfoModel> = new EventEmitter<VSBookmarkInfoModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Input() assetId: string;
   @Input() isSharedBookmarkPermitted: boolean = true;
   form: UntypedFormGroup;
   sharedOption: VSBookmarkType = VSBookmarkType.ALLSHARE;
   VSBookmarkType = VSBookmarkType;
   formValid = () => this.model && this.form && this.form.valid;

   ngOnInit(): void {
      if(this.model.type == VSBookmarkType.GROUPSHARE || this.shareToAllDisabled) {
         this.sharedOption = VSBookmarkType.GROUPSHARE;
      }

      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.bookmarkSpecialCharacters,
         ])
      });
   }

   selectType(type: VSBookmarkType): void {
      this.model.type = type;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.model.name = this.model.name.trim();
      this.onCommit.emit(this.model);
   }

   isGlobalScope(): boolean {
      return (!!this.assetId && this.assetId.startsWith("1^")) || this.assetId === "";
   }
}
