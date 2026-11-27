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
package inetsoft.uql.xmla;

import inetsoft.util.Tool;

import java.io.Serializable;

/**
 * MemberObject represents a member, caption for label and unique name for
 * value.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
public final class MemberObject implements Serializable, Cloneable {
   /**
    * Create one instance of Member.
    */
   public MemberObject() {
      super();
   }

   /**
    * Create one instance of Member.
    */
   public MemberObject(String uName) {
      super();
      this.uName = uName;
   }

   /**
    * Create one instance of Member.
    */
   public MemberObject(String uName, String fCaption) {
      super();
      this.uName = uName;
      this.fullCaption = fCaption;
   }

   /**
    * String representation.
    */
   public String toString() {
      return uName;
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof MemberObject)) {
         return false;
      }

      MemberObject obj0 = (MemberObject) obj;
      return Tool.equals(uName, obj0.uName);
   }

   /**
    * Calculate the hash.
    */
   public int hashCode() {
      return uName.hashCode();
   }

   /**
    * Check if a string the identity of member object.
    */
   public boolean isIdentity(Object id) {
      if(id == null) {
         return false;
      }

      return XMLAUtil.isIdentity(id.toString(), this);
   }

   /**
    * Get a cloned object.
    * @return the cloned object, null if exception occurs.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
      }

      return null;
   }

   /**
    * Get the caption.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * Get the full caption.
    */
   public String getFullCaption() {
      return fullCaption;
   }

   /**
    * Get the text to parse as a date for an as-date level. The date pattern describes the
    * member caption ("1997", "01/15/26"), while the full caption of a member may be a bracketed
    * unique-name path ("[1997]", "[26].[01/26].[01/15/26]") that no date pattern can parse.
    * @return the caption, else the last segment of the full caption path, else the unique
    * name, null if the member has none of them.
    */
   public String getDateText() {
      if(caption != null) {
         return caption;
      }

      if(fullCaption != null) {
         return lastSegment(fullCaption);
      }

      return uName == null ? null : lastSegment(uName);
   }

   /**
    * Get the last segment of a bracketed unique-name path, with the escaped "]]" restored to
    * "]". A text that is not a well-formed bracketed path is returned as is.
    */
   private static String lastSegment(String path) {
      int len = path.length();
      int i = 0;
      String last = null;

      while(i < len && path.charAt(i) == '[') {
         StringBuilder seg = new StringBuilder();
         i++;
         boolean closed = false;

         while(i < len) {
            char ch = path.charAt(i++);

            if(ch == ']') {
               if(i < len && path.charAt(i) == ']') {
                  seg.append(']');
                  i++;
               }
               else {
                  closed = true;
                  break;
               }
            }
            else {
               seg.append(ch);
            }
         }

         if(!closed) {
            return path;
         }

         last = seg.toString();

         if(i == len) {
            return last;
         }

         if(path.charAt(i) != '.') {
            return path;
         }

         i++;
      }

      return path;
   }

   /**
    * Get the hierarchy.
    */
   public String getHierarchy() {
      return hierarchy;
   }

   /**
    * Get the unique name.
    */
   public String getUName() {
      return uName;
   }

   /**
    * Get the last name.
    */
   public String getLName() {
      return lName;
   }

   /**
    * Get a string for display purpose.
    */
   public String toView() {
      if(fullCaption != null) {
         return fullCaption;
      }

      if(caption != null) {
         return caption;
      }

      return toString();
   }

   String hierarchy;
   String lName;
   String caption;
   String fullCaption;
   String parent;
   String uName;

   int plNum = -1;
   int lNum = -1;
}