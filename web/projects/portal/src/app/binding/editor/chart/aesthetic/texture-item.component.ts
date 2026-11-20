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
import { Component, Input } from "@angular/core";
import { ChartConfig } from "../../../../common/util/chart-config";
import { TEXTURE_PATTERNS, TexturePatternTile } from "./texture-patterns";

/**
 * A 20px swatch of a texture pattern in a frame, drawn as svg from the same designs the server
 * draws in charts. The tiles start at the bottom-left of the swatch, as they do in a chart. The
 * swatch is in the text color, so it follows the theme.
 */
@Component({
    selector: "texture-item",
    template: `
      <svg class="texture-item__swatch" width="20" height="20" viewBox="0 0 20 20"
           aria-hidden="true" fill="currentColor" stroke="none">
        <svg x="2" y="2" width="16" height="16" viewBox="0 0 16 16">
          @if(!pattern) {
            <rect x="0" y="0" width="16" height="16"/>
          }
          @else {
            @for(t of tiles; track $index) {
              <g [attr.transform]="'translate(' + t.x + ' ' + t.y + ') scale(' + scale + ')'">
                @for(s of pattern.shapes; track $index) {
                  @switch(s.type) {
                    @case("rect") {
                      <rect [attr.x]="s.x" [attr.y]="s.y" [attr.width]="s.w" [attr.height]="s.h"/>
                    }
                    @case("dot") {
                      <circle [attr.cx]="s.cx" [attr.cy]="s.cy" [attr.r]="s.r"/>
                    }
                    @case("ring") {
                      <circle [attr.cx]="s.cx" [attr.cy]="s.cy" [attr.r]="s.r" fill="none"
                              stroke="currentColor" stroke-width="1.2"/>
                    }
                    @case("outline") {
                      <rect [attr.x]="s.x" [attr.y]="s.y" [attr.width]="s.w" [attr.height]="s.h"
                            fill="none" stroke="currentColor" stroke-width="1.2"/>
                    }
                    @case("poly") {
                      <polygon [attr.points]="s.points"/>
                    }
                  }
                }
              </g>
            }
          }
        </svg>
        <rect x="0.5" y="0.5" width="19" height="19" fill="none" stroke="currentColor"/>
      </svg>`,
    styles: [".texture-item__swatch { display: block; }"]
})
export class TextureItem {
   @Input() isLongerImage: boolean = false;
   pattern: TexturePatternTile = null;
   tiles: { x: number, y: number }[] = [];
   // how much the tiles are scaled down so the 16px swatch shows at least two across
   scale: number = 1;
   private _texture: number;

   @Input() set texture(texture: number) {
      this._texture = texture;
      this.update();
   }

   get texture(): number {
      return this._texture;
   }

   /** The pattern for the texture, or null for the solid fill (-1) and unknown textures. */
   private update(): void {
      const idx = ChartConfig.TEXTURE_STYLES.indexOf(this._texture);
      // TEXTURE_STYLES starts with the solid fill (-1), then the patterns 0 to 19
      this.pattern = idx > 0 ? TEXTURE_PATTERNS[idx - 1] ?? null : null;
      this.tiles = [];
      this.scale = 1;

      if(this.pattern) {
         const size = 16;
         // a chart draws the tiles at full size, but the swatch is only 16px: scale the tile so
         // a whole number of them, at least two, fit across, so a large tile is not cut off
         const across = Math.max(2, Math.round(size / this.pattern.w));
         this.scale = size / (across * this.pattern.w);
         const tw = this.pattern.w * this.scale;
         const th = this.pattern.h * this.scale;
         const cols = across;
         const rows = Math.ceil(size / th - 0.001);

         // tiles start at the bottom-left corner of the swatch, as in a chart
         for(let r = 0; r < rows; r++) {
            for(let c = 0; c < cols; c++) {
               this.tiles.push({ x: c * tw, y: size - (r + 1) * th });
            }
         }
      }
   }
}
