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
import { DOCUMENT } from "@angular/common";
import { Inject, Injectable } from "@angular/core";

declare const window: any;

@Injectable({
   providedIn: "root"
})
export class LogoutService {
   private logoutUrl = "../logout";
   private expiredUrl = "../sessionexpired";
   private loggingOut = false;
   private fromEm = false;

   constructor(@Inject(DOCUMENT) private document: HTMLDocument) {}

   setLogoutUrl(url: string): void {
      this.logoutUrl = url;
   }

   setFromEm(em: boolean) {
      this.fromEm = em;
   }

   logout(indirect: boolean = false, em: boolean = false): void {
      this.setSessionExpired(false);

      if(indirect) {
         if(!this.loggingOut) {
            document.location.replace(this.expiredUrl + (!!em ? "?fromEm=true" : ""));
         }

         this.loggingOut = false;
      }
      else {
         // if explicitly logging out, a session timeout will be triggered immediately.
         // needs to ignore the next session expiration.
         this.loggingOut = true;
         document.location.replace(this.logoutUrl);
      }
   }

   sessionExpired(): void {
      this.setSessionExpired(true);
      document.location.replace(this.expiredUrl + "?fromEm=" + this.fromEm);
   }

   private setSessionExpired(value: boolean): void {
      window.sessionStorage?.setItem("inetsoftSessionExpired", `${value}`);
   }
}
