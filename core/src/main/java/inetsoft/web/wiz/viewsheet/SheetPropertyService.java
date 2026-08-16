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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Reads and writes the viewsheet's <b>own</b> properties — the settings behind the Composer's
 * Viewsheet Property dialog — through {@link ViewsheetPropertyDialogService}.
 *
 * <p>A sibling to {@link AssemblyPropertyService} rather than an extension of it.
 * {@code AssemblyPropertyService} reflects one signature onto every dialog service —
 * {@code (runtimeId, objectId, principal)} / {@code (runtimeId, objectId, model, linkUri,
 * principal, dispatcher)} — because every assembly dialog service actually shares it.
 * {@code ViewsheetPropertyDialogService} does not: {@code getViewsheetInfo(runtimeId, principal)}
 * and {@code setViewsheetInfo(runtimeId, value, principal, dispatcher, linkUri, refLayoutName)}
 * take no assembly name, order their arguments differently, and add {@code refLayoutName} for
 * the device-layout path this tool does not touch. There is also exactly one target, so the
 * reflective per-type dispatch {@code AssemblyPropertyService} needs has nothing to dispatch
 * over here — both methods are called directly.
 *
 * <p>{@code refLayoutName} is always passed as {@code null}: it exists so the dialog can report
 * back which device layout tab moved when the caller was mid-edit on one, and this tool never
 * touches the layout/{@code screensPane} path (see {@link PropertyAliases}).
 */
@Service
public class SheetPropertyService {
   @Autowired
   public SheetPropertyService(ViewsheetSessionService sessions,
                                ViewsheetPropertyDialogService dialogService)
   {
      this.sessions = sessions;
      this.dialogService = dialogService;
   }

   /** The viewsheet's property vocabulary, with current values. */
   public Map<String, Object> list(String sessionToken, Principal user) throws Exception {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(rvs.getID(), user);
      PropertyAliases.TypeAliases entry = PropertyAliases.forType(VIEWSHEET);
      List<Map<String, Object>> properties = new ArrayList<>();

      for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
         Map<String, Object> property = new LinkedHashMap<>();
         property.put("name", alias.getKey());
         property.put("path", alias.getValue());
         property.put("type",
                      PropertyPath.typeOf(entry.modelClass(), alias.getValue()).getSimpleName());
         property.put("value", PropertyPath.get(model, alias.getValue()));
         properties.add(property);
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("properties", properties);
      return out;
   }

   /** Current values, by alias. {@code raw} returns the whole dialog model instead. */
   public Object get(String sessionToken, Principal user, boolean raw) throws Exception {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(rvs.getID(), user);

      if(raw) {
         return model;
      }

      PropertyAliases.TypeAliases entry = PropertyAliases.forType(VIEWSHEET);
      Map<String, Object> values = new LinkedHashMap<>();

      for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
         values.put(alias.getKey(), PropertyPath.get(model, alias.getValue()));
      }

      return values;
   }

   /** Applies a patch of aliases and/or raw paths. One {@code mutate}, so one checkpoint. */
   public void set(String sessionToken, Principal user, Map<String, Object> patch, String linkUri)
      throws Exception
   {
      if(patch == null || patch.isEmpty()) {
         throw new IllegalArgumentException(
            "set_viewsheet_properties needs at least one property to set.");
      }

      // Resolved whole before anything is written, exactly as the assembly path is: a bad or
      // refused key anywhere in the patch must not leave the properties before it applied,
      // which would be a partial edit the caller has no way to detect from the error alone.
      Map<String, String> resolved = new LinkedHashMap<>();

      for(String key : patch.keySet()) {
         resolved.put(key, PropertyAliases.resolveForWrite(VIEWSHEET, key));
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(runtimeId, user);

         // PropertyPath.set returns the root to keep using: width/height/preview live directly
         // on this Immutables model with no nested pane to absorb a wither's rebuild, so the
         // reassignment is load-bearing, not defensive boilerplate.
         for(Map.Entry<String, String> entry : resolved.entrySet()) {
            model = (ViewsheetPropertyDialogModel)
               PropertyPath.set(model, entry.getValue(), patch.get(entry.getKey()));
         }

         dialogService.setViewsheetInfo(runtimeId, model, user, dispatcher, linkUri, null);
      });
   }

   private static final String VIEWSHEET = PropertyAliases.SHEET;

   private final ViewsheetSessionService sessions;
   private final ViewsheetPropertyDialogService dialogService;
}
