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
import { AfterViewInit, Component, OnInit } from "@angular/core";
import { LoadingSpinnerService } from "../../../../common/util/loading-spinner/loading-spinner.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { Secured } from "../../../../secured";

@Secured({
   route: "/settings/security/provider",
   label: "Providers",
   hiddenForMultiTenancy: true
})
@Component({
   selector: "em-security-provider-page",
   templateUrl: "./security-provider-page.component.html",
   styleUrls: ["./security-provider-page.component.scss"]
})
export class SecurityProviderPageComponent implements OnInit, AfterViewInit {
   constructor(private pageTitle: PageHeaderService,
               private loadingSpinnerService: LoadingSpinnerService) {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Security Settings Provider)";
      this.loadingSpinnerService.show();
   }

   ngAfterViewInit() {
      setTimeout(() => {
         this.loadingSpinnerService.hide();
      }, 200);
   }
}
