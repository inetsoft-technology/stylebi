/*
 * maven-shade-transformers - StyleBI is a business intelligence web application.
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
package com.inetsoft.build.shade;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;
import org.apache.xmlbeans.impl.util.LongUTFDataInputStream;
import org.apache.xmlbeans.impl.util.LongUTFDataOutputStream;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class XsbTransformer implements ReproducibleResourceTransformer {
   private final Map<String, byte[]> transformed = new HashMap<>();
   private long time = Long.MIN_VALUE;

   @Override
   public void processResource(String resource, InputStream is, List<Relocator> relocators,
                               long time) throws IOException
   {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      LongUTFDataOutputStream dataOut = new LongUTFDataOutputStream(os);
      LongUTFDataInputStream dataIn = new LongUTFDataInputStream(is);
      dataOut.writeInt(dataIn.readInt()); // magic number
      dataOut.writeShort(dataIn.readShort()); // major version
      dataOut.writeShort(dataIn.readShort()); // minor version
      dataOut.writeShort(dataIn.readShort()); // release number
      dataOut.writeShort(dataIn.readShort()); // file type

      int size = dataIn.readUnsignedShortOrInt();
      dataOut.writeShortOrInt(size);

      for(int i = 1; i < size; i++) {
         String str;

         try {
            str = dataIn.readLongUTF();
         }
         catch(Exception e) {
            throw new IOException("Error reading string " + i + " of size " + size + " in " + resource, e);
         }

         for(Relocator relocator : relocators) {
            if(relocator.canRelocateClass(str)) {
               str = relocator.relocateClass(str);
               break;
            }
         }

         dataOut.writeLongUTF(str);
      }

      dataOut.flush();
      IOUtils.copy(is, os);
      transformed.put(resource, os.toByteArray());

      if(time > this.time) {
         this.time = time;
      }
   }

   @Override
   public boolean canTransformResource(String resource) {
      return resource.endsWith(".xsb");
   }

   @Override
   public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException
   {
      processResource(resource, is, relocators, 0L);
   }

   @Override
   public boolean hasTransformedResource() {
      return !transformed.isEmpty();
   }

   @Override
   public void modifyOutputStream(JarOutputStream os) throws IOException {
      for(Map.Entry<String, byte[]> e : transformed.entrySet()) {
         JarEntry entry = new JarEntry(e.getKey());
         entry.setTime(time);
         os.putNextEntry(entry);
         IOUtils.write(e.getValue(), os);
         os.closeEntry();
      }
   }
}
