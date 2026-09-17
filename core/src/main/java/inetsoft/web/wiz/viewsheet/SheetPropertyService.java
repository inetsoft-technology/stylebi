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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.composer.model.vs.ConvertToWorksheetResponseModel;
import inetsoft.web.composer.model.vs.ViewsheetParametersDialogModel;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import inetsoft.web.composer.vs.dialog.ViewsheetSettingsService;
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
      PropertyAliases.TypeAliases entry = PropertyAliases.forType(SHEET_TYPE);
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

      PropertyAliases.TypeAliases entry = PropertyAliases.forType(SHEET_TYPE);
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
         resolved.put(key, PropertyAliases.resolveForWrite(SHEET_TYPE, key));
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(runtimeId, user);
         requireKnownParameterNames(model, resolved, patch);

         // Keep PropertyPath.set's returned root. Every alias in the vocabulary currently nests
         // under a pane, which absorbs an Immutables wither's rebuild, so today this reassignment
         // changes nothing. It guards the NEXT alias that targets a bare top-level field of this
         // model: without it, the wither's new instance would be discarded and the write would
         // vanish with no error and no compile failure.
         for(Map.Entry<String, String> entry : resolved.entrySet()) {
            model = (ViewsheetPropertyDialogModel)
               PropertyPath.set(model, entry.getValue(), patch.get(entry.getKey()));
         }

         dialogService.setViewsheetInfo(runtimeId, model, user, dispatcher, linkUri, null);
      });
   }

   /**
    * {@code enabledParameters}/{@code disabledParameters} are not a free-text list — they
    * partition the viewsheet's OWN query-declared variables (a {@code $(varName)} used somewhere
    * in the underlying query/condition), the same closed set
    * {@link ViewsheetSettingsService#getViewsheetParameterInfo} discovers live and the Composer's
    * own "Customize" dialog only ever offers by toggling/reordering — a human using that dialog
    * can never type an unrecognized name in the first place.
    *
    * <p>{@link ViewsheetSettingsService#setViewsheetParameterInfo} itself does not enforce this:
    * it stores whatever the model contains into {@code ViewsheetInfo} unconditionally. An
    * unrecognized name is not rejected there — it is silently orphaned, because the NEXT read
    * re-derives these two arrays from the live query variables and filters it back out. Confirmed
    * live (2026-09-16): setting {@code enabledParameters:["Region"]} against a viewsheet whose
    * query declares no variables at all returned {@code ok:true}, and the very next
    * {@code get_viewsheet_properties} read back an empty array — success reported, nothing
    * changed, exactly the defect this plugin family exists to catch (Redmine #76739 follow-up).
    * Refused here, at the one place both the alias and the raw-dotted-path escape hatch funnel
    * through, rather than trusting every caller to list-then-set in that order.
    *
    * <p>{@code model} is the pre-patch read already fetched by the caller (its own
    * {@code enabledParameters}/{@code disabledParameters} are the correctly-filtered current
    * values), so the closed set below costs no extra round trip.
    */
   private static void requireKnownParameterNames(
      ViewsheetPropertyDialogModel model, Map<String, String> resolved, Map<String, Object> patch)
   {
      boolean touchesParameters = resolved.values().stream().anyMatch(PARAMETER_LIST_PATHS::contains);

      if(!touchesParameters) {
         return;
      }

      ViewsheetParametersDialogModel current = model.vsOptionsPane().getViewsheetParametersDialogModel();
      Set<String> known = new LinkedHashSet<>();
      known.addAll(namesOf(current.getEnabledParameters()));
      known.addAll(namesOf(current.getDisabledParameters()));

      for(Map.Entry<String, String> entry : resolved.entrySet()) {
         if(!PARAMETER_LIST_PATHS.contains(entry.getValue())) {
            continue;
         }

         for(String name : namesOf(patch.get(entry.getKey()))) {
            if(!known.contains(name)) {
               throw new IllegalArgumentException(
                  "'" + name + "' is not a parameter of this viewsheet's query. " +
                  (known.isEmpty()
                     ? "This viewsheet's query declares no variables at all, so there is " +
                       "nothing to enable or disable."
                     : "Known parameters: " + known + ".") +
                  " A name outside this set would be stored but silently dropped on the next " +
                  "read -- the query's own declared variables (a $(varName) somewhere in its " +
                  "condition/SQL), not an assembly name, are what these two lists partition.");
            }
         }
      }
   }

   /** Coerces a patch value (a JSON array deserializes as a {@code List<?>}) or an already-read
    *  model's {@code String[]} into plain names, uniformly. */
   private static List<String> namesOf(Object value) {
      if(value == null) {
         return List.of();
      }

      if(value instanceof String[] array) {
         return Arrays.asList(array);
      }

      if(value instanceof Collection<?> collection) {
         List<String> names = new ArrayList<>(collection.size());

         for(Object item : collection) {
            names.add(String.valueOf(item));
         }

         return names;
      }

      throw new IllegalArgumentException(
         "'enabledParameters'/'disabledParameters' expect a JSON array of parameter names; '" +
         value + "' is not one.");
   }

   private static final Set<String> PARAMETER_LIST_PATHS = Set.of(
      "vsOptionsPane.viewsheetParametersDialogModel.enabledParameters",
      "vsOptionsPane.viewsheetParametersDialogModel.disabledParameters");

   /**
    * Rebinds or clears the viewsheet's own Data Source — the Options dialog's "Select"/"Clear"
    * buttons ({@code vsOptionsPane.selectDataSourceDialogModel.dataSource}), the one field of
    * that dialog {@link #set} cannot reach through its alias vocabulary (Redmine #76739): every
    * alias there is a scalar leaf {@link PropertyPath#coerce} can build from JSON alone, while
    * this field needs a resolved, permission-checked {@link AssetEntry} instead. Resolving that
    * entry from a caller-supplied path/type is
    * {@code ViewsheetAssemblyAgentController.resolveDataSourceEntry}'s job — the same lookup
    * {@code attach_base_worksheet}/{@code create_viewsheet} already share — so this method takes
    * the entry already resolved.
    *
    * <p>{@code entry} null clears the binding, matching the dialog's "Clear" button.
    *
    * <p>Goes through the exact same {@link ViewsheetPropertyDialogService#setViewsheetInfo} apply
    * path {@link #set} uses for its ordinary aliases, so the dependency-refresh/sandbox-reset/
    * {@code VSDependencyChangedCommand} side effects that method's data-source branch performs
    * happen exactly as they would from a human editing the dialog — nothing here re-implements
    * that logic. Unlike {@code attach_base_worksheet}, this always replaces whatever base is
    * currently set rather than refusing when one already exists, matching what the dialog itself
    * allows.
    */
   public void setDataSource(String sessionToken, Principal user, AssetEntry entry, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ViewsheetPropertyDialogModel model = dialogService.getViewsheetInfo(runtimeId, user);
         model.vsOptionsPane().getSelectDataSourceDialogModel().setDataSource(entry);
         dialogService.setViewsheetInfo(runtimeId, model, user, dispatcher, linkUri, null);
      });
   }

   /**
    * "Convert Source to Worksheet" (Redmine #76739) — the Options dialog's own conversion
    * action for a viewsheet whose base is a Logical Model, offered there via a link shown only
    * for that data source type. Saves a brand-new, editable worksheet asset built from the
    * model's query and returns its path, going through the exact same
    * {@link ViewsheetPropertyDialogService#convertLogicModelToWorksheet} the Composer UI itself
    * calls (see {@code viewsheet-options-pane.component.ts}'s {@code doConvert()}) — nothing
    * here re-implements that logic.
    *
    * <p>Like the dialog's own action, this only <b>saves</b> the new worksheet asset — it does
    * not rebind the viewsheet to it. The dialog itself defers that until the whole Options
    * dialog is subsequently saved; here, call {@link #setDataSource} with the returned path to
    * actually attach it as the viewsheet's base.
    *
    * @return {@code path}, the new worksheet's path (usable directly as
    *         {@code set_viewsheet_data_source}'s {@code path} with {@code type:"worksheet"}),
    *         and {@code hasMaterializedViews} — whether this viewsheet has materialized views
    *         that switching its base away from the logical model would invalidate. The dialog
    *         shows a confirmation for that; this tool surfaces it as data instead of blocking,
    *         since there is no one here to click through a confirmation.
    * @throws Exception if the viewsheet's base is not a logical model, or the viewsheet has not
    *                    been saved yet — the same refusals
    *                    {@code convertLogicModelToWorksheet} itself throws.
    */
   public Map<String, Object> convertDataSourceToWorksheet(String sessionToken, Principal user)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ConvertToWorksheetResponseModel response =
         dialogService.convertLogicModelToWorksheet(rvs.getID(), user);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("path", response.getModel().getDataSource().getPath());
      result.put("hasMaterializedViews", response.isHasMvs());
      return result;
   }

   /**
    * The vocabulary key for the sheet's own properties.
    *
    * <p>Deliberately not the string "viewsheet": that is already an assembly type name, derived
    * from {@code Viewsheet implements VSAssembly}. This constant is the one place the distinction
    * has to be stated, so it defers to {@link PropertyAliases#SHEET} rather than repeating it.
    */
   private static final String SHEET_TYPE = PropertyAliases.SHEET;

   private final ViewsheetSessionService sessions;
   private final ViewsheetPropertyDialogService dialogService;
}
