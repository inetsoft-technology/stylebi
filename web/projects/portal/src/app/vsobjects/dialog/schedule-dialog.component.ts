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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   Output,
   OnInit,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { ScheduleDialogModel } from "../model/schedule/schedule-dialog-model";
import { SimpleScheduleDialogModel } from "../model/schedule/simple-schedule-dialog-model";
import { ComponentTool } from "../../common/util/component-tool";

const CHECK_SCHEDULE_VALID_URI: string = "../api/vs/check-schedule-dialog/";
const SIMPLE_SCHEDULE_URI: string = "../api/vs/simple-schedule-dialog-model/";

@Component({
   selector: "schedule-dialog",
   templateUrl: "schedule-dialog.component.html",
})
export class ScheduleDialog implements OnInit {
   @Input() model: ScheduleDialogModel;
   @Input() runtimeId: string;
   @Input() principal: string;
   @Input() exportTypes: {label: string, value: string}[] = [];
   @Input() securityEnabled: boolean = false;
   @Output() onCommit = new EventEmitter<ScheduleDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("simpleScheduleDialog") simpleScheduleDialog: TemplateRef<any>;
   name: string;
   form: UntypedFormGroup;

   constructor(private http: HttpClient, private modalService: NgbModal) {
   }

   ngOnInit() {
      if(!this.securityEnabled) {
         this.model.currentBookmark = true;
      }
   }

   ok(): void {
      if(this.model.bookmarkEnabled && (!this.model.bookmark || this.model.bookmark.length == 0)) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:viewer.viewsheet.bookmark.emptyName)");
         return;
      }

      let formControl = new UntypedFormControl(this.model.bookmark, [
         FormValidators.bookmarkSpecialCharacters
      ]);

      if(formControl.errors && formControl.errors["bookmarkSpecialCharacters"]) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:viewer.viewsheet.bookmark.nameFormat)");
         return;
      }

      const params = new HttpParams()
         .set("bookmarkName", this.model.bookmark)
         .set("useCurrent", String(this.model.currentBookmark));

      this.http.get<MessageCommand>(CHECK_SCHEDULE_VALID_URI + Tool.encodeURIPath(this.runtimeId), {params})
         .subscribe(
            (data: MessageCommand) => {
               if(data.type == "OK") {
                  this.getSimpleScheduleDialog();
               }
               else if(data.type == "CONFIRM") {
                  ComponentTool.showConfirmDialog(this.modalService, data.type, data.message)
                     .then((result: string) => {
                        if(result === "ok") {
                           this.getSimpleScheduleDialog();
                        }
                        else if(result === "cancel") {
                           // bail out
                        }
                     });
               }
               else {
                  ComponentTool.showMessageDialog(this.modalService,
                     ComponentTool.getDialogTitle(data.type), data.message);
               }
            },
            (err) => {
               // TODO handle error
               console.error("Failed to check if export valid: ", err);
            }
         );
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   getSimpleScheduleDialog(): void {
      const params = new HttpParams()
         .set("bookmarkName", this.model.bookmark)
         .set("useCurrent", String(this.model.currentBookmark));

      this.http.get<SimpleScheduleDialogModel>(SIMPLE_SCHEDULE_URI + Tool.encodeURIPath(this.runtimeId), {params})
         .subscribe(
            (data: SimpleScheduleDialogModel) => {
               this.model.simpleScheduleDialogModel = data;

               this.modalService.open(this.simpleScheduleDialog, {backdrop: "static", scrollable: true}).result.then(
                  (result: SimpleScheduleDialogModel) => {
                     this.onCommit.emit(this.model);
                  },
                  (reason: any) => {
                     this.onCancel.emit("cancel");
                  }
               );
            },
            (err) => {
               // TODO handle error
               console.error("Failed to get simple schedule dialog: ", err);
            }
         );
   }
}
