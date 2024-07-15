/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.ClipboardService;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.event.CopyHighlightEvent;
import inetsoft.web.composer.vs.objects.event.PasteHighlightEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.UpdateHighlightPasteCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint for viewsheet clipboard events.
 */
@Controller
public class ClipboardController {
   /**
    * Creates a new instance of <tt>ClipboardController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ClipboardController(RuntimeViewsheetRef runtimeViewsheetRef,
                              PlaceholderService placeholderService,
                              VSObjectTreeService vsObjectTreeService,
                              ViewsheetService viewsheetService,
                              VSAssemblyInfoHandler assemblyHandler,
                              VSObjectPropertyService vsObjectPropertyService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.viewsheetService = viewsheetService;
      this.assemblyHandler = assemblyHandler;
      this.vsObjectPropertyService = vsObjectPropertyService;
   }

   /**
    * Copy or cut composer vs object.
    *
    * @param event     the event parameters.
    * @param principal a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/objects/copy")
   public void copyObject(@Payload CopyVSObjectsEvent event,
                          SimpMessageHeaderAccessor headerAccessor, Principal principal,
                          CommandDispatcher dispatcher,
                          @LinkUri String linkUri) throws Exception
   {
      copyOrCut(event, headerAccessor, principal, dispatcher, linkUri);
   }

   @Undoable
   @MessageMapping("composer/viewsheet/objects/cut")
   public void cutObject(@Payload CopyVSObjectsEvent event,
                          SimpMessageHeaderAccessor headerAccessor, Principal principal,
                          CommandDispatcher dispatcher,
                          @LinkUri String linkUri) throws Exception
   {
      copyOrCut(event, headerAccessor, principal, dispatcher, linkUri);
   }

   private void copyOrCut(CopyVSObjectsEvent event,
                          SimpMessageHeaderAccessor headerAccessor, Principal principal,
                          CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final Set<Assembly> oldAssemblies = new HashSet<>();

      for(String assemblyName: event.getObjects()) {
         final Assembly assembly = viewsheet.getAssembly(assemblyName);

         if(assembly instanceof VSAssembly) {
            final VSAssembly vsAssembly = (VSAssembly) assembly;
            oldAssemblies.add(vsAssembly);

            if(vsAssembly instanceof ContainerVSAssembly) {
               final ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
               final String[] names = container.getAbsoluteAssemblies();
               addAllDescendants(names, viewsheet, oldAssemblies);
            }
         }
      }

      final List<Assembly> clonedAssemblies = oldAssemblies.stream()
         .map((assembly) -> assembly.clone())
         .map(Assembly.class::cast)
         .collect(Collectors.toList());

      if(event.isCut()) {
         VSAssembly[] vsAssemblies = oldAssemblies.stream()
            .filter(a -> a instanceof VSAssembly)
            .toArray(VSAssembly[]::new);

         placeholderService.removeVSAssemblies(rvs, linkUri, dispatcher, false, true, true,
                                               vsAssemblies);

         if(oldAssemblies.stream().anyMatch(a -> a instanceof TableVSAssembly)) {
            assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         }
      }
      else {
         for(Assembly clone : clonedAssemblies) {
            if(clone instanceof ChartVSAssembly) {
               ((ChartVSAssembly) clone).getChartInfo().setBrushSelection(null);
            }
         }
      }

      final VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      final PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
      Map<String, Object> attributes = headerAccessor.getSessionAttributes();
      ClipboardService service = (ClipboardService) attributes.get(ClipboardService.CLIPBOARD);
      service.copy(clonedAssemblies);
   }

   /**
    * Copy or cut composer vs object.
    *
    * @param principal a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/paste/{x}/{y}")
   public void pasteObject(@DestinationVariable("x") int x,
                           @DestinationVariable("y") int y,
                           Principal principal, CommandDispatcher dispatcher,
                           SimpMessageHeaderAccessor headerAccessor,
                           @LinkUri String linkUri)
      throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final ViewsheetSandbox vbox = rvs.getViewsheetSandbox();

      final Map<String, Object> attributes = headerAccessor.getSessionAttributes();
      final ClipboardService service =
         (ClipboardService) attributes.get(ClipboardService.CLIPBOARD);

      if(service == null) {
         return;
      }

      vbox.lockWrite();

      try {
         final List<Assembly> oldassemblies = service.paste();

         if(oldassemblies == null) {
            return;
         }

         boolean self = oldassemblies.stream().filter(a -> a instanceof Viewsheet)
            .anyMatch(a -> Objects.equals(((Viewsheet) a).getEntry(), rvs.getEntry()));

         if(self) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.selfUseForbidden"));
         }

         final List<Assembly> assemblies = new ArrayList<>();
         oldassemblies.forEach(assembly -> assemblies.add((Assembly) assembly.clone()));

         final Point upperLeft = getUpperLeftPosition(assemblies);
         final int dx = x - upperLeft.x;
         final int dy = y - upperLeft.y;

         // process container before children
         assemblies.sort((a1, a2) -> {
            if(a1 instanceof VSAssembly && a2 instanceof VSAssembly) {
               if(isChild(a1, a2)) {
                  return 1;
               }

               if(isChild(a2, a1)) {
                  return -1;
               }

               return ((VSAssembly) a1).getZIndex() - ((VSAssembly) a2).getZIndex();
            }

            return 0;
         });

         DualHashBidiMap<String, String> namemap = new DualHashBidiMap<>();

         // add all assemblies before performing other execution. otherwise if an
         // assembly depends on another pasted assembly, there will be execution error
         for(int i = 0; i < assemblies.size(); i++) {
            if(assemblies.get(i) instanceof VSAssembly) {
               VSAssembly vsAssembly = (VSAssembly) assemblies.get(i);
               String name = vsAssembly.getName();

               copyImage(viewsheet, vsAssembly);

               if(viewsheet.getAssembly(name) != null) {
                  String oname = name;
                  final int assemblyType = vsAssembly.getAssemblyType();

                  name = AssetUtil.getNextName(viewsheet, assemblyType);
                  vsAssembly.getVSAssemblyInfo().setName(name);
                  fixScript(vsAssembly, oname, name);
                  namemap.put(oname, name);
               }

               viewsheet.addAssembly((VSAssembly) assemblies.get(i), true, true);
            }
         }

         rvs.getViewsheetSandbox().resetRuntime();

         for(int i = 0; i < assemblies.size(); i++) {
            final Assembly assembly = assemblies.get(i);

            if(assembly instanceof VSAssembly) {
               Assembly containerInvolved = null;
               final VSAssembly vsAssembly = (VSAssembly) assembly;

               if(vsAssembly instanceof ImageVSAssembly) {
                  String img = ((ImageVSAssembly) vsAssembly).getImage();

                  if(img != null && img.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
                     String name = img.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length());
                     byte[] buf = vsAssembly.getViewsheet().getUploadedImageBytes(name);

                     if(buf != null) {
                        viewsheet.addUploadedImage(name, buf);
                     }
                  }
               }

               if(vsAssembly instanceof TableDataVSAssembly) {
                  ((TableDataVSAssembly) vsAssembly).setLastStartRow(0);
               }

               if(vsAssembly.getContainer() != null) {
                  String containerName = vsAssembly.getContainer().getName();
                  containerInvolved = assemblies.stream()
                     .filter(a -> containerName.equals(a.getAbsoluteName()))
                     .findFirst()
                     .orElse(null);
               }

               if(containerInvolved == null) {
                  final int assemblyType = vsAssembly.getAssemblyType();

                  if(assemblyType == AbstractSheet.IMAGE_ASSET) {
                     final ImageVSAssembly image = (ImageVSAssembly) vsAssembly;
                     uploadImg(image, viewsheet, viewsheet);
                  }

                  vsAssembly.getPixelOffset().translate(dx, dy);
               }

               if(vsAssembly instanceof ContainerVSAssembly) {
                  final ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
                  final List<VSAssembly> children =
                     Arrays.stream(container.getAbsoluteAssemblies())
                        .map((name) -> (VSAssembly) viewsheet.getAssembly(namemap.getOrDefault(name, name)))
                        .collect(Collectors.toList());
                  final String[] newChildren = new String[children.size()];
                  int selected = -1;

                  if(!(container instanceof TabVSAssembly)) {
                     children.sort((a1, a2) -> a1.getZIndex() - a2.getZIndex());
                  }

                  for(int j = 0; j < children.size(); j++) {
                     final VSAssembly childAssembly = children.get(j);

                     if(container instanceof TabVSAssembly) {
                        // get selected index based on the old name
                        if(((TabVSAssembly) container).getSelectedValue().equals(
                           namemap.getKey(childAssembly.getAbsoluteName())))
                        {
                           selected = j;
                        }
                     }

                     final Point childOffset = childAssembly.getPixelOffset();
                     final Point newOffset = new Point(childOffset.x + dx, childOffset.y + dy);

                     if(childAssembly instanceof GroupContainerVSAssembly ||
                        childAssembly instanceof TabVSAssembly)
                     {
                        ((AbstractContainerVSAssembly) childAssembly)
                           .setPixelOffset(newOffset, false);
                     }
                     else {
                        childAssembly.setPixelOffset(newOffset);
                     }

                     newChildren[j] = childAssembly.getAbsoluteName();
                  }

                  container.setAssemblies(newChildren);

                  if(container instanceof TabVSAssembly && selected >= 0) {
                     ((TabVSAssembly) container).setSelectedValue(newChildren[selected]);
                  }
               }

               if(containerInvolved == null) {
                  placeholderService.addDeleteVSObject(rvs, vsAssembly, dispatcher);
               }

               if(vsAssembly instanceof ContainerVSAssembly) {
                  final ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
                  final List<VSAssembly> children =
                     Arrays.stream(container.getAbsoluteAssemblies())
                        .map((name) -> (VSAssembly) viewsheet.getAssembly(name))
                        .sorted((a1, a2) -> a1.getZIndex() - a2.getZIndex())
                        .collect(Collectors.toList());

                  // in case of container, its children would be changed so if it's in
                  // another container, it would already be sent to client by the parent
                  // container logic, and we send it again to update the child list
                  if(containerInvolved != null) {
                     placeholderService.addDeleteVSObject(rvs, vsAssembly, dispatcher);
                  }

                  for(VSAssembly child : children) {
                     placeholderService.addDeleteVSObject(rvs, child, dispatcher);
                     placeholderService.loadTableLens(rvs, child.getAbsoluteName(), null, dispatcher);
                  }
               }

               // Load any tables in the embedded viewsheet.
               if(vsAssembly instanceof Viewsheet) {
                  final Viewsheet container = (Viewsheet) vsAssembly;
                  final Assembly[] children = container.getAssemblies();
                  final List<TableDataVSAssembly> tables = Arrays.stream(children)
                     .filter(TableDataVSAssembly.class::isInstance)
                     .map(TableDataVSAssembly.class::cast)
                     .collect(Collectors.toList());

                  for(TableDataVSAssembly table: tables) {
                     placeholderService
                        .loadTableLens(rvs, table.getAbsoluteName(), null, dispatcher);
                  }
               }

               if(vsAssembly instanceof ChartVSAssembly) {
                  placeholderService.execute(rvs, vsAssembly.getAbsoluteName(), linkUri,
                                             VSAssembly.VIEW_CHANGED, dispatcher);
               }

               List<String> assemblyNames = assemblies.stream()
                  .map(Assembly::getAbsoluteName)
                  .collect(Collectors.toList());

               // load data
               placeholderService.loadTableLens(rvs, vsAssembly.getAbsoluteName(), null,
                                                     dispatcher);
               placeholderService.updateAnchoredLines(rvs, assemblyNames, dispatcher);
            }

            AssemblyRef[] vrefs = viewsheet.getViewDependings(assembly.getAssemblyEntry());

            if(vrefs != null) {
               for(AssemblyRef aref: vrefs) {
                  placeholderService.refreshVSAssembly(rvs, aref.getEntry().getAbsoluteName(), dispatcher);
               }
            }
         }

         final VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         final PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         dispatcher.sendCommand(treeCommand);
      }
      finally {
         vbox.unlockWrite();
      }
   }

   // copy embedded image to target viewsheet
   private void copyImage(Viewsheet target, VSAssembly vsAssembly) {
      if(vsAssembly instanceof ImageVSAssembly) {
         String img = ((ImageVSAssembly) vsAssembly).getImageValue();
         Viewsheet ovs = vsAssembly.getViewsheet();

         if(img != null && img.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE) &&
            ovs != null)
         {
            String imgname = img.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length());
            byte[] buf = ovs.getUploadedImageBytes(imgname);

            if(buf != null) {
               byte[] obuf = target.getUploadedImageBytes(imgname);

               if(obuf != null && !Arrays.equals(buf, obuf)) {
                  for(int i = 1; true; i++) {
                     if(target.getUploadedImageBytes(imgname + i) == null) {
                        imgname = imgname + i;
                        ((ImageVSAssemblyInfo) vsAssembly.getInfo()).setImageValue(imgname);
                        break;
                     }
                  }
               }

               target.addUploadedImage(imgname, buf);
            }
         }
      }
   }

   /**
    * Fix script for the created assembly by paste action.
    */
   private void fixScript(VSAssembly assembly, String oname, String nname) {
      if(assembly.containsScript()) {
         String script = ((AbstractVSAssembly) assembly).getScript();
         ((AbstractVSAssembly) assembly).setScript(script.replaceAll(oname, nname));
      }
   }

   private boolean isChild(Assembly assembly, Assembly container) {
      if(container instanceof ContainerVSAssembly) {
         String[] children = ((ContainerVSAssembly) container).getAssemblies();
         Viewsheet vs = ((ContainerVSAssembly) container).getViewsheet();

         for(String child : children) {
            if(child.equals(assembly.getName())) {
               return true;
            }

            Assembly myChild = vs.getAssembly(child);

            if(isChild(assembly, myChild)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * copy table cell highlight
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    * @param headerAccessor the header accessor for the current session
    * @throws Exception if unable to retrieve/edit object.
    */
   @MessageMapping("/composer/viewsheet/table/copyHighlight")
   public void copyHighlight(@Payload CopyHighlightEvent event, Principal principal,
                             CommandDispatcher dispatcher, SimpMessageHeaderAccessor headerAccessor)
      throws Exception
   {
      String runtimeId = this.runtimeViewsheetRef.getRuntimeId();
      Viewsheet viewsheet = viewsheetService.getViewsheet(runtimeId, principal).getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());
      TableDataVSAssemblyInfo assemblyInfo = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      TableHighlightAttr tableHighlightAttr = assemblyInfo.getHighlightAttr();

      if(tableHighlightAttr != null) {
         Map<String, Object> attributes = headerAccessor.getSessionAttributes();
         @SuppressWarnings("unchecked")
         Map<String, HighlightGroup> clipboard = (Map) attributes.get(HIGHLIGHT_CLIPBOARD);

         if(clipboard == null) {
            clipboard = new HashMap<>();
            attributes.put(HIGHLIGHT_CLIPBOARD, clipboard);
         }

         HighlightGroup cellHighlights = tableHighlightAttr.getHighlight(event.getSelectedCell());
         clipboard.put(runtimeId, cellHighlights);

         // Copied highlight can only be pasted within the same table. It cannot be copied across
         // tables. Once a highlight is copied, send command to notify other tables to
         // disable paste function.
         Arrays.stream(viewsheet.getAssemblies()).forEach((vsAssembly) -> {
            if(vsAssembly instanceof TableDataVSAssembly) {
               boolean pasteEnabled = vsAssembly.getAbsoluteName().equals(event.getName()) ||
                  Objects.equals(((TableDataVSAssembly) vsAssembly).getSourceInfo(),
                                 assemblyInfo.getSourceInfo());

               final UpdateHighlightPasteCommand update
                  = UpdateHighlightPasteCommand.builder()
                                               .name(vsAssembly.getAbsoluteName())
                                               .pasteEnabled(pasteEnabled)
                                               .build();

               dispatcher.sendCommand(vsAssembly.getAbsoluteName(), update);
            }
         });
      }
   }

   /**
    * paste table cell highlight
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    * @param headerAccessor the header accessor for the current session
    * @param linkUri  the link Uri
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @MessageMapping("/composer/viewsheet/table/pasteHighlight")
   public void pasteHighlight(@Payload PasteHighlightEvent event, Principal principal,
                              SimpMessageHeaderAccessor headerAccessor,
                              CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String runtimeId = this.runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      VSAssembly assembly = rvs.getViewsheet().getAssembly(event.getName());
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      TableHighlightAttr tableHighlightAttr = info.getHighlightAttr();

      if(tableHighlightAttr == null) {
         info.setHighlightAttr(tableHighlightAttr = new TableHighlightAttr());
      }

      Map<String, Object> attributes = headerAccessor.getSessionAttributes();
      Map clipboard = (Map) attributes.get(HIGHLIGHT_CLIPBOARD);

      TableHighlightAttr finalTableHighlightAttr = tableHighlightAttr;
      Arrays.stream(event.getSelectedCells()).
         forEach(path -> finalTableHighlightAttr.setHighlight(
                    path, (HighlightGroup) clipboard.get(runtimeId)));

      this.vsObjectPropertyService.editObjectProperty(
         rvs, assembly.getVSAssemblyInfo(), event.getName(), event.getName(), linkUri, principal,
         dispatcher);
   }

   /**
    * Get the upperLeft point of a list of assemblies.
    * @return the upperLeft position.
    */
   private Point getUpperLeftPosition(List<Assembly> assemblies) {
      Point upperLeft = null;

      if(assemblies != null) {
         upperLeft = assemblies.stream()
            .filter(a -> a instanceof VSAssembly)
            .map(a -> ((VSAssembly) a).getVSAssemblyInfo().getPixelOffset())
            .reduce(null, (result, pos) -> {
               if(result == null) {
                  result = (Point) pos.clone();
               }
               else {
                  result.x = Math.min(result.x, pos.x);
                  result.y = Math.min(result.y, pos.y);
               }

               return result;
            });
      }

      if(upperLeft == null) {
         upperLeft = new Point(0, 0);
      }

      return upperLeft;
   }

   /**
    * Upload image from the source viewsheet to target viewsheet.
    */
   private void uploadImg(ImageVSAssembly image, Viewsheet vs, Viewsheet srcvs) {
      String img = image.getImage();

      if(img != null && img.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
         img = img.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length());
         vs.addUploadedImage(img, srcvs.getUploadedImageBytes(img));
      }
   }

   //adds all vsobjects that are in containers that are selected, avoids duplicates
   private void addAllDescendants(String[] names, Viewsheet viewsheet, Set<Assembly> assemblySet) {
      final Queue<Assembly> tempList = new ArrayDeque<>();
      Arrays.stream(names).map(viewsheet::getAssembly).forEach(tempList::add);

      while(!tempList.isEmpty()) {
         final Assembly assembly = tempList.remove();

         if(assembly instanceof ContainerVSAssembly) {
            final ContainerVSAssembly container = (ContainerVSAssembly) assembly;
            final String[] childNames = container.getAbsoluteAssemblies();
            Arrays.stream(childNames).map(viewsheet::getAssembly).forEach(tempList::add);
         }

         assemblySet.add(assembly);
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSObjectTreeService vsObjectTreeService;
   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final VSObjectPropertyService vsObjectPropertyService;

   private static final String HIGHLIGHT_CLIPBOARD = "highlight clipboard";
}
