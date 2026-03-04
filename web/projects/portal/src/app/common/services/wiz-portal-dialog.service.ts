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

import { Injectable, Injector } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { WizPortalDialogComponent } from "../../../../../shared/wiz-portal/wiz-portal-dialog.component";
import { WizPortalService } from "../../../../../shared/wiz-portal/wiz-portal.service";
import { ComponentTool } from "../util/component-tool";

@Injectable({
   providedIn: "root"
})
export class WizPortalDialogService {
   constructor(private wizPortalService: WizPortalService,
               private modalService: NgbModal)
   {
   }

   openWizPortalDialog(): void {
      const injector = Injector.create({
         providers: [
            {provide: WizPortalService, useValue: this.wizPortalService}
         ],
      });

      ComponentTool.showDialog(this.modalService, WizPortalDialogComponent, () => {
      }, {
         backdrop: true,
         windowClass: "wiz-portal-container",
         injector: injector
      });
   }
}