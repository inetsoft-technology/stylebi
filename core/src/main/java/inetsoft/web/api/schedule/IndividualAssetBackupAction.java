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

package inetsoft.web.api.schedule;


import inetsoft.util.dep.XAsset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Validated
@Schema(description = "An action that backs up a specified asset file to specified location.")
public class IndividualAssetBackupAction extends ScheduleAction {

   /**
    * Creates a new instance of {@code IndividualAssetBackupAction}.
    */
   public IndividualAssetBackupAction() {
      setActionType(ActionType.BACKUP);
   }

   public IndividualAssetBackupAction(inetsoft.sree.schedule.IndividualAssetBackupAction action) {
      this();

      if(action == null) {
         return;
      }

      List<XAsset> xAssets = action.getAssets();

      if(xAssets != null) {
         assetIdentifiers = xAssets.stream()
            .filter(asset -> asset != null)
            .map(asset -> asset.toIdentifier())
            .collect(Collectors.toList());
      }

      if(action.getServerPath() != null) {
         pathInfo = new ServerPathInfo(action.getServerPath());
      }

      encoding = action.isEncoding();
   }

   @NotNull
   @Schema(description = "The identifiers list of the assets which need to be backuped by this action.",
      example = "inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples/Census^host-org\n" +
      "inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^Examples/Analysis^host-org\n" +
      "inetsoft.util.dep.XDataSourceAsset^Examples/Orders\n" +
      "inetsoft.util.dep.XLogicalModelAsset^Examples/Orders^Order Model\n" +
      "inetsoft.util.dep.ScheduleTaskAsset^admin~;~host-org:Task1^IdentityID{name='admin', orgID='host-org'}\n")
   public List<String> getAssetIdentifiers() {
      return assetIdentifiers;
   }

   public void setAssetIdentifiers(List<String> assetIdentifiers) {
      this.assetIdentifiers = assetIdentifiers;
   }

   @Schema(description = "The server path to store the backup files.")
   public ServerPathInfo getPathInfo() {
      return pathInfo;
   }

   public void setPathInfo(ServerPathInfo pathInfo) {
      this.pathInfo = pathInfo;
   }

   public boolean isEncoding() {
      return encoding;
   }

   @Schema(description = "If to byte encode the backup file content.")
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   private List<String> assetIdentifiers = new ArrayList<>();
   private ServerPathInfo pathInfo;
   private boolean encoding = true;
}
