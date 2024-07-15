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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.GradientColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.GradientColorFrameWrapper;
import inetsoft.util.Tool;

public class GradientColorModel extends ColorFrameModel {
   public GradientColorModel() {
   }

   public GradientColorModel(GradientColorFrameWrapper wrapper) {
      super(wrapper);
      GradientColorFrame frame = (GradientColorFrame) wrapper.getVisualFrame();
      setFromColor(Tool.toString(frame.getUserFromColor()));
      setCssFromColor(Tool.toString(frame.getCssFromColor()));
      setDefaultFromColor(Tool.toString(frame.getDefaultFromColor()));
      setToColor(Tool.toString(frame.getUserToColor()));
      setCssToColor(Tool.toString(frame.getCssToColor()));
      setDefaultToColor(Tool.toString(frame.getDefaultToColor()));
   }

   public void setFromColor(String from) {
      this.fromColor = from;
   }

   public String getFromColor() {
      return fromColor;
   }

   public String getCssFromColor() {
      return cssFromColor;
   }

   public void setCssFromColor(String css) {
      this.cssFromColor = css;
   }

   public String getDefaultFromColor() {
      return defaultFromColor;
   }

   public void setDefaultFromColor(String defaultColor) {
      this.defaultFromColor = defaultColor;
   }

   public void setToColor(String to) {
      this.toColor = to;
   }

   public String getToColor() {
      return toColor;
   }

   public String getCssToColor() {
      return cssToColor;
   }

   public void setCssToColor(String css) {
      this.cssToColor = css;
   }

   public String getDefaultToColor() {
      return defaultToColor;
   }

   public void setDefaultToColor(String defaultColor) {
      this.defaultToColor = defaultColor;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new GradientColorFrame();
   }

   private String fromColor;
   private String cssFromColor;
   private String defaultFromColor;
   private String toColor;
   private String cssToColor;
   private String defaultToColor;
}
