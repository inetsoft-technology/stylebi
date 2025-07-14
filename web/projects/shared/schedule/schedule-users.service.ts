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
import { HttpClient } from "@angular/common/http";
import { Inject, Injectable, InjectionToken, OnDestroy, Optional } from "@angular/core";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { IdentityIdWithLabel } from "../../em/src/app/settings/security/users/idenity-id-with-label";
import { IdentityId } from "../../em/src/app/settings/security/users/identity-id";
import { StompClientConnection } from "../stomp/stomp-client-connection";
import { StompClientService } from "../stomp/stomp-client.service";
import { UsersModel } from "./model/users-model";

export const PORTAL = new InjectionToken<boolean>("PORTAL");

@Injectable()
export class ScheduleUsersService implements OnDestroy {
   owners = new BehaviorSubject<IdentityIdWithLabel[]>([]);
   groups = new BehaviorSubject<IdentityId[]>([]);
   emailGroups = new BehaviorSubject<IdentityId[]>([]);
   emailUsers = new BehaviorSubject<IdentityId[]>([]);
   adminName = new BehaviorSubject<string>(null);
   private reload = false;
   private loading = false;
   private url = "../api/em/schedule/users-model";
   private connection: StompClientConnection;
   private subscription = new Subscription();

   get isLoading(): boolean {
      return this.loading;
   }

   constructor(private http: HttpClient, private stompClient: StompClientService,
               @Optional() @Inject(PORTAL) private portal: boolean)
   {
      if(portal) {
         this.url = "../api/portal/schedule/users-model";
      }

      this.stompClient.connect("../vs-events", !portal).subscribe(connection => {
         this.connection = connection;

         this.subscription.add(connection.subscribe("/user/schedule/users-change",
            (message) => this.loadScheduleUsers()));
      });

      this.loadScheduleUsers();
   }

   loadScheduleUsers() {
      if(!this.loading) {
         this.loading = true;
         this.http.get<UsersModel>(this.url).subscribe(
            (usersModel) => {
               this.owners.next(usersModel.owners);
               this.groups.next(usersModel.groups);
               this.emailGroups.next(usersModel.emailGroups);
               this.emailUsers.next(usersModel.emailUsers);

               this.adminName.next((usersModel.adminName));
            },
            () => {},
            () => {
               this.loading = false;

               if(this.reload) {
                  this.reload = false;
                  this.loadScheduleUsers();
               }
            }
         );
      }
      else {
         this.reload = true;
      }
   }

   getOwners(): Observable<IdentityIdWithLabel[]> {
      return this.owners;
   }

   getGroups(): Observable<IdentityId[]> {
      return this.groups;
   }

   getEmailGroups(): Observable<IdentityId[]> {
      return this.emailGroups;
   }

   getEmailUsers(): Observable<IdentityId[]> {
      return this.emailUsers;
   }

   getAdminName(): Observable<string> {
      return this.adminName;
   }

   ngOnDestroy(): void {
      this.subscription.unsubscribe();

      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }

      this.owners.complete();
      this.groups.complete();
      this.emailGroups.complete();
      this.emailUsers.complete();
      this.adminName.complete();
   }
}
