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
package inetsoft.report.lib.physical;

import java.io.*;
import java.util.Properties;
import java.util.zip.*;

/**
 * Library entry that is stored as an entry in a zip file.
 *
 * @author InetSoft Technology
 * @since  11.1
 */
public final class ZipLibraryEntry implements PhysicalLibraryEntry {
   /**
    * Creates a new instance of <tt>ZipLibraryEntry</tt>.
    *
    * @param entry the zip entry for the library entry.
    * @param input the zip file input stream.
    */
   public ZipLibraryEntry(ZipEntry entry, ZipInputStream input) {
      this.entry = entry;
      this.input = input;
      this.output = null;
   }

   /**
    * Creates a new instance of <tt>ZipLibraryEntry</tt>.
    *
    * @param entry  the zip entry for the library entry.
    * @param output the zip file output stream.
    */
   public ZipLibraryEntry(ZipEntry entry, ZipOutputStream output) {
      this.entry = entry;
      this.input = null;
      this.output = output;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getPath() {
      return entry.getName();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isDirectory() {
      return entry.isDirectory();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getComment() {
      String comment = entry.getComment();

      if(comment == null) {
         byte[] extra = entry.getExtra();

         if(extra != null) {
            comment = new String(extra);
         }
      }

      return comment;
   }

   @Override
   public Properties getCommentProperties(){
      return new Properties();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public InputStream getInputStream() {
      if(input == null) {
         throw new IllegalStateException(
            "The library was opened for writing");
      }

      return new FilterInputStream(input) {
         @Override
         public void close() {
            // no-op
         }
      };
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public OutputStream getOutputStream() {
      if(output == null) {
         throw new IllegalStateException("The library was opened for reading");
      }

      return new FilterOutputStream(output) {
         @Override
         public void close() {
            // no-op
         }
      };
   }

   private final ZipEntry entry;
   private final ZipInputStream input;
   private final ZipOutputStream output;
}
