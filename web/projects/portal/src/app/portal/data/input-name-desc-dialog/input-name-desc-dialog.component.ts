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
import {
   Component,
   Input,
   Output,
   EventEmitter,
   SimpleChanges,
   ChangeDetectionStrategy,
   OnInit,
   OnChanges, ChangeDetectorRef, NgZone
} from "@angular/core";
import { UntypedFormControl, ValidatorFn } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../common/util/component-tool";

export interface NameDescResult {
   name: string;
   description: string;
}

export interface ValidatorMessageInfo {
   validatorName: string;
   message: string;
}
@Component({
   selector: "input-name-desc-dialog",
   templateUrl: "input-name-desc-dialog.component.html",
   styleUrls: [ "input-name-desc-dialog.component.scss" ],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class InputNameDescDialog implements OnChanges, OnInit {
   @Input() validators: ValidatorFn[] = [
      FormValidators.required
   ];
   @Input() validatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "_#(js:viewer.nameValid)"},
      {validatorName: "pattern", message: "_#(js:data.databases.enterValidName)"}
   ];
   @Input() namePattern: RegExp;
   @Input() title: string = "_#(js:Input)";
   @Input() label: string = "_#(js:Name)";
   @Input() value: string;
   @Input() descLabel: string = "_#(js:Description)";
   @Input() description: string = "";
   @Input() hasDuplicateCheck: (string) => Observable<boolean>;
   @Input() duplicateMessage: string = "_#(js:Duplicate Name)";
   @Input() helpLinkKey: string;
   @Output() onCommit = new EventEmitter<NameDescResult>();
   @Output() onCancel = new EventEmitter<string>();
   control: UntypedFormControl;

   formValid = () => !!this.control && !this.control.errors;
   constructor(private zone: NgZone,
               private modalService: NgbModal,
               private changeDetectorRef: ChangeDetectorRef)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      this.initFormControl();
   }

   ngOnInit() {
      this.initFormControl();
   }

   private initFormControl() {
      this.control = new UntypedFormControl(this.value, this.validators);
   }

   ok(): void {
      if(!!this.hasDuplicateCheck && this.value != this.control.value.trim()) {
         this.hasDuplicateCheck(this.control.value).subscribe(
            (duplicate: boolean) => {
               if(duplicate) {
                  this.zone.run(() => {
                     this.changeDetectorRef.detach();
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        this.duplicateMessage).then(() =>
                     {
                        this.changeDetectorRef.reattach();
                     });
                  });
               }
               else {
                  this.onCommit.emit({
                     name: this.control.value,
                     description: this.description
                  });
               }
            },
         );
      }
      else {
         this.onCommit.emit({
            name: this.control.value,
            description: this.description
         });
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   get errorMessage(): string {
      for(let msgInfo of this.validatorMessages) {
         if(this.control.errors && this.control.errors[msgInfo.validatorName]) {
            return msgInfo.message;
         }
      }

      return null;
   }
}
