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
package inetsoft.analytic.web.adhoc;

import inetsoft.report.LibManager;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Class to manage Adhoc Query.
 * @author InetSoft Technology Corp.
 * @since  6.0
 */
public class AdHocQueryHandler {
   /**
    * Get the list of functions that can
    * be used in an expression
    */
   public static ItemMap getScriptFunctions(boolean viewsheet) {
      return getScriptFunctions(viewsheet, false);
   }
   /**
    * Get the list of functions that can
    * be used in an expression
    */
   public static ItemMap getScriptFunctions(boolean viewsheet, boolean task) {
      String[] files = null;

      if(task) {
         files = new String[] {"/inetsoft/report/gui/jsFunctions.tree"};
      }
      else if(viewsheet) {
         files = new String[] {"/inetsoft/report/gui/vsJsFunctions.tree",
                               "/inetsoft/report/gui/jsFunctions.tree"};
      }
      else {
         files = new String[] {"/inetsoft/report/gui/jsFunctions.tree"};
      }

      ItemMap funcs = getFunctionsMap(files);
      List<String> list = getUserDefinedScriptFunctions();

      if(list.size() > 0) {
         LibManager mgr = LibManager.getManager();
         String ufolder = Catalog.getCatalog().getString("User Defined");
         StringBuilder ufuncs = new StringBuilder();

         for(int j = 0; j < list.size(); j++) {
            String func = list.get(j) + "()";
            func = mgr.getUserSignature(func) + ";" + func;
            ufuncs.append(func);

            if(j != list.size() - 1) {
               ufuncs.append("^");
            }
         }

         funcs.putItem(ufolder, ufuncs);
      }

      return funcs;
   }

   public static ItemMap getScriptFunctions() {
      String[] files = null;

      files = new String[] {"/inetsoft/report/gui/jsFunctions.tree"};

      ItemMap funcs = getFunctionsMap(files);
      List<String> listAll = getUserDefinedScriptFunctions();
      Collections.sort(listAll, String.CASE_INSENSITIVE_ORDER);

      if(listAll.size() > 0) {
         LibManager mgr = LibManager.getManager();
         String ufolder = Catalog.getCatalog().getString("User Defined");
         StringBuilder ufuncs = new StringBuilder();
         List<String> list = new ArrayList<>();
         int i = 0;

         for(int j = 0; j < listAll.size(); j++) {
            if(!mgr.isAuditScript(listAll.get(j))) {
               list.add(listAll.get(j));
            }
            else {
               continue;
            }

            String func = list.get(i++) + "()";
            func = mgr.getUserSignature(func) + ";" + func;
            ufuncs.append(func);

            if(j != listAll.size() - 1) {
               ufuncs.append("^");
            }
         }

         funcs.putItem(ufolder, ufuncs);
      }

      return funcs;
   }

   /**
    * Get the list of functions that can be used in an expression
    */
   public static ItemMap getExcelScriptFunctions() {
      return getFunctionsMap("/inetsoft/report/gui/excelJsFunctions.tree");
   }

   private static ItemMap getFunctionsMap(String... files) {
      ItemMap funcs = new ItemMap();
      funcs.setName("Functions");

      for(int i = 0; i < files.length; i++) {
         InputStream input =
            AdHocQueryHandler.class.getResourceAsStream(files[i]);

         try(BufferedReader rdr = new BufferedReader(new InputStreamReader(input))) {
            String line;
            String currBranch = "";
            StringBuilder funcVals = new StringBuilder();

            while((line = rdr.readLine()) != null) {
               if(line == null || line.length() == 0 ||
                  // ignore report only functions for formula columns
                  line.indexOf("ENV_REPORT") > 0)
               {
                  continue;
               }

               if(line.charAt(0) != '\t') {
                  if(!currBranch.equals("")) {
                     // truncate off the last '^' sign
                     String fvals = funcVals.toString();

                     if(fvals.lastIndexOf("^") >= 0) {
                        fvals = fvals.substring(0, fvals.length() - 1);
                     }

                     funcs.putItem(currBranch, fvals);
                  }

                  line = line.trim();

                  line = Tool.replace(line, ";", "");
                  line = Tool.replaceAll(line, "\t", ";");

                  currBranch = line;

                  funcVals.delete(0, funcVals.length());
               }
               else {
                  line = line.trim();

                  line = Tool.replace(line, ";", "");
                  line = Tool.replaceAll(line, "\t", ";");

                  funcVals.append(line);
                  funcVals.append("^");
               }
            }

            if(!currBranch.equals("")) {
               // truncate off the last '^' sign
               String fvals = funcVals.toString();

               if(fvals.lastIndexOf("^") >= 0) {
                  fvals = fvals.substring(0, fvals.length() - 1);
               }

               funcs.putItem(currBranch, fvals);
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to load JavaScript functions", ex);
         }
      }

      return funcs;
   }

   public static List<String> getUserDefinedScriptFunctions() {
      List<String> list = new ArrayList<>();
      LibManager mgr = LibManager.getManager();
      Enumeration<?> names = mgr.getScripts();

      while(names.hasMoreElements()) {
         String fname = (String) names.nextElement();

         try {
            if(!SecurityEngine.getSecurity().checkPermission(ThreadContext.getContextPrincipal(),
               ResourceType.SCRIPT, fname, ResourceAction.READ))
            {
               continue;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to check script permission", exc);
         }

         String source = mgr.getScript(fname);

         if(source != null && source.length() > 0) {
            list.add(fname);
         }
      }

      return list;
   }

   /**
    * Get the list of operators that can
    * be used in an expression
    */
   public static ItemMap getScriptOperators() {
      ItemMap ops = new ItemMap();
      ops.setName("Operators");
      InputStream input = AdHocQueryHandler.class.getResourceAsStream
         ("/inetsoft/report/gui/jsOperators.tree");

      try(BufferedReader rdr = new BufferedReader(new InputStreamReader(input))) {
         String line;
         String currBranch = "";
         StringBuilder opVals = new StringBuilder();

         while((line = rdr.readLine()) != null) {
            if(line == null || line.length() == 0 ||
               opVals.toString() == null)
            {
               continue;
            }

            if(line.charAt(0) != '\t') {
               if(!currBranch.equals("")) {
                  // truncate off the last '$' sign
                  String ovals = opVals.toString();

                  if(ovals.lastIndexOf("$") >= 0) {
                    ovals = ovals.substring(0, ovals.length() - 1);
                  }

                  ops.putItem(currBranch, ovals);
               }

               currBranch = line.trim();
               opVals.delete(0, opVals.length());
            }
            else {
               line = line.trim();

               StringBuilder sb = new StringBuilder();
               int idx = line.indexOf("\t");

               sb.append(line.substring(0, idx));
               sb.append(";");

               line = line.substring(idx + 1);

               idx = line.lastIndexOf("\t");

               if(idx >= line.length() || idx < 0) { // try a space
                  idx = line.lastIndexOf(" ");
               }

               if(line.equals("in") ||
                  (currBranch.equals("Misc") && idx < 0))
               {
                  sb.append(" ;");
                  sb.append(line);
               }
               else {
                  sb.append(line, 0, idx);
                  sb.append(";");
                  sb.append(line.substring(idx + 1));
               }

               opVals.append(sb.toString());
               opVals.append("$");
            }
         }

         if(!currBranch.equals("")) {
            // truncate off the last '$' sign
            String ovals = opVals.toString();

            if(ovals.lastIndexOf("$") >= 0) {
              ovals = ovals.substring(0, ovals.length() - 1);
            }

            ops.putItem(currBranch, ovals);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to load JavaScript operations", ex);
      }

      return ops;
   }

   /**
    * mark function need dot.
    */
   public static final String DOT_FLAG = "DOT";

   private static final Logger LOG =
      LoggerFactory.getLogger(AdHocQueryHandler.class);
}
