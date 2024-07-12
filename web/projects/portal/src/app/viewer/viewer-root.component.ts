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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { StompClientConnection } from "../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../common/viewsheet-client";
import { ComponentTool } from "../common/util/component-tool";

@Component({
   selector: "v-viewer-root",
   templateUrl: "viewer-root.component.html",
   styleUrls: ["viewer-root.component.scss"]
})
export class ViewerRootComponent implements OnInit, OnDestroy {
   private connection: StompClientConnection;

   constructor(private socket: StompClientService, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      if(document.body.className.indexOf("app-loaded") == -1) {
         document.body.className += " app-loaded";
      }

      this.socket.connect("../vs-events").subscribe((connection) => {
         this.connection = connection;
      });
   }

   ngOnDestroy(): void {
      if(this.connection) {
         this.connection.disconnect();
      }
   }

   downloadStarted(url: string): void {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)", "_#(js:common.downloadStart)");
   }
}
