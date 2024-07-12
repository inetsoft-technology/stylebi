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
import {HttpClient, HttpParams} from "@angular/common/http";
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import {ResourcePermissionModel} from "../../../security/resource-permission/resource-permission-model";
import {RepositoryEditorModel} from "../../../../../../../shared/util/model/repository-editor-model";
import {Tool} from "../../../../../../../shared/util/tool";

export interface RepositoryPermissionEditorModel extends RepositoryEditorModel {
   label: string;
   permissionModel: ResourcePermissionModel;
}

@Component({
   selector: "em-repository-permission-editor-page",
   templateUrl: "./repository-permission-editor-page.component.html",
   styleUrls: ["./repository-permission-editor-page.component.scss"]
})
export class RepositoryPermissionEditorPageComponent implements OnChanges {
   @Input() model: RepositoryPermissionEditorModel;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();

   private _oldModel: RepositoryPermissionEditorModel;
   private permissionChanged: boolean = false;

   constructor(private http: HttpClient) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      this._oldModel = Tool.clone(this.model);
   }

   apply() {
      const params: HttpParams = new HttpParams()
         .set("path", this.model.path)
         .set("type", this.model.type + "");

      this.http.post("../api/em/content/repository/tree/node/permission",
         this.model.permissionModel, {params})
          .subscribe(() => {
             this.editorChanged.emit();
             this.permissionChanged = false;
          });
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.permissionChanged = false;
      this.model = this._oldModel;
      this._oldModel = Tool.clone(this.model);
   }

   changed() {
      this.permissionChanged = true;
   }

   get disabled(): boolean {
      return Tool.isEquals(this.model, this._oldModel) && !this.permissionChanged;
   }
}
