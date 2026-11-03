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
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.PlanHashInputs;
import inetsoft.web.admin.general.WebMapSettingsService;
import inetsoft.web.admin.presentation.*;
import inetsoft.web.admin.presentation.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.util.*;
import java.util.function.BooleanSupplier;

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

      // Bug #80087: organization-scope reset = the same per-sub-model removal Enterprise Manager's
      // Reset button runs (resetSettings(false)); fontMapping and ai are global-only and have none.
      resetters = new EnumMap<>(PresentationSubModel.class);
      resetters.put(PresentationSubModel.FORMATS, p -> formats.resetSettings(false));
      resetters.put(PresentationSubModel.DASHBOARD, p -> dashboard.resetSettings(false));
      resetters.put(PresentationSubModel.VIEWSHEET_TOOLBAR, p -> toolbar.resetSettings(false));
      resetters.put(PresentationSubModel.LOOK_AND_FEEL, p -> lookAndFeel.resetSettings(p, false));
      resetters.put(PresentationSubModel.WELCOME_PAGE, p -> welcomePage.resetSettings(false));
      resetters.put(PresentationSubModel.LOGIN_BANNER, p -> loginBanner.resetSettings(false));
      resetters.put(PresentationSubModel.PORTAL_INTEGRATION,
                    p -> portalIntegration.resetSettings(false));
      resetters.put(PresentationSubModel.PDF_GENERATION, p -> pdfGeneration.resetSettings(false));
      resetters.put(PresentationSubModel.EXPORT_MENU, p -> exportMenu.resetSettings(false));
      resetters.put(PresentationSubModel.SHARE, p -> share.resetSettings(false));
      resetters.put(PresentationSubModel.COMPOSER_MESSAGE,
                    p -> composerMessage.resetSettings(false));
      resetters.put(PresentationSubModel.TIME, p -> time.resetSettings(false));
      resetters.put(PresentationSubModel.DATA_SOURCE_VISIBILITY,
                    p -> dataSourceVisibility.resetSettings(false));
      resetters.put(PresentationSubModel.WEB_MAP, p -> webMap.resetSettings(p, false));
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

   /** Test seam for whether the current organization has its own welcome-page/login-banner entry. */
   void setWelcomeEntryProbe(BooleanSupplier probe) {
      this.welcomeEntryProbe = probe;
   }

   private static boolean orgWelcomeEntryExists() {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      return orgId != null && PortalThemesManager.getManager().getWelcomePage(orgId) != null;
   }

   /**
    * Bug #80087: removes the calling organization's own override of {@code subModel}, so reads fall
    * through to the global value again. Runs the sub-model's own {@code resetSettings(false)} (the
    * same removal Enterprise Manager's Reset performs), then saves explicitly, because
    * {@code ExportMenuSettingsService.resetSettings} does not.
    *
    * <p>The organization's property overrides are snapshotted right before the reset and diffed right
    * after it, so the returned {@link ResetOutcome} can restore exactly what the reset removed or
    * rewrote and delete what it created, whatever keys those are (never a hard-coded key list, and
    * never {@code write(currentModel)}, which would re-pin every key of the sub-model). The toolbar
    * reset re-creates the {@code Viewsheet Toolbar Hidden} org key as {@code false}; it is removed
    * again here so the organization genuinely inherits.
    *
    * <p>If the reset throws, the keys are restored from the snapshot before the exception is
    * rethrown; a restore that itself fails throws {@link ResetStateUnknownException}.
    */
   public ResetOutcome resetOrg(PresentationSubModel subModel, Principal principal) throws Exception {
      Resetter resetter = resetters.get(subModel);

      if(resetter == null) {
         throw new IllegalArgumentException(
            subModel.key() + " has no organization-scoped layer to reset");
      }

      Map<String, String> before = snapshot();

      try {
         resetter.reset(principal);

         if(subModel == PresentationSubModel.VIEWSHEET_TOOLBAR) {
            for(String key : keysFor(PresentationSubModel.VIEWSHEET_TOOLBAR, orgStore.keys())) {
               orgStore.remove(key);
            }
         }

         orgStore.save();
      }
      catch(Exception e) {
         try {
            restore(diff(before, snapshot()));
         }
         catch(Exception restoreFailure) {
            ResetStateUnknownException unknown = new ResetStateUnknownException(
               "reset of " + subModel.key() + " failed (" + e.getMessage() + ") and the " +
               "organization keys could not be restored (" + restoreFailure.getMessage() + ")");
            unknown.addSuppressed(e);
            throw unknown;
         }

         throw e;
      }

      return diff(before, snapshot());
   }

   /**
    * Checks a finished {@link #resetOrg}: the reset must only remove keys (a created or rewritten key
    * means the service re-pinned something), none of the sub-model's own org keys may remain, and
    * the shared welcome/banner entry must be gone for {@code welcomePage}/{@code loginBanner}.
    * Projection equality cannot do this -- it is blind to the toolbar's hidden flag and masks the
    * secret keys.
    *
    * @return the problem, or {@code null} when the reset verified
    */
   public String checkReset(PresentationSubModel subModel, ResetOutcome outcome) {
      List<String> problems = new ArrayList<>();

      if(!outcome.added().isEmpty()) {
         problems.add("the reset created organization keys " + outcome.added());
      }

      if(!outcome.rewritten().isEmpty()) {
         problems.add("the reset rewrote organization keys " + outcome.rewritten() +
                      " instead of removing them");
      }

      Set<String> remaining = keysFor(subModel, orgStore.keys());

      if(!remaining.isEmpty()) {
         problems.add("organization keys still present after the reset: " + remaining);
      }

      if(isWelcomePair(subModel) && welcomeEntryProbe.getAsBoolean()) {
         problems.add("the organization's welcome page/login banner entry still exists");
      }

      return problems.isEmpty() ? null : String.join("; ", problems);
   }

   /**
    * Puts back what {@link #resetOrg} removed or rewrote and deletes what it created, touching only
    * the diffed keys so a concurrent edit to another key is never clobbered, then verifies the keys
    * read back as they were.
    *
    * @return the problem, or {@code null} when the keys are restored
    */
   public String undoReset(ResetOutcome outcome) {
      restore(outcome);
      Set<String> wrong = new TreeSet<>();

      for(Map.Entry<String, String> e : outcome.restore().entrySet()) {
         if(!Objects.equals(e.getValue(), orgStore.get(e.getKey()))) {
            wrong.add(e.getKey());
         }
      }

      for(String key : outcome.added()) {
         if(orgStore.get(key) != null) {
            wrong.add(key);
         }
      }

      return wrong.isEmpty() ? null : "organization keys not restored: " + wrong;
   }

   /** Names (never values) of the organization keys the reset of {@code subModel} will remove,
    * among those the organization currently has. */
   public List<String> resetKeysPresent(PresentationSubModel subModel) {
      return new ArrayList<>(keysFor(subModel, orgStore.keys()));
   }

   /** A digest of every organization override (names and values) plus whether the organization has
    * its own welcome entry, folded into a reset plan's hash so a change between preview and apply is
    * a conflict. A digest, so secret values never appear in a plan. */
   public String orgStateDigest() {
      StringBuilder state = new StringBuilder();

      for(Map.Entry<String, String> e : new TreeMap<>(snapshot()).entrySet()) {
         state.append(e.getKey()).append('=').append(e.getValue()).append('\n');
      }

      state.append("welcomeEntry=").append(welcomeEntryProbe.getAsBoolean());
      return PlanHashInputs.digest("presentation.orgState.", state.toString());
   }

   static boolean isWelcomePair(PresentationSubModel subModel) {
      return subModel == PresentationSubModel.WELCOME_PAGE ||
         subModel == PresentationSubModel.LOGIN_BANNER;
   }

   private Map<String, String> snapshot() {
      Map<String, String> result = new HashMap<>();

      for(String key : orgStore.keys()) {
         String value = orgStore.get(key);

         if(value != null) {
            result.put(key, value);
         }
      }

      return result;
   }

   private static ResetOutcome diff(Map<String, String> before, Map<String, String> after) {
      Map<String, String> restore = new TreeMap<>();
      Set<String> added = new TreeSet<>();
      Set<String> rewritten = new TreeSet<>();

      for(Map.Entry<String, String> e : before.entrySet()) {
         String now = after.get(e.getKey());

         if(now == null) {
            restore.put(e.getKey(), e.getValue());
         }
         else if(!now.equals(e.getValue())) {
            restore.put(e.getKey(), e.getValue());
            rewritten.add(e.getKey());
         }
      }

      for(String key : after.keySet()) {
         if(!before.containsKey(key)) {
            added.add(key);
         }
      }

      return new ResetOutcome(restore, added, rewritten);
   }

   private void restore(ResetOutcome outcome) {
      if(outcome.restore().isEmpty() && outcome.added().isEmpty()) {
         return;
      }

      for(Map.Entry<String, String> e : outcome.restore().entrySet()) {
         orgStore.put(e.getKey(), e.getValue());
      }

      for(String key : outcome.added()) {
         orgStore.remove(key);
      }

      orgStore.save();
   }

   /** The organization keys among {@code keys} that belong to {@code subModel}'s reset. */
   private static Set<String> keysFor(PresentationSubModel subModel, Set<String> keys) {
      List<String> names = RESET_KEY_NAMES.getOrDefault(subModel, List.of());
      Set<String> result = new TreeSet<>();

      for(String key : keys) {
         String lower = key.toLowerCase(Locale.ROOT);

         for(String name : names) {
            if(lower.endsWith("." + name.toLowerCase(Locale.ROOT))) {
               result.add(key);
            }
         }
      }

      return result;
   }

   /** The property names each sub-model's {@code resetSettings(false)} removes (used to disclose and
    * verify the removal; the rollback never relies on it). The toolbar's per-element names are
    * dynamic and omitted. */
   private static final Map<PresentationSubModel, List<String>> RESET_KEY_NAMES = Map.ofEntries(
      Map.entry(PresentationSubModel.FORMATS,
                List.of("format.date", "format.time", "format.date.time")),
      Map.entry(PresentationSubModel.DASHBOARD,
                List.of("dashboard.enabled", "dashboard.tabs.top", "drill.tabs.top")),
      Map.entry(PresentationSubModel.VIEWSHEET_TOOLBAR, List.of("Viewsheet Toolbar Hidden")),
      Map.entry(PresentationSubModel.LOOK_AND_FEEL, List.of("repository.tree.sort")),
      Map.entry(PresentationSubModel.PORTAL_INTEGRATION,
                List.of("portal.customLoadingText", "portal.home.link", "em.home.link")),
      Map.entry(PresentationSubModel.PDF_GENERATION,
                List.of("pdf.compress.text", "pdf.compress.image", "pdf.output.ascii",
                        "pdf.map.symbols", "pdf.embed.cmap", "pdf.embed.font", "font.truetype.path",
                        "font.afm.path", "pdf.open.bookmark", "pdf.open.thumbnail",
                        "pdf.output.attachment", "pdf.generate.links")),
      Map.entry(PresentationSubModel.EXPORT_MENU, List.of("vsexport.menu.options")),
      Map.entry(PresentationSubModel.SHARE,
                List.of("share.email.enabled", "share.facebook.enabled", "share.googlechat.enabled",
                        "share.googlechat.url", "share.linkedin.enabled", "share.slack.enabled",
                        "share.slack.url", "share.twitter.enabled", "share.link.enabled",
                        "share.opengraph.sitename", "share.opengraph.title",
                        "share.opengraph.description", "share.opengraph.image")),
      Map.entry(PresentationSubModel.COMPOSER_MESSAGE,
                List.of("composer.vs.create.messsage", "composer.vs.edit.messsage",
                        "composer.ws.create.messsage", "composer.ws.edit.messsage")),
      Map.entry(PresentationSubModel.TIME, List.of("week.start", "schedule.time.12hours")),
      Map.entry(PresentationSubModel.DATA_SOURCE_VISIBILITY,
                List.of("visible.datasource.types", "hidden.datasource.types")),
      Map.entry(PresentationSubModel.WEB_MAP,
                List.of("webmap.service", "webmap.default", "mapbox.user", "mapbox.token",
                        "mapbox.style", "google.maps.key")));

   /** What {@link #resetOrg} changed in the organization's property overrides: {@code restore} holds
    * the old value of every key it removed or rewrote, {@code added} the keys it created,
    * {@code rewritten} the keys it overwrote instead of removing. Holds raw values (secrets
    * included) in memory only; {@link #toString} deliberately reveals none of them. */
   public record ResetOutcome(Map<String, String> restore, Set<String> added,
                              Set<String> rewritten)
   {
      public Set<String> removed() {
         Set<String> removed = new TreeSet<>(restore.keySet());
         removed.removeAll(rewritten);
         return removed;
      }

      @Override
      public String toString() {
         return "ResetOutcome[removed=" + removed() + ", added=" + added + ", rewritten=" +
            rewritten + "]";
      }
   }

   /** A reset failed and the organization keys it may have touched could not be put back. */
   public static class ResetStateUnknownException extends RuntimeException {
      public ResetStateUnknownException(String message) {
         super(message);
      }
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

   @FunctionalInterface
   private interface Resetter {
      void reset(Principal principal) throws Exception;
   }

   private record Adapter(Reader reader, Writer writer) {
   }

   private final EnumMap<PresentationSubModel, Adapter> adapters;
   private final EnumMap<PresentationSubModel, Resetter> resetters;
   private BooleanSupplier welcomeEntryProbe = PresentationSettingsAccess::orgWelcomeEntryExists;
   private OrgPropertyStore orgStore = new SreeEnvOrgPropertyStore();
}
