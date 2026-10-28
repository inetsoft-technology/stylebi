/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.presentation;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.general.WebMapSettingsService;
import inetsoft.web.admin.presentation.*;
import inetsoft.web.admin.presentation.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.util.*;

/**
 * Bridges the 16-entry {@link PresentationSubModel} catalog to the real sub-services
 * {@code PresentationSettingsController} itself wraps -- never calls the controller (same "wrap the
 * service layer, not the EM controller" decision C.4 made for providers, 01-spec.md section 4a).
 *
 * <p>Each of the 16 real services has a slightly different {@code getModel}/{@code setModel} shape
 * (some take a {@link Principal}, some don't; {@code fontMapping}/{@code ai} take no scope boolean at
 * all) -- normalized here, once, into one uniform {@code (Principal, boolean) -> Object} /
 * {@code (Object, Principal, boolean) -> void} pair per sub-model, so the plan/apply services never
 * need to know which of the 16 shapes they are calling.
 */
@Component
public class PresentationSettingsAccess {
   @Autowired
   public PresentationSettingsAccess(
      PresentationFormatsSettingsService formats,
      PresentationDashboardSettingsService dashboard,
      PresentationToolbarSettingsService toolbar,
      LookAndFeelService lookAndFeel,
      WelcomePageService welcomePage,
      PresentationLoginBannerSettingsService loginBanner,
      PortalIntegrationViewSettingsService portalIntegration,
      PresentationPdfGenerationSettingsService pdfGeneration,
      ExportMenuSettingsService exportMenu,
      PresentationFontMappingSettingsService fontMapping,
      ShareSettingsService share,
      PresentationComposerMessageSettingsService composerMessage,
      PresentationTimeSettingsService time,
      PresentationDataSourceVisibilitySettingsService dataSourceVisibility,
      WebMapSettingsService webMap,
      AISettingsService ai)
   {
      adapters = new EnumMap<>(PresentationSubModel.class);

      adapters.put(PresentationSubModel.FORMATS, new Adapter(
         (principal, global) -> formats.getModel(global),
         (model, principal, global) ->
            formats.setModel((PresentationFormatsSettingsModel) model, global)));

      adapters.put(PresentationSubModel.DASHBOARD, new Adapter(
         (principal, global) -> dashboard.getModel(global),
         (model, principal, global) ->
            dashboard.setModel((PresentationDashboardSettingsModel) model, global)));

      adapters.put(PresentationSubModel.VIEWSHEET_TOOLBAR, new Adapter(
         (principal, global) -> toolbar.getViewsheetOptions(global),
         (model, principal, global) ->
            toolbar.setViewsheetOptions((PresentationViewsheetToolbarOptionsModel) model, global)));

      adapters.put(PresentationSubModel.LOOK_AND_FEEL, new Adapter(
         lookAndFeel::getModel,
         (model, principal, global) ->
            lookAndFeel.setModel((LookAndFeelSettingsModel) model, principal, global)));

      adapters.put(PresentationSubModel.WELCOME_PAGE, new Adapter(
         (principal, global) -> welcomePage.getModel(global),
         (model, principal, global) ->
            welcomePage.setModel((WelcomePageSettingsModel) model, global)));

      adapters.put(PresentationSubModel.LOGIN_BANNER, new Adapter(
         (principal, global) -> loginBanner.getModel(global),
         (model, principal, global) ->
            loginBanner.setModel((PresentationLoginBannerSettingsModel) model, global)));

      adapters.put(PresentationSubModel.PORTAL_INTEGRATION, new Adapter(
         portalIntegration::getModel,
         (model, principal, global) ->
            portalIntegration.setModel((PortalIntegrationSettingsModel) model, principal, global)));

      adapters.put(PresentationSubModel.PDF_GENERATION, new Adapter(
         (principal, global) -> pdfGeneration.getModel(global),
         (model, principal, global) ->
            pdfGeneration.setModel((PresentationPdfGenerationSettingsModel) model, global)));

      adapters.put(PresentationSubModel.EXPORT_MENU, new Adapter(
         (principal, global) -> exportMenu.getExportMenuSettings(global),
         (model, principal, global) ->
            exportMenu.setExportMenuSettings((PresentationExportMenuSettingsModel) model, global)));

      adapters.put(PresentationSubModel.FONT_MAPPING, new Adapter(
         (principal, global) -> fontMapping.getModel(),
         (model, principal, global) ->
            fontMapping.setModel((PresentationFontMappingSettingsModel) model)));

      adapters.put(PresentationSubModel.SHARE, new Adapter(
         (principal, global) -> share.getModel(global),
         (model, principal, global) ->
            share.setModel((PresentationShareSettingsModel) model, global)));

      adapters.put(PresentationSubModel.COMPOSER_MESSAGE, new Adapter(
         (principal, global) -> composerMessage.getModel(global),
         (model, principal, global) ->
            composerMessage.setModel((PresentationComposerMessageSettingsModel) model, global)));

      adapters.put(PresentationSubModel.TIME, new Adapter(
         (principal, global) -> time.getModel(global),
         (model, principal, global) ->
            time.setModel((PresentationTimeSettingsModel) model, global)));

      adapters.put(PresentationSubModel.DATA_SOURCE_VISIBILITY, new Adapter(
         (principal, global) -> dataSourceVisibility.getModel(global),
         (model, principal, global) -> dataSourceVisibility
            .setModel((PresentationDataSourceVisibilitySettingsModel) model, global)));

      adapters.put(PresentationSubModel.WEB_MAP, new Adapter(
         (principal, global) -> webMap.getModel(global),
         (model, principal, global) ->
            webMap.setModel((inetsoft.web.admin.general.model.WebMapSettingsModel) model, principal,
                            global)));

      adapters.put(PresentationSubModel.AI, new Adapter(
         (principal, global) -> global ? ai.getModel() : null,
         (model, principal, global) -> ai.setModel((PresentationAISettingsModel) model)));
   }

   /** Test seam: replaces the organization property store. A setter rather than a constructor
    * parameter because every constructor parameter of a bean under inetsoft.web.admin.ai must itself
    * be a resolvable bean (AdminAiBeanGraphTest). */
   void setOrgStore(OrgPropertyStore orgStore) {
      this.orgStore = orgStore;
   }

   /** Reads the current value of one sub-model, in whichever of the 16 real shapes it actually has. */
   public Object read(PresentationSubModel subModel, Principal principal, boolean global)
      throws Exception
   {
      return adapters.get(subModel).reader.read(principal, global);
   }

   /** Writes a fully-resolved (never partial) sub-model value -- callers must merge a partial
    * {@code spec} onto a fresh {@link #read} result themselves before calling this (the real
    * {@code setModel} methods all expect a complete object, none accept a patch).
    *
    * <p>At organization scope, also unpins (see {@link #unpinOrgKeys}) the org keys THIS write
    * created, in a {@code finally} so a write that throws after partially pinning leaves no pin
    * behind (removal is projection-checked, so it is harmless on a partial write).
    *
    * @return the organization keys this write created and that were kept because they carry the
    *         written change; empty at global scope. A caller that later undoes the write passes
    *         exactly this set to {@link #unpinOrgKeys}, so it never touches keys another write
    *         created. */
   public Set<String> write(PresentationSubModel subModel, Object model, Principal principal,
                            boolean global)
      throws Exception
   {
      Set<String> baseline = global ? Set.of() : new HashSet<>(orgStore.keys());
      Set<String> kept = Set.of();
      boolean written = false;

      try {
         adapters.get(subModel).writer.write(model, principal, global);
         written = true;
      }
      finally {
         try {
            kept = unpinOrgKeys(subModel, principal, global, createdSince(baseline, global));
         }
         catch(Exception e) {
            if(written) {
               throw e;
            }
         }
      }

      return kept;
   }

   private Set<String> createdSince(Set<String> baseline, boolean global) {
      if(global) {
         return Set.of();
      }

      Set<String> created = new TreeSet<>(orgStore.keys());
      created.removeAll(baseline);
      return created;
   }

   /**
    * Bug #79434: every value-scope {@code setModel} writes ALL of its properties with
    * {@code orgScope=true}, so a spec naming one field still creates an organization override for
    * every other property of the sub-model, which then stops inheriting later global changes (and a
    * rollback re-writes the pre-image through the same {@code setModel}, re-creating them).
    *
    * <p>Removes each of {@code candidates} (and only those) that still exists, but only when
    * removing it leaves the sub-model's read projection unchanged -- a key that actually carries the
    * written change is kept (restored if its removal moves the projection). Does nothing at global
    * scope. Callers pass the keys one write created, never a changeset-wide set, so unpinning one
    * entry cannot delete a key another entry created.
    *
    * <p>A candidate the spec itself named whose value merely equals the inherited value is removed
    * too. The organization reads the same value now, but it then FOLLOWS later global changes
    * instead of staying at the value the admin set, so this is not a no-op over time.
    *
    * @return the candidates that were kept
    */
   public Set<String> unpinOrgKeys(PresentationSubModel subModel, Principal principal,
                                   boolean global, Set<String> candidates) throws Exception
   {
      if(global || candidates == null || candidates.isEmpty()) {
         return Set.of();
      }

      Set<String> existing = orgStore.keys();
      Set<String> created = new TreeSet<>(candidates);
      created.retainAll(existing);

      if(created.isEmpty()) {
         return Set.of();
      }

      String written = projection(subModel, principal, global);
      boolean removedAny = false;
      Set<String> kept = new TreeSet<>();

      for(String key : created) {
         String value = orgStore.get(key);
         orgStore.remove(key);
         boolean unchanged;

         try {
            unchanged = value != null && written.equals(projection(subModel, principal, global));
         }
         catch(Exception e) {
            unchanged = false;
         }

         if(unchanged) {
            removedAny = true;
         }
         else {
            if(value != null) {
               orgStore.put(key, value);
            }

            kept.add(key);
         }
      }

      if(removedAny) {
         orgStore.save();
      }

      return kept;
   }

   private String projection(PresentationSubModel subModel, Principal principal, boolean global)
      throws Exception
   {
      return PresentationChangePlanService.projectedValue(
         subModel, PresentationJson.toNode(read(subModel, principal, global)));
   }

   /** Access to the current organization's property overrides, a seam so tests need no running
    * {@code PropertiesEngine}. */
   interface OrgPropertyStore {
      /** Full names of the current organization's override keys. */
      Set<String> keys();

      String get(String key);

      void put(String key, String value);

      void remove(String key);

      void save();
   }

   private static final class SreeEnvOrgPropertyStore implements OrgPropertyStore {
      @Override
      public Set<String> keys() {
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         Set<String> result = new HashSet<>();

         if(orgId == null) {
            return result;
         }

         String prefix = PropertiesEngine.getOrgPropertyPrefix(orgId);

         for(String name : SreeEnv.getProperties().stringPropertyNames()) {
            if(name.startsWith(prefix)) {
               result.add(name);
            }
         }

         return result;
      }

      @Override
      public String get(String key) {
         return SreeEnv.getProperties().getProperty(key);
      }

      @Override
      public void put(String key, String value) {
         SreeEnv.setProperty(key, value);
      }

      @Override
      public void remove(String key) {
         SreeEnv.remove(key);
      }

      @Override
      public void save() {
         try {
            SreeEnv.save();
         }
         catch(IOException e) {
            throw new UncheckedIOException(e);
         }
      }
   }

   @FunctionalInterface
   private interface Reader {
      Object read(Principal principal, boolean global) throws Exception;
   }

   @FunctionalInterface
   private interface Writer {
      void write(Object model, Principal principal, boolean global) throws Exception;
   }

   private record Adapter(Reader reader, Writer writer) {
   }

   private final EnumMap<PresentationSubModel, Adapter> adapters;
   private OrgPropertyStore orgStore = new SreeEnvOrgPropertyStore();
}
