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

import inetsoft.util.swap.XSwapUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * MVBlockInfo, store data for block.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class MVBlockInfo implements Cloneable {
   /**
    * Default Constructor.
    */
   public MVBlockInfo() {
      super();
   }

   /**
    * Create an instance of MVBlockInfo.
    *
    * @param cloumnInfos all column info for current block info.
    * @param rowCount current block row count.
    */
   public MVBlockInfo(MVColumnInfo[] cloumnInfos, int rowCount) {
      this.columnInfos = cloumnInfos;
      this.rowCount = rowCount;
   }

   /**
    * Get ColumnInfo.
    */
   public MVColumnInfo getColumnInfo(int col) {
      return columnInfos[col];
   }

   /**
    * Get ColumnInfos.
    */
   public MVColumnInfo[] getColumnInfos() {
      return columnInfos;
   }

   /**
    * Get dictionary.
    */
   public XDimDictionaryIndex getDictionary(int col) {
      return columnInfos[col].getDictionary();
   }

   /**
    * Get dictionarys.
    */
   public XDimDictionaryIndex[] getDictionaries() {
      XDimDictionaryIndex[] indexs = new XDimDictionaryIndex[columnInfos.length];

      for(int i = 0; i < indexs.length; i++) {
         indexs[i] = columnInfos[i].getDictionary();
      }

      return indexs;
   }

   /**
    * Load from binary storage.
    */
   public void read(ChannelProvider channelProvider, ReadableByteChannel channel) throws IOException
   {
      ByteBuffer buf = ByteBuffer.allocate(8);
      channel.read(buf);
      XSwapUtil.flip(buf);
      rowCount = buf.getInt();
      int length = buf.getInt();
      columnInfos = new MVColumnInfo[length];

      for(int i = 0; i < columnInfos.length; i++) {
         columnInfos[i] = new MVColumnInfo();
         columnInfos[i].read(channelProvider, channel);
      }
   }

   /**
    * Save to binary storage.
    */
   public void write(WritableByteChannel channel) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.putInt(rowCount);
      buf.putInt(columnInfos.length);
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      for(MVColumnInfo columnInfo : columnInfos) {
         columnInfo.write(channel);
      }
   }

   /**
    * Set block row count.
    *
    * @param rowCount the block row count.
    */
   public void setRowCount(int rowCount) {
      this.rowCount = rowCount;
   }

   /**
    * Get block row count.
    */
   public int getRowCount() {
      return rowCount;
   }

   /**
    * Clone MVBlockInfo.
    */
   @Override
   public Object clone() {
      try {
         MVBlockInfo binfo = (MVBlockInfo) super.clone();
         binfo.columnInfos = new MVColumnInfo[columnInfos.length];

         for(int i = 0; i < columnInfos.length; i++) {
            binfo.columnInfos[i] = (MVColumnInfo) columnInfos[i].clone();
         }

         return binfo;
      }
      catch(CloneNotSupportedException e) {
         return null;
      }
   }

   private int rowCount;
   private MVColumnInfo[] columnInfos;
}
