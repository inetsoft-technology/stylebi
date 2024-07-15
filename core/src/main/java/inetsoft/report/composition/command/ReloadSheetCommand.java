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
package inetsoft.report.composition.command;

import inetsoft.report.composition.AssetCommand;
import inetsoft.util.Catalog;

/**
 * Message command.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class ReloadSheetCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public ReloadSheetCommand(String id) {
      Catalog catalog = Catalog.getCatalog();

      String msg = catalog.getString("common.expiredSheet");
      String previewMsg = catalog.getString("common.expiredPreviewSheet", id);
      put("msg", msg);
      put("previewMsg", previewMsg);
      put("id", id);
   }

}
