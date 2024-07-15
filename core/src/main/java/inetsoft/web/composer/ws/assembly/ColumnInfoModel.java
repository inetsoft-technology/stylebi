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
package inetsoft.web.composer.ws.assembly;

import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.web.binding.drm.ColumnRefModel;

public class ColumnInfoModel {
   public ColumnInfoModel(){}

   public ColumnInfoModel(ColumnInfo info, int index) {
      setRef(new ColumnRefModel(info.getColumnRef()));
      setName(info.getName());
      setAlias(info.getAlias());
      setAssembly(info.getAssembly());
      setFormat(info.getFormat());
      setHeader(info.getHeader());
      setVisible(info.isVisible());
      setAggregate(info.isAggregate());
      setGroup(info.isGroup());
      setCrosstab(info.isCrosstab());
      setSortType(info.getSortType());
      setTimeSeries(info.isTimeSeries());
      setIndex(index);

      if(info.getPixelWidth() == 0) {
         info.setPixelWidth(150);
      }

      setWidth(info.getPixelWidth());
   }

   public ColumnRefModel getRef() {
      return ref;
   }

   public void setRef(ColumnRefModel ref) {
      this.ref = ref;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getAssembly() {
      return assembly;
   }

   public void setAssembly(String assembly) {
      this.assembly = assembly;
   }

   public String getFormat() {
      return format;
   }

   public void setFormat(String format) {
      this.format = format;
   }

   public String getHeader() {
      return header;
   }

   public void setHeader(String header) {
      this.header = header;
   }

   public int getSortType() {
      return sortType;
   }

   public void setSortType(int sortType) {
      this.sortType = sortType;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   public boolean isAggregate() {
      return aggregate;
   }

   public void setAggregate(boolean aggregate) {
      this.aggregate = aggregate;
   }

   public boolean isGroup() {
      return group;
   }

   public void setGroup(boolean group) {
      this.group = group;
   }

   public boolean isCrosstab() {
      return crosstab;
   }

   public void setCrosstab(boolean crosstab) {
      this.crosstab = crosstab;
   }

   public int getWidth() {
      return width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public boolean isTimeSeries() {
      return timeseries;
   }

   public void setTimeSeries(boolean timeseries) {
      this.timeseries = timeseries;
   }

   public int getIndex() {
      return index;
   }

   public void setIndex(int index) {
      this.index = index;
   }

   private ColumnRefModel ref;
   private String name;
   private String alias;
   private String assembly;
   private String format;
   private String header;
   private boolean visible;
   private boolean aggregate;
   private boolean group;
   private boolean crosstab;
   private int sortType;
   private int width;
   private boolean timeseries;
   private int index;
}
