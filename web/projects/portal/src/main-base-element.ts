/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { enableProdMode } from "@angular/core";
import { environment } from "./environments/environment";
import { BehaviorSubject } from "rxjs";

declare const window: any;

if(environment.production) {
   enableProdMode();
}

window.inetsoftConnected = new BehaviorSubject(false);

window.inetsoftLogin = function inetsoftLogin(userName: string, password: string): void {
   fetch(getInetsoftBase(), {
      "headers": {
         "X-Requested-With": "XMLHttpRequest",
         "Authorization": "Basic " + btoa(encodeURIComponent(userName) + ":" + encodeURIComponent(password)),
      }
   })
      .then((res) => {
         if(res.ok && res.status == 200) {
            window.inetsoftConnected.next(true);
         }
         else if(res.status == 401) {
            console.error("Authentication credentials are incorrect.");
         }
      })
      .catch((error) => {
         console.error(error);
      });
};

window.inetsoftSSOLogin = async function inetsoftSSOLogin(options: RequestInit = null): Promise<Response> {
   let response: Response;

   await fetch(getInetsoftBase(), options).then((res) => {
      response = res;

      if(res.ok && res.status == 200 && !res.redirected) {
         window.inetsoftConnected.next(true);
      }
      else if(res.status == 401) {
         console.error("Authentication credentials are incorrect.");
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
window.checkInetsoftConnection = function checkInetsoftConnection(options: RequestInit = null,
                                                                  logError: boolean = true)
{
   fetch(getInetsoftBase(), options)
      .then((res) => {
         if(res.ok && res.status == 200 && !res.redirected) {
            window.inetsoftConnected.next(true);
         }
      })
      .catch((error) => {
         if(logError) {
            console.error(error);
         }
      });
};