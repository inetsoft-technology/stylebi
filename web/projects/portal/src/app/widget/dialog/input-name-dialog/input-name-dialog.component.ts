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
   Input,
   Output,
   EventEmitter,
   SimpleChanges,
   ChangeDetectionStrategy,
   ViewChild,
   ElementRef,
   AfterViewInit,
   NgZone,
   ChangeDetectorRef,
   OnInit,
   OnChanges
} from "@angular/core";
import { UntypedFormControl, ValidatorFn } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";

export interface ValidatorMessageInfo {
   validatorName: string;
   message: string;
}
@Component({
   selector: "input-name-dialog",
   templateUrl: "input-name-dialog.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class InputNameDialog implements OnChanges, OnInit, AfterViewInit {
   @Input() validators: ValidatorFn[] = [
      FormValidators.required,
      FormValidators.alphanumericalCharacters,
   ];
   @Input() validatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "Name is required"},
      {validatorName: "alphanumericalCharacters", message: "Name must contain alphanumeric characters"}
   ];
   @Input() title: string = "Input";
   @Input() label: string = "Name";
   @Input() value: string;
   @Input() hasDuplicateCheck: (string) => Observable<boolean>;
   @Input() duplicateMessage: string = "_#(js:Duplicate Name)";
   @Input() helpLinkKey: string = "GroupingCrosstabHeaders";
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild("input") input: ElementRef;

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

   ngAfterViewInit() {
      this.input.nativeElement.focus();
      this.input.nativeElement.select();
   }

   private initFormControl() {
      this.control = new UntypedFormControl(this.value, this.validators);
   }

   ok(): void {
      let newName: string = this.control.value;
      // eslint-disable-next-line no-control-regex
      newName = newName.replace(/[\x00-\x1F\x7F]/g, "");

      if(!!this.hasDuplicateCheck && this.value != this.control.value.trim()) {
         this.hasDuplicateCheck(newName).subscribe(
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
                  this.onCommit.emit(newName);
               }
            },
         );
      }
      else {
         this.onCommit.emit(newName);
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
