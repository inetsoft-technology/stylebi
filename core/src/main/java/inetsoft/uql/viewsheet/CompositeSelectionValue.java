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
package inetsoft.uql.viewsheet;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CompositeSelectionValue is used to represent a selection value that is
 * composed of other values. In short, it is the folder (branch) node on
 * a selection tree.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CompositeSelectionValue extends SelectionValue {
   /**
    * Constructor.
    */
   public CompositeSelectionValue() {
      this(null, null);
   }

   /**
    * Constructor.
    */
   public CompositeSelectionValue(String label, String value) {
      super(label, value);

      list = new SelectionList();
   }

   /**
    * Get the selection list of selection value.
    * @return the selection list of selection value.
    */
   public SelectionList getSelectionList() {
      return list;
   }

   /**
    * Set the selection list of selection value.
    * @param list the selection list of selection value.
    */
   public void setSelectionList(SelectionList list) {
      this.list = list == null ? new SelectionList() : list;
   }

   /**
    * Sort the items in the list. The sorting may be delayed until the list is
    * sent to the client.
    * @param sortType sort type is one of SORT_ASC, SORT_DESC, and
    * SORT_SPECIFIC. The SORT_SPECIFIC sorts the selected items on top.
    */
   public void sort(int sortType) {
      // optimization, delay sorting
      sortPending = sortType;

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue val = list.getSelectionValue(i);

         if(val instanceof CompositeSelectionValue) {
            ((CompositeSelectionValue) val).sort(sortType);
         }
      }
   }

   /**
    * Find sub-tree with nodes matching the search string.
    * @param not true to return the selected values not matching the string.
    * Otherwise returns all matching values.
    */
   public CompositeSelectionValue findAll(String str, boolean not) {
      CompositeSelectionValue root = (CompositeSelectionValue) clone();
      root.list = list.findAll(str, not);
      return root;
   }

   /**
    * Check if the string matches.
    */
   @Override
   public boolean match(String str, boolean recursive) {
      if(super.match(str, false)) {
         return true;
      }

      if(recursive) {
         for(int i = 0; i < list.getSelectionValueCount(); i++) {
            SelectionValue val = list.getSelectionValue(i);

            if(val.match(str, true)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the string matches.
    */
   @Override
   public boolean contains(String str, boolean recursive) {
      if(super.contains(str, false)) {
         return true;
      }

      if(recursive) {
         for(int i = 0; i < list.getSelectionValueCount(); i++) {
            SelectionValue val = list.getSelectionValue(i);

            if(val.contains(str, true)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Make sure pending sort is performed.
    */
   private void validateSort() {
      if(sortPending != 0) {
         list.sort(sortPending);
         sortPending = 0;
      }
   }

   /**
    * Merge the values in the composite values.
    */
   public void mergeSelectionValue(CompositeSelectionValue cvalue) {
      list.mergeSelectionList(cvalue.list);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "CompositeSelectionValue("+ getLabel() + ", " + getLevel() + " " + list + ")";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         CompositeSelectionValue value =
            (CompositeSelectionValue) super.clone();

         if(list != null) {
            value.list = (SelectionList) list.clone();
         }

         return value;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CompositeSelectionValue", ex);
      }

      return null;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output, int start, int count)
      throws IOException
   {
      writeAttributes(output, null);
      writeContents(output, start, count);
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int start, int count)
      throws IOException
   {
      super.writeContents(output, 1, null, false);

      output.writeBoolean(true);
      validateSort();
      list.writeData(output, 0, start, count, list.getSelectionValueCount(),
                     false);
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList plist) throws IOException
   {
      writeContents(output, levels, plist, true);
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList plist, boolean containsFormat) throws IOException
   {
      super.writeContents(output, levels, plist, containsFormat);

      // if node is selected, load children otherwise selection state is wrong
      // on flash side on submit
      boolean flag = levels > 0 || isSelected();
      output.writeBoolean(flag);

      if(flag) {
         validateSort();
         list.writeData(output, levels - 1, 0, Integer.MAX_VALUE, containsFormat);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param levels the number of levels of nodes to write.
    * @param plist the list this value is on.
    */
   @Override
   protected void writeContents(PrintWriter writer, int levels,
                                SelectionList plist)
   {
      super.writeContents(writer, levels, plist);

      // if node is selected, load children otherwise selection state is wrong
      // on flash side on submit
      if(levels > 0 || isSelected()) {
         validateSort();
         list.writeXML(writer, levels - 1);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element snode = Tool.getChildNodeByTagName(elem, "SelectionList");
      list = new SelectionList();

      if(snode != null) {
         list.parseXML(snode);
      }
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CompositeSelectionValue)) {
         return false;
      }

      CompositeSelectionValue cval = (CompositeSelectionValue) obj;
      return list.equals(cval.list);
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReset() {
      if(super.requiresReset()) {
         return true;
      }

      return list != null && list.requiresReset();
   }

   /**
    * Get the number of selection values on this tree, excluding self.
    */
   public int getValueCount() {
      int cnt = list.getSelectionValueCount();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         if(list.getSelectionValue(i) instanceof CompositeSelectionValue) {
            CompositeSelectionValue cval = (CompositeSelectionValue)
               list.getSelectionValue(i);

            cnt += cval.getValueCount();
         }
      }

      return cnt;
   }

   /**
    * Get the selected objects.
    * @param level only the specified level or -1 to include all levels.
    * @param state the state that must be set in the node.
    * @param notstate the state that must not be set in the node.
    * @return the selected objects.
    */
   public List<SelectionValue> getSelectionValues(int level, final int state,
                                                  final int notstate)
   {
      final int level0 = level;
      final List<SelectionValue> list = new ArrayList<>();

      SelectionValueIterator iterator = new SelectionValueIterator(this) {
         @Override
         protected void visit(SelectionValue val, List<SelectionValue> pvals) {
            if((level0 < 0 || val.getLevel() == level0) &&
               (state & val.getState()) == state)
            {
               if((notstate & val.getState()) != 0) {
                  return;
               }

               list.add(val);
            }
         }
      };

      try {
         iterator.iterate();
      }
      catch(Exception ex) {
         // ignore it
      }

      return list;
   }

   /**
    * Set the level of the selection value.
    * @param level the level of the selection value.
    */
   @Override
   public void setLevel(int level) {
      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue svalue = list.getSelectionValue(i);
         svalue.setLevel(level + 1);
      }

      super.setLevel(level);
   }

   private SelectionList list;
   private int sortPending = 0;

   private static final Logger LOG =
      LoggerFactory.getLogger(CompositeSelectionValue.class);
}
