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
package inetsoft.web.graph.model;

import java.awt.*;
import java.awt.geom.RectangularShape;
import java.util.ArrayList;
import java.util.List;

public class LegendContainer {
   public LegendContainer(int legendIndex, RectangularShape bounds, String border,
                          String field, List<String> targetFields, Dimension minSize,
                          List<Legend> legendObjects, String aestheticType)
   {
      this(legendIndex, bounds, border, field, targetFields, minSize, legendObjects, aestheticType,
         false);
   }

   public LegendContainer(int legendIndex, RectangularShape bounds, String border,
                          String field, List<String> targetFields, Dimension minSize,
                          List<Legend> legendObjects, String aestheticType, boolean nodeAesthetic)
   {
      this.legendIndex = legendIndex;
      this.bounds = bounds;
      this.border = border;
      this.field = field;
      this.targetFields = targetFields;
      this.minSize = minSize;
      this.legendObjects = legendObjects;
      this.aestheticType = aestheticType;
      this.nodeAesthetic = nodeAesthetic;
   }

   public int getLegendIndex() {
      return legendIndex;
   }

   public RectangularShape getBounds() {
      return bounds;
   }

   public String getBorder() {
      return border;
   }

   public String getField() {
      return field;
   }

   public List<String> getTargetFields() {
      return targetFields;
   }

   public List<Legend> getLegendObjects() {
      return legendObjects;
   }

   public String getAestheticType() {
      return aestheticType;
   }

   public Dimension getMinSize() {
      return minSize;
   }

   public boolean isNodeAesthetic() {
      return nodeAesthetic;
   }

   public void setNodeAesthetic(boolean nodeAesthetic) {
      this.nodeAesthetic = nodeAesthetic;
   }

   private int legendIndex;
   private RectangularShape bounds;
   private String border;
   private String field;
   private List<String> targetFields;
   private List<Legend> legendObjects = new ArrayList<>();
   private String aestheticType;
   private Dimension minSize;
   private boolean nodeAesthetic;
}
