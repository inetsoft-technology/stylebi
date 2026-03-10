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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartBinding implements BindingInfo {
   public List<SimpleFieldInfo> getX() {
      return x;
   }

   public void setX(List<SimpleFieldInfo> x) {
      this.x = x;
   }

   public List<SimpleFieldInfo> getY() {
      return y;
   }

   public void setY(List<SimpleFieldInfo> y) {
      this.y = y;
   }

   public List<SimpleFieldInfo> getGroup() {
      return group;
   }

   public void setGroup(List<SimpleFieldInfo> group) {
      this.group = group;
   }

   public List<DimensionFieldInfo> getT() {
      return t;
   }

   public void setT(List<DimensionFieldInfo> t) {
      this.t = t;
   }

   public SimpleFieldInfo getColor() {
      return color;
   }

   public void setColor(SimpleFieldInfo color) {
      this.color = color;
   }

   public SimpleFieldInfo getShape() {
      return shape;
   }

   public void setShape(SimpleFieldInfo shape) {
      this.shape = shape;
   }

   public SimpleFieldInfo getSize() {
      return size;
   }

   public void setSize(SimpleFieldInfo size) {
      this.size = size;
   }

   public SimpleFieldInfo getText() {
      return text;
   }

   public void setText(SimpleFieldInfo text) {
      this.text = text;
   }

   public SimpleFieldInfo getPath() {
      return path;
   }

   public void setPath(SimpleFieldInfo path) {
      this.path = path;
   }

   public MeasureFieldInfo getHigh() {
      return high;
   }

   public void setHigh(MeasureFieldInfo high) {
      this.high = high;
   }

   public MeasureFieldInfo getLow() {
      return low;
   }

   public void setLow(MeasureFieldInfo low) {
      this.low = low;
   }

   public MeasureFieldInfo getClose() {
      return close;
   }

   public void setClose(MeasureFieldInfo close) {
      this.close = close;
   }

   public MeasureFieldInfo getOpen() {
      return open;
   }

   public void setOpen(MeasureFieldInfo open) {
      this.open = open;
   }

   public DimensionFieldInfo getStart() {
      return start;
   }

   public void setStart(DimensionFieldInfo start) {
      this.start = start;
   }

   public DimensionFieldInfo getEnd() {
      return end;
   }

   public void setEnd(DimensionFieldInfo end) {
      this.end = end;
   }

   public DimensionFieldInfo getMilestone() {
      return milestone;
   }

   public void setMilestone(DimensionFieldInfo milestone) {
      this.milestone = milestone;
   }

   public DimensionFieldInfo getSource() {
      return source;
   }

   public void setSource(DimensionFieldInfo source) {
      this.source = source;
   }

   public DimensionFieldInfo getTarget() {
      return target;
   }

   public void setTarget(DimensionFieldInfo target) {
      this.target = target;
   }

   public NodeBinding getNode() {
      return node;
   }

   public void setNode(NodeBinding node) {
      this.node = node;
   }

   public List<SimpleFieldInfo> getLongitude() {
      return longitude;
   }

   public void setLongitude(List<SimpleFieldInfo> longitude) {
      this.longitude = longitude;
   }

   public List<SimpleFieldInfo> getLatitude() {
      return latitude;
   }

   public void setLatitude(List<SimpleFieldInfo> latitude) {
      this.latitude = latitude;
   }

   public List<SimpleFieldInfo> getGeo() {
      return geo;
   }

   public void setGeo(List<SimpleFieldInfo> geo) {
      this.geo = geo;
   }

   private List<SimpleFieldInfo> x;
   private List<SimpleFieldInfo> y;
   private List<SimpleFieldInfo> group;
   private List<DimensionFieldInfo> t;

   private SimpleFieldInfo color;
   private SimpleFieldInfo shape;
   private SimpleFieldInfo size;
   private SimpleFieldInfo text;
   private SimpleFieldInfo path;

   // Stock
   private MeasureFieldInfo high;
   private MeasureFieldInfo low;
   private MeasureFieldInfo close;
   private MeasureFieldInfo open;

   // Gantt
   private DimensionFieldInfo start;
   private DimensionFieldInfo end;
   private DimensionFieldInfo milestone;

   // Tree
   private DimensionFieldInfo source;
   private DimensionFieldInfo target;
   private NodeBinding node;

   private List<SimpleFieldInfo> longitude;
   private List<SimpleFieldInfo> latitude;
   private List<SimpleFieldInfo> geo;

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class NodeBinding {
      public SimpleFieldInfo getColor() {
         return color;
      }

      public void setColor(SimpleFieldInfo color) {
         this.color = color;
      }

      public SimpleFieldInfo getSize() {
         return size;
      }

      public void setSize(SimpleFieldInfo size) {
         this.size = size;
      }

      public SimpleFieldInfo getText() {
         return text;
      }

      public void setText(SimpleFieldInfo text) {
         this.text = text;
      }

      private SimpleFieldInfo color;
      private SimpleFieldInfo size;
      private SimpleFieldInfo text;
   }
}
