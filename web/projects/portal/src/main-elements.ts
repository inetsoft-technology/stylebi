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
import {platformBrowserDynamic} from "@angular/platform-browser-dynamic";
import {AppElementsModule} from "./app/app-elements.module";
import {BehaviorSubject} from "rxjs";

declare const window: any;

platformBrowserDynamic().bootstrapModule(AppElementsModule);

window.inetsoftConnected = new BehaviorSubject(false);

window.inetsoftLogin = function inetsoftLogin(userName: string, password: string): void {
   fetch(getInetsoftBase(), {
      "headers": {
         "X-Requested-With": "XMLHttpRequest",
         "Authorization": "Basic " + btoa(encodeURIComponent(userName) + ":" + encodeURIComponent(password)),
      }
   }).then((res) => {
      if(res.ok && res.status == 200) {
         window.inetsoftConnected.next(true);
      }
   });
};

window.inetsoftSSOLogin = async function inetsoftSSOLogin(options: RequestInit = null): Promise<Response> {
   let response: Response;

   await fetch(getInetsoftBase(), options).then((res) => {
      response = res;

      if(res.ok && res.status == 200 && !res.redirected) {
         window.inetsoftConnected.next(true);
      }
   });

   return response;
};

/**
 * Gets inetsoft base href with the last / removed
 */
function getInetsoftBase() {
   let inetsoftBase = document.getElementsByTagName(
      "inetsoft-base")?.item(0)?.attributes?.getNamedItem("href")?.value;

   if(!inetsoftBase) {
      inetsoftBase = document.getElementsByTagName(
         "base")?.item(0)?.attributes?.getNamedItem("href")?.value;
   }

   if(inetsoftBase) {
      inetsoftBase = inetsoftBase.replace(/\/$/, "");
   }

   inetsoftBase = inetsoftBase ? inetsoftBase : "";
   return inetsoftBase;
}

/**
 * Check inetsoft connection
 */
window.checkInetsoftConnection = function checkInetsoftConnection(options: RequestInit = null) {
   fetch(getInetsoftBase(), options)
      .then((res) => {
         if(res.ok && res.status == 200 && !res.redirected) {
            window.inetsoftConnected.next(true);
         }
      })
      .catch((error) => {
         // do nothing
      });
};

/**
 * Check if inetsoft is connected on app load in case there is no need to log in such as when
 * security is disabled or there is an active session
 */
window.checkInetsoftConnection();
