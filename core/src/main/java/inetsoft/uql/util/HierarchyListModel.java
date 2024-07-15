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
package inetsoft.uql.util;

import inetsoft.uql.*;

import javax.swing.*;

/**
 * HierarchyListModel provides the capability to edit a <tt>HierarchyList</tt>
 * properly. It's also a <tt>ListModel</tt>, so may be used as the list model
 * in a GUI.
 *
 * @version 8.0
 * @since 5.1
 * @author InetSoft Technology Corp
 */
public class HierarchyListModel extends AbstractListModel {
   /**
    * Constructor.
    */
   public HierarchyListModel() {
      super();
   }

   /**
    * Constructor.
    */
   public HierarchyListModel(HierarchyList hierarchyList) {
      this();
      setHierarchyList(hierarchyList);
   }

   /**
    * Set the <tt>HierarchyList</tt> to edit.
    * @param hierarchyList the specified <tt>HierarchyList</tt>.
    */
   protected void setHierarchyList(HierarchyList hierarchyList) {
      this.hierarchyList = hierarchyList;
   }

   /**
    * Get the <tt>HierarchyList</tt>.
    * @return the <tt>HierarchyList</tt> as a result.
    */
   public HierarchyList getHierarchyList() {
      return hierarchyList;
   }

   /**
    * Get the size.
    * @return size of the ConditionModel.
    */
   @Override
   public int getSize() {
      return hierarchyList.getSize();
   }

   /**
    * Get element at the specified index.
    * @param index the specified index.
    * @return the element at the specified index.
    */
   @Override
   public Object getElementAt(int index) {
      return hierarchyList.getItem(index);
   }

   /**
    * Append a HierarchyItem.
    * @param the HierarchyItem to append.
    */
   public void append(HierarchyItem elem) {
      int index = hierarchyList.getSize();

      hierarchyList.append(elem);
      fireIntervalAdded(this, index, index);
   }

   /**
    * Remove a HierarchyItem at the specified index.
    * @param index the specified index.
    */
   public void remove(int index) {
      if(index >= 0 && index < getSize()) {
         hierarchyList.remove(index);
         fireIntervalRemoved(this, index, index);
      }
   }

   /**
    * Remove all items in the ConditionModel.
    */
   public void removeAllItems() {
      if(hierarchyList.getSize() > 0) {
         int index = hierarchyList.getSize() - 1;

         hierarchyList.removeAllItems();
         fireIntervalRemoved(this, 0, index);
      }
   }

   /**
    * Replace the HierarchyItem's level at the specified index.
    * @param index the specified index.
    * @param level the new level.
    */
   public void setLevel(int index, int level) {
      if(index >= 0 && index < getSize()) {
         hierarchyList.setLevel(index, level);
         fireContentsChanged(this, index, index);
      }
   }

   /**
    * Check if the item at the specified index is a ConditionItem.
    * @param index the specified index.
    * @return true if is a ConditionItem.
    */
   public boolean isConditionItem(int index) {
      return hierarchyList.isConditionItem(index);
   }

   /**
    * Check if the item at the specified index is a JunctionOperator.
    * @param index the specified index.
    * @return true if is a JunctionOperator.
    */
   public boolean isJunctionOperator(int index) {
      return hierarchyList.isJunctionOperator(index);
   }

   /**
    * Check if the item at the specified index can be modified.
    * @param index the specified index.
    * @return true if can be modified.
    */
   public boolean isModifyable(int index) {
      return index >= 0 && index < getSize();
   }

   /**
    * Check if the item at the specified index can be removed.
    * @param index the specified index.
    * @return true if can be removed.
    */
   public boolean isRemoveable(int index) {
      return hierarchyList.isConditionItem(index);
   }

   /**
    * Remove the specified ConditionItem.
    * @param index the specified index.s
    */
   public void removeConditionItem(int index) {
      if(!isRemoveable(index)) {
         return;
      }

      // test if the last element
      if(getSize() == 1) {
         remove(index);
         return;
      }

      // common remove
      int ljidx = index - 1;
      int rjidx = index + 1;
      int rmjidx;

      if(ljidx < 0) {
         rmjidx = rjidx;
      }
      else if(rjidx >= getSize()) {
         rmjidx = ljidx;
      }
      else {
         if(hierarchyList.getItem(ljidx).getLevel() ==
            hierarchyList.getItem(index).getLevel()) {
            rmjidx = ljidx;
         }
         else {
            rmjidx = rjidx;
         }
      }

      unindentChildren(rmjidx);

      index = rmjidx < index ? rmjidx : index;
      remove(index + 1);
      remove(index);
      fixConditions();
   }

   /**
    * Check if the item at the specified index can be moved up.
    * @param index the item's index.
    * @return true if can be moved up.
    */
   public boolean isMoveUpable(int index) {
      return hierarchyList.isConditionItem(index) && (index > 0);
   }

   /**
    * Check if the item at the specified index can be moved down.
    * @param index the item's index.
    * @return true if can be moved down.
    */
   public boolean isMoveDownable(int index) {
      return hierarchyList.isConditionItem(index) &&
         (index < (getSize() - 1));
   }

   /**
    * Move the specified ConditionItem up.
    * @param index the index of the condition to move.
    */
   public int moveUp(int index) {
      if(isMoveUpable(index)) {
         return exchangeConditionItem(index, index - 2);
      }

      return index;
   }

   /**
    * Move the specified condition item down.
    * @param index the index of the condition to move.
    */
   public int moveDown(int index) {
      if(isMoveDownable(index)) {
         return exchangeConditionItem(index, index + 2);
      }

      return index;
   }

   /**
    * Exchange the two specified ConditionItem.
    * @param index the first item's index.
    * @param index2 the second item's index.
    */
   protected int exchangeConditionItem(int index, int index2) {
      throw new RuntimeException("not yet implemented!");
   }

   /**
    * Check if the item at the specified index can be indented.
    * @param the item's index.
    * @return true if can be indented.
    */
   public boolean isIndentable(int index) {
      boolean indentable = false;

      if(hierarchyList.isJunctionOperator(index)) {
         HierarchyItem op = hierarchyList.getItem(index);

         for(int i = index - 1; i >= 0; i--) {
            if(i % 2 == 1) { // is junction
               HierarchyItem item1 = hierarchyList.getItem(i);

               if(item1.getLevel() > op.getLevel()) {
                  continue;
               }
               else if(item1.getLevel() == op.getLevel()) {
                  indentable = true;
                  break;
               }
               else {
                  break;
               }
            }
         }

         if(!indentable) {
            for(int i = index + 1; i < getSize(); i++) {
               if(i % 2 == 1) { // is junction
                  HierarchyItem item1 = hierarchyList.getItem(i);

                  if(item1.getLevel() > op.getLevel()) {
                     continue;
                  }
                  else if(item1.getLevel() == op.getLevel()) {
                     indentable = true;
                     break;
                  }
                  else {
                     break;
                  }
               }
            }
         }
      }

      return indentable;
   }

   /**
    * Check if the item at the specified index can be unindented.
    * @param index the item's index.
    * @return true if can be unindented.
    */
   public boolean isUnindentable(int index) {
      boolean unindentable = false;

      if(hierarchyList.isJunctionOperator(index)) {
         HierarchyItem op = hierarchyList.getItem(index);

         unindentable = (op.getLevel() != ConditionList.ROOT_LEVEL);
      }

      return unindentable;
   }

   /**
    * Indent the item at the specified index.
    * @param index the item's index.
    */
   public void indent(int index) {
      if(isIndentable(index)) {
         HierarchyItem op = hierarchyList.getItem(index);

         indentChildren(index);
         setLevel(index, op.getLevel() + 1);
         fixConditions();
      }
   }

   /**
    * Unindent the item at the specified index.
    * @param index the item's index.
    */
   public void unindent(int index) {
      if(isUnindentable(index)) {
         HierarchyItem op = hierarchyList.getItem(index);

         unindentChildren(index);
         setLevel(index, op.getLevel() - 1);
         fixConditions();
      }
   }

   /**
    * Indent a JunctionOperator's children.
    * @param index the item's index.
    */
   private void indentChildren(int index) {
      HierarchyItem op = hierarchyList.getItem(index);

      for(int i = index - 1; i >= 0; i--) {
         HierarchyItem item = hierarchyList.getItem(i);

         if(item.getLevel() > op.getLevel() ||
            (item.getLevel() == op.getLevel() && i % 2 == 0)) {
            setLevel(i, item.getLevel() + 1);
         }
         else {
            break;
         }
      }

      for(int i = index + 1; i < getSize(); i++) {
         HierarchyItem item = hierarchyList.getItem(i);

         if(item.getLevel() > op.getLevel() ||
            (item.getLevel() == op.getLevel() && i % 2 == 0)) {
            setLevel(i, item.getLevel() + 1);
         }
         else {
            break;
         }
      }
   }

   /**
    * Unindent a JunctionOperator's children.
    * @param index the item's index.
    */
   private void unindentChildren(int index) {
      HierarchyItem op = hierarchyList.getItem(index);

      /* deleting a child shouldn't change the indentation of parent
      for(int i = index - 1; i >= 0; i--) {
         int level = hierarchyList.getItem(i).getLevel();

         if(level > op.getLevel() ||
            (level == op.getLevel() && i % 2 == 0 && level > 0))
         {
            setLevel(i, level - 1);
         }
         else {
            if(i % 2 == 1 && level > hierarchyList.getItem(i + 1).getLevel()) {
               setLevel(i + 1, level);
            }

            break;
         }
      }
      */

      for(int i = index + 1; i < getSize(); i++) {
         int level = hierarchyList.getItem(i).getLevel();

         if(level > op.getLevel() ||
            (level == op.getLevel() && i % 2 == 0 && level > 0)) {
            setLevel(i, level - 1);
         }
         else {
            if(i % 2 == 1 && level > hierarchyList.getItem(i - 1).getLevel()) {
               setLevel(i - 1, level);
            }

            break;
         }
      }
   }

   /**
    * Fix hierarchy list model children levels.
    */
   public void fixConditions() {
      for(int i = 0; i < getSize(); i += 2) {
         HierarchyItem item = hierarchyList.getItem(i);
         item.setLevel(0);

         if(i - 1 >= 0 &&
            hierarchyList.getItem(i - 1).getLevel() > item.getLevel())
         {
            item.setLevel(hierarchyList.getItem(i - 1).getLevel());
         }

         if(i + 1 < getSize() &&
            hierarchyList.getItem(i + 1).getLevel() > item.getLevel())
         {
            item.setLevel(hierarchyList.getItem(i + 1).getLevel());
         }
      }
   }

   protected HierarchyList hierarchyList;
}
