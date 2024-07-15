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
import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, ViewChild} from "@angular/core";
import { DataSourceDefinitionModel } from "../../../../../../../../shared/util/model/data-source-definition-model";
import { NotificationsComponent } from "../../../../../widget/notifications/notifications.component";

@Component({
   selector: "datasources-datasource-dialog",
   templateUrl: "./datasources-datasource-dialog.component.html",
   styleUrls: ["./datasources-datasource-dialog.component.scss"]
})
export class DatasourcesDatasourceDialogComponent implements OnInit {
   @Input() title: string = "_#(js:Additional Connections)";
   @Input() datasource: DataSourceDefinitionModel;
   @Input() usedNames: string[];
   @Output() onCommit = new EventEmitter<DataSourceDefinitionModel>();
   @Output() onCancel = new EventEmitter<void>();
   @ViewChild("notifications") notifications: NotificationsComponent;
   datasourceValid = true;

   constructor(private changeRef: ChangeDetectorRef) {
   }

   ngOnInit(): void {
   }

   ok(): void {
      this.onCommit.emit(this.datasource);
   }

   cancel(): void {
      this.onCancel.emit();
   }

   updateDataSourceValid($event: boolean) {
      this.datasourceValid = $event;
      this.changeRef.detectChanges();
   }
}
