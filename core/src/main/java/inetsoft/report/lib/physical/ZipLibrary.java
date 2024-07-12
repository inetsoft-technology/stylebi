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
package inetsoft.report.lib.physical;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Library that stores its entries in a zip file.
 *
 * @author InetSoft Technology
 * @since  11.1
 */
public abstract class ZipLibrary implements PhysicalLibrary {
   /**
    * Creates a new instance of <tt>ZipLibrary</tt>.
    */
   public ZipLibrary() {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public PhysicalLibraryEntry createEntry(String path, Properties properties) throws IOException {
      if(output == null) {
         output = new ZipOutputStream(getOutputStream());
      }

      ZipEntry entry = new ZipEntry(path);

      if(properties != null && properties.get("comment") != null) {
         entry.setExtra(((String) properties.get("comment")).getBytes());
      }

      output.putNextEntry(entry);
      return new ZipLibraryEntry(entry, output);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Iterator<PhysicalLibraryEntry> getEntries() throws IOException {
      if(input == null) {
         input = new ZipInputStream(getInputStream());
      }

      return new Iterator<PhysicalLibraryEntry>() {
         @Override
         public boolean hasNext() {
            if(!nextChecked) {
               try {
                  next = input.getNextEntry();
               }
               catch(IOException exc) {
                  throw new RuntimeException("Failed to read zip entry", exc);
               }

               nextChecked = true;
            }

            return next != null;
         }

         @Override
         public PhysicalLibraryEntry next() {
            if(!hasNext()) {
               throw new NoSuchElementException();
            }

            ZipEntry current = next;
            next = null;
            nextChecked = false;

            return new ZipLibraryEntry(current, input);
         }

         @Override
         public void remove() {
            throw new UnsupportedOperationException();
         }

         private boolean nextChecked = true;
         private ZipEntry next = input.getNextEntry();
      };
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws IOException {
      if(input != null) {
         input.close();
         input = null;
      }

      if(output != null) {
         output.close();
         output = null;
      }
   }

   /**
    * Gets the raw input stream for the library file.
    *
    * @return the input stream.
    *
    * @throws IOException if an I/O exception occurs.
    */
   protected abstract InputStream getInputStream() throws IOException;

   /**
    * Gets the raw output stream for the library file.
    *
    * @return the output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected OutputStream getOutputStream() throws IOException {
      throw new UnsupportedOperationException();
   }

   private ZipInputStream input;
   private ZipOutputStream output;
}
