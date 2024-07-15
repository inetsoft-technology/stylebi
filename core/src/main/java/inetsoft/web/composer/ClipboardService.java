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
package inetsoft.web.composer;

import inetsoft.uql.asset.Assembly;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton used for storing clipboard items.
 *
 * @since 12.3
 */
@Service
public class ClipboardService {
   /**
    * Copy or cut objects.
    *
    * @param assemblies the objects.
    */
   public void copy(List<Assembly> assemblies) {
      this.assemblies = new ArrayList<>();
      this.assemblies.addAll(assemblies);
   }

   /**
    * Paste/retrieve object from clipboard.
    *
    * @return the cloned assemblies.
    */
   public List<Assembly> paste() {
      return assemblies;
   }

   private List<Assembly> assemblies;
   public static final String CLIPBOARD = "__private_" +
      ClipboardService.class.getName() + ".clipboard";
}
