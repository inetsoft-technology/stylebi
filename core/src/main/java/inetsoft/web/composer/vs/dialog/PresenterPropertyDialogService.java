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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.Presenter;
import inetsoft.report.TableDataPath;
import inetsoft.report.bean.BeanUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.command.AddLayoutObjectCommand;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.beans.PropertyDescriptor;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class PresenterPropertyDialogService {

   public PresenterPropertyDialogService(ViewsheetService viewsheetService,
                                         CoreLifecycleService coreLifecycleService,
                                         VSObjectModelFactoryService objectModelService,
                                         VSLayoutService vsLayoutService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.objectModelService = objectModelService;
      this.vsLayoutService = vsLayoutService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public PresenterPropertyDialogModel getPresenterPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                       String objectId, int row, int col,
                                                                       String presenter, boolean layout,
                                                                       int layoutRegion, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(runtimeId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssembly assembly;
      Presenter pr;
      PresenterRef ref;

      if(layout) {
         PrintLayout printLayout = viewsheet.getLayoutInfo().getPrintLayout();
         VSAssemblyLayout layoutAssembly =
            vsLayoutService.findAssemblyLayout(printLayout, objectId, layoutRegion)
               .orElse(null);

         if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
            return null;
         }

         assembly = new TextVSAssembly(viewsheet, layoutAssembly.getName());
         assembly.setVSAssemblyInfo(((VSEditableAssemblyLayout) layoutAssembly).getInfo());
      }
      else {
         assembly = (VSAssembly) viewsheet.getAssembly(objectId);
      }

      if(assembly instanceof TableDataVSAssembly) {
         TableDataVSAssembly table = (TableDataVSAssembly) assembly;
         FormatInfo info = table.getVSAssemblyInfo().getFormatInfo();
         String oname = table.getAbsoluteName();
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);
         TableDataPath path = getTableDataPath(row, col, rvs, table ,lens);
         Object data = lens.getObject(row, col);
         VSCompositeFormat format = info.getFormat(path, false);
         ref = format.getPresenterValue();

         if(ref == null) {
            ref = new PresenterRef(presenter);
            format.getUserDefinedFormat().setPresenterValue(ref);
         }

         if(data instanceof PresenterPainter) {
            PresenterPainter painter = (PresenterPainter) data;
            pr = painter.getPresenter();
         }
         else {
            pr = ref.createPresenter();
         }
      }
      else if(assembly instanceof TextVSAssembly) {
         TextVSAssembly text = (TextVSAssembly) assembly;
         VSCompositeFormat format = text.getVSAssemblyInfo().getFormat();
         ref = format.getPresenterValue();

         if(ref == null) {
            ref = new PresenterRef(presenter);
            format.getUserDefinedFormat().setPresenter(ref);
         }

         pr = ref.createPresenter();
      }
      else {
         return null;
      }

      PropertyDescriptor[] descs = ref.getPropertyDescriptors();
      List<PresenterDescriptorModel> descriptors = new ArrayList<>();

      for(PropertyDescriptor desc : descs) {
         try {
            Object paramValue = ref.getParameter(desc.getName());
            PresenterDescriptorModel model = getDescriptorModel(rvs, desc, pr, paramValue);
            descriptors.add(model);
         }
         catch(Exception ex) {
            LOG.error("generate property '{}' for presenter '{}' error", desc, presenter, ex);
         }
      }

      return PresenterPropertyDialogModel.builder()
         .descriptors(descriptors)
         .presenter(presenter)
         .build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setPresenterDialogModel(@ClusterProxyKey String runtimeId, String objectId, int row,
                                       int col, boolean layout, int layoutRegion,
                                       PresenterPropertyDialogModel model, String linkUri,
                                       Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssembly assembly;
      VSAssemblyLayout layoutAssembly = null;
      PresenterRef ref;

      if(layout) {
         PrintLayout printLayout = viewsheet.getLayoutInfo().getPrintLayout();
         layoutAssembly =
            vsLayoutService.findAssemblyLayout(printLayout, objectId, layoutRegion)
               .orElse(null);

         if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
            return null;
         }

         assembly = new TextVSAssembly(viewsheet, layoutAssembly.getName());
         assembly.setVSAssemblyInfo(((VSEditableAssemblyLayout) layoutAssembly).getInfo());
      }
      else {
         assembly = viewsheet.getAssembly(objectId);
      }

      if(assembly instanceof TableDataVSAssembly) {
         TableDataVSAssembly table = (TableDataVSAssembly) assembly;
         FormatInfo info = table.getVSAssemblyInfo().getFormatInfo();
         String oname = table.getAbsoluteName();
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);
         TableDataPath path = getTableDataPath(row, col, rvs, table ,lens);
         VSCompositeFormat format = info.getFormat(path, false);
         ref = format.getPresenterValue();

         if(ref == null) {
            ref = new PresenterRef(model.presenter());
            format.getUserDefinedFormat().setPresenterValue(ref);
         }
      }
      else if(assembly instanceof TextVSAssembly) {
         TextVSAssembly text = (TextVSAssembly) assembly;
         VSCompositeFormat format = text.getVSAssemblyInfo().getFormat();
         ref = format.getUserDefinedFormat().getPresenterValue();
      }
      else {
         return null;
      }

      PropertyDescriptor[] descs = ref.getPropertyDescriptors();
      List<PresenterDescriptorModel> descriptorModels = model.descriptors();

      for(int i = 0; i < descs.length; i++) {
         PropertyDescriptor desc = descs[i];
         PresenterDescriptorModel descModel = descriptorModels.get(i);
         desc.setName(descModel.name());
         desc.setDisplayName(descModel.displayName());
         Object value = null;

         if(descModel instanceof LineDescriptorModel) {
            value = Util.getStyleConstantsFromString(
               ((LineDescriptorModel) descModel).value());
         }
         else if(descModel instanceof BooleanDescriptorModel) {
            value = ((BooleanDescriptorModel) descModel).value();
         }
         else if(descModel instanceof IntDescriptorModel) {
            value = ((IntDescriptorModel) descModel).value();
         }
         else if(descModel instanceof DoubleDescriptorModel) {
            value = ((DoubleDescriptorModel) descModel).value();
         }
         else if(descModel instanceof FontDescriptorModel) {
            value = ((FontDescriptorModel) descModel).value().toFont();
         }
         else if(descModel instanceof ColorDescriptorModel) {
            String color = ((ColorDescriptorModel) descModel).value();
            value = color == null || color.isEmpty() ? null : Color.decode(color);
         }
         else if(descModel instanceof SizeDescriptorModel) {
            SizeDescriptorModel size = (SizeDescriptorModel) descModel;
            value = new Dimension(size.width(), size.height());
         }
         else if(descModel instanceof InsetsDescriptorModel) {
            InsetsDescriptorModel insets = (InsetsDescriptorModel) descModel;
            value = new Insets(insets.top(), insets.left(), insets.bottom(), insets.right());
         }
         else if(descModel instanceof ImageDescriptorModel) {
            ImageLocation location = new ImageLocation(null);
            location.setPath(((ImageDescriptorModel) descModel).value().selectedImage());

            if(location.getPath() != null) {
               location.setPathType(3);
               value = new MetaImage(location);
            }
         }
         else {
            value = ((StringDescriptorModel) descModel).value();
         }

         ref.setParameter(descModel.name(), value);
      }

      int hint = AbstractVSAssembly.VIEW_CHANGED;

      if(layout) {
         AddLayoutObjectCommand command = new AddLayoutObjectCommand();
         command.setObject(vsLayoutService.createObjectModel(rvs, layoutAssembly,
                                                             objectModelService));
         command.setRegion(layoutRegion);
         commandDispatcher.sendCommand(command);
      }
      else {
         coreLifecycleService.execute(rvs, assembly.getName(), linkUri, hint, commandDispatcher);
      }

      return null;
   }


   private PresenterDescriptorModel getDescriptorModel(RuntimeViewsheet rvs,
                                                       PropertyDescriptor desc,
                                                       Presenter presenter,
                                                       Object paramValue)
      throws Exception
   {
      String name = desc.getName();
      String displayName = BeanUtil.getBeanDisplayName(name);
      Object value = desc.getReadMethod().invoke(presenter, new Object[0]);
      PresenterDescriptorModel model;
      Class cls = desc.getPropertyType();

      if("border".equals(name)) {
         model = LineDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .displayName(displayName)
            .editor("LinePropertyEditor")
            .value(Util.getLineStyleName((Integer) value))
            .build();
      }
      else if(cls == boolean.class || cls == Boolean.class) {
         model = BooleanDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((Boolean) value)
            .editor("AsCheckboxPropertyEditor")
            .build();
      }
      else if(cls == int.class || cls == Integer.class) {
         model = IntDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((Integer) value)
            .editor("IntPropertyEditor")
            .build();
      }
      else if(cls == long.class || cls == Long.class) {
         model = IntDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((Integer) value)
            .editor("IntPropertyEditor")
            .build();
      }
      else if(cls == float.class || cls == Float.class) {
         model = DoubleDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((Double) value)
            .editor("DoublePropertyEditor")
            .build();
      }
      else if(cls == double.class || cls == Double.class ||
         Number.class.isAssignableFrom(cls))
      {
         model = DoubleDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((Double) value)
            .editor("DoublePropertyEditor")
            .build();
      }
      else if(Font.class.isAssignableFrom(cls)) {
         model = FontDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value(new FontInfo((Font) value))
            .editor("FontPropertyEditor")
            .build();
      }
      else if(Color.class.isAssignableFrom(cls)) {
         String color = value == null ? "" : "#" + Tool.colorToHTMLString(((Color) value));
         model = ColorDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value(color)
            .editor("ColorPropertyEditor")
            .build();
      }
      else if(Dimension.class.isAssignableFrom(cls)) {
         model = SizeDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .width(((Dimension) value).width)
            .height(((Dimension) value).height)
            .editor("SizePropertyEditor")
            .build();
      }
      else if(Insets.class.isAssignableFrom(cls)) {
         model = InsetsDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .top(((Insets) value).top)
            .bottom(((Insets) value).bottom)
            .left(((Insets) value).left)
            .right(((Insets) value).right)
            .editor("InsetsPropertyEditor")
            .build();
      }
      else if(cls == Image.class) {
         ImagePreviewPaneController imageController = new ImagePreviewPaneController();
         String path = null;

         if(value instanceof MetaImage) {
            path = ((MetaImage) value).getImageLocation().getPath();
         }
         else if(paramValue instanceof MetaImage) {
            path = ((MetaImage) paramValue).getImageLocation().getPath();
         }

         ImagePreviewPaneModel imageModel = ImagePreviewPaneModel.builder()
            .alpha(100)
            .animateGifImage(false)
            .allowNullImage(true)
            .selectedImage(path)
            .imageTree(imageController.getImageTree(rvs))
            .build();

         model = ImageDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value(imageModel)
            .editor("ImagePropertyEditor")
            .build();
      }
      else {
         model = StringDescriptorModel.builder()
            .name(name)
            .displayName(displayName)
            .value((String) value)
            .editor("AsTextPropertyEditor")
            .build();
      }

      return model;
   }

   private TableDataPath getTableDataPath(int row, int col, RuntimeViewsheet rvs,
                                          VSAssembly assembly, VSTableLens lens)
   {
      TableDataPath path = null;

      if(rvs.isBinding() && assembly.getVSAssemblyInfo() instanceof CalcTableVSAssemblyInfo) {
         CalcTableVSAssemblyInfo calcInfo =
            (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
         path = calcInfo.getCellDataPath(row, col);
      }

      if(path == null) {
         path = lens.getTableDataPath(row, col);
      }

      return path;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(PresenterPropertyDialogService.class);

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSObjectModelFactoryService objectModelService;
   private final VSLayoutService vsLayoutService;
}
