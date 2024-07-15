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
package inetsoft.uql.viewsheet;

import java.util.ArrayList;
import java.util.List;

/**
 * SelectionValueIterator iterates a <tt>SelectionValue</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class SelectionValueIterator {
   /**
    * Create a selection value iterator.
    * @param val the specified selection value.
    */
   public SelectionValueIterator(SelectionValue val) {
      super();

      this.val = val;
   }

   /**
    * Iterate a selection value, which might be a
    * <tt>CompositeSelectionValue</tt> or <tt>SelectionValue</tt>.
    */
   public void iterate() throws Exception {
      iterate0(val, new ArrayList<>());
   }

   /**
    * Iterate a selection value, which might be a
    * <tt>CompositeSelectionValue</tt> or <tt>SelectionValue</tt>.
    * @param val the specified selection value.
    * @param parents the parent nodes from the root to val.
    */
   private void iterate0(SelectionValue val, List<SelectionValue> parents) throws Exception {
      visit(val, parents);

      if(val instanceof CompositeSelectionValue) {
         CompositeSelectionValue cval = (CompositeSelectionValue) val;
         SelectionList list = cval.getSelectionList();
         SelectionValue[] values = list.getSelectionValues();

         List<SelectionValue> parents2 = new ArrayList<>(parents);
         parents2.add(val);

         for(SelectionValue value : values) {
            iterate0(value, parents2);
         }
      }
   }

   /**
    * Find the selection value in the tree.
    */
   public static SelectionValue find(SelectionValue tree, 
                                     final SelectionValue val,
                                     final List<SelectionValue> parents) 
   {
      final SelectionValue[] rc = {null};
      
      SelectionValueIterator iter = new SelectionValueIterator(tree) {
         @Override
         public void visit(SelectionValue val2, List<SelectionValue> parents2) throws Exception {
            if(val.getLevel() != val2.getLevel() ||
               !val.equalsValue(val2) || parents.size() != parents2.size())
            {
               return;
            }

            for(int i = 0; i < parents.size(); i++) {
               if(!parents.get(i).equalsValue(parents2.get(i))) {
                  return;
               }
            }

            rc[0] = val2;
            throw new RuntimeException("found");
         }
      };

      try {
         iter.iterate();
      }
      catch(Exception ex) {
         // ignore it
      }

      return rc[0];
   }

   /**
    * Iterate a selection value, which might be a
    * <tt>CompositeSelectionValue</tt> or <tt>SelectionValue</tt>.
    * @param val the specified selection value.
    * @param parents the parent nodes from the root to val.
    */
   protected abstract void visit(SelectionValue val, List<SelectionValue> parents) throws Exception;

   private SelectionValue val;
}
