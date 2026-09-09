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
package inetsoft.web.wiz.controller;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptEnvRepository;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Wraps StyleBI's Script Library asset (LibManager, AssetEntry.Type.SCRIPT) -- a reusable script
 * function created once and callable from any viewsheet's script by name, independent of any
 * particular sheet -- under wiz's own JWT-authenticated, CSRF-exempt /api/wiz namespace (see
 * WizServiceAuthenticationFilter / CSRFFilter#isWizApi). Same shape DatasourceMetaApiController
 * already established for another repository-level, session-less asset.
 *
 * <p>Talks to LibManager directly rather than proxying OpenScriptController/ScriptController/
 * AssetTreeController/RemoveAssetController -- StyleBI's own internal Composer-SPA REST paths
 * (/api/composer/*, /api/script/*), which are session-cookie + CSRF protected and not reachable by
 * a stateless bearer-token client. Reusing the real service directly, not the internal controller,
 * is the same rule every other wiz agent controller already follows.
 *
 * <p>A script function is addressed by its plain name throughout -- LibManager's own key -- rather
 * than by an AssetEntry identifier round-tripped through an asset-tree lookup, which is what the
 * internal controllers do because they also render a tree UI. This surface has no tree to render.
 */
@RestController
@RequestMapping("/api/wiz/v1/script-library")
public class ScriptLibraryController {
   public ScriptLibraryController(LibManagerProvider libManagerProvider,
                                  SecurityEngine securityEngine)
   {
      this.libManagerProvider = libManagerProvider;
      this.securityEngine = securityEngine;
   }

   public record ScriptLibraryFunction(String name, String comment) {}

   public record ScriptLibraryFunctionDetail(String name, String text, String comment) {}

   public record CreateScriptLibraryFunctionRequest(String name, String text, String comment) {}

   public record UpdateScriptLibraryFunctionRequest(String text, String comment) {}

   public record CheckScriptSyntaxResult(boolean ok, String message, Integer line, Integer column) {}

   public record CheckScriptSyntaxRequest(String script) {}

   @GetMapping
   public List<ScriptLibraryFunction> list(Principal principal) {
      LibManager lib = libManagerProvider.getManager(principal);
      List<ScriptLibraryFunction> out = new ArrayList<>();
      Enumeration<String> names = lib.getScripts();

      while(names.hasMoreElements()) {
         String name = names.nextElement();

         if(lib.isAuditScript(name) || !hasPermission(principal, name, ResourceAction.READ)) {
            continue;
         }

         out.add(new ScriptLibraryFunction(name, lib.getScriptComment(name)));
      }

      out.sort(Comparator.comparing(ScriptLibraryFunction::name));
      return out;
   }

   @GetMapping("/{name}")
   public ScriptLibraryFunctionDetail read(@PathVariable String name, Principal principal) {
      LibManager lib = libManagerProvider.getManager(principal);
      requireExists(lib, name);
      requirePermission(principal, name, ResourceAction.READ);
      return new ScriptLibraryFunctionDetail(name, lib.getScript(name), lib.getScriptComment(name));
   }

   @PostMapping
   public ScriptLibraryFunctionDetail create(@RequestBody CreateScriptLibraryFunctionRequest request,
                                             Principal principal) throws Exception
   {
      String name = request.name();

      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("create_script_library_function requires 'name'.");
      }

      LibManager lib = libManagerProvider.getManager(principal);

      if(lib.getScript(name) != null) {
         throw new IllegalArgumentException(
            "A script library function named '" + name + "' already exists. Use " +
            "update_script_library_function to change it, or pick a different name.");
      }

      requirePermission(principal, name, ResourceAction.WRITE);
      lib.setScript(name, request.text() == null ? "" : request.text());

      if(request.comment() != null && !request.comment().isBlank()) {
         lib.setScriptComment(name, request.comment());
      }

      lib.save();
      return new ScriptLibraryFunctionDetail(name, lib.getScript(name), lib.getScriptComment(name));
   }

   @PutMapping("/{name}")
   public ScriptLibraryFunctionDetail update(@PathVariable String name,
                                             @RequestBody UpdateScriptLibraryFunctionRequest request,
                                             Principal principal) throws Exception
   {
      LibManager lib = libManagerProvider.getManager(principal);
      requireExists(lib, name);
      requirePermission(principal, name, ResourceAction.WRITE);
      lib.setScript(name, request.text() == null ? "" : request.text());

      if(request.comment() != null) {
         lib.setScriptComment(name, request.comment());
      }

      lib.save();
      return new ScriptLibraryFunctionDetail(name, lib.getScript(name), lib.getScriptComment(name));
   }

   /**
    * {@code force=false} (the default) refuses a delete that would break another asset -- the
    * same dependency check {@code RemoveAssetController.checkScriptRemoveable} makes when the
    * human Composer's own delete dialog is not pre-confirmed. Skipping it (as the earlier
    * SPA-wrapping tool did by always sending {@code confirmed:true}) silently breaks whatever
    * referenced this function.
    */
   @DeleteMapping("/{name}")
   public void delete(@PathVariable String name,
                      @RequestParam(required = false, defaultValue = "false") boolean force,
                      Principal principal) throws Exception
   {
      LibManager lib = libManagerProvider.getManager(principal);
      requireExists(lib, name);
      requirePermission(principal, name, ResourceAction.DELETE);

      if(!force) {
         List<AssetObject> deps = DependencyTransformer.getDependencies(scriptEntry(name).toIdentifier());

         if(!deps.isEmpty()) {
            List<String> names = new ArrayList<>();

            for(AssetObject dep : deps) {
               names.add(dep instanceof AssetEntry entry ? entry.getDescription() : dep.toString());
            }

            throw new IllegalArgumentException(
               "Script library function '" + name + "' is used by: " + String.join(", ", names) +
               ". Deleting it would break those. Pass force=true to delete anyway.");
         }
      }

      lib.removeScript(name);
      lib.save();
   }

   @PostMapping("/check")
   public CheckScriptSyntaxResult checkSyntax(@RequestBody CheckScriptSyntaxRequest request) {
      ScriptEnv env = ScriptEnvRepository.getScriptEnv();

      try {
         env.checkFunction("script", request.script());
      }
      catch(Exception e) {
         int line = 0;
         int column = 0;
         Throwable cause = e;

         // unwrap to the GraalJS PolyglotException to recover source location, same as
         // OpenScriptController.checkScript.
         while(cause != null && !(cause instanceof PolyglotException)) {
            cause = cause.getCause();
         }

         if(cause instanceof PolyglotException polyglot && polyglot.getSourceLocation() != null) {
            SourceSection loc = polyglot.getSourceLocation();
            line = loc.getStartLine();
            column = loc.getStartColumn();
         }

         return new CheckScriptSyntaxResult(false, e.getMessage(), line, column);
      }

      return new CheckScriptSyntaxResult(true, null, null, null);
   }

   private void requireExists(LibManager lib, String name) {
      if(lib.getScript(name) == null) {
         throw new IllegalArgumentException(
            "No script library function named '" + name + "'. list_script_library reports what " +
            "exists.");
      }
   }

   /**
    * Matches {@code checkScriptRemoveable}'s own dependency-lookup entry (GLOBAL_SCOPE) -- the
    * scope a script's dependency records are actually keyed under, confirmed by reading that
    * method rather than assumed.
    */
   private AssetEntry scriptEntry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCRIPT, name, null);
   }

   private void requirePermission(Principal principal, String name, ResourceAction action) {
      if(!hasPermission(principal, name, action)) {
         throw new SecurityException(
            "No " + action + " permission on script library function '" + name + "'.");
      }
   }

   private boolean hasPermission(Principal principal, String name, ResourceAction action) {
      try {
         return securityEngine.checkPermission(principal, ResourceType.SCRIPT, name, action);
      }
      catch(Exception e) {
         return false;
      }
   }

   private final LibManagerProvider libManagerProvider;
   private final SecurityEngine securityEngine;
}
