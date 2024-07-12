/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ColorPalette } from "./color-classes";

export class DefaultPalette {
   public static palette: ColorPalette = [
      ["#000000", "#993300", "#333300", "#003300", "#003366", "#000080", "#333399", "#333333"],
      ["#800000", "#ff6600", "#808000", "#008000", "#008080", "#0000ff", "#666699", "#808080"],
      ["#ff0000", "#ff9900", "#99cc00", "#339966", "#33cccc", "#3366ff", "#800080", "#969696"],
      ["#ff00ff", "#ffcc00", "#ffff00", "#00ff00", "#00ffff", "#00ccff", "#993366", "#c0c0c0"],
      ["#ff99cc", "#ffcc99", "#ffff99", "#ccffcc", "#ccffff", "#99ccff", "#cc99ff", "#ffffff"]];

   public static chart: ColorPalette = [
      ["#518db9", "#b9dbf4", "#62a640", "#ade095", "#fc8f2a", "#fde3a7", "#d64541", "#fda7a5"],
      ["#9368be", "#be90d4", "#95a5a6", "#dadfe1", "#19b5fe", "#c5eff7", "#869530", "#c8d96f"],
      ["#a88637", "#d2b267", "#019875", "#68c3a3", "#99CCFF", "#999933", "#CC9933", "#006666"],
      ["#993300", "#666666", "#663366", "#CCCCCC", "#669999", "#CCCC66", "#CC6600", "#9999FF"],
      ["#0066CC", "#FFCC00", "#009999", "#99CC33", "#FF9900", "#66CCCC", "#339966", "#CCCC33"]];

   public static fgWithTransparent: ColorPalette = [
      ["", "#000000", "#993300", "#003300", "#003366", "#000080", "#333399", "#333333"],
      ["#800000", "#ff6600", "#808000", "#008000", "#008080", "#0000ff", "#666699", "#808080"],
      ["#ff0000", "#ff9900", "#99cc00", "#339966", "#33cccc", "#3366ff", "#800080", "#969696"],
      ["#ff00ff", "#ffcc00", "#ffff00", "#00ff00", "#00ffff", "#00ccff", "#993366", "#c0c0c0"],
      ["#ff99cc", "#ffcc99", "#ffff99", "#ccffcc", "#ccffff", "#99ccff", "#cc99ff", "#ffffff"]];

   public static bgWithTransparent: ColorPalette = [
      ["", "#ffffff", "#eeeeee", "#dddddd", "#cccccc", "#bbbbbb", "#aaaaaa", "#999999"],
      ["#e8efe8", "#ecebe6", "#fff8de", "#fffac3", "#faf1a2", "#f4f3b0", "#fee893", "#ffe19b"],
      ["#ffe4b7", "#fee4cb", "#fdcda7", "#fdd2c2", "#f9c8cb", "#f8cad7", "#feeae9", "#e9d0e5"],
      ["#c2a1da", "#ccb2d5", "#b6b2d7", "#aeb0d7", "#8ca4d4", "#d2e3f3", "#bae3f7", "#d6e9f8"],
      ["#dbf2f8", "#ceecec", "#c1e4dd", "#d0eadd", "#cce6bf", "#c5d9a6", "#dbe9c6", "#f0f6e8"]];

   public static bgWithNoTransparent: ColorPalette = [
      ["#ffffff", "#eeeeee", "#dddddd", "#cccccc", "#bbbbbb", "#aaaaaa", "#999999", "#888888"],
      ["#e8efe8", "#ecebe6", "#fff8de", "#fffac3", "#faf1a2", "#f4f3b0", "#fee893", "#ffe19b"],
      ["#ffe4b7", "#fee4cb", "#fdcda7", "#fdd2c2", "#f9c8cb", "#f8cad7", "#feeae9", "#e9d0e5"],
      ["#c2a1da", "#ccb2d5", "#b6b2d7", "#aeb0d7", "#8ca4d4", "#d2e3f3", "#bae3f7", "#d6e9f8"],
      ["#dbf2f8", "#ceecec", "#c1e4dd", "#d0eadd", "#cce6bf", "#c5d9a6", "#dbe9c6", "#f0f6e8"]];
}
