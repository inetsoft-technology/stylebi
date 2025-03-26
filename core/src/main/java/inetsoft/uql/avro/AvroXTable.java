/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.avro;

import inetsoft.report.TableDataDescriptor;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.*;
import org.apache.avro.generic.*;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;

import java.io.*;
import java.util.List;

/**
 * Supports serializing a table using the avro format
 */
public class AvroXTable implements XTable, Externalizable {
   public AvroXTable() {
   }

   public AvroXTable(XTable table) {
      this.table = table;
   }

   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   @Override
   public boolean isNull(int r, int c) {
      return table.isNull(r, c);
   }

   @Override
   public Object getObject(int r, int c) {
      return table.getObject(r, c);
   }

   @Override
   public double getDouble(int r, int c) {
      return table.getDouble(r, c);
   }

   @Override
   public float getFloat(int r, int c) {
      return table.getFloat(r, c);
   }

   @Override
   public long getLong(int r, int c) {
      return table.getLong(r, c);
   }

   @Override
   public int getInt(int r, int c) {
      return table.getInt(r, c);
   }

   @Override
   public short getShort(int r, int c) {
      return table.getShort(r, c);
   }

   @Override
   public byte getByte(int r, int c) {
      return table.getByte(r, c);
   }

   @Override
   public boolean getBoolean(int r, int c) {
      return table.getBoolean(r, c);
   }

   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, c, v);
   }

   @Override
   public Class<?> getColType(int col) {
      return table.getColType(col);
   }

   @Override
   public boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   @Override
   public void dispose() {
      table.dispose();
   }

   @Override
   public String getColumnIdentifier(int col) {
      return table.getColumnIdentifier(col);
   }

   @Override
   public void setColumnIdentifier(int col, String identifier) {
      table.setColumnIdentifier(col, identifier);
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   @Override
   public String getReportType() {
      return table.getReportType();
   }

   @Override
   public String getReportName() {
      return table.getReportName();
   }

   @Override
   public void setProperty(String key, Object value) {
      table.setProperty(key, value);
   }

   @Override
   public Object getProperty(String key) {
      return table.getProperty(key);
   }

   @Override
   public boolean isDisableFireEvent() {
      return table.isDisableFireEvent();
   }

   @Override
   public void setDisableFireEvent(boolean disableFireEvent) {
      table.setDisableFireEvent(disableFireEvent);
   }

   @Override
   public void writeExternal(ObjectOutput out) throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getCacheTempFile(
         "AvroXTable" + System.identityHashCode(this), "avro");

      // write the table data with avro to a file
      try(FileOutputStream fos = new FileOutputStream(file)) {
         writeAvro(fos);
      }

      try(InputStream fin = new FileInputStream(file)) {
         byte[] buffer = new byte[4096];
         int bytesRead;

         // write out the length of the file so we know how much to read later
         out.writeLong(file.length());

         while((bytesRead = fin.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead); // Write chunk data
         }
      }

      // delete avro file
      file.delete();
   }

   @Override
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getCacheTempFile(
         "AvroXTable" + System.identityHashCode(this), "avro");
      long fileSize = in.readLong();

      // save the avro data to a file
      try(OutputStream fout = new FileOutputStream(file)) {
         byte[] buffer = new byte[4096];
         long remaining = fileSize;

         while(remaining > 0) {
            int bytesToRead = (int) Math.min(buffer.length, remaining);
            int bytesRead = in.read(buffer, 0, bytesToRead);

            if(bytesRead == -1) {
               break; // Stop if EOF (shouldn't happen if size is correct)
            }

            fout.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
         }
      }

      // read the avro file into XSwappableTable
      try {
         readAvro(new SeekableFileInput(file));
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      // delete avro file
      file.delete();
   }

   /**
    * Constructs avro schema for the table data
    */
   private Schema getSchema() {
      SchemaBuilder.RecordBuilder<Schema> tableRecord = SchemaBuilder.record("table");
      SchemaBuilder.FieldAssembler<Schema> tableFields = tableRecord.fields();

      for(int i = 0; i < table.getColCount(); i++) {
         tableFields
            .name("_" + i)
            .prop("colType", Tool.getDataType(table.getColType(i)))
            .type().stringType()
            .noDefault();
      }

      return tableFields.endRecord();
   }

   /**
    * Writes out table data with avro
    */
   private void writeAvro(OutputStream out) throws IOException {
      Schema schema = getSchema();
      DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);

      try(DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
         dataFileWriter.create(schema, out);
         table.moreRows(XTable.EOT);

         for(int r = 0; r < table.getRowCount(); r++) {
            GenericRecord tableRecord = new GenericData.Record(schema);

            for(int c = 0; c < table.getColCount(); c++) {
               tableRecord.put("_" + c, Tool.getPersistentDataString(table.getObject(r, c)));
            }

            dataFileWriter.append(tableRecord);
         }
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Reads table data from avro
    */
   private void readAvro(SeekableInput in) throws Exception {
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
      DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(in, datumReader);

      Schema schema = dataFileReader.getSchema(); // Retrieve schema from the file
      List<Schema.Field> fields = schema.getFields();
      String[] colTypes = new String[fields.size()];
      Class<?>[] colClasses = new Class<?>[fields.size()];

      for(int i = 0; i < fields.size(); i++) {
         colTypes[i] = fields.get(i).getProp("colType");
         colClasses[i] = Tool.getDataClass(colTypes[i]);
      }

      XSwappableTable table = new XSwappableTable(colClasses);
      GenericRecord tableRecord;
      boolean headerRow = true;

      while(dataFileReader.hasNext()) {
         tableRecord = dataFileReader.next();
         Object[] values = new Object[fields.size()];

         for(int i = 0; i < fields.size(); i++) {
            Schema.Field colField = fields.get(i);
            Object val = tableRecord.get(colField.name());
            values[i] = Tool.getPersistentData(headerRow ? XSchema.STRING : colTypes[i],
                                               val == null ? null : val.toString());
         }

         headerRow = false;
         table.addRow(values);
      }

      table.complete();
      this.table = table;
   }

   private transient XTable table;
}
