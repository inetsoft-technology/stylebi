/*
 * Copyright (c) 2025, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

import { HttpClient } from "@angular/common/http";
import { Directive, ElementRef, EventEmitter, Input, OnDestroy, Output, Renderer2 } from "@angular/core";
import { SafeValue } from "@angular/platform-browser";
import { Subscription } from "rxjs";

@Directive({
    selector: "[chartImage]",
    standalone: true
})
export class ChartImageDirective implements OnDestroy {
   @Input()
   get chartImage(): string | SafeValue {
      return this._chartImage;
   }

   set chartImage(value: string | SafeValue) {
      if(value !== this._chartImage) {
         this._chartImage = value;
         this.resetRetry();

         if(this._loadTimer !== null) {
            clearTimeout(this._loadTimer);
            this._loadTimer = null;
         }

         // a retry armed for the previous address must not fire after the address changed.
         if(this.retryTimer !== null) {
            clearTimeout(this.retryTimer);
            this.retryTimer = null;
         }

         if(!!value) {
            this._loadTimer = setTimeout(() => {
               this._loadTimer = null;
               this.loadImage();
            }, 50);
         }
         else {
            this.renderer.removeAttribute(this.element.nativeElement, "src");
         }
      }
   }

   @Output() onLoading = new EventEmitter<void>();
   @Output() onLoaded = new EventEmitter<void>();
   @Output() onError = new EventEmitter<void>();
   private _chartImage: string | SafeValue = null;
   private _loadTimer: ReturnType<typeof setTimeout> | null = null;
   private currentBlobUrl: string = null;
   private loadSubscription: Subscription = null;
   private retryTimer: ReturnType<typeof setTimeout> | null = null;
   private retryCount = 0;
   private retryStart = 0;
   // the server answers with Retry-After while the chart graph is still being generated.
   // give up only after a generous wall-clock budget: a large/slow graph (big dataset, mv
   // build, web map) may legitimately take a long time, and the facet/legend/title areas
   // don't listen to onError, so giving up early leaves those tiles permanently blank.
   private readonly MAX_RETRY_TIME = 300000;
   private readonly MAX_RETRY_INTERVAL = 5000;

   constructor(private element: ElementRef, private http: HttpClient, private renderer: Renderer2) {
   }

   ngOnDestroy(): void {
      if(this.retryTimer != null) {
         clearTimeout(this.retryTimer);
         this.retryTimer = null;
      }

      this.loadSubscription?.unsubscribe();
      this.loadSubscription = null;

      if(this.currentBlobUrl) {
         URL.revokeObjectURL(this.currentBlobUrl);
         this.currentBlobUrl = null;
      }

      if(this._loadTimer !== null) {
         clearTimeout(this._loadTimer);
         this._loadTimer = null;
      }
   }

   private resetRetry(): void {
      this.retryCount = 0;
      this.retryStart = 0;
   }

   private loadImage(reloading = false): void {
      if(this.retryTimer != null) {
         clearTimeout(this.retryTimer);
         this.retryTimer = null;
      }

      this.loadSubscription?.unsubscribe();
      this.loadSubscription = null;

      if(!!this.chartImage) {
         if(!reloading) {
            this.onLoading.emit();
         }

         const requestedImage = this._chartImage;

         this.loadSubscription = this.http.get(this.chartImage as string, { observe: "response", responseType: "blob" }).subscribe(
            response => {
               if(response.headers?.has("Retry-After")) {
                  // ignore a late response for an address we no longer want, so it doesn't
                  // consume the current address's retry budget or reload the wrong image.
                  if(requestedImage != this.chartImage) {
                     return;
                  }

                  const now = Date.now();

                  if(this.retryCount == 0) {
                     this.retryStart = now;
                  }
                  else if(now - this.retryStart >= this.MAX_RETRY_TIME) {
                     console.warn("Giving up loading image after " + this.retryCount +
                                  " retries " + this.chartImage);
                     this.resetRetry();
                     this.onError.emit();
                     return;
                  }

                  this.retryCount++;
                  const seconds = parseInt(response.headers.get("Retry-After"), 10);
                  // a missing/malformed header must not turn the retry into a busy loop.
                  const interval = isNaN(seconds) ? 1000 : Math.max(seconds, 1) * 1000;
                  // escalate up to MAX_RETRY_INTERVAL, but never poll faster than the
                  // server asked for.
                  const delay = Math.max(
                     interval, Math.min(interval * this.retryCount, this.MAX_RETRY_INTERVAL));

                  this.retryTimer = setTimeout(() => {
                     this.retryTimer = null;
                     this.loadImage(true);
                  }, delay);
               }
               else if(requestedImage == this.chartImage) {
                  // Do not set if image address changed before the request returned
                  this.resetRetry();

                  if(this.currentBlobUrl) {
                     URL.revokeObjectURL(this.currentBlobUrl);
                  }

                  this.currentBlobUrl = URL.createObjectURL(response.body);
                  this.renderer.setAttribute(this.element.nativeElement, "src", this.currentBlobUrl);
                  this.onLoaded.emit();
               }
            },
            error => {
               console.warn("Failed to load image " + this.chartImage + "\n", error);
               this.onError.emit();
            }
         );
      }
      else {
         this.renderer.removeAttribute(this.element.nativeElement, "src");
      }
   }
}