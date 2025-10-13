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
import { Directive, ElementRef, EventEmitter, Input, Output, Renderer2 } from "@angular/core";
import { SafeValue } from "@angular/platform-browser";

@Directive({
   selector: "[chartImage]"
})
export class ChartImageDirective {
   @Input()
   get chartImage(): string | SafeValue {
      return this._chartImage;
   }

   set chartImage(value: string | SafeValue) {
      if(value !== this._chartImage) {
         this._chartImage = value;
         this.loadImage();
      }
   }

   @Output() onLoading = new EventEmitter<void>();
   @Output() onLoaded = new EventEmitter<void>();
   @Output() onError = new EventEmitter<void>();
   private _chartImage: string | SafeValue = null;

   constructor(private element: ElementRef, private http: HttpClient, private renderer: Renderer2) {
   }

   private loadImage(reloading = false): void {
      if(!!this.chartImage) {
         if(!reloading) {
            this.onLoading.emit();
         }

         this.http.get(this.chartImage as string, { observe: "response", responseType: "blob" }).subscribe(
            response => {
               if(response.headers?.has("Retry-After")) {
                  const interval = parseInt(response.headers.get("Retry-After"), 10) * 1000;
                  setTimeout(() => this.loadImage(true), interval);
               }
               else {
                  this.renderer.setAttribute(this.element.nativeElement, "src", URL.createObjectURL(response.body));
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