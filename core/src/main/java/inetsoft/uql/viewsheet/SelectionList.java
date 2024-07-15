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
package inetsoft.uql.viewsheet;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import inetsoft.report.filter.ImmutableDefaultComparer;
import inetsoft.report.filter.TextComparer;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import inetsoft.util.swap.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.text.Format;
import java.util.*;
import java.util.function.Function;

/**
 * SelectionList stores selection list values and states.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionList extends XSwappable implements AssetObject, DataSerializable {
   /**
    * Constructor.
    */
   public SelectionList() {
      super();

      list = new ArrayList<>();
   }

   /**
    * Get the selection values of selection list.
    * @return the selection values of selection list.
    */
   public synchronized SelectionValue[] getSelectionValues() {
      return getList().toArray(new SelectionValue[0]);
   }

   /**
    * Get all selection values, including the values under composite selection
    * values.
    */
   public synchronized SelectionValue[] getAllSelectionValues() {
      ArrayList<SelectionValue> vals = new ArrayList<>();
      getAllSelectionValues0(this, vals, new HashSet<>());

      return vals.toArray(new SelectionValue[0]);
   }

   /**
    * Collection all selection values.
    */
   private void getAllSelectionValues0(SelectionList slist, List<SelectionValue> vals,
                                       Set<SelectionValue> added)
   {
      List<SelectionValue> list = slist.getList();

      for(SelectionValue sval : list) {
         if(added.add(sval)) {
            vals.add(sval);
         }

         if(sval instanceof CompositeSelectionValue) {
            getAllSelectionValues0(((CompositeSelectionValue) sval).getSelectionList(),
                                   vals, added);
         }
      }
   }

   /**
    * Set the selection values of selection list.
    * @param values the selection values of selection list.
    */
   public synchronized void setSelectionValues(SelectionValue[] values) {
      ArrayList<SelectionValue> list = new ArrayList<>();

      for(int i = 0; values != null && i < values.length; i++) {
         list.add(values[i]);
      }

      this.list = list;
      valid = true;
   }

   /**
    * Set selection value.
    */
   public synchronized void setSelectionValue(int index, SelectionValue value) {
      getList().set(index, value);
   }

   /**
    * Add a selection value.
    * @param value the specified selection value.
    */
   public synchronized void addSelectionValue(SelectionValue value) {
      getList().add(value);
   }

   /**
    * Remove a selection value.
    * @param index the specified index.
    */
   public synchronized void removeSelectionValue(int index) {
      getList().remove(index);
   }

   /**
    * Get the selection value.
    * @param index the specified index.
    */
   public synchronized SelectionValue getSelectionValue(int index) {
      return getList().get(index);
   }

   /**
    * Get the contained selection value by comparing value only.
    * @param value the specified selection value.
    * @return the contained selection value.
    */
   public synchronized SelectionValue getSelectionValue(SelectionValue value) {
      for(int i = 0; i < getSelectionValueCount(); i++) {
         SelectionValue tvalue = getSelectionValue(i);

         if(tvalue.equalsValue(value)) {
            return tvalue;
         }
      }

      return null;
   }

   /**
    * Get the selection value count.
    * @return the selection value count.
    */
   public synchronized int getSelectionValueCount() {
      return list.size();
   }

   /**
    * Clear all the selection values.
    */
   public synchronized void clear() {
      list.clear();
      valid = true;
   }

   /**
    * Set the min value of the measure.
    */
   public void setMeasureMin(double mmin) {
      this.mmin = mmin;
   }

   /**
    * Get the min value of the measure.
    */
   public double getMeasureMin() {
      return mmin;
   }

   /**
    * Set the max value of the measure.
    */
   public void setMeasureMax(double mmax) {
      this.mmax = mmax;
   }

   /**
    * Get the max value of the measure.
    */
   public double getMeasureMax() {
      return mmax;
   }

   /**
    * Sort the items in the list.
    * @param sortType sort type is one of SORT_ASC, SORT_DESC, and
    * SORT_SPECIFIC. The SORT_SPECIFIC sorts the selected items on top.
    */
   public synchronized void sort(int sortType) {
      Comparator<SelectionValue> comp = createComparator(sortType);
      List<SelectionValue> list = getList();
      list.sort(comp);

      for(SelectionValue selectionValue : list) {
         if(selectionValue instanceof CompositeSelectionValue) {
            ((CompositeSelectionValue) selectionValue).getSelectionList().sort(sortType);
         }
      }
   }

   /**
    * Create a comparator for sorting.
    */
   private Comparator<SelectionValue> createComparator(int sortType) {
      boolean range = XSchema.STRING.equals(dtype);

      if(range) {
         List<SelectionValue> list = getList();
         int cnt = Math.min(list.size(), 10); // look-ahead to avoid scan all

         for(int i = 0; i < cnt; i++) {
            if(!isRange(list.get(i).getValue())) {
               range = false;
               break;
            }
         }
      }

      return range ? new RangeValueComparator(sortType)
         : new SelectionValueComparator(sortType);
   }

   /**
    * Check if the string is a value range.
    */
   private static boolean isRange(String val) {
      if(val == null || val.length() == 0) {
         return false;
      }

      char fc = val.charAt(0);

      if(fc == '>' || fc == '<') {
         try {
            Double.parseDouble(val.substring(1));
            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }
      else if(fc == '-' || Character.isDigit(fc)) {
         int dash = val.indexOf('-', 1);

         try {
            Double.parseDouble(val.substring(0, dash));
            Double.parseDouble(val.substring(dash + 1));
            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }

      return false;
   }

   /**
    * Merge the selection list into this list.
    */
   public synchronized void mergeSelectionList(SelectionList olist) {
      for(int i = 0; i < olist.getSelectionValueCount(); i++) {
         SelectionValue value = olist.getSelectionValue(i);
         SelectionValue value0 = findValue(value.getValue());

         if(value0 == null) {
            addSelectionValue(value);
         }
         else if(value0 instanceof CompositeSelectionValue &&
                 value instanceof CompositeSelectionValue)
         {
            ((CompositeSelectionValue) value0).mergeSelectionValue(
               (CompositeSelectionValue) value);
         }
      }
   }

   /**
    * Find the selection value with the specified value.
    */
   public SelectionValue findValue(String val) {
      return findValue(val, true);
   }

   /**
    * Find the selection value with the specified value.
    */
   public SelectionValue findValue(String val, boolean recursive) {
      for(SelectionValue obj : getList()) {
         if(Tool.equals(val, obj.getValue())) {
            return obj;
         }

         if(recursive && obj instanceof CompositeSelectionValue) {
            SelectionList list = ((CompositeSelectionValue) obj).getSelectionList();
            SelectionValue result = list == null ? null : list.findValue(val);

            if(result != null) {
               return result;
            }
         }
      }

      return null;
   }

   /**
    * Get the string representation.
    * @return the string representaion.
    */
   public String toString() {
      return "SelectionList[" + getList() + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public synchronized Object clone() {
      try {
         getList();
         SelectionList slist = (SelectionList) super.clone();
         slist.list = new ArrayList<>();
         slist.swapFile = null;

         for(int i = 0; i < getSelectionValueCount(); i++) {
            SelectionValue sv = (SelectionValue) getSelectionValue(i).clone();
            slist.addSelectionValue(sv);
         }

         slist.complete();
         return slist;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SelectionList", ex);
      }

      return null;
   }

   /**
    * Get the index of the shared format.
    */
   int getFormatIndex(VSCompositeFormat fmt, int level) {
      return fmtmap.computeIfAbsent(fmt, k -> level);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      writeData(output, Integer.MAX_VALUE);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   public void writeData(DataOutputStream output, int levels)
      throws IOException
   {
      writeData(output, levels, Integer.MAX_VALUE);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   public void writeData(DataOutputStream output, int levels, int limit)
      throws IOException
   {
      writeData(output, levels, 0, limit, true);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   public void writeData(DataOutputStream output, int levels,
                         int start, int limit, boolean containsFormat)
         throws IOException
   {
      fmtmap.clear();

      writeAttributes(output);
      writeContents(output, start, levels, limit, containsFormat);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   public void writeData(DataOutputStream output, int levels, int start,
      int count, int max, boolean showOthers) throws IOException
   {
      fmtmap.clear();

      writeAttributes(output);
      writeContents(output, levels, start, count, max, showOthers);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeXML(writer, Integer.MAX_VALUE);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param levels the number of levels of nodes to write.
    */
   public void writeXML(PrintWriter writer, int levels) {
      writeXML(writer, levels, 0, Integer.MAX_VALUE);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param levels the number of levels of nodes to write.
    * @param limit the maximum number of items to write out.
    */
   public void writeXML(PrintWriter writer, int levels, int start, int limit) {
      fmtmap.clear();

      writer.print("<SelectionList class=\"" + getClass().getName() + "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer, levels, start, limit);
      writer.print("</SelectionList>");
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int levels, int limit)
      throws IOException
   {
      writeContents(output, levels, 0, limit, true);
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int start,
                                int levels, int limit,
                                boolean containsFormat) throws IOException
   {
      // write selection values
      int skipped = 0;
      ArrayList<SelectionValue> values = new ArrayList<>();
      List<SelectionValue> list = getList();

      for(int i = start; i < list.size(); i++) {
         SelectionValue value = list.get(i);

         if(i >= start + limit && !value.isSelected() && !value.isIncluded()) {
            skipped++;
            continue;
         }

         values.add(value);
      }

      writeSelectionList(output, values, levels, containsFormat);

      output.writeInt(start + limit);
      output.writeInt(skipped);

      if(containsFormat) {
         // don't write format again in loadMore
         if(start > 0) {
            output.writeInt(0);
         }
         else {
            writeFormats(output);
         }
      }
   }

   /**
    * Write the selection list in binary.
    */
   private void writeSelectionList(DataOutputStream output, List<SelectionValue> values,
                                   int levels, boolean containsFormat)
         throws IOException
   {
      int len = values.size();
      output.writeInt(len);

      for(SelectionValue sval : values) {
         if(sval instanceof CompositeSelectionValue) {
            output.writeUTF("c");
         }
         else {
            output.writeUTF("s");
         }

         sval.writeData(output, levels, this, containsFormat);
      }
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int levels, int start,
      int count, int max, boolean showOthers) throws IOException
   {
      // write selection values
      List<SelectionValue> values = new ArrayList<>();
      List<SelectionValue> list = getList();

      for(int i = 0; i < list.size(); i++) {
         SelectionValue value = list.get(i);

         if(i >= max && !value.isSelected()) {
            continue;
         }

         if(showOthers) {
            if(value.isExcluded() && !value.isSelected() && i < max) {
               values.add(value);
            }
         }
         else {
            values.add(value);
         }
      }

      if(!showOthers) {
         try {
            values = values.subList(start, Math.min(values.size(), start + count));
         }
         catch(Exception ex) {
            values = new ArrayList<>();
         }
      }

      writeSelectionList(output, values, levels, false);

      int skipped = 0;

      for(int i = 0; i < list.size(); i++) {
         SelectionValue value = list.get(i);

         if(i >= max && !value.isSelected()) {
            skipped++;
         }
      }

      output.writeInt(start + count);
      output.writeInt(skipped);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param levels the number of levels of nodes to write.
    * @param limit the maximum number of items to write out.
    */
   protected void writeContents(PrintWriter writer, int levels, int start, int limit) {
      int skipped = 0;
      List<SelectionValue> list = getList();

      writer.print("<selectionValues>");

      for(int i = 0; i < list.size(); i++) {
         SelectionValue value = list.get(i);

         if(i >= limit && !value.isSelected()) {
            skipped++;
            continue;
         }

         value.writeXML(writer, levels, this);
      }

      writer.println("</selectionValues>");
      writer.print("<loaded>" + (start + limit) + "</loaded>");

      if(skipped > 0) {
         writer.print("<omitted>" + skipped + "</omitted>");
      }

      writeFormats(writer);
   }

   /**
    *  Write format.
    */
   protected void writeFormats(DataOutputStream output) throws IOException {
      // write shared VSFormat and table data path
      output.writeInt(fmtmap.size());

      for(VSCompositeFormat fmt : fmtmap.keySet()) {
         Integer idx = fmtmap.get(fmt);

         output.writeInt(idx);
         fmt.writeData(output);
      }
   }

   /**
    *  Write format.
    */
   protected void writeFormats(PrintWriter writer) {
      // write shared VSFormat and table data path

      for(VSCompositeFormat fmt : fmtmap.keySet()) {
         Integer idx = fmtmap.get(fmt);

         writer.println("<cellFormat index=\"" + idx + "\">");
         fmt.writeXML(writer);
         writer.println("</cellFormat>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element valuesNode = Tool.getChildNodeByTagName(elem, "selectionValues");
      NodeList valuesList = Tool.getChildNodesByTagName(valuesNode, "VSValue");

      for(int i = 0; i < valuesList.getLength(); i++) {
         Element vnode = (Element) valuesList.item(i);
         String cls = Tool.getAttribute(vnode, "class");
         SelectionValue value = (SelectionValue) Class.forName(cls).newInstance();
         value.parseXML(vnode);
         list.add(value);
      }
   }

   /**
    * Write attributes.
    * @param output the specified writer.
    */
   protected void writeAttributes(DataOutputStream output) throws IOException {
      output.writeDouble(mmin);
      output.writeDouble(mmax);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" mmin=\"" + mmin + "\"");
      writer.print(" mmax=\"" + mmax + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      // do nothing
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof SelectionList)) {
         return false;
      }

      SelectionList slist = (SelectionList) obj;
      return getList().equals(slist.getList());
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   public boolean requiresReset() {
      for(int i = 0; i < getSelectionValueCount(); i++) {
         SelectionValue value = getSelectionValue(i);

         if(value.requiresReset()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Set the data type.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Get the data type.
    * @return the dtype.
    */
   public String getDataType() {
      return dtype;
   }

   /**
    * Set the special comparator.
    * @param comp the specified comparator.
    */
   public void setComparator(Comparator comp) {
      this.comp = comp;
   }

   /**
    * Get the special comparator.
    * @return the comparator.
    */
   public Comparator getComparator() {
      return comp;
   }

   @Override
   public void complete() {
      synchronized(this) {
         completed = true;
         list.trimToSize();
      }

      super.complete();
   }

   @Override
   public double getSwapPriority() {
      // hold the array for 5s so if the selection value is updated, it would be
      // swapped out before the update happens. this is not 100% bullet-proof but
      // given the current usage, where a SelectionValue would be updated immediately
      // after swapping, it should be safe. we will need to make SelectionValue
      // immutable to make it completely safe.
      if(!completed || !valid || XSwapper.cur < lastAccess + 5000) {
         return 0;
      }

      // allow SelectionList to be swapped. but don't do it unless really necessary.
      return getAgePriority(XSwapper.cur - lastAccess, alive * 3);
   }

   @Override
   public boolean isCompleted() {
      return completed;
   }

   @Override
   public boolean isSwappable() {
      // don't need to swap small lists.
      return completed && list.size() > 50;
   }

   @Override
   public boolean isValid() {
      return valid;
   }

   @Override
   public synchronized boolean swap() {
      if(!valid) {
         return false;
      }

      if(swapFile == null) {
         swapFile = getFile(prefix + "_slist.swap");
      }

      final Object2IntOpenHashMap<Format> defFmtMap = new Object2IntOpenHashMap<>();
      final Object2IntOpenHashMap<VSCompositeFormat> fmtMap = new Object2IntOpenHashMap<>();
      defFmtDict = new ArrayList<>();
      fmtDict = new ArrayList<>();

      Function<Format, Integer> defFmtMapper = (fmt) -> {
         int idx = defFmtMap.getOrDefault(fmt, -1);

         if(idx < 0) {
            defFmtMap.put(fmt, idx = defFmtDict.size());
            defFmtDict.add(fmt);
         }

         return idx;
      };

      Function<VSCompositeFormat, Integer> fmtMapper = (fmt) -> {
         int idx = fmtMap.getOrDefault(fmt, -1);

         if(idx < 0) {
            fmtMap.put(fmt, idx = fmtDict.size());
            fmtDict.add(fmt);
         }

         return idx;
      };

      // don't reuse swap data. since selection values are mutable, need to write them out.
      try(Output objout = new Output(new FileOutputStream(swapFile))) {
         Kryo kryo = XSwapUtil.getKryo();
         ArrayList<SelectionValue> list2 = new ArrayList<>();

         for(int i = 0; i < this.list.size(); i++) {
            SelectionValue v = this.list.get(i);

            if(v instanceof CompositeSelectionValue) {
               list2.add(v);
            }
            else {
               list2.add(null);
               v.writeObject(kryo, objout, defFmtMapper, fmtMapper);
            }
         }

         this.list = list2;
         valid = false;
      }
      catch(Exception ex) {
         LOG.warn("Swapping failed for selection list: " + swapFile, ex);
      }

      return true;
   }

   private List<SelectionValue> getList() {
      ArrayList<SelectionValue> list = this.list;
      lastAccess = XSwapper.cur;

      if(!valid) {
         synchronized(this) {
            if(!valid) {
               ArrayList<SelectionValue> list2 = new ArrayList<>();
               Function<Integer, Format> defFmtMapper = idx -> defFmtDict.get(idx);
               Function<Integer, VSCompositeFormat> fmtMapper = idx -> fmtDict.get(idx);

               try(Input inp = new Input(new FileInputStream(swapFile))) {
                  Kryo kryo = XSwapUtil.getKryo();

                  for(int i = 0; i < this.list.size(); i++) {
                     SelectionValue val = this.list.get(i);

                     if(val == null) {
                        list2.add(SelectionValue.readObject(kryo, inp, defFmtMapper, fmtMapper));
                     }
                     else {
                        list2.add(val);
                     }
                  }

                  this.list = list = list2;
                  defFmtDict = null;
                  fmtDict = null;
                  valid = true;
               }
               catch(Exception ex) {
                  LOG.warn("Restore failed for selection list: " + swapFile, ex);
               }
            }
         }
      }

      return list;
   }

   @Override
   public synchronized void dispose() {
      if(swapFile != null) {
         swapFile.delete();
      }

      list.clear();
   }

   @Override
   public void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Find a list of all items that matches the string.
    * @param not true to return the selected values not matching the string.
    * Otherwise returns all matching values.
    */
   public synchronized SelectionList findAll(String str, boolean not) {
      SelectionList list = new SelectionList();

      for(SelectionValue value : getList()) {
         boolean rc = value.match(str, true);

         if(not) {
            rc = !rc && value.isSelected();
         }

         if(rc) {
            if(value instanceof CompositeSelectionValue &&
               // if find the match in a node, show all children
               !value.match(str, false))
            {
               value = ((CompositeSelectionValue) value).findAll(str, not);
            }

            list.addSelectionValue(value);
         }
      }

      return list;
   }

   /**
    * Get format map.
    */
   public Map getFormatMap() {
      return fmtmap;
   }

   /**
    * A selection value comparison object.
    */
   private class SelectionValueComparator implements Comparator<SelectionValue> {
      public SelectionValueComparator(int sortType) {
         this.type = sortType;
         strcomp = dtype == null || XSchema.STRING.equals(dtype);
      }

      private int compareValue(SelectionValue v1, SelectionValue v2) {
         String s1 = v1.getLabel();
         String s2 = v2.getLabel();

         // sort null to the end of the list
         if(!s1.equals(s2)) {
            if(s1.length() == 0) {
               return Integer.MAX_VALUE;
            }
            else if(s2.length() == 0) {
               return -Integer.MAX_VALUE;
            }
         }

         if(comp != null) {
            return comp.compare(v1.getLabel(), v2.getLabel());
         }

         if(strcomp) {
            return textComp.compare(v1.getLabel(), v2.getLabel());
         }

         Object o1 = Tool.getData(dtype, s1);
         Object o2 = Tool.getData(dtype, s2);

         return Tool.compare(o1, o2);
      }

      @Override
      public int compare(SelectionValue v1, SelectionValue v2) {
         switch(type) {
         case XConstants.SORT_ASC:
            return compareValue(v1, v2);
         case XConstants.SORT_DESC:
            return -compareValue(v1, v2);
         case XConstants.SORT_VALUE_ASC:
            return compareMeasureValue(v1, v2);
         case XConstants.SORT_VALUE_DESC:
            return -compareMeasureValue(v1, v2);
         case XConstants.SORT_SPECIFIC:
            int s1 = v1.getState();
            int s2 = v2.getState();

            if(s1 != s2) {
               int rank1 = getRanking(s1);
               int rank2 = getRanking(s2);

               if(rank1 != rank2) {
                  return rank1 - rank2;
               }
            }

            return compareValue(v1, v2);
         case XConstants.SORT_NONE:
         case XConstants.SORT_ORIGINAL:
            return 0;
         }

         return compareValue(v1, v2);
      }

      private int compareMeasureValue(SelectionValue v1, SelectionValue v2) {
         try {
            double d1 = v1.getMeasureValue();
            double d2 = v2.getMeasureValue();
            String l1 = v1.getMeasureLabel();
            String l2 = v2.getMeasureLabel();

            // measure label is null, means measure no value, fix bug1364295449735
            if(l1 == null && l2 != null) {
               return -1;
            }

            if(l2 == null && l1 != null) {
               return 1;
            }

            if(d1 > d2) {
               return 1;
            }

            if(d1 < d2) {
               return -1;
            }

            return compareValue(v1, v2);
         }
         catch(Exception ex) {
            return compareValue(v1, v2);
         }
      }

      private int type;
      private boolean strcomp;
   }

   /**
    * A range value comparison object.
    */
   private class RangeValueComparator extends SelectionValueComparator {
      public RangeValueComparator(int sortType) {
         super(sortType);
      }

      protected int compareValue(SelectionValue v1, SelectionValue v2) {
         try {
            double d1 = getLowerBound(v1.getValue());
            double d2 = getLowerBound(v2.getValue());

            return Double.compare(d1, d2);
         }
         catch(Exception ex) {
            return v1.getValue().compareTo(v2.getValue());
         }
      }
   }

   /**
    * Get the range lower bound.
    */
   private static double getLowerBound(String val) {
      if(val.length() == 0) { // sort null to the end
         return Double.MAX_VALUE;
      }

      char fc = val.charAt(0);

      switch(fc) {
      case '<':
         return -Double.MAX_VALUE;
      case '>':
         return Double.parseDouble(val.substring(1));
      default:
         int dash = val.indexOf('-', 1);

         return Double.parseDouble(val.substring(0, dash));
      }
   }

   /**
    * Get the sorting order for the selection state.
    * @param state the specified state.
    * @return the sorting order.
    */
   private static int getRanking(int state) {
      final int rank;
      state = state & (SelectionValue.STATE_SELECTED |
                       SelectionValue.STATE_INCLUDED |
                       SelectionValue.STATE_EXCLUDED);

      switch(state) {
      case SelectionValue.STATE_SELECTED:
      case SelectionValue.STATE_SELECTED | SelectionValue.STATE_INCLUDED:
         rank = 0;
         break;
      case SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED:
         rank = 1;
         break;
      case SelectionValue.STATE_INCLUDED:
         rank = 2;
         break;
      default:
         if((state & SelectionValue.STATE_EXCLUDED) != 0) {
            rank = 4;
         }
         else {
            rank = 3;
         }
         break;
      }

      return rank;
   }

   private ArrayList<SelectionValue> list;
   private String dtype;
   private Comparator comp;
   private final Map<VSCompositeFormat, Integer> fmtmap = new HashMap<>(); // VSFormat -> index
   private double mmin = 0;
   private double mmax = 100;
   private Comparator textComp =
      Locale.getDefault().getLanguage().equals("en") ? ImmutableDefaultComparer.getInstance() :
         new TextComparer(Collator_CN.getCollator());
   // for swapping
   private boolean completed;
   private boolean valid = true;
   private File swapFile;
   private long lastAccess = 0;
   private List<Format> defFmtDict;
   private List<VSCompositeFormat> fmtDict;

   private static final Logger LOG = LoggerFactory.getLogger(SelectionList.class);
}
