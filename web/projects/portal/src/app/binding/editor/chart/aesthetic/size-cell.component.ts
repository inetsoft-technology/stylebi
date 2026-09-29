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
import {
   Component,
   DoCheck,
   ElementRef,
   KeyValueDiffer,
   KeyValueDiffers,
   AfterViewInit,
   OnChanges,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { AestheticIconCell } from "./aesthetic-icon-cell";

@Component({
    selector: "size-cell",
    template: `<canvas #canvasElem style="vertical-align:middle;"></canvas>`,
    standalone: true
})
export class SizeCell extends AestheticIconCell implements AfterViewInit, DoCheck, OnChanges {
   @ViewChild("canvasElem") canvasElem: ElementRef;
   differ: KeyValueDiffer<any, any>;
   private mixedChanged: boolean = false;
   private paintToken: number = 0;

   constructor(differs: KeyValueDiffers) {
      super();
      this.differ = differs.find({}).create();
   }

   ngAfterViewInit() {
      this.paintsizeCell(this.frameModel, this.canvasElem.nativeElement);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.isMixed && !changes.isMixed.firstChange) {
         this.mixedChanged = true;
      }
   }

   /**
    * Repaint when the frame's values change, including when the frame is replaced by one
    * of a different type (e.g. static <-> linear), which adds/removes keys, or when isMixed
    * changes. An equal frame (new object, same values) produces no diff and no repaint.
    */
   ngDoCheck() {
      const frameChanged = this.differ.diff(this.frameModel) != null;
      const repaint = frameChanged || this.mixedChanged;
      this.mixedChanged = false;

      // canvasElem is not available until ngAfterViewInit, which does the initial paint
      if(repaint && this.canvasElem) {
         this.paintsizeCell(this.frameModel, this.canvasElem.nativeElement);
      }
   }

   private paintsizeCell(sframe: any, canvas: any) {
      let imgSrc: string = "";
      const token = ++this.paintToken;
      canvas.width = this.cellWidth;
      canvas.height = this.cellHeight;
      canvas.style.border = "1px solid #cccccc";

      let cxt = canvas.getContext("2d");
      cxt.clearRect(0, 0, canvas.width, canvas.height);

      if(!sframe) {
         imgSrc = "assets/size_linear.png";
      }
      else if(sframe.clazz.indexOf("StaticSizeModel") != -1 && !this.isMixed) {
         let w0 = Math.ceil(canvas.width * sframe.size / 30);
         let x0 = (canvas.width - w0) / 2;

         cxt.fillStyle = "#a6e7e4";
         cxt.fillRect(x0, 0, w0, canvas.height);
      }
      else if(sframe.clazz.indexOf("SizeModel") != -1) {
         imgSrc = "assets/size_linear.png";
      }

      if(imgSrc) {
         let img = new Image();
         img.src = imgSrc;

         img.onload = () => {
            // ignore a late load from a paint that has since been superseded
            if(token === this.paintToken) {
               cxt.drawImage(img, 0, 0);
            }
         };
      }
   }
}
