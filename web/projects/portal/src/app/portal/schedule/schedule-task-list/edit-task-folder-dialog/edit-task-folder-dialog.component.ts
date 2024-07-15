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
  EventEmitter,
  Input, OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  AfterViewInit,
  ViewChild,
  ElementRef
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EditTaskFolderDialogModel } from "../../../../../../../em/src/app/settings/schedule/model/edit-task-folder-dialog-model";
import { HttpClient } from "@angular/common/http";
import { Tool } from "../../../../../../../shared/util/tool";
import { CheckDuplicateResponse } from "../../../data/commands/check-duplicate-response";
import { FormValidators } from "../../../../../../../shared/util/form-validators";

const TASK_FOLDER_CHECK_DUPLICATE_URI: string = "../api/portal/schedule/rename/checkDuplicate";

@Component({
  selector: "c-edit-task-folder-dialog",
  templateUrl: "./edit-task-folder-dialog.component.html",
  styleUrls: ["./edit-task-folder-dialog.component.scss"]
})
export class EditTaskFolderDialog implements OnInit, OnChanges, AfterViewInit {
  @Input() model: EditTaskFolderDialogModel;
  @Output() onCommit = new EventEmitter<EditTaskFolderDialogModel>();
  @Output() onCancel = new EventEmitter<string>();
  @ViewChild("inputFocus") inputFocus: ElementRef;
  oldModel: EditTaskFolderDialogModel;
  form: UntypedFormGroup;
  duplicate: boolean = false;
  unchanged: boolean = false;

  constructor(private http: HttpClient) {}

  ngOnChanges(changes: SimpleChanges) {
    this.initFormControl();
  }

  ngOnInit() {
    this.oldModel = Tool.clone(this.model);
    this.initFormControl();
  }

  ngAfterViewInit() {
     this.inputFocus.nativeElement.focus();
  }

  private initFormControl() {
    this.form = new UntypedFormGroup({
      "folderName": new UntypedFormControl(this.model.folderName,
         [Validators.required, FormValidators.invalidTaskName])
    });

    this.form.get("folderName").valueChanges.subscribe(() => {
      this.duplicate = false;
      this.unchanged = false;
    });
  }

  ok(): void {
    this.model.folderName = this.form.get("folderName").value;

    if(this.model.folderName == this.oldModel.folderName &&
       this.model.owner == this.oldModel.owner)
    {
      this.unchanged = true;
      return;
    }

    this.http.post<CheckDuplicateResponse>(TASK_FOLDER_CHECK_DUPLICATE_URI, this.model)
       .subscribe(res => {
         if(res.duplicate) {
           this.duplicate = true;
         }
         else {
           this.onCommit.emit(this.model);
         }
       });
  }

  cancel(): void {
    this.onCancel.emit("cancel");
  }
}
