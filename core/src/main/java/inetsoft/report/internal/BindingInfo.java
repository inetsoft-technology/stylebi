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
package inetsoft.report.internal;

import inetsoft.report.SectionLens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Section name and row number.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BindingInfo implements java.io.Serializable, Cloneable {
   /**
    * Section header type.
    */
   public static final String HEADER = SectionLens.HEADER;
   /**
    * Section content type.
    */
   public static final String CONTENT = SectionLens.CONTENT;
   /**
    * Section footer type.
    */
   public static final String FOOTER = SectionLens.FOOTER;

   public BindingInfo(String id, int row, Object type, int level) {
      this.id = id;
      this.row = row;
      this.level = (short) level;

      // @by larryl, optimization, save 24 bites by storing type as byte
      this.type = findType(type);
   }

   /**
    * Get the section element ID.
    */
   public String getID() {
      return id;
   }

   /**
    * Get the row number in the data table this element is bound to.
    */
   public int getRow() {
      return row;
   }

   /**
    * Get the band type this element belongs to.
    */
   public Object getType() {
      return TYPES[type];
   }

   /**
    * Get the grouping level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Get the decimal points of this column. This attribute is shared by all
    * BindingInfo for one element.
    */
   public int getDecimalPoints() {
      return (decimal == null) ? 0 : decimal[0];
   }

   /**
    * Set the decimal points.
    */
   public void setDecimalPoints(int dot) {
      decimal[0] = (short) dot;
   }

   /**
    * Create a section info object. The new object shares the same decimal
    * points property as the original object.
    */
   public BindingInfo clone(int row, Object type, int level) {
      try {
         BindingInfo info = (BindingInfo) super.clone();
         info.row = row;
         info.type = findType(type);
         info.level = (short) level;

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone binding info", ex);
      }

      return null;
   }

   public boolean equals(Object obj) {
      if(obj instanceof BindingInfo) {
         BindingInfo info = (BindingInfo) obj;

         return id.equals(info.id) && row == info.row &&
            type == info.type && level == info.level;
      }

      return false;
   }

   public int hashCode() {
      return id.hashCode() + row;
   }

   /**
    * Find type index.
    */
   private byte findType(Object type) {
      if(type.equals(TYPES[0])) {
         return (byte) 0;
      }
      else if(type.equals(TYPES[1])) {
         return (byte) 1;
      }
      else if(type.equals(TYPES[2])) {
         return (byte) 2;
      }

      return 0;
   }

   private static final String[] TYPES = {CONTENT, HEADER, FOOTER};

   private String id;
   private int row;
   private byte type;
   private short level;
   private short[] decimal = {0};

   private static final Logger LOG =
      LoggerFactory.getLogger(BindingInfo.class);
}
