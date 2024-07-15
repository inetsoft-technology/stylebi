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
package inetsoft.report.internal;


import inetsoft.report.*;
import inetsoft.report.internal.table.PagedRegionTableLens;
import inetsoft.report.internal.table.RegionTableLens;
import inetsoft.uql.*;
import inetsoft.util.graphics.ImageWrapper;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * This class helps to serialize and deserialize data in a report.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DataSerializer {
   /**
    * Check if cancel the thread who is reading data.
    */
   public static boolean isCancelled(Object controller) {
      if(!(controller instanceof Controller)) {
         return false;
      }

      return ((Controller) controller).isCancelled();
   }

   /**
    * Read a table from a serialized data.
    */
   public static TableLens readTable(ObjectInputStream s, Object controller)
      throws java.io.IOException, ClassNotFoundException
   {
      int nrow = 0;
      int ncol = 0;
      int hrow = 0;
      int hcol = 0;
      int trow = 0;
      int tcol = 0;

      int marker = s.readInt();
      int version = 0;

      // marker found
      if(marker == 0) {
         version = s.readInt();
         // in 6.5, we added trailer row/col count. The version tag can be
         // used for backward compatibility in the future

         nrow = s.readInt();
         ncol = s.readInt();
         hrow = s.readInt();
         hcol = s.readInt();
         trow = s.readInt();
         tcol = s.readInt();
      }
      // pre-6.5
      else {
         nrow = marker;
         ncol = s.readInt();
         hrow = s.readInt();
         hcol = s.readInt();
      }

      PagedRegionTableLens lens = new PagedRegionTableLens(nrow, ncol, hrow,
            hcol, new Rectangle(hcol, hrow, ncol - hcol, nrow - hrow));

      lens.setTrailerRowCount(trow);
      lens.setTrailerColCount(tcol);

      for(int i = 0; i < nrow; i++) {
         lens.setRowHeight(i, s.readInt());
      }

      for(int i = 0; i < ncol; i++) {
         lens.setColWidth(i, s.readInt());
      }

      Object[] row = null;

      try {
         for(int i = 0; i < nrow; i++) {
            row = new Object[ncol];

            for(int j = 0; j < ncol && !isCancelled(controller); j++) {
               readCell(s, lens, i, j, row);
            }

            if(isCancelled(controller)) {
               return null;
            }

            lens.addRow(row);
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to load table data", ex);
      }

      lens.complete();

      for(int i = -1; i < nrow && !isCancelled(controller); i++) {
         lens.setColBorder(i, -1, s.readInt());
         lens.setColBorderColor(i, -1, (Color) s.readObject());
         lens.completeCell(i, -1);
      }

      if(isCancelled(controller)) {
         return null;
      }

      for(int j = -1; j < ncol && !isCancelled(controller); j++) {
         lens.setRowBorder(-1, j, s.readInt());
         lens.setRowBorderColor(-1, j, (Color) s.readObject());
         lens.completeCell(-1, j);
      }

      if(isCancelled(controller)) {
         return null;
      }

      readXMetaInfos(s, lens);

      if(version >= 133) {
         readCalcCellPaths(s, lens);
      }

      return lens;
   }

   private static void readCalcCellPaths(ObjectInputStream s, PagedRegionTableLens lens)
      throws IOException, ClassNotFoundException
   {
      int rows = s.readInt();
      XSwappableObjectList<TableDataPath[]> cellPaths =
         new XSwappableObjectList<>(TableDataPath[].class);

      for(int i = 0; i < rows; i++) {
         cellPaths.add(s.readObject());
      }

      cellPaths.complete();
      lens.setCellPaths(cellPaths);
   }

   private static void readXMetaInfos(ObjectInputStream stream, PagedRegionTableLens lens)
      throws java.io.IOException, ClassNotFoundException
   {
      boolean exist = stream.readBoolean();

      if(!exist) {
         return;
      }

      Map mmap = (Map) stream.readObject();

      if(mmap != null) {
         lens.setXMetaInfoMap(mmap);
      }
   }

   /**
    * Serialize a table.
    */
   public static void writeTable(ObjectOutputStream stream, TableLens lens, boolean dataonly)
      throws IOException
   {
      lens.moreRows(TableLens.EOT);
      stream.writeInt(0); // marker
      //stream.writeInt(65); // version number
      stream.writeInt(133); // version number
      stream.writeInt(lens.getRowCount());
      stream.writeInt(lens.getColCount());
      stream.writeInt(lens.getHeaderRowCount());
      stream.writeInt(lens.getHeaderColCount());
      stream.writeInt(lens.getTrailerRowCount());
      stream.writeInt(lens.getTrailerColCount());

      for(int i = 0; i < lens.getRowCount(); i++) {
         stream.writeInt(lens.getRowHeight(i));
      }

      for(int i = 0; i < lens.getColCount(); i++) {
         stream.writeInt(lens.getColWidth(i));
      }

      for(int i = 0; i < lens.getRowCount(); i++) {
         for(int j = 0; j < lens.getColCount(); j++) {
            writeCell(stream, lens, i, j, dataonly);
         }

         // reset the stream on every thousand rows to reduce memory usage
         // in the ObjectOutputStream
         if(i > 0 && i % 1000 == 0) {
            try {
               stream.reset();
            }
            catch(IOException ioe) {
               // @by jasons, if the writeTable() method is called from the
               //             writeObject() method of a serializable object,
               //             the stream.reset() call will fail
               if("stream active".equals(ioe.getMessage())) {
                  LOG.debug("Unable to reset object stream, the " +
                     "recursion depth is greater than 0", ioe);
               }
               else {
                  throw ioe;
               }
            }
         }
      }

      for(int i = -1; i < lens.getRowCount(); i++) {
         stream.writeInt(lens.getColBorder(i, -1));
         stream.writeObject(lens.getColBorderColor(i, -1));
      }

      for(int j = -1; j < lens.getColCount(); j++) {
         stream.writeInt(lens.getRowBorder(-1, j));
         stream.writeObject(lens.getRowBorderColor(-1, j));
      }

      writeXMetaInfos(stream, lens);
      writeCalcCellPaths(stream, lens);
   }

   // write the cell data paths for calc table so the cell data path will be correct when
   // it's read back. (42429)
   private static void writeCalcCellPaths(ObjectOutputStream stream, TableLens lens)
      throws IOException
   {
      TableDataDescriptor desc = lens.getDescriptor();

      if(desc.getType() == TableDataDescriptor.CALC_TABLE) {
         stream.writeInt(lens.getRowCount());

         for(int i = 0; i < lens.getRowCount(); i++) {
            TableDataPath[] paths = new TableDataPath[lens.getColCount()];

            for(int j = 0; j < lens.getColCount(); j++) {
               paths[j] = desc.getCellDataPath(i, j);
            }

            stream.writeObject(paths);
         }
      }
      else {
         stream.writeInt(0);
      }
   }

   private static void writeXMetaInfos(ObjectOutputStream stream, TableLens lens)
      throws IOException
   {
      TableDataDescriptor desc = lens.getDescriptor();
      stream.writeBoolean(desc != null);

      if(desc == null) {
         return;
      }

      List<TableDataPath> list = desc.getXMetaInfoPaths();
      Map<TableDataPath, XMetaInfo> mmap = new HashMap<>();

      for(int i = 0; list != null && i < list.size(); i++) {
         TableDataPath path = list.get(i);

         if(path != null && desc.getXMetaInfo(path) != null) {
            mmap.put(path, desc.getXMetaInfo(path));
         }
      }

      stream.writeObject(mmap);
   }

   /**
    * Write a cell to outputstream.
    */
   private static void writeCell(ObjectOutputStream stream, TableLens lens,
      int i, int j, boolean dataonly) throws IOException
   {
      // check if this cell has the same attributes as the last
      int sameR = (i > 0) ? (dataonly ? i - 1 : findIdenticalCell(lens, i, j))
         : -1;

      if(sameR >= 0) {
         // this is the negative offset of the row from the current row
         // we don't add an extra flag here for backward compatibility
         stream.writeInt(sameR - i); // mark use last cell attributes
      }
      else {
         // write primitive types first so objectstream can block them together.
         stream.writeInt(lens.getRowBorder(i, j));
         stream.writeInt(lens.getColBorder(i, j));
         stream.writeInt(lens.getAlignment(i, j));
         stream.writeBoolean(lens.isLineWrap(i, j));
         writeColor(stream, lens.getRowBorderColor(i, j));
         writeColor(stream, lens.getColBorderColor(i, j));
         writeColor(stream, lens.getForeground(i, j));
         writeColor(stream, lens.getBackground(i, j));
         stream.writeObject(lens.getInsets(i, j));
         stream.writeObject(lens.getSpan(i, j));
         writeFont(stream, lens.getFont(i, j));
      }

      Object obj = lens.getObject(i, j);

      if(obj instanceof Image) {
         obj = new ImageWrapper((Image) obj);
      }

      stream.writeObject(obj);
   }

   /**
    * Find a cell above the current cell that has the same attributes.
    */
   private static int findIdenticalCell(TableLens lens, int i, int j) {
      // go backward up to 5 rows
      for(int k = i - 1; k >= 0 && k > i - 5; k--) {
         Color color1 = lens.getRowBorderColor(i, j);
         Color color2 = lens.getRowBorderColor(k, j);

         if(!((color1 == null && color2 == null) ||
               (color1 != null && color1.equals(color2)))) {
            continue;
         }

         color1 = lens.getColBorderColor(i, j);
         color2 = lens.getColBorderColor(k, j);

         if(!((color1 == null && color2 == null) ||
               (color1 != null && color1.equals(color2)))) {
            continue;
         }

         color1 = lens.getForeground(i, j);
         color2 = lens.getForeground(k, j);

         if(!((color1 == null && color2 == null) ||
               (color1 != null && color1.equals(color2)))) {
            continue;
         }

         color1 = lens.getBackground(i, j);
         color2 = lens.getBackground(k, j);

         if(!((color1 == null && color2 == null) ||
               (color1 != null && color1.equals(color2)))) {
            continue;
         }

         Font font1 = lens.getFont(i, j);
         Font font2 = lens.getFont(k, j);

         if(!((font1 == null && font2 == null) ||
               (font1 != null && font1.equals(font2)))) {
            continue;
         }

         Dimension span1 = lens.getSpan(i, j);
         Dimension span2 = lens.getSpan(k, j);

         if(!((span1 == null && span2 == null) ||
               (span1 != null && span1.equals(span2)))) {
            continue;
         }

         Insets insets1 = lens.getInsets(i, j);
         Insets insets2 = lens.getInsets(k, j);

         if(!((insets1 == null && insets2 == null) ||
               (insets1 != null && insets1.equals(insets2)))) {
            continue;
         }

         if(lens.getRowBorder(i, j) != lens.getRowBorder(k, j) ||
            lens.getColBorder(i, j) != lens.getColBorder(k, j) ||
            lens.getAlignment(i, j) != lens.getAlignment(k, j) ||
            lens.isLineWrap(i, j) != lens.isLineWrap(k, j)) {
            continue;
         }

         return k;
      }

      return -1;
   }

   /**
    * Restore a cell from object stream.
    */
   private static void readCell(ObjectInputStream s, RegionTableLens lens,
                                int i, int j, Object[] row)
                                throws ClassNotFoundException, IOException
   {
      int border = s.readInt();

      // if the border is -1, it marks this cell having the same attribute
      // as the last cell
      if(border < 0) {
         lens.setTableCellInfo(i, j, lens.getTableCellInfo(i + border, j));
      }
      else {
         lens.setRowBorder(i, j, border);
         lens.setColBorder(i, j, s.readInt());
         lens.setAlignment(i, j, s.readInt());
         lens.setLineWrap(i, j, s.readBoolean());
         lens.setRowBorderColor(i, j, readColor(s));
         lens.setColBorderColor(i, j, readColor(s));
         lens.setForeground(i, j, readColor(s));
         lens.setBackground(i, j, readColor(s));
         lens.setInsets(i, j, (Insets) s.readObject());
         lens.setSpan(i, j, (Dimension) s.readObject());
         lens.setFont(i, j, readFont(s));
      }

      Object obj = s.readObject();

      if(obj instanceof ImageWrapper) {
         obj = ((ImageWrapper) obj).unwrap();
      }

      row[j] = obj;
   }

   // @by larryl, the DataSerializer uses writeObject since the writeObject
   // is very memory intensive, and for large table lens, it would run out of
   // memory very quickly. It is not a problem in TablePaintable since it
   // only handles a region, but could be a problem in DataSerializer
   private static Color readColor(ObjectInputStream s)
      throws IOException, ClassNotFoundException {
      Integer color = (Integer) s.readObject();

      return (color == null) ? null : new Color(color.intValue());
   }

   private static void writeColor(ObjectOutputStream s, Color color)
      throws IOException {
      s.writeObject((color == null) ? null : Integer.valueOf(color.getRGB()));
   }

   private static Font readFont(ObjectInputStream s)
      throws IOException, ClassNotFoundException {
      String font = (String) s.readObject();

      return (font == null) ? null : StyleFont.decode(font);
   }

   private static void writeFont(ObjectOutputStream s, Font font)
      throws IOException
   {
      s.writeObject((font == null) ? null : StyleFont.toString(font));
   }

   /**
    * The controller is used to determine if cancel to continue read the data.
    */
   public static class Controller {
      public boolean isCancelled() {
         return cancel;
      }

      public void cancel() {
         this.cancel = true;
      }

      private boolean cancel = false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(DataSerializer.class);
}
