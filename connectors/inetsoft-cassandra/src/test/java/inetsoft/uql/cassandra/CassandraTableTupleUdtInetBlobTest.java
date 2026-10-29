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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the values {@link CassandraTable#getObject(int)} returns for tuple, UDT, inet and blob
 * columns: readable text for the first three, {@code 0x} hex for non-image blob bytes, and a real
 * {@link Image} for blobs that decode as an image.
 */
class CassandraTableTupleUdtInetBlobTest {
   @Test
   void tuple_returnsFormattedContents() throws Exception {
      TupleValue tuple = mock(TupleValue.class);
      when(tuple.getFormattedContents()).thenReturn("(1,'t')");
      Row row = mock(Row.class);
      when(row.getTupleValue(0)).thenReturn(tuple);

      assertEquals("(1,'t')", value(ProtocolConstants.DataType.TUPLE, row));
   }

   @Test
   void udt_returnsFormattedContents() throws Exception {
      UdtValue udt = mock(UdtValue.class);
      when(udt.getFormattedContents()).thenReturn("{street:'s',zip:'z'}");
      Row row = mock(Row.class);
      when(row.getUdtValue(0)).thenReturn(udt);

      assertEquals("{street:'s',zip:'z'}", value(ProtocolConstants.DataType.UDT, row));
   }

   @Test
   void inet_hasNoLeadingSlash() throws Exception {
      Row row = mock(Row.class);
      when(row.getInetAddress(0)).thenReturn(
         InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, 1, 10 }));

      assertEquals("192.168.1.10", value(ProtocolConstants.DataType.INET, row));
   }

   @Test
   void blob_nonImageBytesReturnHex() throws Exception {
      Row row = mock(Row.class);
      when(row.getByteBuffer(0)).thenReturn(
         ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));

      assertEquals("0x68656c6c6f", value(ProtocolConstants.DataType.BLOB, row));
   }

   @Test
   void blob_realImageStillReturnsImage() throws Exception {
      BufferedImage png = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(png, "png", out);
      Row row = mock(Row.class);
      when(row.getByteBuffer(0)).thenReturn(ByteBuffer.wrap(out.toByteArray()));

      assertInstanceOf(Image.class, value(ProtocolConstants.DataType.BLOB, row));
   }

   @Test
   void blob_slicedBufferUsesOnlyRemainingBytes() throws Exception {
      byte[] backing = "xxhelloyy".getBytes(StandardCharsets.UTF_8);
      ByteBuffer slice = ByteBuffer.wrap(backing, 2, 5).slice();
      Row row = mock(Row.class);
      when(row.getByteBuffer(0)).thenReturn(slice);

      assertEquals("0x68656c6c6f", value(ProtocolConstants.DataType.BLOB, row));
   }

   @Test
   void blob_slicedImageBufferStillDecodes() throws Exception {
      BufferedImage png = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(png, "png", out);
      byte[] img = out.toByteArray();
      byte[] backing = new byte[img.length + 4];
      System.arraycopy(img, 0, backing, 2, img.length);
      Row row = mock(Row.class);
      when(row.getByteBuffer(0)).thenReturn(ByteBuffer.wrap(backing, 2, img.length).slice());

      assertInstanceOf(Image.class, value(ProtocolConstants.DataType.BLOB, row));
   }

   @Test
   void nullColumns_returnNull() throws Exception {
      Row row = mock(Row.class);

      assertNull(value(ProtocolConstants.DataType.TUPLE, row));
      assertNull(value(ProtocolConstants.DataType.UDT, row));
      assertNull(value(ProtocolConstants.DataType.INET, row));
      assertNull(value(ProtocolConstants.DataType.BLOB, row));
   }

   private static Object value(int protocolCode, Row row) throws Exception {
      DataType type = mock(DataType.class);
      when(type.getProtocolCode()).thenReturn(protocolCode);

      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row, (Row) null);

      CassandraTable table = new CassandraTable(result, mock(CqlSession.class), 0);
      assertTrue(table.next());
      return table.getObject(0);
   }
}
