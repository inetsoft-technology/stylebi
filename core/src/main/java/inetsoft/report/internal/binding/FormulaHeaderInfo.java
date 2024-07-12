/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.report.internal.binding;

import inetsoft.uql.XMetaInfo;

/**
 * Formula header info, include the formula header name, its original column
 * header name, and the default created meta info for this formula header,
 * and the really meta info for this formula header is this meta info merge
 * with the original column's meta info.
 */
public class FormulaHeaderInfo {
   public FormulaHeaderInfo(String fname, String oname, boolean autoCreated, XMetaInfo minfo) {
      this.formualName = fname;
      this.originalName = oname;
      this.auto = autoCreated;
      this.metaInfo = minfo;
   }

   public String getFormualName() {
      return formualName;
   }

   public void setFormualName(String formualName) {
      this.formualName = formualName;
   }

   public String getOriginalName() {
      return originalName;
   }

   public void setOriginalName(String originalName) {
      this.originalName = originalName;
   }

   public boolean isAuto() {
      return auto;
   }

   public void setAuto(boolean auto) {
      this.auto = auto;
   }

   public XMetaInfo getMetaInfo() {
      return metaInfo;
   }

   public void setMetaInfo(XMetaInfo metaInfo) {
      this.metaInfo = metaInfo;
   }

   public String getOriginalColName() {
      return originalName;
   }

   public void setOriginalColName(String oname) {
      this.originalName = oname;
   }

   public boolean containsFormat() {
      return metaInfo != null && !metaInfo.isXFormatInfoEmpty();
   }

   public XMetaInfo merge(XMetaInfo info) {
      if(info == null) {
         return metaInfo;
      }

      info = info.clone();

      // user created formula, do not apply drill
      if(!auto) {
         info.setXDrillInfo(null);
      }

      // always apply range self's format
      if(metaInfo != null) {
         info.setXFormatInfo(metaInfo.getXFormatInfo());
      }

      return info;
   }

   public String toString() {
      return "[" + formualName + ", " + originalName + ", " + auto + ", " + metaInfo + "]";
   }

   private String formualName;
   private String originalName;
   private boolean auto;
   private XMetaInfo metaInfo;
}