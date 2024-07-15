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
package inetsoft.web.portal.service.datasource;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.*;
import inetsoft.util.*;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.database.DatabaseModelUtil;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.database.cube.XCubeMemberModel;
import inetsoft.web.portal.model.database.cube.XMetaInfoModel;
import inetsoft.web.portal.model.database.cube.xmla.*;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class XmlaDatasourceService extends DatasourcesBaseService {
   public XmlaDatasourceService(XRepository repository, SecurityProvider securityProvider) {
      super(repository, securityProvider);
   }

   public DataSourceXmlaDefinition getNewDataSourceModel(String parentPath) throws Exception {
      DataSourceListing listing = DataSourceListingService.getDataSourceListing("XMLA");
      XDataSource dataSource = null;

      if(listing != null) {
         dataSource = listing.createDataSource();
      }

      if(dataSource == null) {
         throw new FileNotFoundException();
      }

      DataSourceXmlaDefinition result = new DataSourceXmlaDefinition();
      result.setName(dataSource.getName());
      result.setDescription(dataSource.getDescription());
      result.setParentPath(parentPath);

      return result;
   }

   public DataSourceXmlaDefinition getDataSourceModel(String path, Principal principal) throws Exception {
      XDataSource dataSource = getRepository().getDataSource(path);

      if(!(dataSource instanceof XMLADataSource)) {
         throw new FileNotFoundException("Data source not found: " + path);
      }

      return createDataSourceDefinition((XMLADataSource) dataSource, principal);
   }

   private DataSourceXmlaDefinition createDataSourceDefinition(XMLADataSource dataSource,
                                                               Principal principal)
      throws Exception
   {
      String path = dataSource.getFullName();

      if(path == null) {
         return null;
      }

      DataSourceXmlaDefinition result = new DataSourceXmlaDefinition(dataSource);
      Domain domain = getDomain(dataSource.getFullName());

      if(domain != null) {
         result.setDomain(createDomainModel(domain, principal));
      }

      return result;
   }

   /**
    * Create a new data source connection.
    *
    * @param definition new data source definition.
    * @param ds existing data source if updating (not new).
    * @return data source object for the new connection
    */
   public XDataSource createDataSource(DataSourceXmlaDefinition definition, XMLADataSource ds) {
      if(ds == null) {
         ds = new XMLADataSource();
      }

      String parentPath = definition.getParentPath();
      String sourceName = "/".equals(parentPath) || Tool.isEmptyString(parentPath) ?
         definition.getName() : parentPath + "/" + definition.getName();
      ds.setName(sourceName);
      ds.setDataSource(definition.getDatasource());
      ds.setDatasourceName(definition.getDatasourceName());
      ds.setDatasourceInfo(definition.getDatasourceInfo());
      ds.setCatalogName(definition.getCatalogName());
      ds.setURL(definition.getUrl());
      ds.setUser(definition.getUser());
      ds.setPassword(definition.getPassword());
      ds.setRequireLogin(definition.isLogin());
      ds.setDescription(definition.getDescription());

      return ds;
   }

   private Domain convertToDomain(DomainModel domainModel) {
      if(domainModel == null) {
         return null;
      }

      Domain domain = new Domain();
      domain.setDataSource(domainModel.getDatasource());
      List<CubeModel> cubesModel = domainModel.getCubes();

      if(cubesModel != null) {
         List<Cube> cubes = new ArrayList<>();

         for(CubeModel cubeModel : cubesModel) {
            if(cubeModel == null) {
               continue;
            }

            cubes.add(convertToCube(cubeModel));
         }

         domain.setCubes(cubes);
      }

      return domain;
   }

   private Cube convertToCube(CubeModel cubeModel) {
      if(cubeModel == null) {
         return null;
      }

      Cube cube = new Cube();
      cube.setName(cubeModel.getName());
      cube.setCaption(cubeModel.getCaption());
      cube.setType(cubeModel.getType());
      List<CubeDimensionModel> dimensionsModel = cubeModel.getDimensions();

      if(dimensionsModel != null) {
         List<Dimension> dimensions = new ArrayList<>();

         for(CubeDimensionModel dimensionModel : dimensionsModel) {
            dimensions.add(convertToDimension(dimensionModel));
         }

         cube.setDimensions(dimensions);
      }

      List<CubeMeasureModel> measureModels = cubeModel.getMeasures();

      if(measureModels != null) {
         List<Measure> measures = new ArrayList<>();

         for(CubeMeasureModel measureModel : measureModels) {
            measures.add(convertToMeasure(measureModel));
         }

         cube.setMeasures(measures);
      }

      return cube;
   }

   private Measure convertToMeasure(CubeMeasureModel measureModel) {
      Measure measure  = new Measure();
      measure.setName(measureModel.getName());
      measure.setUniqueName(measureModel.getUniqueName());
      measure.setCaption(measureModel.getCaption());
      measure.setType(measureModel.getType());
      measure.setFolder(measureModel.getFolder());
      measure.setXMetaInfo(convertToMetaInfo(measureModel.getMetaInfo()));
      measure.setOriginalType(measureModel.getOriginalType());

      return measure;
   }

   private XMetaInfo convertToMetaInfo(XMetaInfoModel meta) {
      if(meta == null) {
         return null;
      }

      XMetaInfo xMetaInfo = DatabaseModelUtil.createXMetaInfo(meta.getFormatInfo(),
         meta.getDrillInfo());
      xMetaInfo.setAsDate(meta.isAsDate());
      xMetaInfo.setDatePattern(meta.getDatePattern());

      if(!Tool.isEmptyString(meta.getLocale())) {
         xMetaInfo.setLocale(new Locale(meta.getLocale()));
      }

      return xMetaInfo;
   }

   private Dimension convertToDimension(CubeDimensionModel dimensionModel) {
      Dimension dimension;

      if(dimensionModel instanceof HierDimensionModel) {
         HierDimension hierDimension = new HierDimension();
         HierDimensionModel cubeHierDimensionModel = (HierDimensionModel) dimensionModel;
         hierDimension.setHierarchyName(cubeHierDimensionModel.getHierarchyName());
         hierDimension.setHierarchyUniqueName(cubeHierDimensionModel.getHierarchyUniqueName());
         hierDimension.setHierCaption(cubeHierDimensionModel.getHierCaption());
         hierDimension.setUserDefined(cubeHierDimensionModel.isUserDefined());
         dimension = hierDimension;
      }
      else {
         dimension = new Dimension();
      }

      dimension.setDimensionName(dimensionModel.getDimensionName());
      dimension.setUniqueName(dimensionModel.getUniqueName());
      dimension.setCaption(dimensionModel.getCaption());
      dimension.setType(dimensionModel.getType());
      dimension.setOriginalOrder(dimensionModel.isOriginalOrder());

      List<XCubeMemberModel> levelModels = dimensionModel.getMembers();

      if(levelModels != null) {
         List<DimMember> members = new ArrayList<>();

         for(XCubeMemberModel levelModel : levelModels) {
            if(!(levelModel instanceof CubeDimMemberModel)) {
               continue;
            }

            members.add(convertToDimMember((CubeDimMemberModel) levelModel));
         }

         dimension.setLevels(members);
      }

      return dimension;
   }

   private DimMember convertToDimMember(CubeDimMemberModel levelModel) {
      DimMember dimMember = new DimMember();
      dimMember.setName(levelModel.getName());
      dimMember.setUniqueName(levelModel.getUniqueName());
      dimMember.setCaption(levelModel.getCaption());
      dimMember.setNumber(levelModel.getNumber());
      dimMember.setXMetaInfo(convertToMetaInfo(levelModel.getMetaInfo()));
      dimMember.setOriginalType(levelModel.getOriginalType());
      dimMember.setDateLevel(levelModel.getDateLevel());

      return dimMember;
   }

   @Override
   public XDataSource createDataSource(BaseDataSourceDefinition definition, XDataSource ds) {
      checkDatasourceNameValid(ds == null ? null : ds.getName(), definition.getName(),
         definition.getParentPath());

      return createDataSource((DataSourceXmlaDefinition) definition, (XMLADataSource) ds);
   }

   @Override
   protected void afterUpdateSourceCallback(BaseDataSourceDefinition definition, XDataSource ds,
                                            boolean create)
   {
      if(definition instanceof DataSourceXmlaDefinition &&
         ((DataSourceXmlaDefinition) definition).getDomain() != null)
      {
         Domain domain = convertToDomain(((DataSourceXmlaDefinition) definition).getDomain());

         try {
            XDomain odomain = getRepository().getDomain(ds.getFullName());
            getRepository().updateDomain(domain, !create);
            DependencyHandler.getInstance().updateCubeDomainDependencies(odomain, false);
            DependencyHandler.getInstance().updateCubeDomainDependencies(domain, true);
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
         }
      }
   }

   public List<String> getCatalogs(DataSourceXmlaDefinition definition) throws Exception {
      XMLAHandler handler = new XMLAHandler();

      if(Tool.isEmptyString(definition.getUrl())) {
         throw new MessageException(Catalog.getCatalog().getString("olap.common.emptyURL"));
      }

      try {
         handler.connect(createDataSource(definition, null), null);
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage());
         throw new MessageException(ex.getMessage());
      }

      return Arrays.stream(handler.getCatalogs()).collect(Collectors.toList());
   }

   public CubeMetaModel refreshCubes(DataSourceXmlaDefinition definition, Principal principal) {
      try {
         XMLAHandler handler = new XMLAHandler();
         XDataSource dataSource = createDataSource(definition, null);
         handler.connect(dataSource, null);
         XNode node = handler.getMetaData(null);

         if(node == null) {
            return null;
         }

         Collection cubes = (Collection) node.getValue();

         if(cubes == null) {
            return null;
         }

         Domain domain = new Domain();
         domain.setDataSource(dataSource.getFullName());
         domain.setCubes(cubes);
         domain.clearCache();

         return getCubeMetaModel(domain, principal);

      }
      catch(Exception error) {
         LOG.debug(error.getMessage(), error);
      }

      return null;
   }

   public TreeNodeModel getCubeMetaTree(String path) throws Exception {
      Domain domain = getDomain(path);

      if(domain == null) {
         return null;
      }

      return getCubeMetaTree(domain);
   }

   private CubeMetaModel getCubeMetaModel(Domain domain, Principal principal) throws Exception {
      CubeMetaModel cubeMetaModel = new CubeMetaModel();

      if(domain == null) {
         return cubeMetaModel;
      }

      cubeMetaModel.setCubeTree(getCubeMetaTree(domain));
      cubeMetaModel.setDomain(createDomainModel(domain, principal));

      return cubeMetaModel;
   }

   private Domain getDomain(String path) throws Exception {
      XDataSource dataSource = getRepository().getDataSource(path);

      if(!(dataSource instanceof XMLADataSource)) {
         throw new FileNotFoundException("Data source not found: " + path);
      }

      try {
         Domain domain = (Domain) XFactory.getRepository().getDomain(dataSource.getFullName());

         if(domain == null) {
            domain = new Domain();
            domain.setDataSource(dataSource.getFullName());
         }

         if(domain != null) {
            domain = (Domain) domain.clone();
         }

         return domain;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return null;
   }

   private DomainModel createDomainModel(Domain domain, Principal principal) throws Exception {
      DomainModel domainModel = new DomainModel();
      domainModel.setDatasource(domain.getDataSource());
      List<CubeModel> cubeModels = new ArrayList<>();
      domainModel.setCubes(cubeModels);
      Enumeration cubes = domain.getCubes();

      while(cubes.hasMoreElements()) {
         cubeModels.add(createCubeModel((Cube) cubes.nextElement(), principal));
      }

      return domainModel;
   }

   private CubeModel createCubeModel(Cube cube, Principal principal) throws Exception {
      CubeModel cubeModel = new CubeModel();
      cubeModel.setName(cube.getName());
      cubeModel.setType(cube.getType());
      cubeModel.setCaption(cube.getCaption());
      List<CubeDimensionModel> dimensions = new ArrayList<>();
      List<CubeMeasureModel> measures = new ArrayList<>();
      cubeModel.setDimensions(dimensions);
      cubeModel.setMeasures(measures);
      Enumeration cubeDimensions = cube.getDimensions();

      while(cubeDimensions.hasMoreElements()) {
         Dimension dim = (Dimension) cubeDimensions.nextElement();

         if(dim != null) {
            dimensions.add(createCubeDimensionModel(cube.getName(), dim, principal));
         }
      }

      Enumeration cubeMeasures = cube.getMeasures();

      while(cubeMeasures.hasMoreElements()) {
         Measure measure = (Measure) cubeMeasures.nextElement();

         if(measure != null) {
            measures.add(createCubeMeasureModel(measure, principal));
         }
      }

      return cubeModel;
   }

   private CubeMeasureModel createCubeMeasureModel(Measure measure, Principal principal)
      throws Exception
   {
      CubeMeasureModel measureModel = new CubeMeasureModel();
      measureModel.setName(measure.getName());
      measureModel.setUniqueName(measure.getUniqueName());
      measureModel.setCaption(measure.getCaption());
      measureModel.setType(measure.getType());
      measureModel.setFolder(measure.getFolder());
      measureModel.setMetaInfo(createXMetaInfoModel(measure.getXMetaInfo(), principal));
      measureModel.setOriginalType(measure.getOriginalType());

      return measureModel;
   }

   private CubeDimensionModel createCubeDimensionModel(String cube, Dimension dimension,
                                                       Principal principal)
      throws Exception
   {
      CubeDimensionModel dimensionModel = getCubeDimensionModel(dimension);
      dimensionModel.setDimensionName(dimension.getDimensionName());
      dimensionModel.setUniqueName(dimension.getUniqueName());
      dimensionModel.setCaption(dimension.getCaption());
      dimensionModel.setType(dimension.getType());
      dimensionModel.setOriginalOrder(dimension.isOriginalOrder());
      List<XCubeMemberModel> levels = new ArrayList<>();
      dimensionModel.setMembers(levels);

      for(int i = 0; i < dimension.getLevelCount(); i++) {
         XCubeMember level = dimension.getLevelAt(i);

         if(level == null) {
            continue;
         }

         levels.add(createCubeDimMemberModel(cube, dimension.getName(),
            (DimMember) level, principal));
      }

      return dimensionModel;
   }

   private static CubeDimensionModel getCubeDimensionModel(Dimension dimension) {
      CubeDimensionModel dimensionModel = new CubeDimensionModel();

      if(dimension instanceof HierDimension) {
         HierDimension hierDimension = (HierDimension) dimension;
         HierDimensionModel cubeHierDimensionModel = new HierDimensionModel();
         cubeHierDimensionModel.setHierarchyName(hierDimension.getHierarchyName());
         cubeHierDimensionModel.setHierarchyUniqueName(hierDimension.getHierarchyUniqueName());
         cubeHierDimensionModel.setHierCaption(hierDimension.getHierCaption());
         cubeHierDimensionModel.setParentCaption(hierDimension.getParentCaption());
         cubeHierDimensionModel.setUserDefined(hierDimension.isUserDefined());
         dimensionModel = cubeHierDimensionModel;
      }

      return dimensionModel;
   }

   private CubeDimMemberModel createCubeDimMemberModel(String cube, String dimension,
                                                       DimMember member, Principal principal)
      throws Exception
   {
      CubeDimMemberModel dimMemberModel = new CubeDimMemberModel();
      dimMemberModel.setName(member.getName());
      dimMemberModel.setUniqueName(member.getUniqueName());
      dimMemberModel.setCaption(member.getCaption());
      dimMemberModel.setNumber(member.getNumber());
      dimMemberModel.setMetaInfo(createXMetaInfoModel(member.getXMetaInfo(), principal));
      dimMemberModel.setOriginalType(member.getOriginalType());
      dimMemberModel.setDateLevel(member.getDateLevel());
      dimMemberModel.setCube(cube);
      dimMemberModel.setDimension(dimension);

      return dimMemberModel;
   }

   private XMetaInfoModel createXMetaInfoModel(XMetaInfo metaInfo, Principal principal)
      throws Exception
   {
      return DatabaseModelUtil.createXMetaInfoModel(metaInfo, getRepository(), principal);
   }

   private TreeNodeModel getCubeMetaTree(Domain domain) {
      TreeNodeModel.Builder rootBuilder = TreeNodeModel.builder();

      if(domain != null) {
         final Enumeration cubes = domain.getCubes();
         List<TreeNodeModel> children = new ArrayList<>();

         if(cubes != null) {
            while(cubes.hasMoreElements()) {
               final Cube cube = (Cube) cubes.nextElement();
               TreeNodeModel.Builder cubeBuilder = TreeNodeModel.builder();
               cubeBuilder.label(cube.getCaption());
               CubeItemDataModel data = new CubeItemDataModel();
               data.setType(CubeItemType.CUBE);
               data.setUniqueName(cube.getName());
               data.setCubeName(cube.getName());
               cubeBuilder.data(data);
               loadCube(cube, cubeBuilder);
               children.add(cubeBuilder.build());
            }
         }

         sortNodes(children);
         rootBuilder.children(children);
      }

      return rootBuilder.build();
   }

   private void sortNodes(List<TreeNodeModel> children) {
      if(children == null) {
         return;
      }

      children.sort((n1, n2) ->
         Tool.compare(n1 == null ? null : n1.label(), n2 == null ? null : n2.label()));
   }

   /**
    * Load a single cube node branch.
    */
   private void loadCube(Cube cube, TreeNodeModel.Builder cubeBuilder) {
      Map dgroups = XMLAUtil.getDimensionGroups(cube);

      if(dgroups != null) {
         Iterator it = dgroups.keySet().iterator();
         List<TreeNodeModel> children = new ArrayList<>();

         while(it.hasNext()) {
            String dimStr = (String) it.next();
            TreeNodeModel.Builder groupBuilder = TreeNodeModel.builder();
            groupBuilder.label(dimStr);
            CubeItemDataModel data = new CubeItemDataModel();
            data.setType(CubeItemType.DIMENSION_FOLDER);
            data.setUniqueName(dimStr);
            data.setCubeName(cube.getName());
            groupBuilder.data(data);
            Vector vec = (Vector) dgroups.get(dimStr);
            vec.sort((d1, d2) ->
               Tool.compare(getDimensionLabel((Dimension) d1), getDimensionLabel((Dimension) d2)));

            for(int i = 0; i < vec.size(); i++) {
               Dimension dim = (Dimension) vec.get(i);
               addInDimNode(cube.getName(), dim, groupBuilder);
            }

            children.add(groupBuilder.build());
         }

         sortNodes(children);
         cubeBuilder.children(children);
      }
      else {
         Enumeration dimensions = cube.getDimensions();
         ArrayList dimensionList = Collections.list(dimensions);
         dimensionList.sort((d1, d2) ->
            Tool.compare(getDimensionLabel((Dimension) d1), getDimensionLabel((Dimension) d2)));

         for(Object item : dimensionList) {
            Dimension dim = (Dimension) item;

            if(cube.getMeasure(dim.getDimensionName()) != null) {
               continue;
            }

            addInDimNode(cube.getName(), dim, cubeBuilder);
         }
      }

      Enumeration measures = cube.getMeasures();
      Map<String, List<TreeNodeModel>> folderMap = new HashMap<>();
      List<TreeNodeModel> noneFolderMeasureNodes = new ArrayList<>();

      while(measures.hasMoreElements()) {
         Measure measure = (Measure) measures.nextElement();

         if(cube.getDimension(measure.getName()) != null) {
            continue;
         }

         String folder = measure.getFolder();
         List<TreeNodeModel> folderNodes = null;

         if(folder != null && folder.length() > 0) {
            folderNodes = folderMap.computeIfAbsent(folder, f -> new ArrayList<>());
         }

         TreeNodeModel.Builder measureBuilder = TreeNodeModel.builder();
         measureBuilder.label(measure.getCaption());
         CubeItemDataModel data = new CubeItemDataModel();
         data.setType(CubeItemType.MEASURE);
         data.setUniqueName(measure.getUniqueName());
         data.setCubeName(cube.getName());
         measureBuilder.data(data);
         measureBuilder.leaf(true);

         if(folderNodes != null) {
            folderNodes.add(measureBuilder.build());
         }
         else {
            noneFolderMeasureNodes.add(measureBuilder.build());
         }
      }

      folderMap.keySet().stream()
         .sorted((f1, f2) -> Tool.compare(f1, f2))
         .forEach(folder -> {
            TreeNodeModel.Builder newFolderBuilder = TreeNodeModel.builder();
            newFolderBuilder.label(folder);
            CubeItemDataModel data = new CubeItemDataModel();
            data.setType(CubeItemType.MEASURE_FOLDER);
            data.setUniqueName(folder);
            data.setCubeName(cube.getName());
            newFolderBuilder.data(data);
            List<TreeNodeModel> treeNodeModels = folderMap.get(folder);

            if(treeNodeModels != null)  {
               sortNodes(treeNodeModels);
               newFolderBuilder.children(treeNodeModels);
            }

            cubeBuilder.addChildren(newFolderBuilder.build());
         });
   }

   /**
    * Add in dimension node.
    */
   private void addInDimNode(String cubeName, Dimension dimension,
                             TreeNodeModel.Builder parentBuilder)
   {
      CubeItemDataModel data = new CubeItemDataModel();

      if(dimension instanceof HierDimension) {
         data.setHierarchy(true);
         data.setUserDefined(((HierDimension) dimension).isUserDefined());
         data.setUniqueName(((HierDimension) dimension).getHierarchyUniqueName());
      }
      else {
         data.setUniqueName(dimension.getUniqueName());
      }

      TreeNodeModel.Builder dimensionBuilder = TreeNodeModel.builder();
      dimensionBuilder.label(getDimensionLabel(dimension));
      data.setType(CubeItemType.DIMENSION);
      data.setCubeName(cubeName);
      dimensionBuilder.data(data);
      int cnt = dimension.getLevelCount();

      for(int i = 0; i < cnt; i++) {
         DimMember member = (DimMember) dimension.getLevelAt(i);
         TreeNodeModel.Builder memberBuilder = TreeNodeModel.builder();
         memberBuilder.leaf(true);
         memberBuilder.label(member.getCaption());
         CubeItemDataModel memberData = new CubeItemDataModel();
         memberData.setType(CubeItemType.LEVEL);
         memberData.setUniqueName(member.getUniqueName());
         memberData.setCubeName(cubeName);
         memberBuilder.data(memberData);
         dimensionBuilder.addChildren(memberBuilder.build());
      }

      parentBuilder.addChildren(dimensionBuilder.build());
   }

   private String getDimensionLabel(Dimension dimension) {
      if(dimension == null) {
         return "";
      }

      return dimension instanceof HierDimension ? ((HierDimension) dimension).getHierCaption() :
         dimension.getCaption();
   }

   public PreviewTableCellModel[][] getSampleData(ViewSampleDataRequest model) {
      XDataSource dataSource = createDataSource(model.datasource(), null);
      CubeDimMemberModel member = model.member();
      DataRef ref = new ColumnRef(new AttributeRef(member.getDimension(), member.getName()));
      XMLAQuery query = new XMLAQuery();
      query.setDataSource(dataSource);
      query.setProperty("noEmpty", "false");
      query.setCube(member.getCube());
      query.addMemberRef(ref);
      query.setRuntimeCube(convertToCube(model.cube()));
      int rowCount = 0;
      TableLens lens;

      try {
         XDataService service = XFactory.getDataService();
         XSessionManager mgr = XSessionManager.getSessionManager();
         lens = new XNodeTableLens(
            service.execute(mgr.getSession(), query, null, null));
         XMLAUtil.reset();
         int maxRowCount = 1001;


         if(lens.moreRows(maxRowCount)) {
            rowCount = maxRowCount;
         }
         else {
            rowCount = lens.getRowCount();
         }
      }
      catch(Exception ex) {
         lens = new DefaultTableLens(1, 2);
         lens.setObject(0, 0, ref.getName());
         lens.setObject(0, 1, Catalog.getCatalog().getString("Date Value"));
      }

      SimpleDateFormat parseDateFormat =
         createParseDateFormat(convertToMetaInfo(member.getMetaInfo()));
      PreviewTableCellModel[][] cellModels = new PreviewTableCellModel[rowCount][2];
      Function<Object, Object> cellConvertor = (data) -> {
         if(data instanceof MemberObject) {
            data = ((MemberObject) data).getFullCaption();
         }

         if(parseDateFormat != null && data != null) {
            try {
               data = parseDateFormat.parse(data.toString());
            }
            catch(Exception ex) {
               data = "";
            }
         }
         else {
            data = "";
         }

         return data;
      };

      Function<Object, Object> dateTitleConvertor =
         (data) -> Catalog.getCatalog().getString("Date Value");

      for(int i = 0; i < rowCount; i++) {
         for(int j = 0; j < 2; j++) {
            Function<Object, Object> convertor = null;

            if(i == 0 && j == 1) {
               convertor = dateTitleConvertor;
            }
            else if(j == 1) {
               convertor = cellConvertor;
            }

            cellModels[i][j] = BaseTableCellModel.createPreviewCell(lens, i, 0,
               false, null, convertor);
         }
      }

      return cellModels;
   }

   private SimpleDateFormat createParseDateFormat(XMetaInfo minfo) {
      if(minfo == null || !minfo.isAsDate() ||
         minfo.getDatePattern() == null || "".equals(minfo.getDatePattern()))
      {
         return null;
      }

      Locale locale = minfo.getLocale();

      if(locale == null) {
         locale = Locale.getDefault();
      }

      return new SimpleDateFormat(minfo.getDatePattern(), locale);
   }

   public ConnectionStatus testConnect(DataSourceXmlaDefinition model) {
      ConnectionStatus connectionStatus = new ConnectionStatus();
      Catalog catalog = Catalog.getCatalog();

      try {
         XRepository rep = XFactory.getRepository();
         XDataSource dataSource = createDataSource(model, null);
         rep.testDataSource(getRepository().bind(System.getProperty("user.name")), dataSource, null);
         connectionStatus.setConnected(true);
         connectionStatus.setStatus(catalog.getString("em.security.testlogin.note2"));
      }
      catch(Exception exp) {
         LOG.info(exp.getMessage(), exp);
         connectionStatus.setConnected(false);
         connectionStatus.setStatus(catalog.getString("common.datasource.connectFailed",
            model.getName()));
      }

      return connectionStatus;
   }

   protected static final Logger LOG = LoggerFactory.getLogger(XmlaDatasourceService.class);
}
