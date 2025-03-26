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

import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.*;
import org.apache.avro.generic.*;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class AvroXTableSerializer {
   public static void writeTable(ObjectOutput out, XTable table) throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getCacheTempFile(
         "AvroXTable" + System.identityHashCode(table), "avro");

      // write the table data with avro to a file
      try(FileOutputStream fos = new FileOutputStream(file)) {
         writeAvro(fos, table);
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

   /**
    * Writes out table data with avro
    */
   private static void writeAvro(OutputStream out, XTable table) throws IOException {
      Schema schema = getSchema(table);
      DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);

      try(DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
         dataFileWriter.create(schema, out);

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

   public static XSwappableTable readTable(ObjectInput in) throws IOException {
      return readTable(in, null);
   }

   public static XSwappableTable readTable(ObjectInput in, XSwappableTable table) throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getCacheTempFile(
         "AvroXTable" + System.identityHashCode(in), "avro");
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
         return readAvro(new SeekableFileInput(file), table);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         // delete avro file
         file.delete();
      }
   }

   /**
    * Reads table data from avro
    */
   private static XSwappableTable readAvro(SeekableInput in, XSwappableTable table) throws Exception {
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

      if(table == null) {
         table = new XSwappableTable();
      }

      XTableColumnCreator[] creators = Arrays.stream(colClasses).map(XObjectColumn::getCreator)
         .toArray(XTableColumnCreator[]::new);
      table.init(creators);

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
      return table;
   }

   /**
    * Constructs avro schema for the table data
    */
   private static Schema getSchema(XTable table) {
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
}
