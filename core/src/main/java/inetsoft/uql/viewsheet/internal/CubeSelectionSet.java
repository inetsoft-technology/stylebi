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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.xmla.MemberObject;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.Tool;

import java.util.Collection;

/**
 * CubeSelectionSet, compares objects using unique name.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CubeSelectionSet extends SelectionSet {
   /**
    * Constructor.
    */
   public CubeSelectionSet() {
      super();
   }

   /**
    * Constructor.
    */
   public CubeSelectionSet(Collection<Object> c) {
      this();
      this.addAll(c);
   }

   /**
    * Check if contains another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if contains the object, <tt>false</tt> otherwise.
    */
   @Override
   public boolean contains(Object obj) {
      if(super.contains(obj)) {
         return true;
      }

      // for backward compatibility, since state selection value in previous
      // versions are String value of full caption
      if(obj instanceof Object[]) {
         Object[] objects = (Object[]) obj;
         String[] captions = new String[objects.length];

         for(int i = 0; i < objects.length; i++) {
            if(objects[i] instanceof MemberObject) {
               captions[i] = ((MemberObject) objects[i]).getFullCaption();
            }
         }

         return contains0(captions);
      }

      if(obj == null) {
         return false;
      }

      String caption = obj instanceof MemberObject ?
         ((MemberObject) obj).getFullCaption() : obj.toString();

      if(contains0(caption)) {
         return true;
      }

      // fix bug1288866376680, process sap bc
      for(Object obj0 : this) {
         if(obj instanceof MemberObject && obj0 instanceof MemberObject) {
            MemberObject mobj = (MemberObject) obj;
            MemberObject mobj0 = (MemberObject) obj0;

            if(XMLAUtil.isIdentity(mobj.getFullCaption(), mobj0) ||
               XMLAUtil.isIdentity(mobj0.getFullCaption(), mobj))
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if contains another object.
    * @param mobj the specified object.
    * @return <tt>true</tt> if contains the object, <tt>false</tt> otherwise.
    */
   private boolean contains0(Object mobj) {
      for(Object obj : this) {
         if(!(obj instanceof MemberObject)) {
            continue;
         }

         String sval = ((MemberObject) obj).getUName();

         if(isSamePath(sval, mobj)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is same path.
    */
   private boolean isSamePath(String path, String caption) {
      return XMLAUtil.isDisplayFullCaption() ?
         caption != null && path.contains(caption) : Tool.equals(path, caption);
   }

      /**
    * Check if is same path.
    */
   private boolean isSamePath(String path, Object mobj) {
      if(mobj instanceof String) {
         return isSamePath(path, (String) mobj);
      }

      String[] captions = (String[]) mobj;
      String[] paths = Tool.split(path, '/');

      if(captions.length != paths.length) {
         return false;
      }

      for(int i = 0; i < paths.length; i++) {
         if(!isSamePath(paths[i], captions[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Add an object.
    * @param obj the specified object to add.
    * @return <tt>true</tt> if the object is not contained, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean add(Object obj) {
      if(obj instanceof String) {
         obj = new MemberObject((String) obj, (String) obj);
      }

      return super.add(obj);
   }
}
