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
package inetsoft.uql;

import java.util.Vector;
import java.util.stream.Stream;

/**
 * A HierarchyList stores a list of HierarchyItem to be applied to
 * the resulting data.
 *
 * @version 5.1, 9/20/2003
 * @since 5.1
 * @author InetSoft Technology Corp
 */
public abstract class HierarchyList implements ConditionListWrapper {
   /**
    * Get the size.
    * @return size of the ConditionList.
    */
   public int getSize() {
      return list.size();
   }

   /**
    * Get the size.
    * @return size of the ConditionList.
    */
   @Override
   public int getConditionSize() {
      return getSize();
   }

   /**
    * Set the HierarchyItem at the specified index.
    * @param index the specified index.
    * @param item the HierarchyItem at the specified index.
    */
   public void setItem(int index, HierarchyItem item) {
      if(index >= 0 && index < getSize()) {
         list.set(index, item);
      }
   }

   /**
    * Get the HierarchyItem at the specified index.
    * @param index the specified index.
    * @return the HierarchyItem at the specified index.
    */
   @Override
   public HierarchyItem getItem(int index) {
      if(index >= 0 && index < getSize()) {
         return (HierarchyItem) list.elementAt(index);
      }
      else {
         return null;
      }
   }

   /**
    * Check if the item at the specified index is a ConditionItem.
    * @param index the specified index.
    * @return true if is a ConditionItem.
    */
   @Override
   public boolean isConditionItem(int index) {
      return index >= 0 && index < getSize() && (index % 2 == 0);
   }

   /**
    * Check if the item at the specified index is a JunctionOperator.
    * @param index the specified index.
    * @return true if is a JunctionOperator.
    */
   @Override
   public boolean isJunctionOperator(int index) {
      return index >= 0 && index < getSize() && (index % 2 == 1);
   }

   /**
    * Append a HierarchyItem.
    * @param item the HierarchyItem to append.
    */
   public void append(HierarchyItem item) {
      list.add(item);
   }

   /**
    * Append a list of items separated by op at the end of this list.
    */
   public void append(HierarchyList items, JunctionOperator op) {
      list.add(op);
      items.stream().forEach(o -> list.add(o));
   }

   /**
    * Insert a HierarchyItem at the specified index.
    * @param index the specified index.
    * @param item the HierarchyItem to insert.
    */
   public void insert(int index, HierarchyItem item) {
      if(index >= 0 && index < getSize()) {
         list.insertElementAt(item, index);
      }
      else if(index >= getSize()) {
         append(item);
      }
   }

   /**
    * Remove a HierarchyItem at the specified index.
    * @param index the specified index.
    */
   public void remove(int index) {
      if(index >= 0 && index < getSize()) {
         list.remove(index);
      }
   }

   /**
    * Remove all items in the list.
    */
   public void removeAllItems() {
      list.clear();
   }

   /**
    * Replace the HierarchyItem's level at the specified index.
    * @param index the specified index.
    * @param level the new level.
    */
   public void setLevel(int index, int level) {
      if(index >= 0 && index < getSize()) {
         HierarchyItem item = getItem(index);
         item.setLevel(level);
      }
   }

   /**
    * Get the HierarchyItem's level at the specified index.
    * @param incr the increment (or decrement) of the level on each item.
    */
   public void indent(int incr) {
      for(int i = 0; i < getSize(); i++) {
         HierarchyItem item = getItem(i);
         item.setLevel(item.getLevel() + incr);
      }
   }

   /**
    * Check if is in a valid order.
    * @return true if in a valid order.
    */
   public boolean isValid() {
      if(getSize() != 0 && getSize() % 2 != 1) {
         return false;
      }

      for(int i = 0; i < getSize(); i++) {
         if(i % 2 == 0 && !isConditionItem(i)) {
            return false;
         }

         if(i % 2 == 1 && !isJunctionOperator(i)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Trim the ConditionList to make it valid.
    */
   public void trim() {
      int index = getSize() - 1;

      if(isJunctionOperator(index)) {
         list.remove(index);
      }
   }

   /**
    * Validate the ConditionList.
    */
   public void validate() {
      validate(false);
   }

   /**
    * Validate the ConditionList.
    * @param rmignore remove the ignored condition items
    */
   public void validate(boolean rmignore) {
      trim();

      if(rmignore) {
         for(int i = 0; i < getSize(); i++) {
            if(!(getItem(i) instanceof ConditionItem)) {
               continue;
            }

            ConditionItem item = (ConditionItem) getItem(i);
            XCondition xcond = item.getXCondition();

            if(!(xcond instanceof Condition)) {
               continue;
            }

            Condition cond = (Condition) xcond;

            if(cond != null && cond.isIgnored()) {
               int level = item.getLevel();
               remove(i);

               if(i > 0 && getItem(i - 1).getLevel() == level) {
                  remove(i - 1);
                  i--;
               }
               else if(i < getSize() - 1) {
                  remove(i);
               }

               i--;
            }
         }
      }

      trim();

      int size = getSize();

      // indent junction item
      int max = getMaxLevel();

      for(int i = max - 1; i >= 0; i--) {
         boolean existing = isLevelExisting(i);

         if(!existing) {
            for(int j = 1; j < size; j += 2) {
               HierarchyItem item = getItem(j);

               if(item.getLevel() > i) {
                  item.setLevel(item.getLevel() - 1);
               }
            }
         }
      }

      // validate condition item
      for(int i = 0; i < size; i += 2) {
         HierarchyItem item = getItem(i);
         int level = 0;

         if(i - 1 >= 0) {
            HierarchyItem op = getItem(i - 1);
            level = Math.max(level, op.getLevel());
         }

         if(i + 1 < size) {
            HierarchyItem op = getItem(i + 1);
            level = Math.max(level, op.getLevel());
         }

         item.setLevel(level);
      }
   }

   /**
    * Check if the level exists.
    */
   private boolean isLevelExisting(int level) {
      for(int i = 1; i < getSize(); i += 2) {
         HierarchyItem item = getItem(i);

         if(item.getLevel() == level) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get max level.
    * @return the minimum level.
    */
   protected int getMaxLevel() {
      if(getSize() <= 0) {
         return 0;
      }

      int level = 0;

      for(int i = 1; i < getSize(); i += 2) {
         HierarchyItem item = getItem(i);
         level = item.getLevel() > level ? item.getLevel() : level;
      }

      return level;
   }

   /**
    * Get minimum level.
    * @return the minimum level.
    */
   protected int getMinLevel() {
      if(getSize() <= 0) {
         return 0;
      }

      int level = Integer.MAX_VALUE;

      for(int i = 1; i < getSize(); i += 2) {
         HierarchyItem item = getItem(i);
         level = item.getLevel() < level ? item.getLevel() : level;
      }

      return level;
   }

   /**
    * Check if this list is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return getSize() == 0;
   }

   public Stream<Object> stream() {
      return list.stream();
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public abstract Object clone();

   protected Vector<Object> list = new Vector<>();
}
