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
package inetsoft.util;

import java.io.*;
import java.nio.file.InvalidPathException;
import java.util.*;

/**
 * The tool has 2 methods for use.
 * The first used to transform 6.5 property file to 7.0 resource bundle file,
 * If 6.5 property file has useless ids or doesn't contain all our ids, give
 * the list to user, The list is placed in the file user designated or printed
 * in System.out.
 * The second used to find the ids still not in users' property file.
 *
 * @version 7.0, 2004-11-2
 * @author InetSoft Technology Corp
 */
public class CatalogVerifier {
   /**
    * Constructor.
    *
    * @param userPropf user property File
    * @param combinePropf combine property File
    * @param outputf mismatch File
    */
   public CatalogVerifier(File userPropf, File combinePropf, File outputf) {
      this.userPropf = userPropf;
      this.combinePropf = combinePropf;
      this.outputf = outputf;
      propUser = new InetProperties();
      propDefault = new InetProperties();
      propLocale = new InetProperties();
      InputStream defaultPropFile = CatalogVerifier.class.getResourceAsStream(
         "/inetsoft/util/srinter.properties");

      try {
         propUser.load(new FileInputStream(userPropf));
         propDefault.load(defaultPropFile);
      }
      catch (IOException e) {
         System.out.println(e);
      }
      catch (Exception e) {
         System.out.println("Failed to load properties");
      }
   }

   /**
    * Constructor.
    *
    * @param userProp user property
    */
   public CatalogVerifier(InetProperties userProp, File outputf) {
      this.outputf = outputf;
      propUser = userProp;
      propDefault = new InetProperties();
      propLocale = new InetProperties();
      InputStream defaultPropFile = CatalogVerifier.class.getResourceAsStream(
         "/inetsoft/util/srinter.properties");

      try {
         propDefault.load(defaultPropFile);
      }
      catch (IOException e) {
         System.out.println(e);
      }
      catch (Exception e) {
         System.out.println("Failed to load properties");
      }
   }

   public static void main(String[] args) {
      int cmdLength = args.length;

      if(cmdLength < 1) {
         usage();
      }

      File userPropf = null;
      File combinePropf = null;
      File outputf = null;
      FileSystemService fileSystemService = FileSystemService.getInstance();

      try {
         //transformer
         if((args[0].equals("-v")) && (cmdLength > 3) &&
            (args[1].equals("6.5")))
         {
            if(args.length == 4) {
               userPropf = fileSystemService.getFile(args[2]);
               combinePropf = fileSystemService.getFile(args[3]);
            }
            else if(args.length == 5) {
               userPropf = fileSystemService.getFile(args[2]);
               combinePropf = fileSystemService.getFile(args[3]);
               outputf = fileSystemService.getFile(args[4]);
            }
            else {
               usage();
            }

            if(!userPropf.exists()) {
               System.out.println("   The file you designated doesn't exist.");
               usage();
            }

            if((outputf != null) && (!userPropf.exists())) {
               outputf.createNewFile();
            }

            if((combinePropf != null) && (!combinePropf.exists())) {
               combinePropf.createNewFile();
            }

            new CatalogVerifier(userPropf, combinePropf, outputf).
               processCatalog();
         }
         // finder
         else if(cmdLength < 3) {
            if(args.length == 1) {
               userPropf = fileSystemService.getFile(args[0]);
            }
            else if(args.length == 2) {
               userPropf = fileSystemService.getFile(args[0]);
               outputf = fileSystemService.getFile(args[1]);
            }
            else {
               usage();
            }

            if(!userPropf.exists()) {
               System.out.println("   The file you designated doesn't exist.");
               usage();
            }

            if((outputf != null) && (!userPropf.exists())) {
               outputf.createNewFile();
            }

            System.out.println("Are you sure you want to remove the useless" +
                    " IDs in your file(Y/N):");

            BufferedReader in = new BufferedReader(
               new InputStreamReader(System.in));
            String s = in.readLine();
            boolean removeOrNot = false;

            if(s != null && s.length() != 0 &&
               s.trim().equalsIgnoreCase("y"))
            {
               removeOrNot = true;
               System.out.println("The useless IDs in your file will be " +
                  "removed! Please see it in list file.");
            }

            new CatalogVerifier(userPropf, combinePropf, outputf).
               findNotIn(removeOrNot);
         }
         else {
            usage();
         }

         System.out.println("==========================================");
         System.out.println("Finished the work.");
      }
      catch(IOException ioe) {
         ioe.printStackTrace(); //NOSONAR
      }
      catch(InvalidPathException ipe) {
         ipe.printStackTrace(); //NOSONAR
      }
   }

   /**
    * Print usage of the tool, and exit.
    */
   static void usage() {
      System.out.println("   Usage:");
      System.out.println("      java CatalogVerifier -v 6.5 "
            + "file_old_property file_new_property [file_to_place_list] ");
      System.out.println("   or");
      System.out.println("      java CatalogVerifier "
            + "file_user_property [file_to_place_list] ");
      System.out.println("\n   Note:");
      System.out.println("      The tool has 2 methods for use." );
      System.out.println("      -v 6.5 transform 6.5 property file to 7.0 " +
             "resource bundle file, file_old_property is your locale " +
             "properties file, file_new_property is the combined file, " +
             "file_to_place_list is the file to put properties useless " +
             "and not contained(without it the result will list in console).");
      System.out.println("      The second find the ids still not in your " +
             "property file, file_old_property is your properties file, " +
             "file_to_place_list is the file to put properties useless " +
             "and not contained(without it the result will list in console).");

      System.exit(1);
   }

   /**
    * Transform 6.5 property file to 6.6 resource bundle file, Left useless ids
    * in propUser and not contained ids in propDefault. Output them to the file
    * user designated or System.out.
    */
   void processCatalog() throws IOException {
      if(propUser.size() == 0) {
         System.out.println("   No user defined property exists!");
         return;
      }

      Enumeration enumKeys = propUser.keys();

      for(Enumeration e = enumKeys; e.hasMoreElements();) {
         String propStrDefault = (String) e.nextElement();
         String propVal = (String) propUser.get(propStrDefault);

         String PropID = isExisting(propStrDefault);

         if(PropID != null) {
            propUser.remove(propStrDefault);
            propDefault.remove(PropID);
            propLocale.put(PropID, propVal);
         }
      }

      //put propLocale to locale resource bundle file.
      FileOutputStream localeOut = new FileOutputStream(combinePropf);
      store(localeOut, null, propLocale, false);
      localeOut.close();

      outputResult();
   }

   /**
    * Construct the outPrint in system console.
    *
    * @param out object where to print.
    * @param header the header of list, describe the list.
    * @param table the list of properties.
    * @throws IOException
    */
   public void showList(PrintWriter out, String header, Hashtable table)
         throws IOException {
      out.println("=======================");

      if(header != null) {
         out.println("#" + header);
         out.flush();
      }

      for(Enumeration e = table.keys(); e.hasMoreElements();) {
         String key = (String) e.nextElement();
         String val = (String) table.get(key);
         out.println(key + "=" + val);
         out.flush();
      }

      out.flush();
   }

   /**
    * Write the property pairs.
    */
   public static void store(OutputStream out, String header,
      InetProperties prop, boolean change) throws IOException
   {
      BufferedWriter awriter;
      awriter = new BufferedWriter(new OutputStreamWriter(out));

      if(header != null) {
         writeln(awriter, "#" + header);
      }

      writeln(awriter, "#" + new Date().toString());

      Set set = prop.keySet();
      Object[] keys = set.toArray();
      Arrays.sort(keys);

      for(int i = 0; i < keys.length; i++) {
         String key = (String) keys[i];
         String val = (String) prop.getProperty(key);

         if(change) {
            key = Tool.replaceAll(key, "_", " ");
         }

         key = saveConvert(key, true);

         if(change) {
            key = "#" + key;
         }

         // No need to escape embedded and trailing spaces for value, hence pass
         // false to flag.
         val = saveConvert(val, false);
         writeln(awriter, key + "=" + val);
      }

      awriter.flush();
   }

   private static String saveConvert(String theString, boolean escapeSpace) {
      int len = theString.length();
      StringBuilder outBuffer = new StringBuilder(len * 2);

      for(int x = 0; x < len; x++) {
         char aChar = theString.charAt(x);
         switch(aChar) {
            case ' ':
               if(x == 0 || escapeSpace)
                  outBuffer.append('\\');

               outBuffer.append(' ');
               break;
            case '\\':
               outBuffer.append('\\');
               outBuffer.append('\\');
               break;
            case '\t':
               outBuffer.append('\\');
               outBuffer.append('t');
               break;
            case '\n':
               outBuffer.append('\\');
               outBuffer.append('n');
               break;
            case '\r':
               outBuffer.append('\\');
               outBuffer.append('r');
               break;
            case '\f':
               outBuffer.append('\\');
               outBuffer.append('f');
               break;
            default:
               if(specialSaveChars.indexOf(aChar) != -1) {
                  outBuffer.append('\\');
               }

               outBuffer.append(aChar);
         }
      }

      return outBuffer.toString();
   }

   private static void writeln(BufferedWriter bw, String s) throws IOException {
      bw.write(s);
      bw.newLine();
   }

   /**
    * Verify whether a user defined property exists in default resouce bundle
    * file.
    *
    * @param propStrDefault a user defined property.
    * @return
    */
   private String isExisting(String propStrDefault) {
      Enumeration enumKeys = propDefault.keys();
      String transPropStr = Tool.replaceAll(propStrDefault, "_", " ");

      //correct faults in user prop file
      transPropStr = Tool.replaceAll(transPropStr, "\n", "\\n");
      transPropStr = Tool.replaceAll(transPropStr, "\t", "\\t");
      transPropStr = Tool.replaceAll(transPropStr, "\f", "\\f");
      transPropStr = Tool.replaceAll(transPropStr, "\r", "\\r");

      for(Enumeration e = enumKeys; e.hasMoreElements();) {
         String propID = (String) e.nextElement();
         String propVal = (String) propDefault.get(propID);

         if(propVal.equals(transPropStr)) {
            return propID;
         }
      }

      return null;
   }

   /**
    * Find the ids still not in your property.
    * Used in 7.0 resource bundle file.
    */
   public void findNotIn(boolean removeOrNot) throws IOException {
      if(propUser.size() == 0) {
         System.out.println("   No user defined property exists!");
         return;
      }

      Enumeration enumKeys = propDefault.keys();

      for(Enumeration e = enumKeys; e.hasMoreElements();) {
         String PropID = (String) e.nextElement();

         if(propUser.containsKey(PropID)) {
            if(removeOrNot){
               String PropVal = propUser.getProperty(PropID);
               propLocale.put(PropID, PropVal);
            }

            propUser.remove(PropID);
            propDefault.remove(PropID);
         }
      }

      if(removeOrNot && userPropf != null){
         FileOutputStream localeOut = new FileOutputStream(userPropf);
         store(localeOut, null, propLocale, false);
         localeOut.close();
      }

      outputResult();
   }

   void outputResult() throws IOException {
      String uselessNote = "useless ids, please make sure whether the " +
         "following IDs should be added in your resource bundle file.";

      //list the two hashtable to output file or sysout
      if(outputf == null) {
         PrintWriter out = new PrintWriter(System.out);

         if(propUser.size() != 0) {
            showList(out, uselessNote, propUser);
         }

         if(propDefault.size() != 0) {
            showList(out, "not contained ids", propDefault);
         }
      }
      else {
         FileOutputStream out = new FileOutputStream(outputf);

         if(propUser.size() != 0) {
            store(out, "============" + uselessNote + "============", propUser,
                  true);
         }

         if(propDefault.size() != 0) {
            store(out, "==========not contained ids========", propDefault,
                  false);
         }

         out.close();
      }
   }

   private File userPropf;
   private File combinePropf;
   private File outputf;
   private InetProperties propUser;
   private InetProperties propDefault;
   private InetProperties propLocale;
   private static final String specialSaveChars = "=: \t\r\n\f#!";
}
