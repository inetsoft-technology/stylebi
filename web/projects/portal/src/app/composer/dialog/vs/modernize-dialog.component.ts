/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { FormControl, ReactiveFormsModule, Validators } from "@angular/forms";
import { EMPTY, Subject, catchError, debounceTime, map, switchMap } from "rxjs";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { InputTrimDirective } from "../../../widget/directive/input-trim.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ModelService } from "../../../widget/services/model.service";
import { ModernizeDialogModel, ModernizeDialogResult } from "../../data/vs/modernize-dialog-model";

const CHECK_NAME_URI = "../api/composer/vs/modernize-dialog-model/check-name/";

interface NameCheck {
   exists: boolean;
   permissionDenied: string;
   invalidName: string;
}

@Component({
   selector: "modernize-dialog",
   templateUrl: "modernize-dialog.component.html",
   styleUrls: ["./modernize-dialog.component.scss"],
   imports: [ModalHeaderComponent, ReactiveFormsModule, InputTrimDirective, DefaultFocusDirective]
})
export class ModernizeDialogComponent implements OnInit {
   @Input() model: ModernizeDialogModel;
   @Input() runtimeId: string;
   @Input() modified: boolean = false;
   @Output() onCommit = new EventEmitter<ModernizeDialogResult>();
   @Output() onCancel = new EventEmitter<void>();

   choice: "keep" | "reset" = "keep";
   saveAs: FormControl<string>;
   submitted = false;
   private checkedName: string = null;
   private nameCheck: NameCheck = null;
   private nameRequests = new Subject<string>();
   private destroyRef = inject(DestroyRef);

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      this.saveAs = new FormControl<string>(this.model.suggestedName, [
         Validators.required,
         FormValidators.assetEntryBannedCharacters,
         FormValidators.assetNameStartWithCharDigit
      ]);

      if(!this.model.keepAvailable) {
         this.choice = "reset";
      }

      this.nameRequests.pipe(
         debounceTime(300),
         switchMap(name => {
            if(!name || this.saveAs.invalid) {
               return EMPTY;
            }

            return this.modelService.sendModel<NameCheck>(
               CHECK_NAME_URI + Tool.byteEncode(this.runtimeId), { name }).pipe(
                  map(res => ({ name, check: res.body })),
                  catchError(() => EMPTY));
         }),
         takeUntilDestroyed(this.destroyRef)
      ).subscribe(result => {
         this.checkedName = result.name;
         this.nameCheck = result.check;
      });

      this.saveAs.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
         .subscribe(() => this.requestCheck());

      if(this.choice === "reset") {
         this.requestCheck();
      }
   }

   get trimmedName(): string {
      return (this.saveAs.value || "").trim();
   }

   selectChoice(choice: "keep" | "reset"): void {
      this.choice = choice;

      if(choice === "reset") {
         this.requestCheck();
      }
   }

   private requestCheck(): void {
      this.checkedName = null;
      this.nameCheck = null;

      if(this.choice === "reset" && !this.model.neverSaved) {
         this.nameRequests.next(this.trimmedName);
      }
   }

   private get nameChecked(): boolean {
      return !!this.nameCheck && this.checkedName === this.trimmedName;
   }

   get permissionDenied(): string {
      return this.nameChecked ? this.nameCheck.permissionDenied : null;
   }

   get invalidName(): string {
      return this.nameChecked ? this.nameCheck.invalidName : null;
   }

   get nameExists(): boolean {
      return this.nameChecked && this.nameCheck.exists;
   }

   get nameInvalid(): boolean {
      return this.saveAs.invalid || this.nameExists || !!this.permissionDenied || !!this.invalidName;
   }

   get canCommit(): boolean {
      if(this.submitted) {
         return false;
      }

      if(this.choice === "keep") {
         return this.model.keepAvailable;
      }

      return !this.model.neverSaved && this.saveAs.valid && this.nameChecked && !this.nameInvalid;
   }

   ok(): void {
      if(!this.canCommit) {
         return;
      }

      this.submitted = true;

      if(this.choice === "keep") {
         this.onCommit.emit({ choice: "keep" });
      }
      else {
         this.onCommit.emit({ choice: "copy", name: this.trimmedName });
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }
}
