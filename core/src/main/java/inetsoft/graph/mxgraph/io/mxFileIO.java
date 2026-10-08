/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.graph.mxgraph.io;

import inetsoft.graph.mxgraph.util.mxXmlUtils;
import org.w3c.dom.Document;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File and URL I/O helpers relocated out of
 * {@link inetsoft.graph.mxgraph.util.mxUtils}. (Bug #78079)
 *
 * <p>The script sandbox keys its file/network boundary on class name, not on
 * what a method does, so every public member of a script-admitted class that
 * reads, writes or parses a script-controlled path ran that I/O for a restricted
 * script. {@code mxUtils} sits under the script-admitted
 * {@code inetsoft.graph.} prefix and must stay reachable, because it also exposes
 * the benign {@code eval}/{@code loadImage} script helpers. Graal 24.1.2 has no
 * member-level deny, so the only way to keep {@code mxUtils} reachable while
 * taking these raw read/write/parse sinks out of script reach is to move them to
 * a class scripts cannot name.
 *
 * <p>This class lives in {@code inetsoft.graph.mxgraph.io}, a package blocked
 * from scripts by name ({@code ScriptHostAccess.BLOCKED_PACKAGES}), so
 * {@code Java.type} cannot look it up and no script can call these statics. The
 * behaviour is unchanged from the original {@code mxUtils} methods; only Java
 * callers (the mxgraph codec) use them.
 */
public class mxFileIO {
   private mxFileIO() {
   }

   /**
    * Reads the given filename into a string.
    *
    * @param filename Name of the file to be read.
    *
    * @return Returns a string representing the file contents.
    *
    * @throws IOException
    */
   public static String readFile(String filename) throws IOException
   {
      return readInputStream(new FileInputStream(filename));
   }

   /**
    * Reads the given stream into a string.
    *
    * @param stream the stream to read.
    *
    * @return Returns a string representing the stream contents.
    *
    * @throws IOException
    */
   public static String readInputStream(InputStream stream) throws IOException
   {
      BufferedReader reader = new BufferedReader(
         new InputStreamReader(stream));
      StringBuffer result = new StringBuffer();
      String tmp = reader.readLine();

      while(tmp != null) {
         result.append(tmp + "\n");
         tmp = reader.readLine();
      }

      reader.close();

      return result.toString();
   }

   /**
    * Writes the given string into the given file.
    *
    * @param contents String representing the file contents.
    * @param filename Name of the file to be written.
    *
    * @throws IOException
    */
   public static void writeFile(String contents, String filename)
      throws IOException
   {
      FileWriter fw = new FileWriter(filename);
      fw.write(contents);
      fw.flush();
      fw.close();
   }

   /**
    * Returns a new DOM document for the given URI. External entities and DTDs are ignored.
    *
    * @param uri URI to parse into the document.
    *
    * @return Returns a new DOM document for the given URI.
    */
   public static Document loadDocument(String uri)
   {
      try {
         return mxXmlUtils.getDocumentBuilder().parse(uri);
      }
      catch(Exception e) {
         log.log(Level.SEVERE, "Failed to load the document from " + uri, e);
      }

      return null;
   }

   private static final Logger log = Logger.getLogger(mxFileIO.class.getName());
}
