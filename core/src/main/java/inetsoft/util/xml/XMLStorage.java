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
package inetsoft.util.xml;

import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XMLStorage parses and stores java object to XML file.

 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XMLStorage {
   /**
    * Constructor.
    */
   public XMLStorage(XMLHelper helper) {
      filtersMap = new HashMap<>();
      this.helper = helper;
      root = new XMLEntity("", this);
   }

   /**
    * Add a filter.
    * Entities would be created for elements with the specified name.
    */
   public void addFilter(Filter filter) {
      filtersMap.put(filter.name, filter);
   }

   /**
    * Get attributes of elements.
    */
   public String[] getAttributes(Filter[] filters, String attribute) {
      XMLEntity[] entities = getEntities(filters, false);
      String[] attributes = new String[entities.length];

      for(int i = 0; i < entities.length; i++) {
         attributes[i] = entities[i].getAttribute(attribute);
      }

      return attributes;
   }

   /**
    * Get the entity.
    */
   public XMLEntity getEntity(Filter[] filters) {
      XMLEntity[] entities = getEntities(filters, false, true);
      return entities.length > 0 ? entities[0] : null;
   }

   /**
    * Get java object.
    */
   public Object getObject(Filter[] filters) {
      XMLEntity[] entities = getEntities(filters, false, true);
      return entities.length > 0 ? entities[0].getObject() : null;
   }

   /**
    * Remove java objects.
    */
   public void removeObjects(Filter[] filters) {
      XMLEntity[] entities = getEntities(filters, false);

      for(XMLEntity entity : entities) {
         entity.getParentEntity().removeEntity(entity);
      }
   }

   /**
    * Set java object.
    */
   public XMLEntity setObject(Filter[] filters, Object obj) {
      XMLEntity[] entities = getEntities(filters, true, true);
      entities[0].setObject(obj);
      return entities[0];
   }

   /**
    * Update java object, and sync attributes.
    */
   public void updateObject(Filter[] filters, Object obj, String[] attributes) {
      XMLEntity entity = setObject(filters, obj);
      entity.setAttribute(attributes[0], attributes[1]);
   }

   /**
    * Get the sub entities.
    */
   public XMLEntity[] getEntities(Filter[] filters, boolean create) {
      return getEntities(filters, create, false);
   }

   /**
    * Get the sub entities.
    */
   public XMLEntity[] getEntities(Filter[] filters, boolean create,
                                  boolean single) {
      List<XMLEntity> list = new ArrayList<>();
      XMLEntity pentity = root;

      Loop:
      for(int i = 0; i < filters.length; i++) {
         Filter filter = filters[i];
         Iterator<XMLEntity> it = pentity.getEntities();
         List<XMLEntity> children = new ArrayList<>();

         while(it.hasNext()) {
            children.add(it.next());
         }

         for(XMLEntity entity : children) {
            if(entity.isValid(filter)) {
            	boolean supported = helper.isSupported(entity, pentity);

            	if(!supported) {
            	   pentity.removeEntity(entity);
            	}
            	else {
                  if(i == filters.length - 1) {
                     if(!filter.isEmpty()) {
                        return new XMLEntity[] {entity};
                     }

                     list.add(entity);

                     if(single) {
                        break Loop;
                     }
                  }
                  else {
                     pentity = entity;
                     continue Loop;
                  }
               }
            }
         }

         if(!create && i != filters.length - 1) {
            break;
         }

         if(create && i != filters.length - 1) {
            XMLEntity entity = new XMLEntity(filter.name, this);
            entity.virtual = true;
            pentity.addEntity(entity);
            pentity = entity;
         }

         if(create && i == filters.length - 1 && list.size() == 0) {
            XMLEntity entity = new XMLEntity(filter.name, this);

            for(String qname : filter.attributes.keySet()) {
               entity.setAttribute(qname, filter.attributes.get(qname));
            }

            pentity.addEntity(entity);
            list.add(entity);

            if(single) {
               break;
            }
         }
      }

      XMLEntity[] entities = new XMLEntity[list.size()];
      list.toArray(entities);

      return entities;
   }

   /**
    * Load data from an input stream.
    * If exception occurs, old data will be restore automatically.
    */
   public void load(InputStream in, boolean overwrite) {
      this.overwrite = overwrite;
      info = new FileInfo();

      finfos.add(info);
      info.file = FileSystemService.getInstance().getCacheTempFile("XMLStorage", "xml");
      FileOutputStream out = null;
      InputStream fin = null;

      try {
         // @by ashur bug1245031001607 temp file lost?
         info.rfile = new RandomAccessFile(info.file, "r");
         out = new FileOutputStream(info.file);
         copyToFile(in, out);
         out.close();
         fin = new BufferedInputStream(new FileInputStream(info.file));
         XMLPParser parser = new XMLPParser();
         parser.setInput(fin, null);
         parseXML(parser);
         fin.close();
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to load XMLStorage file: " + info.file, ex);

         try {
            if(out != null) {
               out.close();
            }

            if(fin != null) {
               fin.close();
            }
         }
         catch(IOException ex2) {
            // ignore it
         }
      }
   }

   private void parseXML(XMLPParser parser) throws Exception {
      int state;
      XMLHandler handler = new XMLHandler();
      handler.parser = parser;
      helper.parser = parser;

      while((state = parser.next()) != XMLPParser.END_DOCUMENT) {
         switch(state) {
         case XMLPParser.START_TAG:
            handler.startElement();
            break;
         case XMLPParser.TEXT:
            handler.characters();
            break;
         case XMLPParser.END_TAG:
            handler.endElement();
            break;
         }
      }
   }

   /**
    * Print string to output stream.
    */
   public void println(String str) {
      writer.println(str);
   }

   /**
    * Print string to output stream.
    */
   public void print(String str) {
      writer.print(str);
   }

   /**
    * Set whether to cache object.
    */
   public void setCache(boolean cache) {
      this.cache = cache;
   }

   /**
    * Check if to cache object.
    */
   public boolean isCache() {
      return cache;
   }

   /**
    * write all XMLEntities.
    */
   public void write() throws Exception {
      if(root != null) {
         writeEntity(root);
      }

      writer.flush();
      cout.flush();

      if(sync) {
         clearFiles();
         info.rfile = new RandomAccessFile(info.file, "r");
         finfos.add(info);
      }
   }

   /**
    * Write java object of this entity to XML data.
    */
   private void writeEntity(XMLEntity entity) throws Exception {
      boolean hasChild = entity.getEntityCount() > 0;
      long start = cout.getPosition();
      boolean isEnd = false;

      if(entity != root) {
         if(entity.virtual) {
            writer.println("<" + entity.name + ">");

            if(!hasChild) {
               writer.println("</" + entity.name + ">");
               isEnd = true;
            }
         }
         else if(entity.obj == null) {
            cout.write(getBytes(entity, false));
         }
         else {
            helper.writeStart(entity, entity.obj, writer);

            if(!hasChild) {
               helper.writeEnd(entity, writer);
               isEnd = true;
            }
         }

         if(sync) {
            entity.info = info;
            entity.startPosition = start;
            entity.endPosition = cout.getPosition();

            if(entity.obj != null) {
               entity.ref = new WeakReference<>(entity.obj);
               entity.obj = null;
            }
         }
      }

      Iterator<XMLEntity> entities = entity.getEntities();

      while(entities.hasNext()) {
         writeEntity(entities.next());
      }

      if(entity != root && (hasChild || entity.isparent) && !isEnd) {
         helper.writeEnd(entity, writer);
      }

      if(sync) {
         entity.isparent = entity.entities.size() > 0;
      }
   }

   /**
    * Copy data from input stream to temporary file.
    */
   private void copyToFile(InputStream in, OutputStream out) throws IOException
   {
      byte[] buf = new byte[4096];
      int cnt;

      while((cnt = in.read(buf)) > 0) {
         out.write(buf, 0, cnt);
      }
   }

   /**
    * Parse XML data to java object.
    */
   protected Object parseObject(XMLEntity entity) {
      byte[] bytes = null;

      try {
         bytes =
            getBytes(entity, entity.getEntityCount() > 0 || entity.isparent);
         Document doc = Tool.parseXML(new ByteArrayInputStream(bytes));
         return helper.parse(entity, (Element) doc.getFirstChild());
      }
      catch(Exception ex) {
         String msg = bytes == null ?
            ex.getMessage() : ex.getMessage() + new String(bytes);
         LOG.error(
                     "Failed to parse XMLEntity in XMLStorage: " + 
                     Tool.convertUserLogInfo(msg), ex);
      }

      return null;
   }

   /**
    * Post process after parse XML data to java object.
    */
   protected void postParseObject(Object obj) {
      helper.postParse(obj);
   }

   /**
    * Get XML data for the entity from file.
    */
   public byte[] getBytes(XMLEntity entity, boolean append) throws IOException {
      String key = "</" + entity.name + ">";
      int len = append ? key.length() : 0;
      len += entity.endPosition - entity.startPosition;
      byte[] bytes = new byte[len];

      // @by jasons, prevent race condition in mv generation
      info.lock.lock();

      try {
         entity.info.rfile.seek(entity.startPosition);
         entity.info.rfile.readFully(bytes);
      }
      finally {
         info.lock.unlock();
      }

      if(append) {
         byte[] abytes = key.getBytes();
         System.arraycopy(abytes, 0, bytes, bytes.length - abytes.length,
                          abytes.length);
      }

      return bytes;
   }

   /**
    * Clear the out-of-date file.
    */
   private void clearFiles() {
      for(FileInfo info : finfos) {
         try {
            if(info.fo != null) {
               info.fo.close();
            }

            if(info.rfile != null) {
               info.rfile.close();
            }
         }
         catch(IOException ex) {
            LOG.info(
                        "Failed to remove XMLStorage temp file: " + info.file, ex);
         }

         if(info.file != null) {
            if(!info.file.delete()) {
               FileSystemService.getInstance().remove(info.file, 60000);
            }
         }
      }

      finfos.clear();
   }

   /**
    * Remove temporary file.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Clear all entities and remove temporary files.
    */
   public void clear() {
      root = new XMLEntity("", this);
      clearFiles();
   }

   /**
    * Dispose this storage.
    */
   public void dispose() {
      clearFiles();
   }

   /**
    * Check if java object has been created.
    */
   public boolean isLoaded(Filter[] filters) {
      XMLEntity[] entities = getEntities(filters, false, true);
      return entities.length > 0 && entities[0].isLoaded();
   }

   /**
    * XML fragment represents an XMLNode in XMLStorage. It is composite,
    * which might contain sub XML fragments as its children.
    */
   public interface XMLFragment {
      void writeStart(PrintWriter writer);
      void writeEnd(PrintWriter writer);
   }

   /**
    * XMLHandler.
    * Create XMLEntity from element.
    */
   private class XMLHandler extends DefaultHandler {
      public void startElement() throws SAXException {
         String name = parser.getName();

         if(first) {
            first = false;
         }
         else {
            Filter filter = null;
            Filter base = null;

            if(filters.size() == 0) {
               filter = XMLStorage.this.filtersMap.get(name);
            }
            else {
               base = filters.getLast().orElse(null);

               if(base != null) {
                  filter = base.filters.get(name);
               }
            }

            if(filter != null) {
               filter = (Filter) filter.clone();
               long pos = parser.istart;
               XMLEntity entity = new XMLEntity(name, XMLStorage.this);
               entity.info = info;
               entity.startPosition = pos;
               entity.virtual = filter.attributes.size() == 0;
               filter.entity = entity;

               for(int i = 0; i < parser.getAttributeCount(); i++) {
                  String aname = parser.getAttributeName(i);

                  if(filter.attributes.containsKey(aname)) {
                     entity.setAttribute(aname, parser.getAttributeValue(i));
                  }
               }

               XMLEntity bentity = base == null ? root : base.entity;

               if(bentity.endPosition == 0) {
                  bentity.endPosition = pos;
               }

               if(filter.checkDuplicated) {
                  int idx = bentity.entities.indexOf(entity);

                  if(idx != -1) {
                     if(overwrite) {
                        bentity.entities.remove(idx);
                        LOG.warn("Multiple xml entities "  +
                           "exist in registry, using the last " +
                           "entity: " + entity);
                     }
                     else {
                        filter = null;
                        LOG.warn("Multiple xml entities "  +
                              "exist in registry, using the " +
                              "original entity: " + entity);
                     }
                  }
               }

               if(filter != null && helper.isValid(entity, bentity)) {
                  bentity.addEntity(entity);
                  helper.filter = filter.names.size() != 0 ? filter : null;
               }
            }
            else {
               helper.name = name;
            }

            filters.add(Optional.ofNullable(filter));
         }
      }

      public void characters() {
         String txt = parser.getText();

         if(txt == null) {
            return;
         }

         txt = txt.trim();

         while(txt.startsWith("\n") || txt.startsWith("\r")) {
            txt = txt.substring(1);
         }

         if(txt.length() == 0) {
	    return;
         }

         Filter filter = helper.filter;

         if(filter != null && filter.names.contains(helper.name)) {
            helper.fill(txt);
            filter.names.remove(helper.name);

            if(filter.names.size() == 0) {
               helper.filter = null;
            }
         }
      }

      public void endElement() {
         if(filters.size() == 0) {
            return;
         }

         Filter filter = filters.getLast().orElse(null);

         if(filter != null) {
            XMLEntity entity = filter.entity;

            if(entity.endPosition == 0) {
               entity.endPosition = parser.iend;
            }
         }

         filters.removeLast();
      }

      private Deque<Optional<Filter>> filters = new ArrayDeque<>();
      private boolean first = true;
      private XMLPParser parser = null;
   }

   /**
    * XMLHelper converts an XMLNode to Java Object, or write down a Java Object
    * in XML format.
    */
   public abstract static class XMLHelper {
      /**
       * Parse element to java object.
       */
      public abstract Object parse(XMLEntity entity, Element elem);

      /**
       * Post proccess after parse element to java object.
       */
      public abstract void postParse(Object obj);

      /**
       * Write start fragment.
       */
      public abstract void writeStart(XMLEntity entity, Object obj,
                                      PrintWriter writer) throws Exception;

      /**
       * Write end fragment.
       */
      public void writeEnd(XMLEntity entity, PrintWriter writer)
                           throws Exception {
         writer.println("</" + entity.getName() + ">");
      }

      /**
       * Check entity is valid or not.
       * If not, this entity will be ignored.
       */
      public boolean isValid(XMLEntity entity, XMLEntity bentity) {
         return true;
      }


      /**
       * Check entity is supported or not.
       * If not, this entity will be ignored.
       */
      public boolean isSupported(XMLEntity entity, XMLEntity bentity) {
         return true;
      }

      /**
       * Fix attributes of entity with properties of child nodes if needed.
       */
      public void fill(String value) {
      }

      protected String name;
      protected Filter filter;
      protected XMLPParser parser;
   }

   /**
    * XMLNode filter filters XMLNode by attributes.
    */
   public static class Filter implements Cloneable {
      public Filter(String name) {
         this.name = name;
      }

      public String toString() {
         return name + attributes;
      }

      public void addFilter(Filter filter) {
         filters.put(filter.name, filter);
      }

      public boolean isEmpty() {
         for(String name : attributes.keySet()) {
            if(name != null && attributes.get(name) != null) {
               return false;
            }
         }

         return true;
      }

      @Override
      protected Object clone() {
         try {
            Filter filter = (Filter) super.clone();
            filter.names = new ArrayList<>(names);
            return filter;
         }
         catch(CloneNotSupportedException ex) {
            return null;
         }
      }

      public String name;
      public boolean checkDuplicated;
      public HashMap<String, String> attributes = new HashMap<>();
      public ArrayList<String> names = new ArrayList<>();
      public HashMap<String, Filter> filters = new HashMap<>();
      public XMLEntity entity;
   }

   class FileInfo {
      File file;
      RandomAccessFile rfile;
      FileOutputStream fo;
      final Lock lock = new ReentrantLock();
   }

   private boolean overwrite;
   private XMLEntity root;
   private XMLHelper helper;
   private HashMap<String, Filter> filtersMap;
   private PrintWriter writer;
   private CounterOutputStream cout;
   private boolean sync;
   private boolean cache = true;
   private static final Logger LOG =
      LoggerFactory.getLogger(XMLStorage.class);

   private ArrayList<FileInfo> finfos = new ArrayList<>();
   private FileInfo info;
}
