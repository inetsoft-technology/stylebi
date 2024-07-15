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
package inetsoft.util.dep;

import inetsoft.report.LibManager;
import inetsoft.report.lib.logical.LogicalLibraryEntry;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.FunctionIterator;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.asset.internal.ScriptIterator.Token;
import inetsoft.util.Tool;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Script asset represents a script type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class ScriptAsset extends AbstractXAsset {
   /**
    * Script type XAsset.
    */
   public static final String SCRIPT = "SCRIPT";

   /**
    * Constructor.
    */
   public ScriptAsset() {
      super();
   }

   /**
    * Constructor.
    * @param script the specified script function name.
    */
   public ScriptAsset(String script) {
      this();
      this.script = script;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> deps = new ArrayList<>();
      final LibManager manager = LibManager.getManager();

      if(manager.findScriptName(script) != null) {
         String check = manager.getScript(script);

         if(check == null || check.equals("")) {
            return new XAssetDependency[0];
         }

         // get the function names in the check string
         final Vector<String> functions = new Vector<>();
         FunctionIterator iterator = new FunctionIterator(check);

         ScriptListener listener = new ScriptListener() {
            @Override
            public void nextElement(Token token, Token pref, Token cref) {
               if(pref != null &&
                  ("showReplet".equalsIgnoreCase(pref.val) ||
                   "runQuery".equalsIgnoreCase(pref.val))
                  && token.val.length() > 0)
               {
                  functions.add(token.val + "^_^" + pref.val);
               }

               if(token.isRef() && !Tool.equals(token.val, script) &&
                  !functions.contains(token.val) &&
                  manager.findScriptName(token.val) != null)
               {
                  functions.add(token.val);
               }
            }
         };

         iterator.addScriptListener(listener);
         iterator.iterate();
         iterator.removeScriptListener(listener);

         for(String function : functions) {
            if(function.toLowerCase().indexOf("^_^runquery") > 0) {
               String qname = function.substring(0,
                  function.toLowerCase().indexOf("^_^runquery"));

               // process worksheet
               if(qname.startsWith("ws:")) {
                  String[] arr = Tool.split(qname, ':');
                  String scope = "";
                  String path;

                  if(arr.length >= 3) {
                     scope = arr[1];
                     path = arr[2];
                  }
                  else if(arr.length == 2) {
                     path = arr[1];
                  }
                  // invalid worksheet name
                  else {
                     continue;
                  }

                  AssetEntry entry;

                  if(scope.length() == 0) {
                     entry = new AssetEntry(AssetRepository.REPORT_SCOPE,
                                            AssetEntry.Type.WORKSHEET, path, null);
                  }
                  else if(scope.equalsIgnoreCase("global")) {
                     entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.WORKSHEET, path, null);
                  }
                  else {
                     entry = new AssetEntry(AssetRepository.USER_SCOPE,
                                            AssetEntry.Type.WORKSHEET, path, IdentityID.getIdentityIDFromKey(scope));
                  }

                  String desc = generateDescription(
                     catalog.getString("common.xasset.script", script),
                     catalog.getString("common.xasset.script.worksheet",
                     getEntryDescription(entry)));

                  if(scope.length() == 0) {
                     processLocalXAsset(new WorksheetAsset(entry), desc, deps);
                  }
                  else {
                     deps.add(new XAssetDependency(new WorksheetAsset(entry),
                        this, XAssetDependency.SCRIPT_WORKSHEET, desc));
                  }
               }
               // process normal query
               else {
                  String desc = generateDescription(
                     catalog.getString("common.xasset.script", script),
                     catalog.getString("common.xasset.script.Query", qname));
                  deps.add(new XAssetDependency(new XQueryAsset(qname), this,
                     XAssetDependency.SCRIPT_QUERY, desc));
               }
            }
            else {
               String desc = generateDescription(
                  catalog.getString("common.xasset.script", script),
                  catalog.getString("common.xasset.script", function));
               deps.add(new XAssetDependency(
                  new ScriptAsset(function), this,
                  XAssetDependency.SCRIPT_SCRIPT, desc));
            }
         }
      }

      return deps.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return script;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return SCRIPT;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return null;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      script = identifier;
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      script = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + script;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      if(script == null) {
         return;
      }

      LibManager manager = LibManager.getManager();
      boolean overwriting = config != null && config.isOverwriting();

      if(manager.findScriptName(script) != null && !overwriting) {
         return;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      StringBuilder buf = new StringBuilder();
      String line;

      while((line = reader.readLine()) != null && !line.equals(COMMENT) && !line.equals(FILE_INFO)) {
         buf.append(line).append("\n");
      }

      manager.setScript(script, buf.toString());
      Properties properties = new Properties();

      if(line != null && line.equals(COMMENT)) {
         buf = new StringBuilder();

         while((line = reader.readLine()) != null && !line.equals(FILE_INFO)) {
            buf.append(line).append("\n");
         }

         buf.substring(0, buf.length() - 2);
         properties.put("comment", buf.toString());
      }

      if(line != null && line.equals(FILE_INFO)) {
         properties.load(reader);
      }

      manager.setScriptCommentProperties(script, properties, true);
      input.close();
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      LibManager manager = LibManager.getManager();
      String body = manager.getScript(script);

      if(body == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
                                          replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);
      PrintWriter writer = new PrintWriter(out);
      writer.println(body);
      LogicalLibraryEntry logicalLibraryEntry = manager.getLogicalLibraryEntry(script);
      String scriptComment = logicalLibraryEntry.comment();

      if(!Tool.isEmptyString(scriptComment) && !Tool.isEmptyString(Encode.forHtml(scriptComment))) {
         writer.println(COMMENT);
         writer.println(Encode.forHtml(scriptComment));
      }

      Properties properties = new Properties();

      if(logicalLibraryEntry.created() != 0) {
         properties.put("created", logicalLibraryEntry.created() + "");
      }

      if(logicalLibraryEntry.modified() != 0) {
         properties.put("modified", logicalLibraryEntry.modified() + "");
      }

      if(logicalLibraryEntry.createdBy() != null) {
         properties.put("createdBy", logicalLibraryEntry.createdBy());
      }

      if(logicalLibraryEntry.modifiedBy() != null) {
         properties.put("modifiedBy", logicalLibraryEntry.modifiedBy());
      }

      if(properties.size() > 0) {
         writer.println(FILE_INFO);
         properties.store(writer, null);
      }

      writer.flush();
      return true;
   }

   public LogicalLibraryEntry getLogicalLibraryEntry() {
      return LibManager.getManager().getLogicalLibraryEntry(script);
   }

   @Override
   public boolean exists() {
      return LibManager.getManager().findScriptName(script) != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      LogicalLibraryEntry logicalLibraryEntry = getLogicalLibraryEntry();
      return logicalLibraryEntry == null ? 0 : logicalLibraryEntry.modified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.SCRIPT, script);
   }

   private String script;
   private static final String COMMENT = "__COMMENT__";
   private static final String FILE_INFO="__FILEINFO__";
   private static final Logger LOG =
      LoggerFactory.getLogger(ScriptAsset.class);
}
