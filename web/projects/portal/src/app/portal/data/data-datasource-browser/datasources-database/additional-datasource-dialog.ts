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
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   NgZone,
   Output,
   ViewChild
} from "@angular/core";
import { DatabaseDefinitionModel } from "../../../../../../../shared/util/model/database-definition-model";
import { ComponentTool } from "../../../../common/util/component-tool";
import { Observable } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";

@Component({
   selector: "additional-datasource-dialog",
   templateUrl: "additional-datasource-dialog.html",
   styleUrls: ["additional-datasource-dialog.scss"]
})
export class AdditionalDatasourceDialog {
   @Input() additionalDataSource: DatabaseDefinitionModel;
   @Input() title: string;
   @Input() primaryDatabasePath: string;
   @Input() uploadEnabled = false;
   @Input() hasDuplicateCheck: (string) => Observable<any>;
   @Output() onCommit = new EventEmitter<DatabaseDefinitionModel>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild("notifications") notifications: NotificationsComponent;

   constructor(private zone: NgZone,
               private modalService: NgbModal,
               private changeDetectorRef: ChangeDetectorRef)
   {
   }

   testedConnection(event: {type: string, message: string}): void {
      if(!event || !this.notifications) {
         return;
      }

      if(event.type === "ERROR") {
         ComponentTool.showMessageDialog(
            this.modalService, "_#(js:Error)", event.message).then();
      }
      else if(event.type === "OK") {
         this.notifications.success(event.message);
      }
   }

   ok(): void {
      //check duplicate
      if(!!this.hasDuplicateCheck) {
         this.hasDuplicateCheck(this.additionalDataSource.name).subscribe(
            (duplicate) => {
               if(duplicate.duplicate) {
                  this.zone.run(() => {
                     this.changeDetectorRef.detach();
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        duplicate.duplicateMessage + "_*" + this.additionalDataSource.name)
                        .then(() => {
                           this.changeDetectorRef.reattach();
                        });
                  });
               }
               else {
                  this.onCommit.emit(this.additionalDataSource);
               }
            },
         );
      }
      else {
         this.onCommit.emit(this.additionalDataSource);
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
