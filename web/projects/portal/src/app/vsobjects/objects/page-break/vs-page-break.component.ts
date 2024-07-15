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
import {Component, NgZone, OnInit} from "@angular/core";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { VSPageBreakModel } from "../../model/vs-page-break-model";
import {ViewsheetClientService} from "../../../common/viewsheet-client";
import {DataTipService} from "../data-tip/data-tip.service";
import {ContextProvider} from "../../context-provider.service";

@Component({
  selector: "vs-page-break",
  templateUrl: "vs-page-break.component.html",
  styleUrls: ["vs-page-break.component.scss"]
})
export class VSPageBreak extends AbstractVSObject<VSPageBreakModel> implements OnInit {

  ngOnInit(): void {

  }

  //constructor effect
  constructor(protected viewsheetClient: ViewsheetClientService,
              zone: NgZone,
              protected context: ContextProvider,
              protected dataTipService: DataTipService)
  {
    super(viewsheetClient, zone, context, dataTipService);
  }

}
