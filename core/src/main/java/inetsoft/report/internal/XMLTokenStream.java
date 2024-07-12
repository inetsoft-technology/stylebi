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
package inetsoft.report.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * XML parser. For internal use only.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XMLTokenStream implements Serializable {
   /**
    */
   public XMLTokenStream(InputStream input) {
      try {
         // find encoding
         int ch;
         char[] carr = new char[256];

         while((ch = input.read()) != '<' && ch >= 0) {
         }

         int cnt = 0;

         for(int i = 0; i < carr.length && (ch = input.read()) != '>'; i++) {
            carr[cnt++] = (char) ch;
         }

         String str = (new String(carr, 0, cnt)).toUpperCase();
         int idx = str.indexOf("ENCODING");
         // find encoding
         String enc = null;

         if(idx > 0) {
            idx = str.indexOf('"', idx);
            if(idx > 0) {
               int si = idx + 1;
               int ei = str.indexOf('"', si);

               if(ei > 0) {
                  enc = str.substring(si, ei);
                  if(enc.equals("UTF-8")) {
                     enc = "UTF8";
                  }
               }
            }
         }

         if(enc != null) {
            reader = new BufferedReader(new InputStreamReader(input, enc));
         }
         else {
            reader = new BufferedReader(new InputStreamReader(input));
         }
      }
      catch(UnsupportedEncodingException e) {
         reader = new BufferedReader(new InputStreamReader(input));
         LOG.error("Failed to create reader for specified encoding", e);
      }
      catch(IOException e) {
         LOG.error("Failed to create reader for input", e);
      }
   }

   /**
    * Get the input stream reader. This should not be used by casual
    * implementations since it can modify the internal parsing sequence
    * of the XML stream.
    * @return input stream reader.
    */
   public Reader getReader() {
      return reader;
   }

   /**
    * This method returns either a String or a Tag.
    */
   public Object getToken() throws IOException {
      if(nextTag != null) {
         Object rc = nextTag;

         nextTag = null;
         return rc;
      }

      // init
      if(nextchar == -999) {
         nextchar = reader.read();
      }

      // eof
      if(nextchar == -1) {
         return null;
      }

      skipWhitespace();

      // read a tag
      if(nextchar == '<') {
         boolean qtag = false;

         nextchar = reader.read(); // skip '<'
         skipWhitespace();

         StringBuilder name = new StringBuilder();

         // comment
         if(nextchar == '!') {
            for(; nextchar >= 0 && nextchar != '>'; nextchar = reader.read()) {
               // ignore linefeed
               if((char) nextchar != '\r') {
                  name.append((char) nextchar);
               }
            }

            // handle CDATA section
            if(name.toString().startsWith("![CDATA[")) {
               name = new StringBuilder(name.toString().substring(8));

               // find the end of CDATA, ']]>'
               if(!name.toString().endsWith("]]")) {
                  for(; nextchar >= 0; nextchar = reader.read()) {
                     if(nextchar == ']') {
                        nextchar = reader.read();

                        if(nextchar != ']') {
                           name.append("]");
                           if(nextchar >= 0) {
                              name.append((char) nextchar);
                           }
                           else {
                              break;
                           }

                           continue;
                        }
                        else {
                           nextchar = reader.read();

                           if(nextchar != '>') {
                              name.append("]]");
                              if(nextchar >= 0) {
                                 name.append((char) nextchar);
                              }
                              else {
                                 break;
                              }

                              continue;
                           }
                           else {
                              // skip '>'
                              nextchar = reader.read();
                              break;
                           }
                        }
                     }

                     // ignore linefeed
                     if((char) nextchar != '\r') {
                        name.append((char) nextchar);
                     }
                  }
               }
               else {
                  name.setLength(name.length() - 2); // truncate ']]'
                  nextchar = reader.read(); // skip '>'
               }

               return name.toString();
            }

            nextchar = reader.read(); // skip '>'
            return new Tag(name.toString());
         }
         else if(nextchar == '?') {
            qtag = true;
            nextchar = reader.read();
         }
         else if(nextchar == '/') {
            name.append("/");
            nextchar = reader.read();
         }

         name.append(getName());
         skipWhitespace();

         Tag tag = new Tag(name.toString());

         while(nextchar != '>') {
            if(qtag && nextchar == '?') {
               nextchar = reader.read();
               continue;
            }

            String attr = tag.getName();

            // a cases: <FONT=+1> or <A HREF=...>
            if(nextchar != '=') {
               attr = getName();
            }

            skipWhitespace();

            char eq = (char) nextchar;

            if(eq == '=') {
               nextchar = reader.read(); // skip '='
               skipWhitespace();
               String value = getValue();

               if(attr == null || eq != '=' || value == null) {
                  throw new IOException("XML tag format error: (" + name +
                     ":" + attr + ":" + eq + ":" + value + ")");
               }

               tag.put(attr, value);
            }
            else if(attr != null) {
               // attribute with no value
               tag.put(attr, "");
            }
            else if(eq == '/') {
               // empty element <tag />
               // should check the next character is '>'
               skipWhitespace();
               nextchar = reader.read();
               if(nextchar == '>') {
                  nextTag = new Tag("/" + tag.getName());
                  break;
               }
               else {
                  throw new IOException("XML tag format error: (" + eq +
                     "): " + tag.getName());
               }
            }
            else {
               throw new IOException("XML tag format error: (" + eq + "): " +
                  tag.getName());
            }

            skipWhitespace();
         }

         // skip '>'x
         nextchar = reader.read();

         return tag;
      }

      // read a string
      return getString();
   }

   public void skipWhitespace() throws IOException {
      for(; nextchar >= 0 && Character.isWhitespace((char) nextchar); 
          nextchar = reader.read()) 
      {

      }
   }

   // html encoding mapping
   private static final String[][] encoding = { {"&nbsp;", "&amp;", "&lt;",
         "&gt;", "&apos;", "&quot;"}, {" ", "&", "<", ">", "'", "\""} };
   protected String getString() throws IOException {
      StringBuilder str = new StringBuilder();

      if(nextchar == '"') {
         while((nextchar = reader.read()) >= 0 && nextchar != '"') {
            str.append((char) nextchar);
         }

         nextchar = reader.read(); // skip '"'
      }
      else {
         rloop:
         do {
            switch(nextchar) {
            case '<':
               break rloop;
            /*
             case '"':
             str.append(getString());
             break;
             */
            case '\t':
            case '\n':
            case '\r': // ignore newlines
               break;
            default:
               str.append((char) nextchar);
            }
         }
         while((nextchar = reader.read()) >= 0);

         // translate encoding
         String rc = str.toString();

         for(int i = 0; i < encoding[0].length; i++) {
            int idx = 0;

            while(true) {
               if((idx = rc.indexOf(encoding[0][i], idx)) >= 0) {
                  rc = rc.substring(0, idx) + encoding[1][i] +
                     rc.substring(idx + encoding[0][i].length());
               }
               else {
                  break;
               }
            }
         }

         return rc;
      }

      // translation is not done in quoted string
      return str.toString();
   }

   protected String getName() throws IOException {
      if(nextchar == '"') {
         return getString();
      }
      else if(Character.isUnicodeIdentifierStart((char) nextchar)) {
         StringBuilder str = new StringBuilder();

         do {
            str.append((char) nextchar);
            nextchar = reader.read();
         }
         while(Character.isUnicodeIdentifierPart((char) nextchar) ||
            nextchar == '-');
         return str.toString();
      }

      return null;
   }

   protected String getValue() throws IOException {
      if(nextchar == '"') {
         return getString();
      }

      StringBuilder str = new StringBuilder();

      do {
         str.append((char) nextchar);
         nextchar = reader.read();
      }
      while(nextchar > 0 && !Character.isWhitespace((char) nextchar) &&
         nextchar != '>');
      return str.toString();
   }

   private BufferedReader reader;
   private int nextchar = -999;
   // set if an empty tag is encountered to return the end tag
   private Object nextTag = null;
   /**
    * This represents a XML tag. All tag name and attribute names are
    * converted to upper case.
    */
   public static class Tag implements Serializable {
      /**
       * Construct a tag with specified name.
       * @param name tag name.
       */
      public Tag(String name) {
         this.name = name.toUpperCase();
      }

      /**
       * Get the tag name.
       * @return tag name.
       */
      public String getName() {
         return name;
      }

      /**
       * Add an attribute and value to the tag.
       * @param attr attribute name.
       * @param value attribute value.
       */
      public void put(String attr, String value) {
         attrmap.put(attr.toUpperCase(), value);
      }

      /**
       * Get the attribute value.
       * @param attr attribute name.
       * @return attribute value.
       */
      public String get(String attr) {
         return (String) attrmap.get(attr.toUpperCase());
      }

      /**
       * Get the list of attribute names.
       * @return attribute names.
       */
      public Enumeration getAttributes() {
         return attrmap.keys();
      }

      public boolean is(String tagname) {
         return name.equals(tagname.toUpperCase());
      }

      public boolean equals(Object obj) {
         if(obj instanceof String) {
            return is((String) obj);
         }
         else if(obj instanceof Tag) {
            return is(((Tag) obj).getName());
         }

         return false;
      }

      public String toString() {
         StringBuilder str = new StringBuilder("<" + name);

         for(Enumeration keys = attrmap.keys(), vals = attrmap.elements(); 
             keys.hasMoreElements();) 
         {
            str.append(" " + keys.nextElement() + "=" + vals.nextElement());
         }

         str.append(">");
         return str.toString();
      }

      private String name = "";
      private Hashtable attrmap = new Hashtable();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLTokenStream.class);
}

