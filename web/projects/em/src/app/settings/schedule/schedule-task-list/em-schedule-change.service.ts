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
import { EventEmitter, Injectable, NgZone, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { ScheduleTaskChange } from "../model/schedule-task-change";

@Injectable()
export class EmScheduleChangeService implements OnDestroy {
  private subscriptions: Subscription = new Subscription();
  private connection: StompClientConnection;
  onChange = new EventEmitter<ScheduleTaskChange>();
  onFolderChange = new EventEmitter();

  constructor(private stompClient: StompClientService, private zone: NgZone) {
    this.stompClient.connect("../vs-events").subscribe(
       (connection) => {
         this.connection = connection;
         this.subscriptions.add(connection.subscribe(
               "/user/em-schedule-changed",
               (message) => this.zone.run(() => this.taskChanged(JSON.parse(message.frame.body))))
         );
         this.subscriptions.add(connection.subscribe(
            "/user/schedule-folder-changed",
            (message) => this.zone.run(() => this.onFolderChange.emit()))
         );
       }
    );
  }

  ngOnDestroy(): void {
    if(this.subscriptions) {
      this.subscriptions.unsubscribe();
      this.subscriptions = null;
    }

    if(this.connection) {
      this.connection.disconnect();
      this.connection = null;
    }
  }

  private taskChanged(change: ScheduleTaskChange): void {
    this.onChange.emit(change);
  }
}
