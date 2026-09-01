/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.WorksheetOptionPaneModel;
import inetsoft.web.composer.model.ws.WorksheetPropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class WorksheetPropertyDialogService extends WorksheetControllerService {

   public WorksheetPropertyDialogService(ViewsheetService viewsheetService, DataSourceRegistry dataSourceRegistry)
   {
      super(viewsheetService, dataSourceRegistry);
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WorksheetPropertyDialogModel getWorksheetInfo(@ClusterProxyKey String runtimeId, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      WorksheetPropertyDialogModel result = new WorksheetPropertyDialogModel();
      WorksheetOptionPaneModel worksheetOptionPaneModel = new WorksheetOptionPaneModel(
         rws);
      result.setWorksheetOptionPaneModel(worksheetOptionPaneModel);
      return result;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setWorksheetInfo(@ClusterProxyKey String runtimeId, WorksheetPropertyDialogModel model,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(
         Tool.byteDecode(runtimeId), null);
      boolean success = process(rws, model, principal, commandDispatcher);

      if(success) {
         SetWorksheetInfoCommand command = SetWorksheetInfoCommand.builder()
            .label(rws.getEntry().toView())
            .build();
         commandDispatcher.sendCommand(command);
      }

      return null;
   }

   /**
    * Process save worksheet event.
    *
    * @return true if property was updated, false otherwise.
    */
   /**
    * The banned-character set {@code worksheet-option-pane.component.ts}'s
    * {@code assetEntryBannedCharacters} validator rejects for an alias.
    */
   private static final java.util.regex.Pattern ALIAS_BANNED_CHARS =
      java.util.regex.Pattern.compile("[\\\\/\"<%^~]");

   /**
    * Mirrors {@code FormValidators.assetNameStartWithCharDigit}'s
    * {@code /^([a-zA-Z0-9À-ɏ一-龥])/} in
    * {@code web/projects/shared/util/form-validators.ts}: ASCII letters/digits, the
    * Latin-1 Supplement + Latin Extended-A block, and CJK Unified Ideographs only.
    * Deliberately narrower than {@link Character#isLetterOrDigit(char)}, which is
    * Unicode-aware and would also accept Cyrillic, Greek, Arabic, Hangul, Devanagari,
    * etc. -- none of which the Composer's own dialog allows as a first character.
    */
   private static final java.util.regex.Pattern ALIAS_START_CHAR =
      java.util.regex.Pattern.compile("[a-zA-Z0-9À-ɏ一-龥]");

   /**
    * L2-Group9: {@code assetEntryBannedCharacters}/{@code assetNameStartWithCharDigit} are
    * Angular-only today -- not even this dialog's own backend re-validates before writing the
    * alias, so a non-UI caller (a raw socket payload, or the wiz agent path's
    * {@code WorksheetAgentController#setProperties}) can set an alias the Composer's own
    * Worksheet Properties dialog would refuse. Banned characters are asset-path/XML-significant
    * (a corrupted alias can affect anything reading {@code entry.getAlias()} elsewhere in the
    * product), so this is a data-integrity guard, not a cosmetic one. Public so both this
    * dialog and the wiz agent path gain it from one change instead of two drifting copies.
    */
   public static void requireValidAlias(String alias) throws MessageException {
      if(alias == null || alias.isEmpty()) {
         return;
      }

      if(ALIAS_BANNED_CHARS.matcher(alias).find()) {
         throw new MessageException(
            "Alias '" + alias + "' contains characters the Composer's own Worksheet " +
            "Properties dialog does not allow: \\ / \" < % ^ ~");
      }

      if(!ALIAS_START_CHAR.matcher(alias.substring(0, 1)).matches()) {
         throw new MessageException(
            "Alias '" + alias + "' must start with a letter or digit.");
      }
   }

   private boolean process(
      RuntimeWorksheet rws, WorksheetPropertyDialogModel model,
      Principal user, CommandDispatcher commandDispatcher) throws Exception
   {
      boolean reportSource = model.getWorksheetOptionPaneModel().getDataSource();
      requireValidAlias(model.getWorksheetOptionPaneModel().getAlias());
      WorksheetInfo winfo = new WorksheetInfo();
      winfo.setAlias(model.getWorksheetOptionPaneModel().getAlias());
      winfo.setDescription(model.getWorksheetOptionPaneModel().getDescription());
      AssetEntry entry = rws.getEntry();
      Worksheet ws = rws.getWorksheet();

      entry.setReportDataSource(reportSource);

      boolean refresh = ws.setWorksheetInfo(winfo);

      if(refresh) {
         rws.getAssetQuerySandbox().resetTableLens();
         WorksheetEventUtil.refreshWorksheet(
            rws, super.getWorksheetEngine(), commandDispatcher, user);
      }

      WorksheetService wengine = super.getWorksheetEngine();
      AssetRepository engine = wengine.getAssetRepository();
      String alias = model.getWorksheetOptionPaneModel().getAlias();
      String desc0 = model.getWorksheetOptionPaneModel().getDescription();

      if(engine.containsEntry(entry)) {
         entry.setAlias(alias != null ? alias : "");
         entry.setProperty("description", desc0);
         String desc = entry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += wengine.localizeAssetEntry(entry.getPath(), user,
                                            true, entry, entry
                                               .getScope() == AssetRepository.USER_SCOPE);
         entry.setProperty("_description_", desc);
         entry.setProperty("localStr",
                           desc.substring(desc.lastIndexOf("/") + 1));
         rws.setEntry(entry);
         rws.setEditable(true);

         return true;
      }

      return false;
   }
}
