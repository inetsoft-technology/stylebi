/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /api/wiz/viewsheet/colors} — sets chart COLORS in place on an existing
 * runtime chart and re-renders. The color mode is chosen from the chart's color binding; provide the
 * fields that match (static for no color binding; palette/list/categoryColors for a dimension on color;
 * paletteName as a gradient for a measure on color). All color fields are optional.
 */
public class ChartColorsRequest {
   public String getWizRuntimeId() { return wizRuntimeId; }
   public void setWizRuntimeId(String wizRuntimeId) { this.wizRuntimeId = wizRuntimeId; }

   public String getAssemblyName() { return assemblyName; }
   public void setAssemblyName(String assemblyName) { this.assemblyName = assemblyName; }

   public String getViewsheetIdentifier() { return viewsheetIdentifier; }
   public void setViewsheetIdentifier(String viewsheetIdentifier) { this.viewsheetIdentifier = viewsheetIdentifier; }

   /** Hex #RRGGBB — one color for the measure(s) when no field is on the color aesthetic. */
   public String getStaticColor() { return staticColor; }
   public void setStaticColor(String staticColor) { this.staticColor = staticColor; }

   /** A named palette (validated against ColorPalettes.getPaletteNames()); categorical or gradient by binding. */
   public String getPaletteName() { return paletteName; }
   public void setPaletteName(String paletteName) { this.paletteName = paletteName; }

   /** Ordered custom palette of hex colors (categorical). */
   public List<String> getColorList() { return colorList; }
   public void setColorList(List<String> colorList) { this.colorList = colorList; }

   /** Per-category overrides: dimension value -> hex (categorical). */
   public Map<String, String> getCategoryColors() { return categoryColors; }
   public void setCategoryColors(Map<String, String> categoryColors) { this.categoryColors = categoryColors; }

   /** When true, duplicate the assembly first (keeping the original untouched) and apply the color
    *  change to the new copy instead of in place. Defaults to false. */
   public boolean isCopy() { return copy; }
   public void setCopy(boolean copy) { this.copy = copy; }

   private String wizRuntimeId;
   private String assemblyName;
   private String viewsheetIdentifier;
   private String staticColor;
   private String paletteName;
   private List<String> colorList;
   private Map<String, String> categoryColors;
   private boolean copy;
}
