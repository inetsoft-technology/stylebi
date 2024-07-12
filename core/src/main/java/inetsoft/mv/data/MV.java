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
package inetsoft.mv.data;

import inetsoft.mv.*;
import inetsoft.storage.BlobChannel;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Materialized view, it stores dimension dictionaries at server node. When
 * executing mv query, we need to restore an int index into its original object.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class MV implements Cloneable {
   /**
    * Create an instance of MV.
    */
   public MV() {
      super();
   }

   /**
    * Create an instance of MV.
    */
   public MV(int dcnt, int mcnt, String[] headers, String[] identifiers,
             Class<?>[] types, XMetaInfo[] infos, MVDef def)
   {
      this();
      this.dcnt = dcnt;
      this.mcnt = mcnt;
      this.headers = headers;
      this.identifiers = identifiers;
      this.types = types;
      this.infos = infos;
      this.def = (def == null) ? new MVDef() : def;
      createPalette(dcnt + mcnt);
   }

   /**
    * Create an instance of MV.
    */
   public MV(String file, long modified) throws IOException {
      this();
      this.file = file;
      this.modified = modified;
   }

   /**
    * Get the MV data file path.
    */
   public String getFile() {
      return file;
   }

   /**
    * Create palette.
    */
   private void createPalette(int size) {
      this.palette = new DictionaryPalette[size];

      for(int i = 0; i < size; i++) {
         palette[i] = new DictionaryPalette(i);
      }
   }

   /**
    * Add dictionary for block.
    */
   public void addBlockInfo(MVBlockInfo binfo) {
      loadContent();
      blockInfos.add(binfo);
   }

   /**
    * Check if this sub mv is valid.
    */
   public boolean isValid() {
      try {
         return file != null && MVStorage.getInstance().getLastModified(file) == modified;
      }
      catch(Exception ignore) {
         return false;
      }
   }

   /**
    * Set mv created success.
    */
   public void setSuccess(boolean success) {
      this.success = success;
   }

   /**
    * Check mv created success or not.
    */
   public boolean isSuccess() {
      return success;
   }

   /**
    * Get the dimension count of this MV.
    */
   public int getDimCount() {
      return dcnt;
   }

   /**
    * Get the measure count of this MV.
    */
   public int getMeasureCount() {
      return mcnt;
   }

   /**
    * Get the index of the specified column header.
    */
   public int indexOfHeader(String header, int start) {
      return MVDef.indexOfHeader(header, headers, start);
   }

   /**
    * Get the meta info if any.
    */
   public XMetaInfo getXMetaInfo(String header) {
      loadContent();
      int idx = indexOfHeader(header, 0);
      return idx >= 0 ? infos[idx] : null;
   }

   /**
    * Get the identifier.
    */
   public String getColumnIdentifier(String header) {
      if(identifiers == null) {
         return null;
      }

      int idx = indexOfHeader(header, 0);
      return idx >= 0 ? identifiers[idx] : null;
   }

   /**
    * Get the header at the specified column.
    */
   public String getHeader(int c) {
      return headers[c];
   }

   /**
    * Set the headers.
    */
   public void setHeader(String[] headers) {
      this.headers = headers;
   }

   /**
    * Get the dictionary at the specified column.
    */
   public XDimDictionary getDictionary(int c, int blockNum) {
      loadContent();
      XDimDictionaryIndex index = blockInfos.get(blockNum).getDictionary(c);

      if(index == null) {
         return null;
      }

      XDimDictionary dict = palette[index.getColumn()].getDict(index.getIndex());

      if(blockBaseRows.size() > blockNum) {
         dict.setBaseRow(blockBaseRows.get(blockNum));
      }

      return dict;
   }

   /**
    * Get all dictionaries for the block.
    */
   public XDimDictionary[] getDictionaries(int blockNum) {
      loadContent();
      XDimDictionaryIndex[] indexs = blockInfos.get(blockNum).getDictionaries();
      XDimDictionary[] dicts = new XDimDictionary[indexs.length];

      for(int i = 0; i < indexs.length; i++) {
         if(indexs[i] != null) {
            int column = indexs[i].getColumn();
            int idx = indexs[i].getIndex();
            dicts[i] = palette[column].getDict(idx);
         }
      }

      return dicts;
   }

   /**
    * Get dictionary.
    */
   public XDimDictionaryIndex getDictionaryIndex(int column, XDimDictionary dict) {
      loadContent();
      return palette[column].getDictionary(dict);
   }

   /**
    * Get the dictionary index value.
    */
   public int getDictIndexValue(int column, XDimDictionary dict) {
      loadContent();
      return palette[column].getIndex(dict);
   }

   /**
    * Add dictionary at the index in current column.
    */
   public void setDictionary(int column, int index, XDimDictionary dict) {
      loadContent();
      palette[column].setDictionary(index, dict);
   }

   /**
    * Check dictionary is shared.
    */
   public boolean checkShareDict(int column, XDimDictionaryIndex dict) {
      loadContent();

      for(MVBlockInfo blockInfo : blockInfos) {
         MVColumnInfo cinfo = blockInfo.getColumnInfo(column);
         XDimDictionaryIndex dictIndex = cinfo.getDictionary();

         if(dict != dictIndex && dictIndex.getIndex() == dict.getIndex()) {
            return true;
         }
      }

      return false;
   }

   // needed by SparkMVIncremental.update0/refresh()
   public void setDef(MVDef mvdef) {
      this.def = mvdef;
   }

   /**
    * Get the mv def.
    */
   public MVDef getDef() {
      return getDef(false);
   }

   /**
    * Get the mv def.
    * @param fill true to fill meta info.
    */
   public MVDef getDef(boolean fill) {
      loadContent();

      if(fill) {
         List<MVColumn> cols = def.getColumns();

         for(MVColumn col : cols) {
            String header = MVDef.getMVHeader(col.getColumn());
            col.setXMetaInfo(getXMetaInfo(header));
         }
      }

      return def;
   }

   /**
    * Fix filters by replacing value with index.
    */
   public List<XNode> fixFilter(XNode xNode) {
      loadContent();

      if(xNode == null) {
         return null;
      }

      List<XNode> xNodes = new ArrayList<>();

      for(int i = 0; i < blockInfos.size(); i++) {
         XNode nxNode = (XNode) xNode.clone();
         fixBlockFilter(nxNode, i);
         xNodes.add(nxNode);
      }

      return xNodes;
   }

   /**
    * Fix filter by replacing value with index.
    */
   public void fixBlockFilter(XNode xNode, int blkNum) {
      loadContent();

      if(xNode instanceof XSet) {
         XSet set = (XSet) xNode;

         for(int i = 0; i < set.getChildCount(); i++) {
            fixBlockFilter(set.getChild(i), blkNum);
         }
      }
      else if(xNode instanceof XBinaryCondition) {
         XBinaryCondition cond = (XBinaryCondition) xNode;
         String col = (String) cond.getExpression1().getValue();
         int idx = indexOfHeader(col, 0);

         if(idx < 0) {
            throw new RuntimeException("Dimension not found: " + col + " in " +
                                          Arrays.toString(headers));
         }
         else if(idx < palette.length) {
            //get dict of the block's column.
            XDimDictionaryIndex index = blockInfos.get(blkNum).getDictionary(idx);

            if(index != null) {
               index.fixFilter(cond);
            }
            else {
               XExpression exp = cond.getExpression2();
               Object val = exp.getValue();

               // date used as measure?
               if(val instanceof Date) {
                  exp.setValue(((Date) val).getTime(), exp.getType());
               }
               // fix bug1328151618606, if the value is Array, should check
               // each value of the array is date?
               else if(val instanceof Object[]) {
                  Object[] vals = (Object[]) val;
                  Object[] dates = new Object[vals.length];

                  for(int i = 0; i < vals.length; i++) {
                     if(vals[i] instanceof Date) {
                        dates[i] = ((Date) vals[i]).getTime();
                     }
                     else {
                        dates[i] = vals[i];
                     }
                  }

                  exp.setValue(dates, exp.getType());
               }
            }
         }
      }
   }

   /**
    * Load from binary storage.
    */
   public void load(ReadableByteChannel channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int val = buf.getInt();
      boolean withIdentifiers = (val == -2);

      if(val == -1 || val == -2) {
         buf = ByteBuffer.allocate(5);
         channel.read(buf);
         XSwapUtil.flip(buf);
         success = buf.get() == 1;
         val = buf.getInt();
      }

      int len = val;
      buf = ByteBuffer.allocate(len);
      channel.read(buf);
      XSwapUtil.flip(buf);
      dcnt = buf.getInt();
      mcnt = buf.getInt();
      headers = new String[dcnt + mcnt];

      for(int i = 0; i < headers.length; i++) {
         int size = buf.getInt();
         char[] chars = new char[size];

         for(int j = 0; j < chars.length; j++) {
            chars[j] = buf.getChar();
         }

         headers[i] = new String(chars);
      }

      if(withIdentifiers) {
         identifiers = new String[dcnt + mcnt];

         for(int i = 0; i < identifiers.length; i++) {
            int size = buf.getInt();

            if(size == 0) {
               continue;
            }

            char[] chars = new char[size];

            for(int j = 0; j < chars.length; j++) {
               chars[j] = buf.getChar();
            }

            identifiers[i] = new String(chars);
         }
      }

      contentPos = ((SeekableByteChannel) channel).position();
   }

   private void loadContent() {
      if(contentPos < 0) {
         return;
      }

      synchronized(this) {
         if(contentPos < 0) {
            return;
         }

         try(BlobChannel channel = MVStorage.getInstance().openReadChannel(file)) {
            channel.position(contentPos);
            loadContent0(channel);
         }
         catch(Exception ex) {
            LOG.error(
               "Failed to load mv content. Format may have changed, try re-generating MV: {}",
               file, ex);
         }
         finally {
            contentPos = -1;
         }
      }
   }

   private void loadContent0(ReadableByteChannel channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int mlen = buf.getInt();
      // don't use a direct byte buffer here because it is not efficient when
      // only using bytes or byte arrays
      buf = ByteBuffer.allocate(mlen);
      channel.read(buf);
      XSwapUtil.flip(buf);
      byte[] bytes = new byte[mlen];
      buf.get(bytes);
      readXMetaInfo(bytes);
      def = new MVDef();
      def.read(channel);

      ChannelProvider channelProvider = MVStorage.getInstance().createChannelProvider(file);

      //read block dictionarys.
      int ccnt = dcnt + mcnt;
      palette = new DictionaryPalette[ccnt];

      for(int i = 0; i < ccnt; i++) {
         palette[i] = new DictionaryPalette();
         palette[i].read(channelProvider, channel);
      }

      //read blockinfos.
      buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int size = buf.getInt();
      int baseRow = 0;

      for(int i = 0; i < size; i++) {
         MVBlockInfo binfo = new MVBlockInfo();
         binfo.read(channelProvider, channel);
         blockInfos.add(binfo);
         blockBaseRows.add(baseRow);
         baseRow += binfo.getRowCount();

         for(MVColumnInfo cinfo : binfo.getColumnInfos()) {
            XDimDictionaryIndex dict = cinfo.getDictionary();

            if(dict != null) {
               int column = dict.getColumn();
               int index = dict.getIndex();
               dict.setDictionary(palette[column].getDict(index));
            }
         }
      }

      breakValues = new ArrayList<>();
      breakValuePos = ((SeekableByteChannel) channel).position();
   }

   private void readBreakValues() {
      loadContent();

      if(breakValuePos == -1) {
         return;
      }

      synchronized(this) {
         if(breakValuePos == -1) {
            return;
         }

         try(BlobChannel channel = MVStorage.getInstance().openReadChannel(file)) {
            channel.position(breakValuePos);
            breakValuePos = -1;
            ByteBuffer buf = ByteBuffer.allocate(4);
            int cnt = channel.read(buf);

            // no break values
            if(cnt != 4) {
               return;
            }

            XSwapUtil.flip(buf);
            buf.getInt();
            Object[] vals = MVTool.readObjects(channel, false);
            Collections.addAll(breakValues, vals);
         }
         catch(Exception ex) {
            LOG.error("Failed to read break values", ex);
         }
      }
   }

   /**
    * Save to file.
    */
   public void save(String file) throws IOException {
      try(BlobTransaction<MVStorage.Metadata> tx = MVStorage.getInstance().beginTransaction();
          BlobChannel channel = tx.newChannel(file, new MVStorage.Metadata()))
      {
         save(channel);
         tx.commit();
      }
   }

   /**
    * Save to binary storage.
    */
   public void save(WritableByteChannel channel) throws IOException {
      readBreakValues();
      ByteBuffer buf = ByteBuffer.allocate(6);
      buf.putInt(-2);
      buf.put((byte) (success ? 1 : 0));
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      int len = 8;

      for(String header : headers) {
         len += (4 + header.length() * 2);
      }

      for(String identifier : identifiers) {
         len += (4 + (identifier == null ? 0 : identifier.length()) * 2);
      }

      buf = ByteBuffer.allocate(len + 4);
      buf.putInt(len);
      buf.putInt(dcnt);
      buf.putInt(mcnt);

      for(String header : headers) {
         buf.putInt(header.length());

         for(int i = 0; i < header.length(); i++) {
            buf.putChar(header.charAt(i));
         }
      }

      for(String identifier : identifiers) {
         buf.putInt(identifier == null ? 0 : identifier.length());

         for(int i = 0; identifier != null && i < identifier.length(); i++) {
            buf.putChar(identifier.charAt(i));
         }
      }

      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      byte[] bytes = writeXMetaInfo();
      // don't use a direct byte buffer here because it is not efficient when
      // only using bytes or byte arrays
      buf = ByteBuffer.allocate(4 + bytes.length);
      buf.putInt(bytes.length);
      buf.put(bytes);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      def.write(channel);

      // write dicts.
      for(DictionaryPalette dictionaryPalette : palette) {
         dictionaryPalette.write(channel);
      }

      // write blockinfos.
      buf = ByteBuffer.allocate(4);
      buf.putInt(blockInfos.size());
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      for(MVBlockInfo binfo : blockInfos) {
         binfo.write(channel);
      }

      int size = breakValues == null ? 0 : breakValues.size();

      if(size > 0) {
         buf = ByteBuffer.allocate(4);
         buf.putInt(size);
         XSwapUtil.flip(buf);

         while(buf.hasRemaining()) {
            channel.write(buf);
         }

         Object[] vals = new Object[size];
         breakValues.toArray(vals);
         buf = MVTool.getObjectsByteBuffer(vals, false);

         while(buf.hasRemaining()) {
            channel.write(buf);
         }
      }
   }

   /**
    * Write XMetaInfo to byte array.
    */
   private byte[] writeXMetaInfo() {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

         writer.println("<XMetaInfos>");

         for(XMetaInfo info : infos) {
            writer.print("<oneXMetaInfo>");

            if(info != null) {
               info.writeXML(writer);
            }

            writer.println("</oneXMetaInfo>");
         }

         for(Class<?> type : types) {
            writer.println("<type classname=\"" + type.getName() + "\"/>");
         }

         writer.println("</XMetaInfos>");
         writer.close();

         return out.toByteArray();
      }
      catch(Exception ex) {
         LOG.error("Failed to write meta info", ex);
      }

      return null;
   }

   /**
    * Read XMetaInfo from byte array.
    */
   private void readXMetaInfo(byte[] bytes) {
      try {
         ByteArrayInputStream out = new ByteArrayInputStream(bytes);
         Document doc = Tool.parseXML(out, "utf-8");
         Element root = doc.getDocumentElement();
         NodeList list = Tool.getChildNodesByTagName(root, "oneXMetaInfo");
         infos = new XMetaInfo[dcnt + mcnt];

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            Element child = Tool.getFirstChildNode(elem);

            if(child != null) {
               infos[i] = new XMetaInfo();
               infos[i].parseXML(child);
            }
         }

         list = Tool.getChildNodesByTagName(root, "type");
         types = new Class[dcnt + mcnt];

         for(int i = 0; i < list.getLength(); i++) {
            types[i] = Class.forName(Tool.getAttribute((Element) list.item(i), "classname"));
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read meta info", ex);
      }
   }

   /**
    * Dispose this mv.
    */
   public void dispose() {
      for(int i = 0; palette != null && i < palette.length; i++) {
         List<XDimDictionary> dicts = palette[i].getDicts();

         for(XDimDictionary dict : dicts) {
            dict.dispose();
         }
      }
   }

   /**
    * Check if the columns are valid dimensions.
    */
   public void checkValidity(GroupRef[] groups) {
      loadContent();

      // check whether one dimension is overflow
      for(GroupRef group : groups) {
         DataRef dref = group.getDataRef();
         String attrName = MVDef.getMVHeader(dref);
         int idx = indexOfHeader(attrName, 0);
         List<XDimDictionary> dicts = palette[idx].getDicts();

         for(XDimDictionary dict : dicts) {
            if(idx >= 0 && idx < palette.length &&
               dict != null && dict.isOverflow())
            {
               String msg = "Dimension column \"" + attrName +
                  "\" has too many distinct values. " +
                  "It can't be used for grouping and filtering! " +
                  "If grouping or filtering of \"" + attrName + "\" is " +
                  "needed, increase the mv.dim.max.size parameter.";
               throw new RuntimeException(msg);
            }
         }
      }
   }

   /**
    * Update last modified time.
    */
   public void updateLastModifiedTime() {
      loadContent();

      if(def == null || file == null) {
         return;
      }

      def.updateLastUpdateTime();

      try(BlobTransaction<MVStorage.Metadata> tx = MVStorage.getInstance().beginTransaction();
          BlobChannel channel = tx.newChannel(file, new MVStorage.Metadata()))
      {
         updateDef(channel);
         tx.commit();
      }
      catch(Exception ex) {
         LOG.error("Failed to write updated definition to file: {}", file, ex);
      }
   }

   /**
    * Update the def's last modified time.
    */
   private void updateDef(BlobChannel channel) {
      long pos = def.getFilePosition();
      int len = def.getLength() + 4 - 8;
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.putLong(def.getLastUpdateTime());
      XSwapUtil.flip(buf);

      try {
         if(!def.isPreV112()) {
            channel.position(pos + len);

            while(buf.hasRemaining()) {
               channel.write(buf);
            }
         }
         else {
            File tempFile = FileSystemService.getInstance().getCacheTempFile("mv", ".dat");

            try(RandomAccessFile tempRaf = new RandomAccessFile(tempFile, "rw");
                FileChannel tempChannel = tempRaf.getChannel())
            {
               channel.position(0);
               Tool.copy(channel, tempChannel, pos + len);

               while(buf.hasRemaining()) {
                  tempChannel.write(buf);
               }

               channel.position(pos + len);
               Tool.copy(channel, tempChannel, channel.size() - pos - len);

               tempChannel.position(pos);
               buf = ByteBuffer.allocate(4);
               tempChannel.read(buf);
               tempChannel.position(pos);
               XSwapUtil.flip(buf);
               len = buf.getInt();
               XSwapUtil.clear(buf);
               buf.putInt(len + 8);
               XSwapUtil.flip(buf);

               while(buf.hasRemaining()) {
                  tempChannel.write(buf);
               }

               channel.position(0);
               Tool.copy(tempChannel, channel);
            }
            finally {
               Tool.deleteFile(tempFile);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to write last update time: {}", file, ex);
      }
   }

   /**
    * Add break values in the index of block in file system.
    */
   public void addBreakValues(Object obj, int blockIndex) {
      readBreakValues();

      if(breakValues == null) {
         breakValues = new ArrayList<>();
      }

      if(blockIndex < breakValues.size()) {
         breakValues.set(blockIndex, obj);
      }
      else {
         breakValues.add(blockIndex, obj);
      }
   }

   /**
    * Get the break value in the index of block in file system.
    */
   public int getBreakValueIndex(Object val) {
      readBreakValues();

      if(breakValues != null) {
         for(; breakIndex < breakValues.size(); breakIndex++) {
            // since the base table is sort, so we can get index sequence
            if(Tool.compare(val, breakValues.get(breakIndex)) <= 0) {
               return breakIndex;
            }
         }
      }

      return -1;
   }

   /**
    * Get block info.
    */
   public MVBlockInfo getBlockInfo(int blockNum) {
      loadContent();
      return blockInfos.get(blockNum);
   }

   /**
    * Get block size.
    */
   public int getBlockSize() {
      loadContent();
      return blockInfos.size();
   }

   /**
    * Get column headers
    */
   public String[] getHeaders() {
      return headers;
   }

   /**
    * Get column identifiers
    */
   public String[] getIdentifiers() {
      return identifiers;
   }

   /**
    * Get column types
    */
   public Class<?>[] getTypes() {
      loadContent();
      return types;
   }

   /**
    * Get the meta infos
    */
   public XMetaInfo[] getInfos() {
      return infos;
   }

   /**
    * Clone MV.
    */
   @Override
   public Object clone() {
      loadContent();

      try {
         MV mv = (MV) super.clone();
         mv.palette = new DictionaryPalette[palette.length];
         mv.blockInfos = new ArrayList<>();

         for(int i = 0; i < palette.length; i++) {
            mv.palette[i] = (DictionaryPalette) palette[i].clone();
         }

         for(int i = 0; i < blockInfos.size(); i++) {
            mv.blockInfos.add((MVBlockInfo) blockInfos.get(i).clone());
         }

         return mv;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Clone failed: " + e);
         return null;
      }
   }

   private List<MVBlockInfo> blockInfos = new ArrayList<>();
   private final List<Integer> blockBaseRows = new ArrayList<>();
   private DictionaryPalette[] palette;
   int dcnt;
   int mcnt;
   String[] headers;
   String[] identifiers;
   Class<?>[] types;
   XMetaInfo[] infos;
   private MVDef def;
   private String file;
   private long contentPos = -1;
   private long breakValuePos = -1;
   private long modified;
   private List<Object> breakValues;
   private int breakIndex = 0;
   private boolean success = true;

   private static final Logger LOG = LoggerFactory.getLogger(MV.class);
}
