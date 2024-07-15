/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.util.*;

/**
 * EnumEnumeration, encapsulates a list of enumerations and performs as an
 * enumeration.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class EnumEnumeration<T> implements Enumeration<T> {
   /**
    * Constructor.
    * @param enums the specified enumeration array.
    */
   public EnumEnumeration(Enumeration<T>[] enums) {
      this(Arrays.asList(enums));
   }

   /**
    * Constructor.
    * @param enums the specified enumeration list.
    */
   public EnumEnumeration(List<? extends Enumeration<T>> enums) {
      super();
      this.enums = enums;
      this.idx = 0;
   }

   /**
    * Check if has more elements.
    * @return <tt>true</tt> if has more elements, <tt>false</tt> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      prepareEnumeration();
      return curr != null;
   }

   /**
    * Get the next element.
    * @return the next element.
    */
   @Override
   public T nextElement() {
      return curr.nextElement();
   }

   /**
    * Prepare an available enumeration.
    */
   private void prepareEnumeration() {
      if(curr != null && curr.hasMoreElements()) {
         return;
      }

      if(curr instanceof DisposableEnumeration) {
         ((DisposableEnumeration) curr).dispose();
      }

      curr = null;

      while(idx < enums.size()) {
         Enumeration<T> tenum = enums.get(idx++);
   
         if(tenum.hasMoreElements()) {
            curr = tenum;
            break;
         }
      }
   }

   private List<? extends Enumeration<T>> enums;
   private Enumeration<T> curr;
   private int idx;
}
