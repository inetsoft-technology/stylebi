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
package inetsoft.web.viewsheet.service;

import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.model.VSObjectModel;

import java.util.List;
import java.util.Objects;

// Combine commands for optimization
public class CommandDebouncer {
   public boolean debounce(List<CommandDispatcher.PendingCommand> pending,
                           CommandDispatcher.PendingCommand cmd)
   {
      boolean wait = false;
      boolean keep = true;
      ViewsheetCommand vcmd = cmd.getCommand();

      // add command overrides previous add/refresh command
      if(vcmd instanceof AddVSObjectCommand) {
         String name = ((AddVSObjectCommand) vcmd).getName();
         VSObjectModel model = ((AddVSObjectCommand) vcmd).getModel();
         wait = true;

         for(int i = pending.size() - 1; i >= 0; i--) {
            CommandDispatcher.PendingCommand cmd0 = pending.get(i);
            ViewsheetCommand vcmd0 = cmd0.getCommand();

            if(vcmd0 instanceof RefreshVSObjectCommand &&
               Objects.equals(((RefreshVSObjectCommand) vcmd0).getInfo().getAbsoluteName(), name))
            {
               pending.remove(i);
            }
            else if(vcmd0 instanceof AddVSObjectCommand &&
                    Objects.equals(((AddVSObjectCommand) vcmd0).getName(), name))
            {
               ((AddVSObjectCommand) vcmd0).setModel(model);
               keep = false;
            }
         }
      }
      // refresh command overrides previous add/refresh command
      else if(vcmd instanceof RefreshVSObjectCommand) {
         VSObjectModel model = ((RefreshVSObjectCommand) vcmd).getInfo();
         String name = model.getAbsoluteName();
         String shared = ((RefreshVSObjectCommand) vcmd).getShared();
         wait = true;

         for(int i = pending.size() - 1; i >= 0; i--) {
            CommandDispatcher.PendingCommand cmd0 = pending.get(i);
            ViewsheetCommand vcmd0 = cmd0.getCommand();

            if(vcmd0 instanceof RefreshVSObjectCommand) {
               RefreshVSObjectCommand rcmd = (RefreshVSObjectCommand) vcmd0;

               if(Objects.equals(rcmd.getInfo().getAbsoluteName(), name) &&
                  Objects.equals(rcmd.getShared(), shared))
               {
                  rcmd.setInfo(((RefreshVSObjectCommand) vcmd).getInfo());
                  keep = false;
               }
            }
            else if(vcmd0 instanceof AddVSObjectCommand) {
               AddVSObjectCommand rcmd = (AddVSObjectCommand) vcmd0;

               if(Objects.equals(rcmd.getName(), name)) {
                  rcmd.setModel(model);
                  keep = false;
               }
            }
         }
      }
      // if removing an object, keeping the last one should suffice.
      else if(vcmd instanceof RemoveVSObjectCommand) {
         String name = ((RemoveVSObjectCommand) vcmd).getName();

         for(int i = pending.size() - 1; i >= 0; i--) {
            CommandDispatcher.PendingCommand cmd0 = pending.get(i);
            ViewsheetCommand vcmd0 = cmd0.getCommand();

            if(vcmd0 instanceof RemoveVSObjectCommand) {
               if(Objects.equals(name, ((RemoveVSObjectCommand) vcmd0).getName())) {
                  pending.remove(i);
               }
            }
         }
      }
      else if(vcmd instanceof InitializingCommand) {
         Class<?> vClass = vcmd.getClass();
         keep = pending.stream().noneMatch(c -> c.getCommand().getClass() == vClass);
      }
      else if(vcmd instanceof CollapsibleCommand) {
         wait = true;
         pending.removeIf(c -> Objects.equals(cmd.getAssembly(), c.getAssembly()) &&
            ((CollapsibleCommand) vcmd).collapse(c.getCommand()));
      }

      if(keep) {
         pending.add(cmd);
      }

      return wait;
   }
}
