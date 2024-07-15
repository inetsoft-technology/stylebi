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
import { Injectable } from "@angular/core";
import { Subject } from "rxjs";
import { NetTool } from "../util/net-tool";

@Injectable({
   providedIn: "root"
})
export class FileUploadService {

   /**
    * @type {number}
    */
   private progressNum: number = 0;
   private progressObserver = new Subject<ProgressEvent>();

   constructor() {
   }

   /**
    * @returns {Observable<number>}
    */
   public getObserver(): Subject<ProgressEvent> {
      return this.progressObserver;
   }

   /**
    * Upload files through XMLHttpRequest
    *
    * @param url
    * @param files
    * @returns {Promise<T>}
    */
   public upload(url: string, files: File[]): Promise<any> {
      return new Promise((resolve, reject) => {
         let formData: FormData = new FormData();
         let xhr: XMLHttpRequest = new XMLHttpRequest();
         const cookies = document && document.cookie || "";
         const COOKIE_NAME = "XSRF-TOKEN";
         const HEADER_NAME = "X-XSRF-TOKEN";
         const token = NetTool.parseCookieValue(cookies, COOKIE_NAME);

         for(let i = 0; i < files.length; i++) {
            formData.append("uploads[]", files[i], files[i].name);
         }

         xhr.onreadystatechange = () => {
            if(xhr.readyState === 4) {
               if(xhr.status === 200) {
                  resolve(JSON.parse(xhr.response));
               }
               else {
                  reject(xhr.response);
               }
            }
         };

         xhr.upload.onprogress = (event) => {
            this.progressObserver.next(event);
         };

         xhr.open("POST", url, true);

         if(token) {
            xhr.setRequestHeader(HEADER_NAME, token);
         }

         xhr.send(formData);
      });
   }
}
