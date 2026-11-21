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
import { ColorPalette } from "./color-classes";

/**
 * The grids offered by the foreground and background color pickers. They are static and do not
 * follow the modern gate: a picked color is stored as a plain hex, so which grid the editor
 * shows changes what can be clicked, not what an existing dashboard renders.
 *
 * Row 1 is a neutral ramp, row 2 the Modern palette's eight colors (VSChartPaletteDefaults
 * MODERN_HEAD), and rows 3 to 5 are lighter, lightest and darker steps of those eight, mixed in
 * OKLab toward white (35% and 65%) and toward black (35%).
 */
export class DefaultPalette {
   public static palette: ColorPalette = [
      ["#ffffff", "#dddddd", "#bbbbbb", "#999999", "#777777", "#555555", "#333333", "#000000"],
      ["#0490ff", "#ff5a35", "#241c4f", "#03d9b3", "#9a2ddc", "#ffb020", "#e5197e", "#8ed604"],
      ["#77b9ff", "#ff9a81", "#67668b", "#8ae7cd", "#bd82ed", "#ffcd85", "#f77fa9", "#b6e681"],
      ["#b7daff", "#ffcabb", "#aaaac0", "#c4f3e4", "#dbbdf7", "#ffe5bf", "#ffbbd0", "#d7f2be"],
      ["#014e8f", "#8f2f19", "#0f0b28", "#017963", "#54147b", "#8f610d", "#800944", "#4d7701"]];

   public static chart: ColorPalette = [
      ["#518db9", "#b9dbf4", "#62a640", "#ade095", "#fc8f2a", "#fde3a7", "#d64541", "#fda7a5"],
      ["#9368be", "#be90d4", "#95a5a6", "#dadfe1", "#19b5fe", "#c5eff7", "#869530", "#c8d96f"],
      ["#a88637", "#d2b267", "#019875", "#68c3a3", "#99CCFF", "#999933", "#CC9933", "#006666"],
      ["#993300", "#666666", "#663366", "#CCCCCC", "#669999", "#CCCC66", "#CC6600", "#9999FF"],
      ["#0066CC", "#FFCC00", "#009999", "#99CC33", "#FF9900", "#66CCCC", "#339966", "#CCCC33"]];

   // the same grid with a "no color" first cell; the neutral ramp keeps white and black and
   // drops one grey to make room
   public static fgWithTransparent: ColorPalette = [
      ["", "#ffffff", "#dddddd", "#bbbbbb", "#999999", "#666666", "#333333", "#000000"],
      DefaultPalette.palette[1], DefaultPalette.palette[2], DefaultPalette.palette[3],
      DefaultPalette.palette[4]];

   public static bgWithTransparent: ColorPalette = DefaultPalette.fgWithTransparent;

   public static bgWithNoTransparent: ColorPalette = DefaultPalette.palette;
}
