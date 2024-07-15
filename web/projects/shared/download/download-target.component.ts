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
import {
   Component,
   ElementRef,
   EventEmitter,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { Subscription } from "rxjs";
import { DownloadService } from "./download.service";
import { HttpClient } from "@angular/common/http";

@Component({
   selector: "dl-download-target",
   templateUrl: "download-target.component.html",
   styleUrls: ["download-target.component.scss"]
})
export class DownloadTargetComponent implements OnInit, OnDestroy {
   @Output() downloadStarted = new EventEmitter<string>();
   @ViewChild("frame") frame: ElementRef;
   private subscription: Subscription;
   private contentSource: string;
   private reloadCounter = 1;

   constructor(private downloadService: DownloadService, private http: HttpClient) {
   }

   ngOnInit(): void {
      this.subscription = this.downloadService.url.subscribe((url) => {
         if(url) {
            const match = new RegExp("checkForResponse=([^&]+)").exec(url);
            const checkForResponse = match ? match[1] === "true" : false;

            if(this.contentSource === url) {
               let reloadUrl = url;

               if(url.indexOf("?") >= 0) {
                  // assume that there's an existing query string
                  reloadUrl += "&";
               }
               else {
                  reloadUrl += "?";
               }

               reloadUrl += "downloadServiceReloadCounter=" + (this.reloadCounter++);
               this.download(reloadUrl, checkForResponse);
            }
            else {
               this.contentSource = url;
               this.reloadCounter = 1;
               this.download(url, checkForResponse);
            }

            this.downloadStarted.emit(url);
         }
      });
   }

   download(url: string, checkForResponse: boolean) {
      if(checkForResponse) {
         this.http.get(url, {observe: "response"}).subscribe(
            (response) => {
               if(response) {
                  this.frame.nativeElement.setAttribute("src", url);
               }
            },
            (error) => {
               if(error.status == 200) {
                  this.frame.nativeElement.setAttribute("src", url);
               }
               else {
                  alert(error.error.message);
               }
            }
         );
      }
      else {
         this.frame.nativeElement.setAttribute("src", url);
      }
   }

   ngOnDestroy(): void {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }
}
