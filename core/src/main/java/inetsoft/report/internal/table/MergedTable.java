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
package inetsoft.report.internal.table;

import inetsoft.uql.XTable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;

/**
 * Merged table memorizes the merged table information of a left table and
 * a right table.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MergedTable {
   /**
    * Constructor.
    */
   public MergedTable() throws Exception {
      super();

      tables = new XTable[0];
      file = FileSystemService.getInstance().getCacheTempFile("mergedTable", "dat");
      Properties prop = new Properties();
      prop.setProperty(BTreeFile.CONFIG_PAGESIZE, "256");
      prop.setProperty(BTreeFile.CONFIG_DIRTYSIZE_MAX, "131072");
      btree = new BTreeFile(file, prop);
      btree.setCached(true);

      if(!btree.open(true)) {
         throw new Exception("Can not open file: " + file);
      }
   }

   /**
    * Adds one table to the merged table.
    * 
    * @param table the table to add.
    * @param index the index of the table.
    * @param cols  the table column indexes.
    * 
    * @throws Exception if the table could not be added.
    */
   public void addTable(XTable table, int index, int[] cols) throws Exception {
      if(tables.length <= index) {
         XTable[] ntables = new XTable[index + 1];
         System.arraycopy(tables, 0, ntables, 0, tables.length);
         tables = ntables;
      }
      
      tables[index] = table;
      RowKeyEnumeration rke = new RowKeyEnumeration(table, cols);
      
      while(rke.hasNext()) {
         BTreeFile.Key key = rke.next();
         
         // ignore invalid values
         if(key == null) {
            continue;
         }
         
         synchronized(this) {
            if(disposed) {
               break;
            }

            MergedRow[] values = null;
            BTreeFile.Value value = btree.getRecord(key);

            if(value != null) {
               ByteBuffer buffer = ByteBuffer.wrap(value.getData());
               buffer.order(ByteOrder.BIG_ENDIAN);
               
               values = new MergedRow[buffer.getShort() & 0xffff];
               
               for(int i = 0; i < values.length; i++) {
                  values[i] = createMergedRow();
                  int ntables = (buffer.getShort() & 0xffff);
                  values[i].rows = new int[ntables][];
                  
                  for(int j = 0; j < ntables; j++) {
                     int nrows = (buffer.getShort() & 0xffff);
                     values[i].rows[j] = new int[nrows];
                     
                     for(int k = 0; k < nrows; k++) {
                        values[i].rows[j][k] = buffer.getInt();
                     }
                  }
               }
            }
            
            MergedRow rvalue = null;
            
            if(values == null) {
               values = new MergedRow[] { rvalue = createMergedRow() };
            }
            else {
               for(int i = 0; i < values.length; i++) {
                  for(int j = 0; j < tables.length; j++) {
                     if(values[i].matches(
                        tables[j], table, j, rke.getLast(), cols))
                     {
                        rvalue = values[i];
                        break;
                     }
                  }
                  
                  if(rvalue != null) {
                     break;
                  }
               }
               
               if(rvalue == null) {
                  MergedRow[] nvalues = new MergedRow[values.length + 1];
                  System.arraycopy(values, 0, nvalues, 0, values.length);
                  nvalues[values.length] = rvalue = createMergedRow();
                  values = nvalues;
               }
            }
            
            rvalue.add(index, rke.getLast());
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataRW.writeChar(output, (char) values.length);
            
            for(int i = 0; i < values.length; i++) {
               DataRW.writeChar(output, (char) values[i].rows.length);
               
               for(int j = 0; j < values[i].rows.length; j++) {
                  DataRW.writeChar(output, (char) values[i].rows[j].length);
                  
                  for(int k = 0; k < values[i].rows[j].length; k++) {
                     DataRW.writeInt(output, values[i].rows[j][k]);
                  }
               }
            }
            
            btree.addRecord(key, new BTreeFile.Value(output.toByteArray()));
         }
      }
   }

   /**
    * Create a merged row.
    * @return the created merged row.
    */
   protected MergedRow createMergedRow() {
      return new MergedRow();
   }

   /**
    * Visit the merged table.
    * @param visitor the specified visitor visits the merged table.
    */
   public final void accept(final Visitor visitor) throws Exception {
      if(visitor == null) {
         return;
      }

      btree.accept(new BTreeFile.Visitor() {
         @Override
         public void visit(BTreeFile.Key key) throws Exception {
            BTreeFile.Value value;

            synchronized(MergedTable.this) {
               if(disposed) {
                  return;
               }

               value = btree.getRecord(key);
            }

            for(MergedRow rvalue : parseValue(value)) {
               visitor.visit(rvalue);
            }
         }
      });
   }
   
   private MergedRow[] parseValue(BTreeFile.Value value) {
      MergedRow[] values = null;

      if(value != null) {
         ByteBuffer buffer = ByteBuffer.wrap(value.getData());
         buffer.order(ByteOrder.BIG_ENDIAN);
         
         values = new MergedRow[buffer.getShort() & 0xffff];
         
         for(int i = 0; i < values.length; i++) {
            values[i] = createMergedRow();
            int ntables = (buffer.getShort() & 0xffff);
            values[i].rows = new int[ntables][];
            
            for(int j = 0; j < ntables; j++) {
               int nrows = (buffer.getShort() & 0xffff);
               values[i].rows[j] = new int[nrows];
               
               for(int k = 0; k < nrows; k++) {
                  values[i].rows[j][k] = buffer.getInt();
               }
            }
         }
      }
      
      return values;
   }

   /**
    * Finalize the merged table.
    */
   @Override
   protected final void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the merged table.
    */
   public final synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;

      try {
         btree.close();
      }
      catch(Exception ex) {
         LOG.error("Failed to close B-tree", ex);
      }

      boolean removed = file.delete();

      if(!removed) {
         FileSystemService.getInstance().remove(file, 60000);
      }
   }

   /**
    * Check if is disposed.
    * @return <tt>true</tt> if disposed, <tt>false</tt> otherwise.
    */
   public final boolean isDisposed() {
      return disposed;
   }

   /**
    * Visitor visits a merged table.
    */
   public interface Visitor {
      /**
       * Visit one merged row.
       * @param val the specified merged row.
       */
      public void visit(MergedRow val) throws Exception;
   }

   private File file;
   private BTreeFile btree;
   private boolean disposed;
   private XTable[] tables;

   private static final Logger LOG =
      LoggerFactory.getLogger(MergedTable.class);
}
