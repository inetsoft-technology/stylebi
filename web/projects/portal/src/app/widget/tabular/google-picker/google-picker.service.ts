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

import { Injectable } from "@angular/core";
import { GoogleFile } from "../../../common/data/tabular/google-picker";

declare const gapi: any; // Google Picker API
declare const google: any; // Google Picker API

@Injectable()
export class GooglePickerService {
   private loaded = false;

   constructor() {
      this.loadGooglePickerScript();
   }

   private loadGooglePickerScript() {
      const script = document.createElement("script");
      script.src = "https://apis.google.com/js/api.js";
      script.onload = () => {
         gapi.load("picker", () => this.onPickerApiLoad());
      };

      document.body.appendChild(script);
   }

   onPickerApiLoad() {
      this.loaded = true;
   }

   openPicker(oauthToken: string, callback: (result: GoogleFile) => void) {
      if(!oauthToken) {
         console.error("OAuth Token is required to open the Google Picker.");
         return;
      }

      if(!this.loaded) {
         console.error("The Google Picker library did not finish loading yet. Please try again.");
         return;
      }

      const view = new google.picker.DocsView(google.picker.ViewId.SPREADSHEETS);
      view.setIncludeFolders(true);

      const picker = new google.picker.PickerBuilder()
         .setOAuthToken(oauthToken)
         .addView(view)
         .setCallback((data) => {
            if(!!data && data.action === google.picker.Action.PICKED) {
               const googleFile = <GoogleFile>{
                  name: data?.docs[0]?.name,
                  id: data?.docs[0]?.id
               };

               callback(googleFile);
            }
         })
         .build();

      picker.setVisible(true);
   }
}
