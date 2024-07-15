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
package inetsoft.mv.data;

import inetsoft.mv.*;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.*;

/**
 * DefaultTableBlock, the default implementation of XTableBlock.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class DefaultTableBlock implements XTableBlock, Cloneable {
   /**
    * Constructor.
    * @param rcnt the number of rows in the table.
    * @param dcnt the number of dimensions.
    * @param mcnt the number of measures.
    */
   public DefaultTableBlock(int rcnt, int dcnt, int mcnt) {
      super();

      this.rcnt = rcnt;
      this.dcnt = dcnt;
      this.mcnt = mcnt;
      this.dcols = new MVDimColumn[dcnt];
      this.mcols = new MVMeasureColumn[mcnt];
   }

   /**
    * Constructor.
    * @param rcnt the number of rows in the table.
    * @param dcnt the number of dimensions.
    * @param mcnt the number of measures.
    */
   public DefaultTableBlock(int rcnt, int dcnt, int mcnt, MVColumnInfo[] cinfos,
                            String[] columnNames, String[] identifiers,
                            Class[] types, MVColumn[] mvcols)
   {
      this(rcnt, dcnt, mcnt);
      this.file = new CacheBlockFile("mvcolswap", "dat");
      isTemp = true;

      for(int i = 0; i < dcnt; i++) {
         XDimDictionary dict = cinfos[i].getDictionary();
         XSwapper.getSwapper().waitForMemory();
         dcols[i] = new MVDimColumn(dict.size(), rcnt, true);
         dcols[i].setRangeMin(dict.getRangeMin());
         dcols[i].setContainsNull(dict.containsNull());
      }

      long[] pos = {0};

      for(int i = 0; i < mcnt; i++) {
         XSwapper.getSwapper().waitForMemory();
         mcols[i] = createMeasureColumn(types[i + dcnt], mvcols[i + dcnt],
            columnNames, cinfos[i + dcnt].getMin(), cinfos[i + dcnt].getMax(), pos);
      }

      init(dcnt, mcnt, columnNames, identifiers, rcnt);
   }

   /**
    * Constructor for creating default table block in spark.
    */
   public DefaultTableBlock(int rcnt, int dcnt, int mcnt, int[] dictSize,
                            int[] rangeMin, boolean[] containsNull,
                            Class[] types, MVColumn[] mvcols,
                            String[] columnNames, String[] identifiers,
                            Object[] min, Object[] max)
   {
      this.rcnt = rcnt;
      this.dcnt = dcnt;
      this.mcnt = mcnt;
      this.dcols = new MVDimColumn[dcnt];
      this.mcols = new MVMeasureColumn[mcnt];

      this.file = new CacheBlockFile("mvcolswap", "dat");
      isTemp = true;

      for(int i = 0; i < dcnt; i++) {
         dcols[i] = new MVDimColumn(dictSize[i], rcnt, true);
         dcols[i].setRangeMin(rangeMin[i]);
         dcols[i].setContainsNull(containsNull[i]);
      }

      long[] pos = {0};

      for(int i = 0; i < mcnt; i++) {
         mcols[i] = createMeasureColumn(types[i + dcnt], mvcols[i + dcnt],
            columnNames, min[i + dcnt], max[i + dcnt], pos);
      }

      init(dcnt, mcnt, columnNames, identifiers, rcnt);
   }

   /**
    * Get original data column.
    */
   public static int getOriginalColumn(String[] columnNames, MVColumn mvcol) {
      String name = mvcol.getName();
      int start = name.indexOf("(");
      int end = name.lastIndexOf(")");

      if(start >= 0 && end >= 0 && start < end) {
         String oname = name.substring(start + 1, end);

         for(int i = 0; i < columnNames.length; i++) {
            if(oname.equals(columnNames[i])) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Create a measure column for the measure type.
    */
   private MVMeasureColumn createMeasureColumn(Class type, MVColumn mvcol,
      String[] columnNames, Object cinfoMin, Object cinfoMax, long[] pos)
   {
      long pos0 = pos[0];

      if(mvcol instanceof DateMVColumn && !((DateMVColumn) mvcol).isReal()) {
         int idx = getOriginalColumn(columnNames, mvcol);

         if(idx >= 0) {
            int level = ((DateMVColumn) mvcol).getLevel();
            MVMeasureColumn measureCol = new MVDateColumnWrapper(
               level, idx, null, pos0, file, rcnt);
            pos[0] += measureCol.getLength();
            return measureCol;
         }
      }

      if(MVQuery.isIntColumn(type, mvcol)) {
         // fix bug1346842806926,
         // see comment MVQuery.isIntColumn(), for non-number type...
         Number min = mvcol.getOriginalMin() == null ? null : (Number) cinfoMin;
         Number max = mvcol.getOriginalMin() == null ? null : (Number) cinfoMax;
         return new MVIntColumn(rcnt, min, max);
      }

      MVMeasureColumn measureCol;
      boolean doubleCol = "true".equals(SreeEnv.getProperty("mv.double.precision"));

      if(Number.class.isAssignableFrom(type) && !doubleCol) {
         measureCol = new MVFloatColumn(null, pos0, file, rcnt, true);
      }
      else if(type.equals(java.sql.Time.class)) {
         measureCol = new MVTimeIntColumn(null, pos0, file, rcnt, true);
      }
      else if(type.equals(java.sql.Date.class)) {
         measureCol = new MVDateIntColumn(null, pos0, file, rcnt, true);
      }
      else if(type.equals(java.sql.Timestamp.class)) {
         measureCol = new MVTimestampIntColumn(null, pos0, file, rcnt, true);
         // Note: measureCol may need to be changed to a MVDoubleColumn, in addRow()
      }
      else {
         measureCol = new MVDoubleColumn(null, pos0, file, rcnt, true);
      }

      pos[0] += measureCol.getLength();
      return measureCol;
   }

   /**
    * Constructor.
    */
   public DefaultTableBlock(SeekableInputStream channel0, BlockFile file) throws IOException {
      super();

      SeekableInputStream channel = channel0;
      this.file = file;
      this.start = channel.position();

      if((channel == null || !channel.isOpen()) && file != null) {
         try {
            channel = file.openInputStream();
         }
         catch(FileNotFoundException ex) {
            LOG.debug("Block file not found: " + file, ex);
         }
      }
      else if(channel != null && !channel.isOpen()) {
         channel = channel0.reopen();
      }

      read(channel);

      if(channel != channel0) {
         try {
            channel.close();
         }
         catch(IOException ex) {
            LOG.debug("Failed to close file: " + file, ex);
         }
      }
   }

   /**
    * Initialize this table block.
    */
   void init(int dcnt, int mcnt, String[] columnNames, String[] identifiers, int rcnt) {
      this.dcnt = dcnt;
      this.mcnt = mcnt;
      this.columnNames = columnNames;
      this.identifiers = identifiers;

      row = new MVRow(new long[dcnt], new double[mcnt]);

      for(MVMeasureColumn mcol : mcols) {
         if(mcol instanceof MVColumnWrapper) {
            ((MVColumnWrapper) mcol).setDefaultTableBlock(this);
         }
      }
   }

   /**
    * Initialize groups and measures and its position.
    */
   public void init(SubMVQuery query) {
      int dsize = query.groups.length;
      dmatrix = new XMVColumn[query.groups.length];

      for(int i = 0; i < dsize; i++) {
         DataRef ref = GroupedTableBlock.getDataRef(query.groups[i]);
         String cname = MVDef.getMVHeader(ref);
         dmatrix[i] = getMVColumn(cname, 0);
      }

      String[] aggregates = query.getAggregateColumns();
      int msize = aggregates.length;
      mmatrix = new XMVColumn[msize];

      row = new MVRow(new long[dsize], new double[msize]);

      for(int i = 0; i < msize; i++) {
         mmatrix[i] = getMVColumn(aggregates[i], dcnt);

         if(mmatrix[i] == null) {
            mmatrix[i] = getMVColumn(aggregates[i], 0);

            if(mmatrix[i] != null) {
               boolean count = query.getAggregate(i).getFormula() == AggregateFormula.COUNT_ALL ||
                query.getAggregate(i).getFormula() == AggregateFormula.COUNT_DISTINCT;

               if(!count) {
                  LOG.warn("Aggregate performed on a column marked as dimension. " +
                           "Check the column type: " + aggregates[i]);
               }
            }
         }
      }
   }

   /**
    * Get the column from the name.
    */
   private XMVColumn getMVColumn(String name, int start) {
      int idx = indexOfHeader(name, start);

      if(idx < 0) {
         return null;
      }

      if(idx < dcols.length) {
         return dcols[idx];
      }

      return mcols[idx - dcols.length];
   }

   /**
    * Add a row.
    */
   public void addRow(int index, int[] groups, double[] measures) {
      for(int i = 0; i < groups.length; i++) {
         addDimension(index, i, groups[i]);
      }

      for(int i = 0; i < measures.length; i++) {
         addMeasure(index, i, measures[i]);
      }
   }

   public void addDimension(int blockIndex, int colIndex, int group) {
      dcols[colIndex].setValue(blockIndex, group);
   }

   public void addMeasure(int blockIndex, int colIndex, double measure) {
      // MVTimestampIntColumn can't handle a negative value,
      // so use a MVDoubleColumn instead if that happens
      if(measure < 0 && mcols[colIndex] instanceof MVTimestampIntColumn) {
         MVTimestampIntColumn oldMcol = (MVTimestampIntColumn) mcols[colIndex];
         mcols[colIndex] = new MVDoubleColumn(null, oldMcol.getFpos(),
                                       oldMcol.getFile(), oldMcol.getRowCount(), true);

         for(int r = 0; r < blockIndex; r++) {
            mcols[colIndex].setValue(r, oldMcol.getValue(r));
         }
      }

      mcols[colIndex].setValue(blockIndex, measure);
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return rcnt;
   }

   /**
    * Reduce the number of rows.
    */
   public void setRowCount(int rcnt) {
      if(rcnt > this.rcnt) {
         throw new RuntimeException("Rows can only be reduced in DefaultTableBlock: " +
                                    this.rcnt + " to " + rcnt);
      }

      if(rcnt == this.rcnt) {
         return;
      }

      this.rcnt = rcnt;

      for(int i = 0; i < dcols.length; i++) {
         dcols[i].setRowCount(rcnt);
      }

      for(int i = 0; i < mcols.length; i++) {
         mcols[i].setRowCount(rcnt);
      }
   }

   /**
    * Get the col count of this XTableBlock.
    */
   @Override
   public int getColCount() {
      return columnNames.length;
   }

   /**
    * Get the dimension count of this XTableBlock.
    */
   @Override
   public int getDimCount() {
      return dcnt;
   }

   /**
    * Get the measure count of this XTableBlock.
    */
   @Override
   public int getMeasureCount() {
      return mcnt;
   }

   /**
    * Get the Header at the specified column.
    */
   @Override
   public String getHeader(int c) {
      return columnNames[c];
   }

   /**
    * Get the index of the specified column header.
    */
   @Override
   public int indexOfHeader(String header) {
      return indexOfHeader(header, 0);
   }

   /**
    * Get the index of the specified column header.
    */
   private int indexOfHeader(String header, int start) {
      int cnt = columnNames.length;

      for(int i = start; i < cnt; i++) {
         if(columnNames[i].equalsIgnoreCase(header)) {
            return i;
         }
      }

      for(int i = start; i < cnt; i++) {
         if(identifiers[i] != null && identifiers[i].equalsIgnoreCase(header)) {
            return i;
         }
      }

      String nheader = MVDef.getLMHeader(header);

      if(nheader != null) {
         return indexOfHeader(nheader, start);
      }

      int idx = MVDef.indexOfHeader(header, columnNames, start);

      if(idx >= 0) {
         return idx;
      }

      if(start > 0) {
         return -1;
      }

      throw new ColumnNotFoundException("Column[" + header + "] not found in [" +
         Arrays.asList(columnNames) + Arrays.asList(identifiers) + "]");
   }

   /**
    * Subclass RuntimeException, so this "missing column" exception can be
    * caught in XTableRecordReader.nextKeyValue()
    */
   public static class ColumnNotFoundException extends RuntimeException {
      public ColumnNotFoundException(String message) {
         super(message);
      }
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) throws IOException {
      // copy dimension values from dmatrix
      for(int i = 0; i < dmatrix.length; i++) {
         row.groups[i] = dmatrix[i].getDimValue(r);
      }

      for(int i = 0; i < mmatrix.length; i++) {
         row.aggregates[i] = mmatrix[i].getMeasureValue(r);
      }

      return row;
   }

   /**
    * Get the row (dimension only) at the specified column.
    */
   public MVRow getDRow(int r) throws IOException {
      for(int i = 0; i < dmatrix.length; i++) {
         row.groups[i] = dmatrix[i].getDimValue(r);
      }

      return row;
   }

   /**
    * Get a row with a single dimension column.
    */
   public MVRow getDRow1(int r) throws IOException {
      row.groups[0] = dmatrix[0].getDimValue(r);
      return row;
   }

   /**
    * Clone this table block.
    */
   @Override
   protected Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // impossible
         return null;
      }
   }

   /**
    * Read from channel.
    */
   public void read(SeekableInputStream channel) throws IOException {
      // --------------- read header
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      int bufferLength = buf.getInt();
      buf = ByteBuffer.allocate(bufferLength);
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      rcnt = buf.getInt();
      dcnt = buf.getInt();
      mcnt = buf.getInt();
      int cnt = dcnt + mcnt;
      columnNames = new String[cnt];
      identifiers = new String[cnt];
      int[] dimTypes = new int[dcnt];
      int[] dimCardinalities = new int[dcnt];
      int[] measureTypes = new int[mcnt];
      int[] measureBits = new int[mcnt];

      for(int i = 0; i < cnt; i++) {
         char[] chars = new char[buf.getInt()];

         for(int j = 0; j < chars.length; j++) {
            chars[j] = buf.getChar();
         }

         columnNames[i] = new String(chars);
      }

      for(int i = 0; i < cnt; i++) {
         int len = buf.getInt();

         if(len != 0) {
            char[] chars = new char[len];

            for(int j = 0; j < chars.length; j++) {
               chars[j] = buf.getChar();
            }

            identifiers[i] = new String(chars);
         }
      }

      // read dim header
      for(int i = 0; i < dcnt; i++) {
         dimTypes[i] = buf.getInt();
         dimCardinalities[i] = buf.getInt();
      }

      // read measure header
      for(int i = 0; i < mcnt; i++) {
         measureTypes[i] = buf.getInt();
         measureBits[i] = buf.getInt();
      }

      // read column sizes
      int[] colsizes = new int[dcnt + mcnt];
      buf = ByteBuffer.allocate(colsizes.length * 4);
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      for(int i = 0; i < colsizes.length; i++) {
         colsizes[i] = buf.getInt();
      }

      // --------------- read header end

      dcols = new MVDimColumn[dcnt];

      for(int i = 0; i < dcnt; i++) {
         int cardinality = dimCardinalities[i];
         long pos = channel.position();
         dcols[i] = new MVDimColumn(cardinality, rcnt, false);
         dcols[i].init(channel, 0, file, false);
         channel.position(pos + colsizes[i]);
      }

      mcols = new MVMeasureColumn[mcnt];

      for(int i = 0; i < mcnt; i++) {
         int type0 = measureTypes[i];
         int bits = measureBits[i];
         long pos = channel.position();
         mcols[i] = createMeasureColumn(type0, channel, rcnt, bits);
         ((XDimIndex) mcols[i]).init(channel, 0, file, false);
         channel.position(pos + colsizes[dcnt + i]);
      }

      init(dcnt, mcnt, columnNames, identifiers, rcnt);
   }

   /**
    * Write to channel.
    */
   public void write(WritableByteChannel channel) throws IOException {
      writeHeader(channel);
      ByteBuffer sbuf = null;
      List<File> files = new ArrayList<>();
      XMVColumn[][] allcols = {dcols, mcols};

      // write dimension/measure values to temp files
      for(XMVColumn[] cols : allcols) {
         for(int i = 0; i < cols.length; i++) {
            File file = FileSystemService.getInstance().getCacheTempFile("mvcolswap", "dat");
            RandomAccessFile rfile = new RandomAccessFile(file, "rw");

            try {
               FileChannel channel2 = rfile.getChannel();

               files.add(file);
               sbuf = cols[i].write(channel2, sbuf);
            }
            finally {
               rfile.close();
            }
         }
      }

      // write column sizes
      ByteBuffer buf = ByteBuffer.allocate((dcnt + mcnt) * 4);

      for(File file : files) {
         buf.putInt((int) file.length());
      }

      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      // copy column data
      for(File file : files) {
         RandomAccessFile rfile = new RandomAccessFile(file, "r");

         try {
            FileChannel channel2 = rfile.getChannel();
            MVTool.copyChannel(channel2, channel);
         }
         finally {
            rfile.close();
            file.delete();
         }
      }
   }

   /**
    * Write table header tag.
    */
   private void writeHeader(WritableByteChannel channel) throws IOException {
      int len = 12; // rcnt, dcnt, mcnt

      len += dcnt * 2 * 4; // dim header
      len += mcnt * 2 * 4; // measure header

      for(String header : columnNames) {
         len += 4 + header.length() * 2;
      }

      for(String identifier : identifiers) {
         len += 4 + (identifier == null ? 0 : identifier.length() * 2);
      }

      ByteBuffer buf = ByteBuffer.allocate(len + 4);
      buf.putInt(len);
      buf.putInt(rcnt);
      buf.putInt(dcnt);
      buf.putInt(mcnt);

      for(String header : columnNames) {
         buf.putInt(header.length());

         for(int i = 0; i < header.length(); i++) {
            buf.putChar(header.charAt(i));
         }
      }

      for(String identifier : identifiers) {
         if(identifier == null) {
            buf.putInt(0);
         }
         else {
            buf.putInt(identifier.length());

            for(int i = 0; i < identifier.length(); i++) {
               buf.putChar(identifier.charAt(i));
            }
         }
      }

      // write dimension values
      for(int i = 0; i < dcnt; i++) {
         writeDimHeader(dcols[i], buf);
      }

      // write measure values
      for(int i = 0; i < mcnt; i++) {
         writeMeasureHeader(mcols[i], buf);
      }

      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }
   }

   /**
    * Write dimension column header.
    */
   private void writeDimHeader(MVDimColumn dim, ByteBuffer buf) {
      buf.putInt(1); // dim type, for future use
      buf.putInt(dim.getCardinality());
   }

   /**
    * Write measure column header.
    */
   private void writeMeasureHeader(MVMeasureColumn mcol, ByteBuffer buf) {
      buf.putInt(getMeasureColumnType(mcol));
      buf.putInt(mcol.getBits());
   }

   /**
    * Get the measure column type. This must match createMeasureColumn().
    */
   private int getMeasureColumnType(MVMeasureColumn mcol) {
      if(mcol instanceof MVDateColumnWrapper) {
         return 3;
      }
      else if(mcol instanceof MVTimeIntColumn) {
         return 6;
      }
      else if(mcol instanceof MVDateIntColumn) {
         return 5;
      }
      else if(mcol instanceof MVTimestampIntColumn) {
         return 7;
      }
      else if(mcol instanceof MVFloatColumn) {
         return 4;
      }
      else if(mcol instanceof MVDoubleColumn) {
         return 1;
      }
      else if(mcol instanceof MVIntColumn) {
         return 2;
      }

      return 0;
   }

   /**
    * Create a measure column with the speicified type type.
    */
   private MVMeasureColumn createMeasureColumn(int type,
      SeekableInputStream channel, int rcnt, int bits)
   {
      switch(type) {
      case 1:
         return new MVDoubleColumn(channel, 0, file, rcnt, false);
      case 2:
         return new MVIntColumn(channel, 0, file, rcnt, bits);
      case 3:
         return new MVDateColumnWrapper(channel, 0, file, rcnt);
      case 4:
         return new MVFloatColumn(channel, 0, file, rcnt, false);
      case 5:
         return new MVDateIntColumn(channel, 0, file, rcnt, false);
      case 6:
         return new MVTimeIntColumn(channel, 0, file, rcnt, false);
      case 7:
         return new MVTimestampIntColumn(channel, 0, file, rcnt, false);
      }

      throw new RuntimeException("Unknown measure column type: " + type);
   }

   /**
    * Make sure the temp files are removed.
    */
   @Override
   public void finalize() {
      dispose();
   }

   /**
    * Clean up any data structures and temporary files.
    */
   public void dispose() {
      if(isTemp && file != null) {
         try {
            file.delete();
         }
         catch(Exception ex) {
            LOG.warn("Failed to delete file: " + file, ex);
         }
      }

      dcols = null;
      mcols = null;
      dmatrix = null;
      mmatrix = null;
   }

   int dcnt;
   int mcnt;
   int rcnt;
   String[] columnNames;
   String[] identifiers;
   MVDimColumn[] dcols;
   MVMeasureColumn[] mcols;

   private XMVColumn[] dmatrix;
   private XMVColumn[] mmatrix;
   private MVRow row;
   private BlockFile file;
   private long start;
   private boolean isTemp = false; // whether the 'file' is a temp file

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultTableBlock.class);
}
