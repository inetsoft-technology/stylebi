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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ScriptFormattingScanner;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.vs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.TreeSet;

@Service
@ClusterProxy
public class ModernizeDialogService {
   public ModernizeDialogService(ViewsheetService viewsheetService,
                                 SaveViewsheetDialogService saveService)
   {
      this.viewsheetService = viewsheetService;
      this.saveService = saveService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ModernizeDialogModel getModel(@ClusterProxyKey String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      AssetEntry entry = rvs.getEntry();
      Catalog catalog = Catalog.getCatalog(principal);
      ModernizeDialogModel model = new ModernizeDialogModel();
      model.setKeepAvailable(VizModernizeUtil.hasUnmarked(vs));
      model.setNeverSaved(entry != null && entry.getScope() == AssetRepository.TEMPORARY_SCOPE);
      model.setParentId(parentId(entry));
      model.setEmbedsDashboards(embedsDashboards(vs));
      model.setScriptWarning(scriptWarning(scan(vs), catalog));

      if(entry != null) {
         String parentId = model.getParentId();

         try {
            model.setSuggestedName(ModernCopyNames.suggest(
               entry.getName(), name -> isTaken(runtimeId, name, parentId, principal), catalog));
         }
         catch(NameCheckFailedException e) {
            LOG.warn("Failed to check the dashboard name {}", e.getName(), e.getCause());
            model.setSuggestedName(
               catalog.getString("composer.vs.modernize.copyName", entry.getName()));
         }
      }

      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ModernCopyNameCheck checkName(@ClusterProxyKey String runtimeId,
                                        ModernCopyRequest request, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      String name = request.getName() == null ? "" : request.getName().trim();
      ModernCopyNameCheck check = new ModernCopyNameCheck();
      String invalid = ModernCopyNames.invalidMessage(name, Catalog.getCatalog(principal));

      if(invalid != null) {
         check.setInvalidName(invalid);
         return check;
      }

      SaveViewsheetDialogModelValidator validator =
         validate(runtimeId, name, parentId(rvs.getEntry()), principal);
      check.setExists(validator.alreadyExists() != null);
      check.setPermissionDenied(validator.permissionDenied());
      return check;
   }

   public static String scriptWarning(ScriptFormattingScanner.Report report, Catalog catalog) {
      if(report == null || report.isEmpty()) {
         return null;
      }

      int count = report.components().size();

      if(count == 0) {
         return catalog.getString("composer.vs.modernize.scripts.dashboardOnly");
      }

      String components = count == 1 ?
         catalog.getString("composer.vs.modernize.componentCount.one") :
         catalog.getString("composer.vs.modernize.componentCount", count);

      if(report.dashboardScript()) {
         return catalog.getString("composer.vs.modernize.scripts.withDashboard", components);
      }

      return catalog.getString("composer.vs.modernize.scripts", components);
   }

   private ScriptFormattingScanner.Report scan(Viewsheet vs) {
      try {
         return ScriptFormattingScanner.scan(vs);
      }
      catch(Exception e) {
         LOG.warn("Failed to scan the dashboard scripts", e);
         return new ScriptFormattingScanner.Report(new TreeSet<>(), false);
      }
   }

   private boolean embedsDashboards(Viewsheet vs) {
      for(Object assembly : vs.getAssemblies(false)) {
         if(assembly instanceof Viewsheet) {
            return true;
         }
      }

      return false;
   }

   private String parentId(AssetEntry entry) {
      AssetEntry parent = entry == null ? null : entry.getParent();

      if(parent == null) {
         return null;
      }

      return parent.getScope() == AssetRepository.TEMPORARY_SCOPE ?
         AssetEntry.createGlobalRoot().toIdentifier() : parent.toIdentifier();
   }

   private boolean isTaken(String runtimeId, String name, String parentId, Principal principal) {
      try {
         return validate(runtimeId, name, parentId, principal).alreadyExists() != null;
      }
      catch(Exception e) {
         throw new NameCheckFailedException(name, e);
      }
   }

   private SaveViewsheetDialogModelValidator validate(String runtimeId, String name,
                                                      String parentId, Principal principal)
      throws Exception
   {
      SaveViewsheetDialogModel model = new SaveViewsheetDialogModel();
      model.setName(name);
      model.setParentId(parentId);
      return saveService.validateSaveViewSheet(runtimeId, model, principal);
   }

   private static final class NameCheckFailedException extends RuntimeException {
      NameCheckFailedException(String name, Throwable cause) {
         super(cause);
         this.name = name;
      }

      String getName() {
         return name;
      }

      private final String name;
   }

   private final ViewsheetService viewsheetService;
   private final SaveViewsheetDialogService saveService;
   private static final Logger LOG = LoggerFactory.getLogger(ModernizeDialogService.class);
}
