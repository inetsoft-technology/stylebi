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
package inetsoft.graph.geo;

import inetsoft.util.FileSystemService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Specialization of GeoMap that only buffers a limited number of shapes in
 * memory.
 *
 * @since 10.2
 * @author InetSoft Technology
 */
public class BufferedGeoMap extends GeoMap {
   /**
    * Gets the input stream for the specified file.
    *
    * @return the input stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   private InputStream getInputStream(String file) throws IOException {
      InputStream inp = getClass().getResourceAsStream(file);

      if(inp == null) {
         throw new IOException("Map file not found: " + file);
      }

      if(file.endsWith(".gz")) {
         inp = new GZIPInputStream(inp);
      }
      
      return inp;
   }
   
   /**
    * {@inheritDoc}
    */
   @Override
   protected void load(String file) throws IOException {
      final File tmpfile = FileSystemService.getInstance().getCacheTempFile("map", "csv");

      InputStream input = null;
      OutputStream output = null;

      byte[] buffer = new byte[1024];
      int len = 0;

      try {
         input = getInputStream(file);
         output = new FileOutputStream(tmpfile);

         while((len = input.read(buffer)) >= 0) {
            output.write(buffer, 0, len);
         }
      }
      finally {
         IOUtils.closeQuietly(input);
         IOUtils.closeQuietly(output);
      }

      RandomAccessFile rafile = new RandomAccessFile(tmpfile, "rw");
      dataInput = new RandomAccessInputStream(rafile);

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               dataInput.close();
            }
            catch(IOException exc) {
               LoggerFactory.getLogger("inetsoft").warn("Failed to close map data file", exc);
            }

            tmpfile.delete();
         }
      }));

      input = getClass().getResourceAsStream(file + ".index");

      if(input != null) {
         try {
            BufferedReader reader =
               new BufferedReader(new InputStreamReader(input));
            String line = null;
            
            while((line = reader.readLine()) != null) {
               line = line.trim();
               
               if(line.length() == 0) {
                  continue;
               }
               
               int idx = line.indexOf('|');
               
               if(idx >= 0) {
                  String key = line.substring(0, idx);
                  long offset = Long.parseLong(line.substring(idx + 1));
                  offsets.put(key, offset);
               }
            }
         }
         finally {
            input.close();
         }
      }
      else {
         try {
            OUTER: while(true) {
               long pos = dataInput.getPosition();
               StringBuilder builder = new StringBuilder();
               
               while(true) {
                  int c = dataInput.read();
                  
                  if(c == '\r' || c == '\n') {
                     continue OUTER;
                  }
                  
                  if(c < 0) {
                     break OUTER;
                  }
                  
                  if(c == '|') {
                     String key = builder.toString();
                     offsets.put(key, pos);
                     
                     while(true) {
                        c = dataInput.read();
                        
                        if(c == '\r' || c == '\n') {
                           continue OUTER;
                        }
                        
                        if(c < 0) {
                           break OUTER;
                        }
                     }
                  }
                  
                  builder.append((char) c);
               }
            }
         }
         finally {
            dataInput.close();
         }
      }
   }
   
   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized GeoShape getShape(String name) throws Exception {
      GeoShape shape = super.getShape(name);
      
      if(shape != null) {
         return shape;
      }
      
      shape = cache.get(name);
      
      if(shape == null && offsets.containsKey(name)) {
         long offset = offsets.get(name);
         
         try {
            dataInput.seek(offset);
            
            BufferedReader reader =
               new BufferedReader(new InputStreamReader(dataInput));
            
            String line = reader.readLine();
            shape = readShape(line, cache, null);
         }
         catch(IOException exc) {
            LOG.error("Failed to load map shape", exc);
         }
      }
      
      return shape;
   }
   
   /**
    * {@inheritDoc}
    */
   @Override
   public Collection<String> getNames() {
      return offsets.keySet();
   }

   private static final class RandomAccessInputStream extends InputStream {
      public RandomAccessInputStream(RandomAccessFile file) {
         this.file = file;
      }

      @Override
      public int read() throws IOException {
         return file.read();
      }

      @Override
      public int read(byte[] b) throws IOException {
         return file.read(b);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         return file.read(b, off, len);
      }

      @Override
      public int available() throws IOException {
         return (int) (file.length() - file.getFilePointer());
      }

      @Override
      public long skip(long n) throws IOException {
         file.seek(file.getFilePointer() + n);
         return n;
      }

      @Override
      public boolean markSupported() {
         return true;
      }

      @Override
      public synchronized void mark(int readlimit) {
         try {
            mark = file.getFilePointer();
         }
         catch(IOException exc) {
            LOG.error("Failed to set input stream mark", exc);
         }
      }

      @Override
      public synchronized void reset() throws IOException {
         file.seek(mark);
      }

      @Override
      public void close() throws IOException {
         file.close();
      }

      public long getPosition() throws IOException {
         return file.getFilePointer();
      }

      public void seek(long pos) throws IOException {
         file.seek(pos);
      }

      private final RandomAccessFile file;
      private long mark = 0L;
   }
   
   private RandomAccessInputStream dataInput = null;
   private Map<String,Long> offsets = new HashMap();
   private Map<String,GeoShape> cache = new LinkedHashMap(16, 0.75F, true)
   {
      @Override
      protected boolean removeEldestEntry(Map.Entry eldest) {
         return size() > 100;
      }
   };

   private static final Logger LOG =
      LoggerFactory.getLogger(BufferedGeoMap.class);
}
