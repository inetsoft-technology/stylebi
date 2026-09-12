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
import inetsoft.report.TableDataPath;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.util.Tool;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.*;
import org.apache.avro.generic.*;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.w3c.dom.Document;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class AvroXTableSerializer {
   public static void writeTable(ObjectOutput out, XTable table) throws IOException {
      Schema schema = getSchema(table);
      DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);

      // Shield the caller's stream: DataFileWriter.close() (called by try-with-resources)
      // would otherwise call close() on the underlying ObjectOutputStream via
      // AutoSyncOutputStream, prematurely finalizing the enclosing serialization stream.
      // write(byte[], int, int) is overridden to delegate in bulk; the default
      // FilterOutputStream implementation iterates byte-by-byte which is extremely slow
      // for the large compressed blocks that Avro's DataFileWriter emits.
      OutputStream shielded = new FilterOutputStream((OutputStream) out) {
         @Override
         public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
         }

         @Override
         public void close() throws IOException {
            super.flush(); // flush Avro container bytes to stream, but do not close it
         }
      };

      try(DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter)) {
         dataFileWriter.setCodec(CodecFactory.deflateCodec(6));
         dataFileWriter.create(schema, shielded);

         for(int r = 0; r < table.getRowCount(); r++) {
            GenericRecord tableRecord = new GenericData.Record(schema);

            for(int c = 0; c < table.getColCount(); c++) {
               tableRecord.put("_" + c, serializeTableData(table.getObject(r, c), r == 0));
            }

            dataFileWriter.append(tableRecord);
         }
      }
   }

   public static XSwappableTable readTable(ObjectInput in) throws IOException {
      return readTable(in, null);
   }

   public static XSwappableTable readTable(ObjectInput in, XSwappableTable table) throws IOException {
      try {
         return readAvro((InputStream) in, table);
      }
      catch(IOException e) {
         throw e;
      }
      catch(Exception e) {
         throw new IOException("Failed to deserialize Avro table", e);
      }
   }

   /**
    * Reads table data from avro. The stream is not closed — the caller owns it.
    */
   private static XSwappableTable readAvro(InputStream in, XSwappableTable table) throws Exception {
      // Wrap in a non-closing filter so DataFileStream.close() does not close the
      // underlying ObjectInput stream, which still has subsequent data to read.
      InputStream shielded = new FilterInputStream(in) {
         @Override public void close() { /* caller owns the stream */ }
      };

      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();

      try(DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(shielded, datumReader)) {
         Schema schema = dataFileReader.getSchema();
         List<Schema.Field> fields = schema.getFields();
         String[] colTypes = new String[fields.size()];
         XMetaInfo[] metaInfos = new XMetaInfo[fields.size()];
         Class<?>[] colClasses = new Class<?>[fields.size()];

         for(int i = 0; i < fields.size(); i++) {
            colTypes[i] = fields.get(i).getProp("colType");
            colClasses[i] = Tool.getDataClass(colTypes[i]);

            try {
               String metaStr = fields.get(i).getProp("meta");

               if(metaStr != null) {
                  XMetaInfo metaInfo = new XMetaInfo();
                  ByteArrayInputStream inputStream = new ByteArrayInputStream(Tool.byteDecode(metaStr).getBytes());
                  Document document = Tool.parseXML(inputStream);
                  metaInfo.parseXML(document.getDocumentElement());
                  metaInfos[i] = metaInfo;
               }
            }
            catch(Exception ignore) {
            }
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
               values[i] = deserializeTableData(val == null ? null : val.toString(), colTypes[i],
                                                headerRow);
            }

            headerRow = false;
            table.addRow(values);
         }

         table.complete();

         try {
            for(int i = 0; i < table.getColCount(); i++) {
               Object header = table.getObject(0, i);

               if(header == null) {
                  continue;
               }

               table.setXMetaInfo(header.toString(), metaInfos[i]);
            }
         }
         catch(Exception ignore) {
         }

         return table;
      }
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
            .prop("meta", Tool.byteEncode(getColMetaInfo(table, i)))
            .type().stringType()
            .noDefault();
      }

      return tableFields.endRecord();
   }

   private static String getColMetaInfo(XTable table, int col) {
      TableDataDescriptor descriptor = table.getDescriptor();

      if(descriptor == null) {
         return "";
      }

      TableDataPath cellDataPath;
      int headerRowCount = table.getHeaderRowCount();

      if(table.moreRows(headerRowCount)) {
         cellDataPath = descriptor.getCellDataPath(headerRowCount, col);
      }
      else {
         cellDataPath = descriptor.getCellDataPath(0, col);
      }

      XMetaInfo xMetaInfo = descriptor.getXMetaInfo(cellDataPath);

      if(xMetaInfo == null) {
         return "";
      }

      try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
         PrintWriter writer = new PrintWriter(outputStream);
         xMetaInfo.writeXML(writer);
         writer.flush();

         return outputStream.toString();
      }
      catch(IOException e) {
         return "";
      }
   }

   private static String serializeTableData(Object data, boolean headerRow) {
      if(headerRow) {
         return Tool.getDataType(data) + ":" + Tool.getPersistentDataString(data);
      }
      else {
         return Tool.getPersistentDataString(data);
      }
   }

   private static Object deserializeTableData(String data, String type, boolean headerRow) {
      if(headerRow && data != null) {
         int idx = data.indexOf(":");
         type = (idx >= 0) ? data.substring(0, idx) : XSchema.STRING;
         data = (idx >= 0) ? data.substring(idx + 1) : data;
      }

      return Tool.getPersistentData(type, data);
   }
}
