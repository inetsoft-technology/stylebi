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
import { Component, Input, Output, EventEmitter, ViewEncapsulation } from "@angular/core";
import { DataModelScriptPane } from "../../database-physical-model/data-model-script-pane/data-model-script-pane.component";

import { NgbNav, NgbNavItem, NgbNavLink, NgbNavLinkBase, NgbNavContent, NgbNavOutlet } from "@ng-bootstrap/ng-bootstrap";

@Component({
    selector: "vpm-lookup",
    templateUrl: "vpm-lookup.component.html",
    styleUrls: ["vpm-lookup.component.scss"],
    encapsulation: ViewEncapsulation.None,
    imports: [NgbNav, NgbNavItem, NgbNavLink, NgbNavLinkBase, NgbNavContent, DataModelScriptPane, NgbNavOutlet]
})
export class VPMLookupComponent {
   @Input() expression: string = "";
   @Input() lookupList: string[] = [];
   @Output() expressionChange: EventEmitter<string> = new EventEmitter<string>();
}