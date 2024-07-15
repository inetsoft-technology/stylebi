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
import { StyleConstants } from "../util/style-constants";
import { ColorMap } from "./color-map";

export class VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel";
   name: string;
   field: string;
   summary: boolean = false;
   changed: boolean = false;
}

export class ColorFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.ColorFrameModel";
}

export class StaticColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel";
   color: string;
   cssColor: string;
   defaultColor: string;
}

export class CategoricalColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel";
   colors: string[];
   cssColors: string[];
   defaultColors: string[];
   colorMaps: ColorMap[];
   globalColorMaps: ColorMap[];
   useGlobal: boolean;
   shareColors: boolean;
   dateFormat: number;
   colorValueFrame?: boolean;
}

export class GradientColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.GradientColorModel";
   fromColor: string;
   cssFromColor: string;
   defaultFromColor: string;
   toColor: string;
   cssToColor: string;
   defaultToColor: string;

   constructor() {
      super();
      this.defaultFromColor = "#ff99cc";
      this.defaultToColor = "#008000";
   }
}

export class HSLColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.HSLColorModel";
   color: string;
   cssColor: string;
   defaultColor: string;
}

export class BrightnessColorModel extends HSLColorModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BrightnessColorModel";

   constructor() {
      super();
      this.color = "#85b8eb";
      this.defaultColor = "#85b8eb";
   }
}

export class SaturationColorModel extends HSLColorModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.SaturationColorModel";

   constructor() {
      super();
      this.color = "#85b8eb";
      this.defaultColor = "#85b8eb";
   }
}

export class BipolarColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BipolarColorModel";
}

export class RainbowColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RainbowColorModel";
}

export class HeatColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.HeatColorModel";
}

export class CircularColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CircularColorModel";
}

export class ShapeFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.ShapeFrameModel";
}

export class StaticShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.StaticShapeModel";
   shape: string = StyleConstants.FILLED_CIRCLE + "";
}

export class CategoricalShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalShapeModel";
   shapes: string[];
}

export class FillShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.FillShapeModel";
}

export class OrientationShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.OrientationShapeModel";
}

export class PolygonShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PolygonShapeModel";
}

export class TriangleShapeModel extends ShapeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.TriangleShapeModel";
}

export class TextureFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.TextureFrameModel";
}

export class StaticTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.StaticTextureModel";
   texture: number = StyleConstants.PATTERN_NONE;
}

export class CategoricalTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalTextureModel";
   textures: number[];
}

export class GridTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.GridTextureModel";
}

export class LeftTiltTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.LeftTiltTextureModel";
}

export class OrientationTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.OrientationTextureModel";
}

export class RightTiltTextureModel extends TextureFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RightTiltTextureModel";
}

export class LineFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.LineFrameModel";
}

export class StaticLineModel extends LineFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.StaticLineModel";
   line: number = StyleConstants.THIN_LINE;
}

export class CategoricalLineModel extends LineFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalLineModel";
   lines: number[];
}

export class LinearLineModel extends LineFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.LinearLineModel";
}

export class SizeFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.SizeFrameModel";
   largest: number = 30;
   smallest: number = 1;
}

export class StaticSizeModel extends SizeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel";
   size: number = 1;
}

export class CategoricalSizeModel extends SizeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.CategoricalSizeModel";
}

export class LinearSizeModel extends SizeFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.LinearSizeModel";
}

export class TextFrameModel extends VisualFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.TextFrameModel";
}

export class BluesColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BluesColorModel";
}

export class BrBGColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BrBGColorModel";
}

export class BuGnColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BuGnColorModel";
}

export class BuPuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.BuPuColorModel";
}

export class GnBuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.GnBuColorModel";
}

export class GreensColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.GreensColorModel";
}

export class GreysColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.GreysColorModel";
}

export class OrangesColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.OrangesColorModel";
}

export class OrRdColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.OrRdColorModel";
}

export class PiYGColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PiYGColorModel";
}

export class PRGnColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PRGnColorModel";
}

export class PuBuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PuBuColorModel";
}

export class PuBuGnColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PuBuGnColorModel";
}

export class PuOrColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PuOrColorModel";
}

export class PuRdColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PuRdColorModel";
}

export class PurplesColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.PurplesColorModel";
}

export class RdBuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RdBuColorModel";
}

export class RdGyColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RdGyColorModel";
}

export class RdPuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RdPuColorModel";
}

export class RdYlGnColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RdYlGnColorModel";
}

export class RedsColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RedsColorModel";
}

export class SpectralColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.SpectralColorModel";
}

export class RdYlBuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.RdYlBuColorModel";
}

export class YlGnBuColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.YlGnBuColorModel";
}

export class YlGnColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.YlGnColorModel";
}

export class YlOrBrColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.YlOrBrColorModel";
}

export class YlOrRdColorModel extends ColorFrameModel {
   clazz: string = "inetsoft.web.binding.model.graph.aesthetic.YlOrRdColorModel";
}
