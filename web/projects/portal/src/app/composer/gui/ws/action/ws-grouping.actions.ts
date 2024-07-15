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
import { HttpParams } from "@angular/common/http";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { Tool } from "../../../../../../../shared/util/tool";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { GroupingDialogModel } from "../../../data/ws/grouping-dialog-model";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSGroupingAssembly } from "../../../data/ws/ws-grouping-assembly";
import { GroupingDialog } from "../../../dialog/ws/grouping-dialog.component";
import { WSAssemblyActions } from "./ws-assembly.actions";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";

const GROUPING_DIALOG_REST_URI = "../api/composer/ws/grouping-assembly-dialog-model/";

export class WSGroupingActions extends WSAssemblyActions {
   constructor(
      private grouping: WSGroupingAssembly,
      worksheet: Worksheet,
      modalService: DialogService,
      private modelService: ModelService,
      featureFlagsService: FeatureFlagsService)
   {
      super(grouping, worksheet, modalService, featureFlagsService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "worksheet grouping properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => true,
            action: () => this.showPropertiesDialog()
         },
         {
            id: () => "worksheet grouping update-mirror",
            label: () => "_#(js:Update Mirror)",
            icon: () => "fa fa-refresh",
            enabled: () => true,
            visible: () => super.updateMirrorVisible(),
            action: () => super.updateMirror()
         }
      ]));

      return super.createMenuActions(groups);
   }

   private showPropertiesDialog() {
      const params = new HttpParams().set("grouping", this.grouping.name);

      this.modelService.getModel(GROUPING_DIALOG_REST_URI + Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((data: GroupingDialogModel) => {
            let options: SlideOutOptions = {
               objectId: this.grouping.name,
               windowClass: "ws-grouping-property-dialog"
            };
               const dialog =
                  this.showDialog(GroupingDialog, options,
                     (resolve) => {
                        if(resolve) {
                           this.worksheet.socketConnection
                              .sendEvent(resolve.controller, resolve.model);
                        }
                     },
                  );
               dialog.worksheet = this.worksheet;
               dialog.groupingName = this.grouping.name;
               dialog.model = data;
            },
            () => {
               console.error("Error fetching table properties from the server");
            }
         );
   }
}
