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
import { VSTableTrapModel } from "../model/vs-table-trap-model";
import { ModelService } from "../../widget/services/model.service";
import { TrapInfo } from "../../common/data/trap-info";
import { ComponentTool } from "../../common/util/component-tool";

@Injectable({
    providedIn: "root"
})
export class VSTrapService {
   constructor(private modelService: ModelService,
               private modalService: NgbModal)
   {
   }

   public checkTrap(trapInfo: TrapInfo, trapContinue?: Function, trapStop?: Function, noTrap?: Function): void {
      this.modelService.sendModel<VSTableTrapModel>(trapInfo.controllerURI, trapInfo.payload)
         .subscribe((res) => {
            const checkTrap = res.body;

            if(checkTrap.showTrap) {
               this.showTrap().then((continueDespiteTrap) => {
                  if(continueDespiteTrap && trapContinue) {
                     trapContinue();
                  }
                  else if(trapStop) {
                     trapStop();
                  }
               });
            }
            else {
               if(noTrap) {
                  noTrap();
               }
            }
         });
   }

   private showTrap(): Promise<boolean> {
      return ComponentTool.showTrapAlert(this.modalService, false, null, {backdrop: false})
         .then((buttonClick) => {
            return Promise.resolve(buttonClick == "yes");
         });
   }
}
