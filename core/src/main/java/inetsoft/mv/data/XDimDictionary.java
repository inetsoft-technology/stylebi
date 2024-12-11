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
package inetsoft.mv.data;

import inetsoft.mv.MVTool;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.Condition;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * XDimDictionary, it converts index to data, so that XTable could be
 * generated in MVQuery.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class XDimDictionary extends XSwappable implements Cloneable {
   /**
    * Get the class for one data type.
    */
   private static Class getDataType(int type) {
      switch(type) {
      case MVTool.STRING_TYPE:
         return String.class;
      case MVTool.BOOLEAN_TYPE:
         return Boolean.class;
      case MVTool.FLOAT_TYPE:
         return Float.class;
      case MVTool.DOUBLE_TYPE:
         return Double.class;
      case MVTool.CHAR_TYPE:
         return Character.class;
      case MVTool.CHARACTER_TYPE:
         return Character.class;
      case MVTool.BYTE_TYPE:
         return Byte.class;
      case MVTool.SHORT_TYPE:
         return Short.class;
      case MVTool.INTEGER_TYPE:
         return Integer.class;
      case MVTool.LONG_TYPE:
         return Long.class;
      case MVTool.TIME_INSTANT_TYPE:
         return java.sql.Timestamp.class;
      case MVTool.DATE_TYPE:
         return java.sql.Date.class;
      case MVTool.TIME_TYPE:
         return java.sql.Time.class;
      default:
         throw new RuntimeException("Unsupported data type: " + type +
            " found!");
      }
   }

   /**
    * Get the max size.
    */
   public static int getMaxSize() {
      if(MAX_SIZE == -1) {
         MAX_SIZE = Integer.parseInt(SreeEnv.getProperty("mv.dim.max.size"));
      }

      return MAX_SIZE;
   }

   /**
    * Create an instance of XDimDictionary.
    */
   public XDimDictionary() {
      super();

      valid = true;
      completed = false;
      disposed = false;
      XSwapper.cur = System.currentTimeMillis();
      accessed = XSwapper.cur;
   }

   /**
    * Check if the dimension value overflows, in which case the dimension
    * can't be used for grouping and filtering.
    */
   public boolean isOverflow() {
      return overflow;
   }

   /**
    * Mark this dictionary as overflow.
    */
   public void setOverflow(boolean overflow) {
      this.overflow = overflow;

      if(overflow) {
         set.clear();
         setDataType(String.class);
      }
   }

   /**
    * Reset overflow.
    * @return true if the user should be warned (for the first time overflow
    * is detected).
    */
   public boolean resetOverflow() {
      if(set == null || overflow || (range != null && isNumberRange())) {
         return false;
      }

      setOverflow(overflow || set.size() >= getMaxSize());
      return isOverflow();
   }

   /**
    * Null out the range array
    */
   public void nullRanges() {
      this.range = null;
   }

   /**
    * Create the range array, and initialize to default values
    */
   public void resetRanges() {
      // resetRanges is followed by ExpandRangeByObject, so the condition should match
      // non-number range is not expanded so it should not be reset
      if(values != null && !isNumberRange()) {
         this.range = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE};
      }
   }

   /**
    * Expand the range array, by using the dictionary index of the supplied object
    */
   public void expandRangeByObject(Object obj) {
      if(values != null && obj != null && range != null && !isNumberRange()) {
         int idx = binarySearch(values, cnull ? 1 : 0, obj);

         if(idx >= 0) {
            range[0] = Math.min(range[0], idx);
            range[1] = Math.max(range[1], idx);
         }
      }
   }

   /**
    * Add one more value to this dimension dictionary.
    */
   public void addValue(Object obj) {
      // rawValues hold a list all values for a dimension (it basically
      // captures a table column) so if the value overflows, the mv can
      // still be used for display detail data. grouping and filtering
      // on overflown dimension is not supported
      rawValues.add(obj);

      if(overflow) {
         if(obj != null) {
            hashCode += obj.hashCode();
         }

         return;
      }

      // @by stephenwebster, fix bug1393961685035
      // when adding a value, validate the column data by calling convert.
      // specifically for a value which is blank in a text datasource.
      // this will convert it to null, so that cnull is set to true.
      obj = convert(obj);

      // very special case for crosstab
      if("".equals(obj)) {
         obj = null;
      }

      if(obj == null) {
         cnull = true;
      }
      else if(range != null && isNumberRange()) {
         try {
            int val = ((Number) obj).intValue();

            if(val >= Integer.MAX_VALUE) {
               LOG.warn("Dictionary value overflow: " + val);
            }

            range[0] = Math.min(range[0], val);
            range[1] = Math.max(range[1], val);
            long result = (long) range[1] - (long) range[0];

            // @by davyc, does this type of data really exists in real world?
            // at the same time, this will cause memory exception if we create
            // so many size of array to store this data in MVDimColumn,
            // if need to support, we may need use another solution
            // to support Integer
            // fix
            if(result >= Integer.MAX_VALUE) {
               throw new RuntimeException("Dictionary value overflow: min=" +
                  range[0] + ", max=" + range[1]);
            }
         }
         catch(ClassCastException ex) {
            LOG.warn("Non-numeric value in range: " + obj, ex);
         }
      }
      else {
         expandRangeByObject(obj);
         boolean add = set.add(obj);

         if(add && obj != null) {
            hashCode += obj.hashCode();
         }
      }
   }

   /**
    * Complete this dimension dictionary.
    */
   @Override
   public void complete() {
      if(range != null && isNumberRange()) {
         if(cnull) {
            range[0]--;
         }

         hashCode = String.valueOf(range[0]).hashCode() +
            String.valueOf(range[1]).hashCode();
      }

      values = new Object[set.size()];
      set.toArray(values);
      Arrays.sort(values, new Comparator() {
         @Override
         public final int compare(Object a, Object b) {
            try {
               // @by davyc, use same compare logic with Row2.compareTo, and
               // case sensitive, otherwise the sort sequence will have problem
               // fix bug1403501082747
               return Tool.compare(a, b, true, true);
            }
            catch(Exception ex) {
               // ignore it
               return a.getClass().getName().compareTo(b.getClass().getName());
            }
         }
      });

      set.clear();
      set = null;

      // first is null?
      if(!overflow && cnull) {
         Object[] nvalues = new Object[values.length + 1];
         System.arraycopy(values, 0, nvalues, 1, values.length);
         values = nvalues;
      }

      // raw value not used if the regular index is not overflown
      if(!overflow) {
         rawValues = null;
      }

      newbuf = true;
      channelProvider = null;
      completed = true;
      dictsize = values.length;
      super.complete();
   }

   /**
    * Set data type.
    */
   public void setDataType(Class cls) {
      int dataType = MVTool.getDataTypeIndex(cls);

      if(overflow && dataType != MVTool.STRING_TYPE) {
         return;
      }

      if(cls != null &&
         dataType == MVTool.STRING_TYPE && !String.class.isAssignableFrom(cls))
      {
         LOG.warn(
                     "An unsupported data type is being coerced to String " +
                        "type which will produce unpredictable results");
         return;
      }

      this.dataType = dataType;
      this.cls = cls;

      switch(dataType) {
      case MVTool.BYTE_TYPE:
      case MVTool.SHORT_TYPE:
      case MVTool.INTEGER_TYPE:
         range = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE};
         break;
      default:
         range = null;
         break;
      }
   }

   /**
    * Get dimension value.
    */
   public Object getValue(int index) {
      // @by ankitmathur, Fix bug1430338458501, Before getting values from
      // rawValues, check if it is read (and the state is valid) through logic
      // in access() >> validate().
      Object[] values = access();

      if(range != null && isNumberRange()) {
         return cnull && index == 0 ? null : index + range[0] - (cnull ? 1 : 0);
      }

      // overflow values are captured in rawValues
      if(overflow) {
         if(rawValues != null) {
            return rawValues.get(index);
         }
         else {
            String file = channelProvider == null ? null : channelProvider.getName();
            throw new RuntimeException("Raw values missing in overflown dim dictionary: " + file);
         }
      }

      return values == null || values.length == 0 ? null : values[index];
   }

   /**
    * Set the base row index of this dictionary.
    * @param baseRow the corresponding block start row.
    */
   public void setBaseRow(int baseRow) {
      this.baseRow = baseRow;
   }

   /**
    * Get the base row index of this dictionary.
    */
   public int getBaseRow() {
      return baseRow;
   }

   /**
    * Access this dim dictionary.
    */
   private final Object[] access() {
      accessed = XSwapper.cur;
      Object[] values = this.values;

      if(values != null) {
         return values;
      }

      synchronized(this) {
         values = this.values;

         if(values != null) {
            return values;
         }

         // invalid? validate it
         if(!valid) {
            validate();
            values = this.values;
         }
      }

      return values;
   }

   /**
    * Get dimension index.
    */
   public int indexOf(Object value, int row) {
      if(overflow) {
         // if overflow, the index of a value is it's row position
         // since the rawValues captures the exact same values in
         // the raw data. The mapping from value -> index is not unique
         // in this case. For example, NJ could be mapped to 5, 9, ...
         // But since we don't allow grouping and filtering, and
         // the index is only used to retrieve the value for displaying
         // details, the lack of uniqueness is not a problem.
         return row - baseRow;
      }

      // @by stephenwebster, fix bug1393961685035
      // It seems like calling convert is a prerequisite prior to a compare.
      // It is possible that this method gets called from external where
      // convert is not available.  The check for value == "" is not enough
      // so here we will ensure that convert gets called on the value so that
      // any blank values will get converted to null and then compare will
      // not fail.
      value = convert(value);

      if("".equals(value) || value == null) {
         return cnull ? 0 : -1;
      }

      if(range != null && isNumberRange()) {
         return ((Number) value).intValue() + (cnull ? 1 : 0) - range[0];
      }

      Object[] values = access();
      return binarySearch(values, cnull ? 1 : 0, value);
   }

   /**
    * Binary search one value.
    */
   private static final int binarySearch(Object[] a, int low, Object key) {
      int high = a.length - 1;

      while (low <= high) {
          int mid = (low + high) >> 1;
          Object midVal = a[mid];
          int cmp = ((Comparable) midVal).compareTo(key);

          if(cmp < 0) {
             low = mid + 1;
          }
          else if(cmp > 0) {
             high = mid - 1;
          }
          else {
             return mid;
          }
      }

      return -(low + 1);
   }

   /**
    * Convert value to make sure that compare works.
    */
   private Object convert(Object val) {
      if(val == null) {
         return val;
      }

      Class cls = val.getClass();

      if(!cls.equals(this.cls)) {
         if(cls == Boolean.class && this.cls == Integer.class) {
            val = (Boolean) val ? 1 : 0;
         }
         else {
            val = Tool.getData(this.cls, val);
         }
      }

      return val;
   }

   /**
    * Get the minimum of the value range.
    */
   public int getRangeMin() {
      return (range != null && isNumberRange()) ? range[0] : 0;
   }

   /**
    * Check if contains null value.
    */
   public boolean containsNull() {
      return cnull;
   }

   /**
    * Map dimension value in condition to index.
    */
   public void fixFilter(XBinaryCondition cond) {
      validate();

      String op = cond.getOp();
      Object col = cond.getExpression1().getValue();
      XExpression exp = cond.getExpression2();
      Object val = exp.getValue();

      if(cnull) {
         cond.setAttribute("containsNull", "true");
      }

      if("=".equals(op)) {
         val = "" + indexOf(convert(val), -1);
      }
      else if("null".equals(op)) {
         val = "" + indexOf(null, -1);
      }
      else if("IN".equals(op)) {
         Object[] vals = null;

         if(val instanceof Object[]) {
            vals = (Object[]) val;
            vals = (Object[]) vals.clone();
         }
         else {
            vals = new Object[] {val};
         }

         for(int i = 0; i < vals.length; i++) {
            vals[i] = "" + indexOf(convert(vals[i]), -1);
         }

         val = vals;
      }
      else if("BETWEEN".equals(op)) {
         Object[] vals = (Object[]) val;
         vals = (Object[]) vals.clone();

         for(int i = 0; i < vals.length; i++) {
            int idx0 = indexOf(convert(vals[i]), -1);
            int idx = idx0;

            if(i == 1 && idx < 0) {
               idx += 1;
            }

            vals[i] = "" + (idx >= 0 ? idx : -idx - 1);

            // if the second value is less than the min value,
            // vals[1] shoud be -1
            if(i == 1 && !cnull && idx0 == -1) {
               vals[i] = -1;
            }
         }

         val = vals;
      }
      else if(">".equals(op) || ">=".equals(op) || "<".equals(op) ||
              "<=".equals(op)) {
         int idx = indexOf(convert(val), -1);

         if(idx == -1 &&
            !((val == null || val.equals("")) && !cnull ||
               (range != null && isNumberRange())))
         {
            val = "-1";
         }
         else {
            if(idx < 0) {
               if("<=".equals(op)) {
                  idx += 1;
               }
               else if(">".equals(op)) {
                  cond.setOp(">=");
               }
            }

            val = "" + (idx >= 0 ? idx : -idx - 1);
         }
      }
      else if("STARTSWITH".equals(op)) {
         // this is supported by regular condition filter.
         if(val instanceof Object[]) {
            Object[] vals = (Object[]) val;

            if(vals.length > 0) {
               val = vals[0];
            }
         }

         if(cls != String.class && cls != Character.class &&
            !Number.class.isAssignableFrom(cls))
         {
            throw new RuntimeException("\"Starts with\" operation is not "
               + "supported for column \"" + col + "\".");
         }

         // number will be done in file system
         // number always used as measure, see MVDef.fixMVColumn
         if(cls == String.class || cls == Character.class) {
            cond.setOp("IN");
            val = getPatternVals(val, (a, b) -> a.startsWith(b));
         }
      }
      else if("CONTAINS".equals(op)) {
         if(cls != String.class && cls != Character.class &&
            !Number.class.isAssignableFrom(cls))
         {
            throw new RuntimeException("\"Contains\" operation is not " +
               "supported for column \"" + col + "\".");
         }

         // number will be done in file system
         if(cls == String.class || cls == Character.class) {
            cond.setOp("IN");
            val = getPatternVals(val, (a, b) -> a.contains(b));
         }
      }
      else if("LIKE".equals(op)) {
         if(cls != String.class && cls != Character.class &&
            !Number.class.isAssignableFrom(cls))
         {
            throw new RuntimeException("\"Contains\" operation is not " +
               "supported for column \"" + col + "\".");
         }

         // number will be done in file system
         if(cls == String.class || cls == Character.class) {
            cond.setOp("IN");
            Pattern pattern = Condition.getLikePattern(val + "", caseSensitive);
            val = getPatternVals(val, (a, b) -> pattern.matcher(a).find());
         }
      }
      else {
         throw new RuntimeException("unsupported operator: " + op + " found!");
      }

      exp.setValue(val, exp.getType());
   }

   /**
    * Load from binary storage.
    */
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel) throws IOException
   {
      this.channelProvider = channelProvider;
      this.fpos = ((SeekableByteChannel) channel).position();

      // delayed read
      ByteBuffer buf = ByteBuffer.allocate(28);
      channel.read(buf);
      XSwapUtil.flip(buf);
      long size = buf.getLong();
      this.hashCode = buf.getInt();
      this.dataType = buf.getInt();
      this.cls = getDataType(dataType);
      ((SeekableByteChannel) channel).position(fpos + size);
      valid = false;
      //read0(channel, true);

      // mark this dimension dictionary to be swappable and register it
      completed = true;
      super.complete();
   }

   /**
    * Load from binary storage.
    */
   private void read0(ReadableByteChannel channel) throws IOException {
      XSwapper.getSwapper().waitForMemory();

      ByteBuffer buf = ByteBuffer.allocate(28);
      channel.read(buf);
      XSwapUtil.flip(buf);
      buf.getLong(); // skip size
      buf.getInt(); // skip hashCode
      buf.getInt(); //skip dataType
      //this.cls = getDataType(dataType);
      int range0 = buf.getInt();
      int range1 = buf.getInt();
      boolean raw = buf.getInt() == 1;

      // invalid range because min is larger than max
      if(range0 == 1 && range1 == 0) {
         range = null;
      }
      else {
         range = new int[] { range0, range1};
      }

      Object[] values = MVTool.readObjects(channel, false);
      cnull = values.length > 0 && values[0] == null;

      if(values.length == 1) {
         overflow = OVERFLOW.equals(values[0]);

         if(overflow) {
            values = new Object[0];
         }
      }

      if(raw && !rawValuesRead) {
         rawValues = new DimValueList();
         rawValues.read(channelProvider, channel);
         rawValuesRead = true;
      }

      // assign to this.values after rawValues.read() finishes otherwise
      // the rawValues may be accessed in middle of rawValues.read()
      this.values = values;
   }

   /**
    * Save to binary storage.
    */
   public void write(WritableByteChannel channel) throws IOException {
      write(channel, true);
   }

   /**
    * Write to file.
    * @param raw true to include the raw values.
    */
   private void write(WritableByteChannel channel, boolean raw) throws IOException {
      SeekableByteChannel fileChannel = (SeekableByteChannel) channel;
      long opos = fileChannel.position();
      ByteBuffer buf = ByteBuffer.allocate(28);
      buf.putLong(0);
      buf.putInt(hashCode);
      buf.putInt(dataType);
      // if range is null, we write a invalid range value for min larger than max
      buf.putInt(range == null ? 1 : range[0]);
      buf.putInt(range == null ? 0 : range[1]);
      buf.putInt(raw && overflow && rawValues != null ? 1 : 0);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      // for incremental, the dictionary is from old MV, so values may be
      // swapped out, here try to get it back
      // fix bug1363703090104
      Object[] values = overflow ? new Object[] {OVERFLOW} : access();
      buf = MVTool.getObjectsByteBuffer(values, false);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      if(raw && overflow && rawValues != null) {
         rawValues.write(channel);
      }

      // write size
      long npos = fileChannel.position();
      buf = ByteBuffer.allocate(8);
      buf.putLong(npos - opos);
      XSwapUtil.flip(buf);
      fileChannel.position(opos);

      while(buf.hasRemaining()) {
         fileChannel.write(buf);
      }

      fileChannel.position(npos);
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid) {
         return 0;
      }

      return getAgePriority(XSwapper.cur - accessed, alive * 2L);
   }

   /**
    * Check if the swappable is completed for swap.
    * @return <tt>true</tt> if completed for swap, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return !disposed && completed;
   }

   /**
    * Check if the swappable is in valid state.
    * @return <tt>true</tt> if in valid state, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return valid;
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   @Override
   public synchronized boolean swap() {
      if(getSwapPriority() == 0) {
         return false;
      }

      if(newbuf) {
         channelProvider = ChannelProvider.file(getFile(prefix + "_dict.tdat"));

         if(!channelProvider.exists()) {
            try(SeekableByteChannel channel = channelProvider.newWriteChannel()) {
               write(channel, false);
            }
            catch(Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         }
      }

      newbuf = false;
      valid = false;
      values = null;

      return true;
   }

   /**
    * Validate the swappable internally.
    */
   private synchronized void validate() {
      if(valid || disposed) {
         return;
      }

      SeekableByteChannel channel = null;

      try {
         channel = channelProvider.newReadChannel();
         channel.position(fpos);
         read0(channel);
         valid = true;
      }
      catch(FileNotFoundException ex) {
         LOG.error("Dimension dictionary file not found: {}", channelProvider.getName(), ex);
      }
      catch(Exception ex) {
         LOG.error("Failed to read from file when validating: {}", channelProvider.getName(), ex);
      }
      finally {
         IOUtils.closeQuietly(channel);
      }
   }

   /**
    * Dispose the swappable.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;

      if(rawValues != null) {
         rawValues.dispose();
      }

      File file = getFile(prefix + "_dict.tdat");

      if(file.exists()) {
         boolean result = file.delete();

         if(!result) {
            FileSystemService.getInstance().remove(file, 30000);
         }
      }
   }

   /**
    * Finalize the object.
    */
   @Override
   protected final void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Clear the XDimDictionary.
    */
   public void clear() {
      valid = false;
      values = null;
   }

   /**
    * Get the size.
    */
   public int size() {
      validate();

      if(overflow && rawValues != null) {
         return rawValues.size();
      }

      // no value added? no size
      if(range != null && isNumberRange() && range[0] > range[1]) {
         return 1;
      }

      return (range != null && isNumberRange()) ? range[1] - range[0] + 1
         : (values != null ? values.length
            : (set != null ? set.size() : dictsize));
   }

   private boolean isNumberRange() {
      int dataType = MVTool.getDataTypeIndex(cls);
      return dataType == MVTool.BYTE_TYPE ||
         dataType == MVTool.SHORT_TYPE ||
         dataType == MVTool.INTEGER_TYPE ||
         dataType == MVTool.LONG_TYPE;
   }

   /**
    * Get the max value.
    */
   public Object max() {
      Object max = null;

      if(range != null) {
         if(isNumberRange()) {
            max = range[1];
         }
         else {
            Object[] values = access();
            if(range[1] >= 0 && range[1] < values.length) {
               return values[range[1]];
            }
            else {
               return null;
            }
         }
      }
      else {
         Object[] values = access();

         if(values != null && values.length > 0) {
            max = values[values.length - 1];
         }
      }

      return convert(max);
   }

   /**
    * Get the min value.
    */
   public Object min() {
      Object min = null;

      if(range != null) {
         if(cnull) {
            min = null;
         }

         if(isNumberRange()) {
            min = range[0];
         }
         else {
            Object[] values = access();
            if(range[0] >= 0 && range[0] < values.length) {
               return values[range[0]];
            }
            else {
               return null;
            }
         }
      }
      else {
         Object[] values = access();

         if(values != null && values.length > 0) {
            min = values[0];
         }
      }

      return convert(min);
   }

   /**
    * Get all values which starts with the specified value.
    */
   private Object[] getPatternVals(Object obj, BiFunction<String, String, Boolean> matcher) {
      if(obj == null) {
         return new String[0];
      }

      String value = toString(obj);
      List<Integer> vals = new ArrayList<>();

      for(int i = 0; i < size(); i++) {
         String val = toString(getValue(i));

         if(val != null && matcher.apply(val, value)) {
            vals.add(0, i);
         }
      }

      Object[] rvals = new Object[vals.size()];

      for(int i = 0; i < vals.size(); i++) {
         rvals[i] = "" + vals.get(i);
      }

      return rvals;
   }

   private String toString(Object obj) {
      if(obj == null) {
         return null;
      }

      String str = null;

      if(obj instanceof String) {
         str = (String) obj;
      }
      else {
         str = obj.toString();
      }

      return caseSensitive ? str : str.toLowerCase();
   }

   /**
    * Merge from dictionary.
    */
   public void mergeFrom(XDimDictionary mdim) {
      cnull = mdim.cnull;

      // @by davyc, this case should not happen(range is null, mdim.range is not
      // or mdim.range is null, but range is not),
      // otherwise means something wrong
      if(range != null && mdim.range != null) {
         if(isNumberRange()) {
            range[0] = cnull ? mdim.range[0]++ : mdim.range[0];
            range[1] = mdim.range[1];
         }
         else {
            range[0] = Math.min(range[0], mdim.range[0]);
            range[1] = Math.max(range[1], mdim.range[1]);
         }
      }
      else {
         // overflow?
         if(mdim.overflow) {
            for(int i = 0; i < mdim.rawValues.size(); i++) {
               rawValues.add(mdim.rawValues.get(i));
            }

            overflow = true;
            set.clear();
            setDataType(String.class);
            return;
         }
      }

      Object[] mdim_values = mdim.access();

      if(mdim_values != null) {
         for(Object obj : mdim_values) {
            addValue(obj);
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object;
    */
   @Override
   public Object clone() {
      try {
         // take care, clone here not means clone, it means initialize an
         // empty same class object
         XDimDictionary dict = (XDimDictionary) super.clone();

         if(range != null) {
            dict.range = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE};

            if(!isNumberRange()) {
               dict.range[0] = range[0];
               dict.range[1] = range[1];
            }
         }

         dict.set = new ObjectOpenHashSet();
         dict.overflow = false;
         dict.rawValues = new DimValueList();
         dict.valid = true;
         dict.completed = false;
         dict.disposed = false;
         dict.hashCode = 0;
         XSwapper.cur = System.currentTimeMillis();
         dict.accessed = XSwapper.cur;
         return dict;
      }
      catch(Exception ex) {
         return null;
      }
   }

   //for test.
   public String toString() {
      validate();
      return super.toString() +  "-->" + " | " + Arrays.toString(range) +
         " | " + Arrays.toString(values);
   }

   /**
    * The hash code for dict.
    */
   public int hashCode() {
      return hashCode;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XDimDictionary)) {
         return false;
      }

      XDimDictionary dict = (XDimDictionary) obj;

      if(overflow != dict.overflow || size() != dict.size() ||
         hashCode() != dict.hashCode() ||
         cnull != dict.cnull || cls != dict.cls)
      {
         return false;
      }

      // overflow (rowValues) values are not accessible during creation
      if(!overflow && range == null) {
         for(int j = 0; j < dict.size(); j++) {
            if(!Tool.equals(getValue(j), dict.getValue(j))) {
               return false;
            }
         }
      }

      return true;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XDimDictionary.class);
   private static int MAX_SIZE = -1;
   private static final String OVERFLOW = "__OVER_FLOW__";

   private Object[] values;
   private DimValueList rawValues = new DimValueList();
   private int[] range; // integer value range
   private boolean overflow;
   private int dataType;
   private Class<?> cls;
   private Set<Object> set = new ObjectOpenHashSet<>();
   private int dictsize = 0;
   private long accessed, fpos;
   private ChannelProvider channelProvider;
   private boolean disposed;
   private boolean cnull;
   private boolean valid;
   private boolean completed;
   private boolean caseSensitive = Tool.isCaseSensitive();
   private boolean rawValuesRead = false;
   private int hashCode;
   // the starting row index in the original data of the corresponding block
   private transient int baseRow = 0;
   private boolean newbuf = false;
}
