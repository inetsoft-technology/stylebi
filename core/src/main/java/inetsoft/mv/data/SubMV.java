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

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.jdbc.*;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sub materialized view, it stores dimension dictionaries at server node. When
 * executing mv query, we need to restore an int index into its original object.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class SubMV implements Cloneable {
   /**
    * Get sub mv by providing a file.
    */
   public static SubMV get(BlockFile file) throws IOException {
      String path = file.getName();
      SubMV mv = map.get(path);

      if(mv == null) {
         mv = new SubMV(file);
         map.putIfAbsent(path, mv);
         mv = map.get(path);
      }

      // initialize outside of locked block to avoid holding up
      // other SubMVs
      mv.init();

      if(!mv.isValid()) {
         map.remove(path);
         return get(file);
      }

      return mv;
   }

   /**
    * Remove sub mv by providing the specified file path.
    * @param file the specified file.
    */
   public static void removeMap(BlockFile file) {
      map.remove(file.getName());
   }

   /**
    * Create an instance of SubMV.
    */
   public SubMV(XDimIndex[] dims, DefaultTableBlock table) {
      this.dims = dims;
      this.table = table;
   }

   /**
    * Create an instance of SubMV.
    */
   private SubMV(BlockFile file) throws IOException {
      this.file = file;
   }

   /**
    * For spark subclass.
    */
   protected SubMV() {
   }

   /**
    * Initialize.
    */
   protected void init() throws IOException {
      if(inited) {
         return;
      }

      this.modified = getModifiedTime();

      synchronized(this) {
         if(inited) {
            return;
         }

         if(file != null) {
            try(SeekableInputStream channel = file.openInputStream()) {
               read(channel);
            }
            finally {
               inited = true;
            }
         }
      }
   }

   /**
    * Check if this sub mv is valid.
    */
   protected final boolean isValid() {
      return modified == getModifiedTime();
   }

   /**
    * Get the last modification time of the base file.
    */
   protected long getModifiedTime() {
      return file != null ? file.lastModified() : System.currentTimeMillis();
   }

   /**
    * Get the table block as data.
    */
   public DefaultTableBlock getData() {
      return (DefaultTableBlock) table.clone();
   }

   /**
    * Get rows of the keys which is filtered by the condition.
    */
   public BitSet getRows(XNode node) {
      access(node);

      if(node instanceof XSet) {
         return getRows((XSet) node);
      }
      else if(node instanceof XBinaryCondition) {
         return getRows((XBinaryCondition) node);
      }
      else if(node instanceof XUnaryCondition) {
         return getRows((XUnaryCondition) node);
      }

      return null;
   }

   /**
    * Access the node.
    */
   private void access(XNode node) {
      if(node instanceof XSet) {
         XSet set = (XSet) node;
         int cnt = set.getChildCount();

         for(int i = 0; i < cnt; i++) {
            access(set.getChild(i));
         }
      }
      else if(node instanceof XBinaryCondition) {
         access((XBinaryCondition) node);
      }
   }

   /**
    * Access the condition.
    */
   private void access(XBinaryCondition cond) {
      String col = (String) cond.getExpression1().getValue();
      int idx = indexOfHeader(col);

      if(idx >= 0 && idx < rtdims.length && rtdims[idx] != null) {
         rtdims[idx].access();
      }
   }

   /**
    * Get rows of the keys which is filtered by the condition.
    */
   private BitSet getRows(XSet set) {
      String relation = set.getRelation();
      boolean and = XSet.AND.equals(relation);
      boolean isNot = set.isIsNot();
      BitSet res = null;

      // special handling for between condition
      if(and && (res = getBetween(set)) != null) {
         return res;
      }

      int cnt = set.getChildCount();

      for(int i = 0; i < cnt; i++) {
         XNode node = set.getChild(i);
         BitSet rows = (node instanceof XSet) ? getRows((XSet) node) :
            getRows((XBinaryCondition) node);

         // first item? do nothing
         if(i == 0) {
            res = rows;
            continue;
         }

         // ignore the filtering
         if(rows == null) {
            continue;
         }

         // and set
         if(and) {
            if(!isNot) {
               if(res == null || rows == null) {
                  res = null;
               }
               else {
                  res = res.and(rows);
               }
            }
            else {
               if(res == null) {
                  res = new BitSet();
               }

               if(rows == null) {
                  rows = new BitSet();
               }

               res = res.andNot(rows);
            }
         }
         // or set
         else {
            if(!isNot) {
               if(res == null) {
                  res = rows;
               }
               else if(rows != null) {
                  res = res.or(rows);
               }
            }
            else {
               if(res == null) {
                  res = new BitSet();
               }

               if(rows == null) {
                  rows = new BitSet();
               }

               res = res.xor(rows);
            }
         }
      }

      return res;
   }

   /**
    * Special processing for between conditions. A between condition can be
    * handled by an index in one call instead of two.
    */
   private BitSet getBetween(XSet cond) {
      if(cond.getChildCount() != 2) {
         return null;
      }

      XNode node1 = cond.getChild(0);
      XNode node2 = cond.getChild(1);

      if(node1 instanceof XBinaryCondition &&
         node2 instanceof XBinaryCondition)
      {
         XBinaryCondition cond1 = (XBinaryCondition) node1;
         XBinaryCondition cond2 = (XBinaryCondition) node2;

         if(cond1.isIsNot() || cond2.isIsNot()) {
            return null;
         }

         String op1 = cond1.getOp();
         String op2 = cond2.getOp();
         Object left1 = cond1.getExpression1().getValue();
         Object right1 = cond1.getExpression2().getValue();
         Object left2 = cond2.getExpression1().getValue();
         Object right2 = cond2.getExpression2().getValue();
         boolean leftInclusive = true;
         boolean rightInclusive = true;
         int idx = indexOfHeader((String) left1);

         if(left1.equals(left2) && idx >= 0) {
            if(">".equals(op1)) {
               leftInclusive = false;
            }
            else if(">=".equals(op1)) {
               leftInclusive = true;
            }
            else {
               return null;
            }

            if("<".equals(op2)) {
               rightInclusive = false;
            }
            else if("<=".equals(op2)) {
               rightInclusive = true;
            }
            else {
               return null;
            }

            boolean cnull = "true".equals(cond1.getAttribute("containsNull"));
            long from = getCondValue(right1, rtdims[idx], cnull);
            long to = getCondValue(right2, rtdims[idx], cnull);
            return rtdims[idx].getRows(from, leftInclusive, to,
                                       rightInclusive, cnull);
         }
      }

      return null;
   }

   /**
    * Set a flag indicating if Nulls should be included in Compare results,
    * instead of discarded
    */
   public void setIncludeNullCompare(boolean inc) {
      includeNullCompare = inc;
   }

   /**
    * Get the flag indicating if Nulls should be included in Compare results,
    * instead of discarded
    */
   public boolean getIncludeNullCompare() {
      return includeNullCompare;
   }

   /**
    * Get rows of the keys which is filtered by the condition.
    */
   private BitSet getRows(XBinaryCondition cond) {
      String col = (String) cond.getExpression1().getValue();
      int idx = indexOfHeader(col);
      String op = cond.getOp();
      Object val = cond.getExpression2() != null ? cond.getExpression2().getValue() : null;
      boolean cnull = "true".equals(cond.getAttribute("containsNull"));

      // 'in null' is same as 'in {}'
      if("IN".equals(op) && !(val instanceof Object[]) && "".equals(val)) {
         val = new Object[0];
      }

      // 'is null' should not be treated as 'in', we should ignore any
      // left-over value in the condition for 'is null' and always compare
      // against the 'null' value
      if("null".equals(op)) {
         val = null;
      }

      if(val instanceof Object[]) {
         Object[] objs = (Object[]) val;
         int size = objs.length;
         long[] vals = new long[size];

         if(idx >= rtdims.length) {
            return null;
         }

         for(int i = 0; i < size; i++) {
            vals[i] = getCondValue(objs[i], rtdims[idx], cnull);
         }

         boolean not = cond.isIsNot();

         // Pass the IncludeNullCompare flag down to the XDimIndex
         rtdims[idx].setIncludeNullCompare(getIncludeNullCompare());
         BitSet bset = rtdims[idx].getRows(op, vals, not, cnull);

         /*
         boolean nop = not ? op.startsWith(">") || op.equals("BETWEEN") :
            op.startsWith("<");

         // when evaluate ops like "<" and "<=", null might be included.
         // Here exclude null by resetting the position for null - 0
         if(nop && bset != null &&
            "true".equals(cond.getAttribute("containsNull")))
         {
            bset.clear(0);
         }
         */

         return bset;
      }
      else {
         if(idx >= rtdims.length) {
            return null;
         }

         // Pass the IncludeNullCompare flag down to the XDimIndex
         rtdims[idx].setIncludeNullCompare(getIncludeNullCompare());
         return rtdims[idx].getRows(op, getCondValue(val, rtdims[idx], cnull),
                                    cond.isIsNot(), cnull);
      }
   }

   /**
    * Get the value for comparison in the column index.
    * @cnull true if column contains null value
    */
   private long getCondValue(Object val, XDimIndex dim, boolean cnull) {
      if(dim instanceof MVMeasureColumn) {
         // @by davyc, see comment in MVDoubleColumn.getDimValue
         double dval = Tool.NULL_DOUBLE;

         if(val instanceof SubQueryValue) {
            throw new MessageException("Subquery condition not supported.");
         }
         else if(val != null && !"".equals(val)) {
            try {
               dval = Double.parseDouble(val + "");
            }
            catch(NumberFormatException ex) {
               throw ex;
            }
         }

         return Double.doubleToLongBits(dval);
      }

      // For Bug #1465, The data for null content was not found using the
      // MIN_VALUE representation, instead default to 0 for null content.
      if(val == null) {
         // if column contains no null, we shouldn't map null to 0 which
         // would be the first value in the dictionary
         return cnull ? 0 : -1;
      }

      return Integer.parseInt(val + "");
   }

   /**
    * Get the dimension count of this SubMV.
    */
   public int getDimCount() {
      return table.getDimCount();
   }

   /**
    * Get the measure count of this SubMV.
    */
   public int getMeasureCount() {
      return table.getMeasureCount();
   }

   /**
    * Get the index of the specified column header.
    */
   public int indexOfHeader(String header) {
      return table.indexOfHeader(header);
   }

   /**
    * Get the header at the specified column.
    */
   public String getHeader(int c) {
      return table.getHeader(c);
   }

   /**
    * Get the dimension index at the specified column.
    */
   public XDimIndex getIndex(int c) {
      return rtdims[c];
   }

   /**
    * Read from channel.
    */
   protected void read(SeekableInputStream channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.readFully(buf);
      XSwapUtil.flip(buf);
      int size = buf.getInt();
      dims = new XDimIndex[size];
      long opos = channel.position();
      buf = ByteBuffer.allocate(size * 4);
      channel.readFully(buf);
      XSwapUtil.flip(buf);
      long pos = channel.position();

      for(int i = 0; i < size; i++) {
         int type = buf.getInt();

         if(type == BIT_INDEX) {
            dims[i] = new BitDimIndex();
         }
         else if(type == DICT_INDEX) {
            // get from table block
            // dims[i] = new DictDimIndex(1);
         }
         else {
            // backward compatibility (10.3), the index type was not part of
            // the file and use BitDimIndex for all
            for(int j = 0; j < size; j++) {
               dims[j] = new BitDimIndex();
            }

            pos = opos;
            break;
         }
      }

      for(int i = 0; i < size; i++) {
         if(dims[i] != null) {
            dims[i].init(channel, 0, file, true);
         }
      }

      table = new DefaultTableBlock(channel, file);
      createRuntimeIndexes();
   }

   /**
    * Get the runtime column index.
    */
   private void createRuntimeIndexes() {
      rtdims = new XDimIndex[dims.length + table.mcols.length];

      for(int i = 0; i < rtdims.length; i++) {
         if(i < dims.length) {
            rtdims[i] = (dims[i] != null) ? dims[i] : table.dcols[i];
         }
         else {
            rtdims[i] = (XDimIndex) table.mcols[i - dims.length];
         }
      }
   }

   /**
    * Get the length of one dimension index.
    */
   private int getIndexLength(FileChannel channel, long pos) throws IOException {
      channel.position(pos);
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);

      return buf.getInt();
   }

   /**
    * Write to file.
    */
   public void write(BlockFile file) throws IOException {
      try(TransactionChannel channel = file.openWriteChannel()) {
         write(channel);
         channel.commit();
      }
   }

   /**
    * Write to channel.
    */
   public void write(WritableByteChannel channel) throws IOException {
      writeHeader(channel);
      ByteBuffer sbuf = null;

      for(XDimIndex dim : dims) {
         sbuf = write(channel, dim, sbuf);
      }

      table.write(channel);
   }

   /**
    * Call the complete on the dim index.
    */
   public void complete() {
      for(XDimIndex dim : dims) {
         if(dim != null) {
            dim.complete();
         }
      }
   }

   //----------------------------incremental functions--------------------------
   /**
    * Write header tag.
    */
   private void writeHeader(WritableByteChannel channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(getHeaderLength());
      buf.putInt(dims.length);

      for(XDimIndex dim : dims) {
         if(dim instanceof BitDimIndex) {
            buf.putInt(BIT_INDEX);
         }
         else if(dim == null || dim instanceof DictDimIndex) {
            buf.putInt(DICT_INDEX);
         }
         else {
            throw new RuntimeException("Unknown index: " + dim);
         }
      }

      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }
   }

   /**
    * Get header tag length.
    */
   private int getHeaderLength() {
      return 4 + dims.length * 4;
   }

   /**
    * Write an dim index.
    */
   private ByteBuffer write(WritableByteChannel channel, XDimIndex dim, ByteBuffer sbuf)
      throws IOException
   {
      if(dim != null) {
         sbuf = dim.write(channel, sbuf);
      }

      return sbuf;
   }

   /**
    * Create new mv columm by old mv column.
    */
   private static XMVColumn createMVColumn(XMVColumn ocol1, XMVColumn ocol2,
                                           long[] pos, BlockFile file, int rcnt)
   {
      XMVColumn ncol = createMVColumn0(ocol1, ocol2, pos, file, rcnt);
      ((XDimIndex) ncol).setCompressed(((XDimIndex) ocol1).isCompressed());
      return ncol;
   }

   private static XMVColumn createMVColumn0(XMVColumn ocol1, XMVColumn ocol2,
                                            long[] pos, BlockFile file, int rcnt)
   {
      long pos0 = pos[0];

      if(ocol1 instanceof MVDimColumn) {
         MVDimColumn odim = (MVDimColumn) ocol1;
         MVDimColumn odim2 = (MVDimColumn) ocol2;
         int card = Math.max(odim.getCardinality(), odim2.getCardinality()) + 3;
         MVDimColumn ndim = new MVDimColumn(card, rcnt, true);
         ndim.setRangeMin(odim.getRangeMin());
         ndim.setContainsNull(odim.containsNull());
         return ndim;
      }
      else if(ocol1 instanceof MVIntColumn) {
         MVIntColumn oicol = (MVIntColumn) ocol1;
         MVIntColumn oicol2 = (MVIntColumn) ocol2;
         oicol.access();
         oicol2.access();
         Number min = minmax(oicol.min(), oicol2.min(), true);
         Number max = minmax(oicol.max(), oicol2.max(), false);

         MVIntColumn ncol = new MVIntColumn(rcnt, min, max);
         return ncol;
      }
      else if(ocol1 instanceof MVDateColumnWrapper) {
         MVDateColumnWrapper dcol = (MVDateColumnWrapper) ocol1;
         MVMeasureColumn measureCol = new MVDateColumnWrapper(
            dcol.getLevel(), dcol.getOriginalColumn(), null, pos0, file, rcnt);
         pos[0] += measureCol.getLength();
         return measureCol;
      }
      else if(ocol1 instanceof MVDoubleColumn) {
         pos[0] += rcnt * 8;
         MVMeasureColumn measureCol = new MVDoubleColumn(null, pos0, file, rcnt, true);
         pos[0] += measureCol.getLength();
         return measureCol;
      }
      else if(ocol1 instanceof MVDateIntColumn) {
         pos[0] += rcnt * 4;
         MVMeasureColumn measureCol = new MVDateIntColumn(null, pos0, file, rcnt, true);
         pos[0] += measureCol.getLength();
         return measureCol;
      }
      else if(ocol1 instanceof MVTimeIntColumn) {
         pos[0] += rcnt * 4;
         MVMeasureColumn measureCol = new MVTimeIntColumn(null, pos0, file, rcnt, true);
         pos[0] += measureCol.getLength();
         return measureCol;
      }
      else if(ocol1 instanceof MVTimestampIntColumn) {
         pos[0] += rcnt * 4;
         MVMeasureColumn measureCol = new MVTimestampIntColumn(null, pos0, file, rcnt,
                                                               true);
         pos[0] += measureCol.getLength();
         return measureCol;
      }
      else if(ocol1 instanceof MVFloatColumn) {
         pos[0] += rcnt * 4;
         MVMeasureColumn measureCol = new MVFloatColumn(null, pos0, file, rcnt, true);
         pos[0] += measureCol.getLength();
         return measureCol;
      }

      throw new RuntimeException("Unsupported mv column: " + ocol1.getClass());
   }

   /**
    * Get the min/max of two numbers.
    */
   private static Number minmax(Number n1, Number n2, boolean min) {
      if(n2 == null) {
         return n1;
      }
      else if(n1 == null) {
         return n2;
      }

      if(min) {
         return (n1.doubleValue() > n2.doubleValue()) ? n2 : n1;
      }
      else {
         return (n1.doubleValue() < n2.doubleValue()) ? n2 : n1;
      }
   }

   /**
    * Apply mv condition to delete records from mv.
    * @return if condition applied.
    */
   public final boolean deleteRecord(XNode cond) throws IOException {
      BitSet rows = getRows(cond);

      if(rows.isEmpty()) {
         return false;
      }

      BlockFile tempfile = new CacheBlockFile("mvcolswap", "dat");

      try(TransactionChannel channel = file.openWriteChannel()) {
         // 1: write SubMV header
         writeHeader(channel);
         ByteBuffer sbuf = null;
         BitSet drows = rows.or(new BitSet());
         BitDimIndex[] bdims = new BitDimIndex[dims.length];
         long[] filepos = {0};

         // re-build BitDimIndex
         for(int i = 0; i < dims.length; i++) {
            if(!(dims[i] instanceof BitDimIndex)) {
               continue;
            }

            bdims[i] = new BitDimIndex();
            bdims[i].setCompressed(dims[i].isCompressed());
         }

         int orcnt = table.rcnt;
         int rcnt = orcnt - drows.rowCount();
         int dcnt = table.dcols.length;
         int mcnt = table.mcols.length;
         int ccnt = dcnt + mcnt;

         DefaultTableBlock block = new DefaultTableBlock(rcnt, dcnt, mcnt);
         block.init(dcnt, mcnt, table.columnNames, table.identifiers, rcnt);
         block.dcols = new MVDimColumn[dcnt];
         block.mcols = new MVMeasureColumn[mcnt];

         for(int i = 0; i < ccnt; i++) {
            boolean dim = i < dcnt;
            int c = dim ? i : i - dcnt;
            MVDimColumn ndcol = null;
            MVMeasureColumn nmcol = null;
            MVDimColumn odcol = null;
            MVMeasureColumn omcol = null;

            if(dim) {
               odcol = table.dcols[c];
               ndcol = (MVDimColumn) createMVColumn(odcol, odcol, filepos, tempfile, rcnt);
            }
            else {
               omcol = table.mcols[c];
               nmcol = (MVMeasureColumn) createMVColumn(omcol, omcol, filepos, tempfile, rcnt);
            }

            int r = 0;

            // wrapper column, create over
            if(dim || !(nmcol instanceof MVColumnWrapper)) {
               for(int j = 0; j < orcnt; j++) {
                  if(drows.get(j)) {
                     continue;
                  }

                  if(dim && ndcol != null && odcol != null) {
                     int gval = odcol.getValue(j);

                     if(bdims[i] != null) {
                        bdims[i].addKey(gval, r);
                     }

                     ndcol.setValue(r, gval);
                  }
                  else if(nmcol != null && omcol != null) {
                     nmcol.setValue(r, omcol.getValue(j));
                  }

                  r++;
               }
            }

            // 2: write SubMV.dims
            if(i < bdims.length && bdims[i] != null) {
               bdims[i].complete();
               sbuf = write(channel, bdims[i], sbuf);
               // release it
               bdims[i] = null;
            }

            // release memory
            if(dim) {
               table.dcols[c] = null;
               block.dcols[c] = ndcol;
            }
            else {
               table.mcols[c] = null;
               block.mcols[c] = nmcol;
            }
         }

         // 3: write SubMV.block
         block.write(channel);
         // 4: rename
         channel.commit();

         // Now that data is deleted, clear all rowscache's within the rtdims
         AbstractMeasureColumn.clearRowCache();

         return true;
      }
      finally {
         try {
            tempfile.delete();
         }
         catch(Exception ex) {
            LOG.warn("Failed to delete temporary file after deleting record: {}", tempfile, ex);
         }
      }
   }

   /**
    * Update the sub mv dim index with the index mappings.
    */
   public void updateData(Map<Integer, Integer>[] maps, int[] sizes,
                          int[] dimRanges, List<Number[]> intRanges)
      throws IOException
   {
      try(TransactionChannel channel = file.openWriteChannel()) {
         // 1: write mv header
         writeHeader(channel);
         ByteBuffer sbuf = null;

         for(int i = 0; i < dims.length; i++) {
            if(!(dims[i] instanceof BitDimIndex)) {
               continue;
            }

            BitDimIndex dim = (BitDimIndex) dims[i];
            dim.access();

            if(maps[i] != null) {
               dim.updateKeys(maps[i]);
            }

            // 2: write dims
            sbuf = write(channel, dim, sbuf);

            // release memory
            dims[i] = null;
         }

         // 3: update columns in DefaultTableBlock
         int rcnt = table.rcnt;
         int mcnt = table.mcnt;
         int dcnt = table.dcnt;
         int ccnt = dcnt + mcnt;

         for(int i = 0; i < ccnt; i++) {
            Map<Integer, Integer> mapping = maps[i];
            int c = i < dcnt ? i : i - dcnt;
            XMVColumn[] columns = i < dcnt ? table.dcols : table.mcols;
            XMVColumn ocolumn = columns[c];

            if(mapping == null) {
               // 4: no mapping? doon't change
            }
            else if(ocolumn instanceof MVIntColumn) {
               Number[] minMax = intRanges.get(c);
               MVIntColumn oicol = (MVIntColumn) ocolumn;
               MVIntColumn ncolumn = new MVIntColumn(rcnt, minMax[0], minMax[1]);
               ncolumn.setCompressed(oicol.isCompressed());

               for(int r = 0; r < rcnt; r++) {
                  int val = (int) oicol.getValue(r);
                  Integer nval = mapping.get(val);
                  val = nval == null ? val : nval;
                  ncolumn.setValue(r, val);
               }

               columns[c] = ncolumn;
            }
            else if(ocolumn instanceof MVDimColumn) {
               MVDimColumn odcol = (MVDimColumn) ocolumn;
               MVDimColumn ncolumn = new MVDimColumn(sizes[c], rcnt, true);
               ncolumn.setRangeMin(dimRanges[c]);
               ncolumn.setContainsNull(odcol.containsNull());
               ncolumn.setCompressed(odcol.isCompressed());

               for(int r = 0; r < rcnt; r++) {
                  int val = odcol.getValue(r);
                  Integer nval = mapping.get(val);
                  val = nval == null ? val : nval;
                  ncolumn.setValue(r, val);
               }

               columns[c] = ncolumn;
            }
         }

         // 5: write table
         table.write(channel);

         // 6: rename
         channel.commit();
      }
   }

   /**
    * Merge the new sub mv to current.
    */
   public final void appendBlock(SubMV nsub)
      throws IOException
   {
      SubMV sub0 = this;
      SubMV sub1 = nsub;

      // prepare dim index
      for(int i = 0; i < sub0.dims.length; i++) {
         // any block dim is null, the new dim is null,
         // here just set this block dim to null, so
         // that when create bak file, the header can
         // write correct
         if(sub0.dims[i] == null || sub1.dims[i] == null) {
            sub0.dims[i] = null;
            sub1.dims[i] = null;
         }
      }

      try(TransactionChannel channel = file.openWriteChannel()) {
         // 1: write sub mv header
         writeHeader(channel);
         int rcnt = sub0.table.rcnt;
         // 2: write bit dim index
         mergeDimIndex(sub0.dims, sub1.dims, rcnt, channel);
         // 3: write table
         mergeBlock(sub0.table, sub1.table, channel);
         // rename
         channel.commit();
      }
   }

   /**
    * Merge dim index.
    */
   private void mergeDimIndex(XDimIndex[] dims0, XDimIndex[] dims1, int rcnt,
                              TransactionChannel channel) throws IOException
   {
      ByteBuffer sbuf = null;

      for(int i = 0; i < dims0.length; i++) {
         if(dims0[i] != null && dims1[i] != null) {
            BitDimIndex dim0 = (BitDimIndex) dims0[i];
            BitDimIndex dim1 = (BitDimIndex) dims1[i];
            BitDimIndex ndim = BitDimIndex.merge(dim0, dim1, rcnt);
            sbuf = write(channel, ndim, sbuf);
            // release memory
            dims0[i] = null;
            dims1[i] = null;
         }
      }
   }

   /**
    * Merge two tables to one block directly
    * @param table0 the target table block.
    * @param table1 a new table block.
    */
   private void mergeBlock(DefaultTableBlock table0, DefaultTableBlock table1,
                           TransactionChannel channel) throws IOException
   {
      int brow = table0.rcnt;
      int rcnt = table0.rcnt + table1.rcnt;
      int dcnt = table0.dcnt;
      int mcnt = table0.mcnt;
      int ccnt = dcnt + mcnt;

      DefaultTableBlock merged = new DefaultTableBlock(rcnt, dcnt, mcnt);
      merged.columnNames = table0.columnNames;
      merged.identifiers = table0.identifiers;

      // 1: merge columns
      // direct byte buffers don't offer any performance enhancement when their
      // size is less than ~1K
      ByteBuffer buf = ByteBuffer.allocate(8);
      ByteBuffer sbuf = null;
      long[] filepos = {0};
      BlockFile tempfile = new CacheBlockFile("mvcolswap", "dat");

      try {
         for(int i = 0; i < ccnt; i++) {
            boolean dim = i < dcnt;
            int c = dim ? i : i - dcnt;
            XMVColumn col1 = dim ? table0.dcols[c] : table0.mcols[c];
            XMVColumn col2 = dim ? table1.dcols[c] : table1.mcols[c];

            // DefaultTableBlock.addRow() can change the column type, based on data
            // so if that occurs, it need to be propagated across the block merge.
            // col1 needs to change from MVTimestampIntColumn to MVDoubleColumn
            if(!dim && col1 instanceof MVTimestampIntColumn &&
               col2 instanceof MVDoubleColumn)
            {
               MVTimestampIntColumn col1t = (MVTimestampIntColumn)col1;
               MVDoubleColumn col1new = new MVDoubleColumn(null, col1t.getFpos(),
                  col1t.getFile(), col1t.getRowCount(), true);

               for(int r = 0; r < col1t.getRowCount(); r++) {
                  col1new.setValue(r, col1t.getValue(r));
               }

               table0.mcols[c] = col1new;
               col1 = col1new;
            }

            // col2 needs to change from MVTimestampIntColumn to MVDoubleColumn
            if(!dim && col2 instanceof MVTimestampIntColumn &&
               col1 instanceof MVDoubleColumn)
            {
               MVTimestampIntColumn col2t = (MVTimestampIntColumn)col2;
               MVDoubleColumn col2new = new MVDoubleColumn(null, col2t.getFpos(),
                  col2t.getFile(), col2t.getRowCount(), true);

               for(int r = 0; r < col2t.getRowCount(); r++) {
                  col2new.setValue(r, col2t.getValue(r));
               }

               table1.mcols[c] = col2new;
               col2 = col2new;
            }

            XMVColumn ncol = createMVColumn(col1, col2, filepos, tempfile, rcnt);

            // column wrapper? created over
            if(!(ncol instanceof MVColumnWrapper)) {
               for(int k = 0; k < rcnt; k++) {
                  Object from = col1;
                  int r = k;

                  if(r >= brow) {
                     from = col2;
                     r -= brow;
                  }

                  if(dim) {
                     ((MVDimColumn) ncol).setValue(
                        k, ((MVDimColumn) from).getValue(r));
                  }
                  else {
                     ((MVMeasureColumn) ncol).setValue(
                        k, ((MVMeasureColumn) from).getValue(r));
                  }
               }
            }

            // write dict column and release memory
            if(dim) {
               merged.dcols[c] = (MVDimColumn) ncol;
               table0.dcols[c] = null;
               table1.dcols[c] = null;
            }
            else {
               merged.mcols[c] = (MVMeasureColumn) ncol;
               table0.mcols[c] = null;
               table1.mcols[c] = null;
            }
         }

         // write table data
         merged.write(channel);
      }
      finally {
         try {
            tempfile.delete();
         }
         catch(Exception ex) {
            LOG.warn("Failed to delete temporary file after merging block: {}", tempfile, ex);
         }
      }
   }

   /**
    * Set this sub mv block index, only for incremental mv.
    */
   public void setBlockIndex(int bidx) {
      this.blockIndex = bidx;
   }

   /**
    * Get block index.
    */
   public int getBlockIndex() {
      return blockIndex;
   }

   /**
    * Clean up any data structure and temporary files.
    */
   public void dispose() {
      table.dispose();
      dims = null;
      rtdims = null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SubMV.class);

   // index type flags
   private static final int BIT_INDEX = 1;
   private static final int DICT_INDEX = 2;

   protected static final ConcurrentMap<String, SubMV> map = new ConcurrentHashMap<>();

   private DefaultTableBlock table;
   private XDimIndex[] dims;
   private XDimIndex[] rtdims = {};

   protected boolean inited = false;
   protected BlockFile file = null;
   protected long modified;

   //-----------------------------incremental properties
   private transient int blockIndex = -1;
   private transient boolean includeNullCompare = false;
}
