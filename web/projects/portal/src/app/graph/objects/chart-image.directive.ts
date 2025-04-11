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
import { Directive, ElementRef, EventEmitter, Input, Output } from "@angular/core";
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

   constructor(private element: ElementRef, private http: HttpClient) {
   }

   private loadImage(): void {
      if(!!this.chartImage) {
         this.onLoading.emit();
         this.http.get(this.chartImage as string, { responseType: "blob" }).subscribe(
            blob => {
               this.element.nativeElement.src = URL.createObjectURL(blob);
               this.onLoaded.emit();
            },
            error => {
               console.warn("Failed to load image " + this.chartImage + "\n", error);
               this.onError.emit();
            }
         );
      }
      else {
         this.element.nativeElement.src = "data:image/gif;base64,R0lGODlhAQABAAAAACwAAAAAAQABAAA=";
      }
   }
}