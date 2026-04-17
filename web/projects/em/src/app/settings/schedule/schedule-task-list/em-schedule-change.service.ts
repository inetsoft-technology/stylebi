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
import { EventEmitter, Injectable, NgZone, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { ScheduleTaskChange } from "../../../../../../shared/schedule/model/schedule-task-change";

@Injectable()
export class EmScheduleChangeService implements OnDestroy {
  private subscriptions: Subscription = new Subscription();
  private connection: StompClientConnection;
  onChange = new EventEmitter<ScheduleTaskChange>();
  onFolderChange = new EventEmitter();

  constructor(private stompClient: StompClientService, private zone: NgZone) {
    this.stompClient.connect("../vs-events", true).subscribe(
       (connection) => {
         this.connection = connection;

         // Schedule-change events carry a JSON payload that updates the task list.
         this.subscriptions.add(connection.subscribe(
               "/user/em-schedule-changed",
               (message) => this.zone.run(() => this.taskChanged(this.parseChange(message.frame.body))))
         );

         // Folder-change events are signal-only; the consumer decides how to refresh.
         this.subscriptions.add(connection.subscribe(
            "/user/em-schedule-folder-changed",
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
    // Invalid or empty payloads are ignored so one bad server event does not break future updates.
    if(!change) {
      return;
    }

    this.onChange.emit(change);
  }

  private parseChange(body: string): ScheduleTaskChange {
    if(!body) {
      return null;
    }

    try {
      return JSON.parse(body);
    }
    catch {
      // STOMP messages should contain JSON, but this keeps the callback resilient to malformed payloads.
      return null;
    }
  }
}
