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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.ref.SoftReference;
import java.util.ArrayList;

/**
 * Sort info contains the sort information of a <tt>TableAssembly</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SortInfo implements AssetObject, ContentObject {
   /**
    * Constructor.
    */
   public SortInfo() {
      super();
      sorts = new ArrayList<>();
   }

   /**
    * Get the sort ref.
    * @param ref the specified attribute.
    * @return the sort ref of the attribute.
    */
   public SortRef getSort(DataRef ref) {
      int index = sorts.indexOf(ref);
      return index == -1 ? null : sorts.get(index);
   }

   /**
    * Get the sort ref.
    * @param index the specified index.
    * @return the sort ref of the attribute.
    */
   public SortRef getSort(int index) {
      return sorts.get(index);
   }

   /**
    * Get all the sort refs.
    * @return all the sort refs.
    */
   public SortRef[] getSorts() {
      SortRef[] refs = new SortRef[sorts.size()];
      sorts.toArray(refs);
      return refs;
   }

   /**
    * Get the sort ref count.
    * @return the sort ref count.
    */
   public int getSortCount() {
      return sorts.size();
   }

   /**
    * Check if contains the sort ref.
    * @param ref the specified attribute.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsSort(DataRef ref) {
      return sorts.contains(ref);
   }

   /**
    * Add one sort ref.
    * @param ref the specified attribute.
    */
   public void addSort(SortRef ref) {
      addSort(-1, ref);
   }

   /**
    * Add one sort ref.
    * @param ref the specified attribute.
    * @param index the specified index.
    */
   public synchronized void addSort(int index, SortRef ref) {
      int oindex = sorts.indexOf(ref);

      if(oindex >= 0) {
         sorts.remove(ref);
         sorts.add(oindex, ref);
      }
      else if(index < 0) {
         sorts.add(ref);
      }
      else {
         sorts.add(index, ref);
      }

      PropertyChangeListener listener = getListener();

      if(listener != null) {
         listener.propertyChange(new PropertyChangeEvent(this,
            "sortRef", ref, ref));
      }
   }

   /**
    * Remove the sort ref.
    * @param ref the specified attribute.
    */
   public synchronized void removeSort(DataRef ref) {
      sorts.remove(ref);
   }

   /**
    * Remove the sort ref.
    * @param index the specified index.
    */
   public synchronized void removeSort(int index) {
      sorts.remove(index);
   }

   /**
    * Clear all the sort refs.
    */
   public synchronized void clear() {
      sorts.clear();
      tempSort = true;
   }

   /**
    * Check if the sort info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return sorts.size() == 0;
   }

   /**
    * Validate the group info.
    * @param columns the specified column selection.
    */
   public synchronized void validate(ColumnSelection columns) {
      for(int i = sorts.size() - 1; i >= 0; i--) {
         SortRef sort = sorts.get(i);
         int index = columns.indexOfAttribute(sort);

         if(index < 0) {
            DataRef ref = columns.getAttribute(sort.getDataRef().getName());

            if(ref != null) {
               sort.setDataRef(ref);
            }
            else {
               sorts.remove(i);
            }
         }
         else {
            sort.refreshDataRef(columns);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<sortInfo>");

      for(int i = 0; i < sorts.size(); i++) {
         SortRef ref = sorts.get(i);
         ref.writeXML(writer);
      }

      writer.println("</sortInfo>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public synchronized void parseXML(Element elem) throws Exception {
      NodeList snodes = Tool.getChildNodesByTagName(elem, "dataRef");

      for(int i = 0; i < snodes.getLength(); i++) {
         Element snode = (Element) snodes.item(i);
         SortRef ref = (SortRef) AbstractDataRef.createDataRef(snode);
         sorts.add(ref);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SortInfo sinfo = (SortInfo) super.clone();
         sinfo.sorts = cloneDataRefList(sorts);

         return sinfo;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clone data ref list.
    */
   private final ArrayList cloneDataRefList(ArrayList list) {
      int size = list.size();
      ArrayList list2 = new ArrayList(size);

      for(int i = 0; i < size; i++) {
         if(list.get(i) != null) {
            Object obj = ((DataRef) list.get(i)).clone();
            list2.add(obj);
         }
      }

      return list2;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof SortInfo)) {
         return false;
      }

      SortInfo sinfo = (SortInfo) obj;

      return sorts.equals(sinfo.sorts);
   }

   /**
    * Get the hash code.
    * @return the hash code.
    */
   public int hashCode() {
      return sorts.hashCode();
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "SortInfo:" + sorts;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("SORTS[");
      int cnt = getSortCount();
      writer.print(cnt);

      for(int i = 0; i < cnt; i++) {
         writer.print(",");
         SortRef sort = getSort(i);
         sort.printKey(writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof SortInfo)) {
         return false;
      }

      SortInfo sinfo = (SortInfo) obj;

      if(getSortCount() != sinfo.getSortCount()) {
         return false;
      }

      for(int i = 0; i < getSortCount(); i++) {
         SortRef sort1 = getSort(i);
         SortRef sort2 = sinfo.getSort(i);

         if(!sort1.equalsSort(sort2)) {
            return false;
         }
      }

      return true;
   }

      /**
    * Set the listener to monitor the change of column selection.
    */
   public void setListener(PropertyChangeListener listener) {
      if(listener == null) {
         this.listener = null;
      }
      else {
         this.listener = new SoftReference(listener);
      }
   }

   /**
    * Get the listener to monitor the change of column selection.
    */
   public PropertyChangeListener getListener() {
      return listener == null ? null : (PropertyChangeListener) listener.get();
   }

   /**
    * Set whether to apply a temporary sort filter to the currently loaded data
    */
   public void setTempSort(boolean tempSort) {
      this.tempSort = tempSort;
   }

   /**
    * Get whether to apply a temporary sort filter to the currently loaded data
    */
   public boolean isTempSort() {
      return tempSort;
   }

   private ArrayList<SortRef> sorts;
   private transient SoftReference listener;
   private transient boolean tempSort = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(SortInfo.class);
}
