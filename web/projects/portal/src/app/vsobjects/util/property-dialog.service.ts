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
import { Injectable } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../widget/services/model.service";
import { ComponentTool } from "../../common/util/component-tool";

@Injectable({
    providedIn: "root"
})
export class PropertyDialogService {
   constructor(private modelService: ModelService,
               private modalService: NgbModal)
   {
   }

   public checkScript(id: string, scripts: string[], ok: Function, cancel: Function): void {
      const uri = "../api/composer/vs/check-script/" + Tool.byteEncode(id);
      this.modelService.sendModel<string>(uri, scripts)
         .subscribe(res => {
            const msg = res.body;

            if(msg) {
               this.showError(msg).then(yes => yes ? ok() : cancel());
            }
            else {
               ok();
            }
         });
   }

   private showError(error: string): Promise<boolean> {
      const msg = Tool.formatCatalogString("_#(js:viewer.viewsheet.scriptFailed)", [error]);
      const buttons = {"yes": "_#(js:Yes)", "no": "_#(js:No)"};
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)", msg, buttons)
         .then(button => Promise.resolve(button == "yes"));
   }
}
