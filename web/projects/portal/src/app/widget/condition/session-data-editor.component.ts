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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";

@Component({
   selector: "session-data-editor",
   templateUrl: "session-data-editor.component.html",
   styleUrls: ["session-data-editor.component.scss"]
})
export class SessionDataEditor implements OnInit {
   @Input() value: string;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   sessionDataChoices: any[] = [
      {label: "_#(js:User)", data: "$(_USER_)"},
      {label: "_#(js:Roles)", data: "$(_ROLES_)"},
      {label: "_#(js:Groups)", data: "$(_GROUPS_)"}
   ];

   ngOnInit(): void {
   }
}
