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
package inetsoft.web.composer.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.composition.SheetLibraryService;
import inetsoft.report.LibManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.GroupedThread;
import inetsoft.util.log.LogContext;
import inetsoft.web.composer.model.script.ScriptModel;
import inetsoft.web.composer.model.script.ScriptTreePaneModel;
import inetsoft.web.composer.script.service.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ScriptController {
   @Autowired
   public ScriptController(SheetLibraryService sheetLibraryService,
                           ScriptService scriptService) {
      this.sheetLibraryService = sheetLibraryService;
      this.scriptService = scriptService;
   }

   @RequestMapping(value = "/api/script/new", method = RequestMethod.GET)
   public ScriptModel newScript(Principal principal) {
      AssetEntry entry = sheetLibraryService.getTemporaryAssetEntry(principal, AssetEntry.Type.SCRIPT);
      ScriptModel script = new ScriptModel();
      script.setLabel(entry.getName());
      script.setId(entry.toIdentifier());
      script.setType("script");
      return script;
   }

   @RequestMapping(value = "api/script/open", method = RequestMethod.GET)
   public ScriptModel openScript(@RequestParam("id") String id) {
      AssetEntry entry = AssetEntry.createAssetEntry(id);
      String name = entry.getName();
      LibManager lib = LibManager.getManager();
      String function = lib.getScript(name);
      ScriptModel script = new ScriptModel();
      script.setLabel(name);
      script.setId(id);
      script.setType("script");
      script.setText(function);
      script.setComment(lib.getScriptComment(name));
      return script;
   }

   @RequestMapping(value = "/api/script/scriptTree", method = RequestMethod.GET)
   public ScriptTreePaneModel getScriptTree(Principal principal) {
      Thread thread = Thread.currentThread();
      String vsContext = null;
      String assemblyContext = null;

      if(thread instanceof GroupedThread) {
         GroupedThread groupedThread = (GroupedThread) thread;
         vsContext = groupedThread.getRecord(LogContext.DASHBOARD);
         assemblyContext = groupedThread.getRecord(LogContext.ASSEMBLY);
      }

      try {
         ScriptTreePaneModel.Builder builder = ScriptTreePaneModel.builder();
         builder.functionTree(scriptService.getFunctionTree(principal));
         return builder.build();
      }
      finally {
         if(thread instanceof GroupedThread) {
            GroupedThread groupedThread = (GroupedThread) thread;
            groupedThread.addRecord(LogContext.DASHBOARD, vsContext);
            groupedThread.addRecord(LogContext.ASSEMBLY, assemblyContext);
         }
      }
   }

   @RequestMapping(value = "/api/script/scriptDefinition", method = RequestMethod.GET)
   public ObjectNode getScriptDefinition() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = scriptService.createScriptDefinitions(mapper);
      return root;
   }


   private final SheetLibraryService sheetLibraryService;
   private final ScriptService scriptService;
}