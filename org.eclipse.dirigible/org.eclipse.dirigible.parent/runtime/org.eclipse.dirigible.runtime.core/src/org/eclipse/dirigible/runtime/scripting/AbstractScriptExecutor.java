/******************************************************************************* 
 * Copyright (c) 2015 SAP and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   SAP - initial API and implementation
 *******************************************************************************/

package org.eclipse.dirigible.runtime.scripting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.eclipse.dirigible.repository.api.ICollection;
import org.eclipse.dirigible.repository.api.ICommonConstants;
import org.eclipse.dirigible.repository.api.IEntity;
import org.eclipse.dirigible.repository.api.IRepository;
import org.eclipse.dirigible.repository.api.IResource;
//import org.eclipse.dirigible.repository.db.DBRepository;
import org.eclipse.dirigible.repository.ext.extensions.ExtensionManager;
import org.eclipse.dirigible.repository.logging.Logger;
import org.eclipse.dirigible.runtime.RuntimeActivator;
import org.eclipse.dirigible.runtime.mail.MailSender;
import org.eclipse.dirigible.runtime.repository.RepositoryFacade;
import org.eclipse.dirigible.runtime.scripting.utils.ConfigStorageUtils;
import org.eclipse.dirigible.runtime.scripting.utils.ConnectivityConfigurationUtils;
import org.eclipse.dirigible.runtime.scripting.utils.DbUtils;
import org.eclipse.dirigible.runtime.scripting.utils.FileStorageUtils;
import org.eclipse.dirigible.runtime.scripting.utils.HttpUtils;
import org.eclipse.dirigible.runtime.scripting.utils.IndexingService;
import org.eclipse.dirigible.runtime.scripting.utils.StorageUtils;
import org.eclipse.dirigible.runtime.scripting.utils.URLUtils;
import org.eclipse.dirigible.runtime.scripting.utils.XMLUtils;

public abstract class AbstractScriptExecutor implements IScriptExecutor {

	private static final String THERE_IS_NO_RESOURCE_AT_THE_SPECIFIED_SERVICE_PATH = Messages
			.getString("ScriptLoader.THERE_IS_NO_RESOURCE_AT_THE_SPECIFIED_SERVICE_PATH"); //$NON-NLS-1$
	
	private static final String THERE_IS_NO_COLLECTION_AT_THE_SPECIFIED_SERVICE_PATH = Messages
			.getString("ScriptLoader.THERE_IS_NO_COLLECTION_AT_THE_SPECIFIED_SERVICE_PATH"); //$NON-NLS-1$

	private static final Logger logger = Logger.getLogger(AbstractScriptExecutor.class);

	/* (non-Javadoc)
	 * @see org.eclipse.dirigible.runtime.scripting.IScriptExecutor#executeServiceModule(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.String, java.util.Map)
	 */
	@Override
	public Object executeServiceModule(HttpServletRequest request, HttpServletResponse response,
			String module, Map<Object, Object> executionContext) throws IOException {
		return executeServiceModule(request, response, null, module, executionContext);
	}

	protected abstract Object executeServiceModule(HttpServletRequest request,
			HttpServletResponse response, Object input, String module, Map<Object, Object> executionContext) throws IOException;

	protected abstract void registerDefaultVariable(Object scope, String name, Object value);
	
	private void registerDefaultVariableInContextAndScope(Map<Object, Object> executionContext, Object scope, String name, Object value) {
		if (executionContext.get(name) == null) {
			registerDefaultVariable(scope, name, value);
			executionContext.put(name, value);
		}
	}

	protected void registerDefaultVariables(HttpServletRequest request,
			HttpServletResponse response, Object input, Map<Object, Object> executionContext,
			IRepository repository, Object scope) {
		
		InjectedAPIWrapper api = new InjectedAPIWrapper();
		
		if (executionContext == null) {
			// in case executionContext is not provided from outside
			executionContext = new HashMap<Object, Object>();
		}
		
		// Objects
		
		// put the execution context
		registerDefaultVariable(scope, IInjectedAPIAliases.EXECUTION_CONTEXT, executionContext); //$NON-NLS-1$
		api.setExecutionContext(executionContext);
		
		// put the system out
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.SYSTEM_OUTPUT, System.out); //$NON-NLS-1$
		api.setSystemOutput(System.out);
		
		// put the default data source
		DataSource dataSource = null;
		dataSource = RepositoryFacade.getInstance().getDataSource();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.DEFAULT_DATASOURCE, dataSource); //$NON-NLS-1$
		api.setDatasource(dataSource);
		
		// put request
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.HTTP_REQUEST, request); //$NON-NLS-1$
		api.setRequest(request);
		if (request != null) {

			// put session
			registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.HTTP_SESSION, request.getSession()); //$NON-NLS-1$
			api.setSession(request.getSession());
		}
		
		// put response
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.HTTP_RESPONSE, response); //$NON-NLS-1$
		api.setResponse(response);
		
		// put repository
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.REPOSITORY, repository); //$NON-NLS-1$
		api.setRepository(repository);
		
		// user name
		String userName = RepositoryFacade.getUser(request);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.USER, userName); //$NON-NLS-1$
		api.setUserName(userName);
		
		// the input from the execution chain if any
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.REQUEST_INPUT, input); //$NON-NLS-1$
		api.setRequestInput(input);

		if (request != null) {
			// JNDI context
			InitialContext initialContext = (InitialContext) request.getSession().getAttribute(ICommonConstants.INITIAL_CONTEXT);
			registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.INITIAL_CONTEXT, initialContext); //$NON-NLS-1$
			api.setInitialContext(initialContext);
		}
		
		// Simple binary storage
		StorageUtils storageUtils = new StorageUtils(dataSource);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.STORAGE, storageUtils); //$NON-NLS-1$
		api.setBinaryStorage(storageUtils);
		
		// Simple file storage
		FileStorageUtils fileStorageUtils = new FileStorageUtils(dataSource);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.FILE_STORAGE, fileStorageUtils); //$NON-NLS-1$
		api.setFileStorage(fileStorageUtils);
		
		// Simple configuration storage
		ConfigStorageUtils configStorageUtils = new ConfigStorageUtils(dataSource);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.CONFIGURATION_STORAGE, configStorageUtils); //$NON-NLS-1$
		api.setConfigurationStorage(configStorageUtils);
		

		
		//Services
		
		// put mail sender
		MailSender mailSender = new MailSender();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.MAIL_SERVICE, mailSender); //$NON-NLS-1$
		api.setMailService(mailSender);
		
		// Extension Manager
		ExtensionManager extensionManager = new ExtensionManager(repository, dataSource);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.EXTENSIONS_SERVICE, extensionManager); //$NON-NLS-1$
		api.setExtensionService(extensionManager);
		
		// Apache Lucene Indexer
		IndexingService<Document> indexingUtils = new IndexingService<Document>();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.INDEXING_SERVICE, indexingUtils); //$NON-NLS-1$
		api.setIndexingService(indexingUtils);
		
		// Connectivity Configuration service
		ConnectivityConfigurationUtils configurationUtils = new ConnectivityConfigurationUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.CONNECTIVITY_SERVICE, configurationUtils); //$NON-NLS-1$
		api.setConnectivityService(configurationUtils);

		
		// Utils
		
		// put Apache Commons IOUtils
		IOUtils ioUtils = new IOUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.IO_UTILS, ioUtils); //$NON-NLS-1$
		api.setIOUtils(ioUtils);
		
		// put Apache Commons HttpClient and related classes wrapped with a factory like HttpUtils
		HttpUtils httpUtils = new HttpUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.HTTP_UTILS, httpUtils); //$NON-NLS-1$
		api.setHttpUtils(httpUtils);
		
		// put Apache Commons Codecs
		Base64 base64Codec = new Base64();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.BASE64_UTILS, base64Codec); //$NON-NLS-1$
		api.setBase64Utils(base64Codec);
		
		Hex hexCodec = new Hex();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.HEX_UTILS, hexCodec); //$NON-NLS-1$
		api.setHexUtils(hexCodec);
		
		DigestUtils digestUtils = new DigestUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.DIGEST_UTILS, digestUtils); //$NON-NLS-1$
		api.setDigestUtils(digestUtils);
		
		// standard URLEncoder and URLDecoder functionality
		URLUtils urlUtils = new URLUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.URL_UTILS, urlUtils); //$NON-NLS-1$
		api.setUrlUtils(urlUtils);
		
		// file upload
		ServletFileUpload fileUpload = new ServletFileUpload(new DiskFileItemFactory());
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.UPLOAD_UTILS, fileUpload); //$NON-NLS-1$
		api.setUploadUtils(fileUpload);
		
		// UUID
		UUID uuid = new UUID(0, 0);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.UUID_UTILS, uuid); //$NON-NLS-1$
		api.setUuidUtils(uuid);
		
		// DbUtils
		DbUtils dbUtils = new DbUtils(dataSource);
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.DB_UTILS, dbUtils); //$NON-NLS-1$
		api.setDatabaseUtils(dbUtils);
		
		// EscapeUtils
		StringEscapeUtils stringEscapeUtils = new StringEscapeUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.XSS_UTILS, stringEscapeUtils); //$NON-NLS-1$
		api.setXssUtils(stringEscapeUtils);
		
		// XML to JSON and vice-versa
		XMLUtils xmlUtils = new XMLUtils();
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.XML_UTILS, xmlUtils); //$NON-NLS-1$
		api.setXmlUtils(xmlUtils);

				
		// register objects via extension
		try {
			BundleContext context = RuntimeActivator.getContext();
			if (context != null) {
				Collection<ServiceReference<IContextService>> serviceReferences = context.getServiceReferences(IContextService.class, null);
				for (Iterator<ServiceReference<IContextService>> iterator = serviceReferences.iterator(); iterator.hasNext();) {
					ServiceReference<IContextService> serviceReference = iterator.next();
					IContextService contextService = context.getService(serviceReference);
					registerDefaultVariableInContextAndScope(executionContext, scope, contextService.getName(), contextService.getInstance());
					api.set(contextService.getName(), contextService.getInstance());
				}
			}
		} catch (InvalidSyntaxException e) {
			logger.error(e.getMessage(), e);
		}
		
		registerDefaultVariableInContextAndScope(executionContext, scope, IInjectedAPIAliases.API, api); //$NON-NLS-1$
	}

	public byte[] readResourceData(IRepository repository, String repositoryPath)
			throws IOException {
		final IResource resource = repository.getResource(repositoryPath);
		if (!resource.exists()) {
			final String logMsg = String.format(THERE_IS_NO_RESOURCE_AT_THE_SPECIFIED_SERVICE_PATH,
					resource.getName(), repositoryPath); 
			logger.error(logMsg);
			throw new IOException(logMsg);
		}
		return resource.getContent();
	}

	public Module retrieveModule(IRepository repository, String module, String extension,
			String... rootPaths) throws IOException {

		for (String rootPath : rootPaths) {
			String resourcePath = createResourcePath(rootPath, module, extension);
			final IResource resource = repository.getResource(resourcePath);
			if (resource.exists()) {
				return new Module(getModuleName(resource.getPath()), resource.getPath(), readResourceData(repository,
						resourcePath));
			}
		}

		final String logMsg = String.format(THERE_IS_NO_RESOURCE_AT_THE_SPECIFIED_SERVICE_PATH,
				(module + extension), Arrays.toString(rootPaths));
		logger.error(logMsg);
		throw new FileNotFoundException(logMsg);
	}

	public List<Module> retrieveModulesByExtension(IRepository repository, String extension,
			String... rootPaths) throws IOException {
		Map<String, Module> modules = new HashMap<String, Module>();
		for (int i = rootPaths.length - 1; i >= 0; i--) {
			List<IEntity> entities = repository.searchName(rootPaths[i], "%" + extension, false);
			for (IEntity entity : entities) {
				if (entity.exists()) {
					String path = entity.getPath();
					String moduleName = getModuleName(path);
					Module module = new Module(moduleName, path, readResourceData(repository, path), entity.getInformation());
					modules.put(moduleName, module);
				}
			}
		}
		return Arrays.asList(modules.values().toArray(new Module[] {}));
	}

	private String getModuleName(String path) {
		String workspace = ICommonConstants.WORKSPACE + ICommonConstants.SEPARATOR;
		String scriptingServices = getModuleType(path) //ICommonConstants.ARTIFACT_TYPE.SCRIPTING_SERVICES
				+ ICommonConstants.SEPARATOR;
		int indexOfSandbox = path.indexOf(ICommonConstants.SANDBOX);
		int indexOfRegistry = path.indexOf(ICommonConstants.REGISTRY);
		String result = null;
		if (indexOfSandbox > 0 || indexOfRegistry > 0) {
			int indexOfScriptingServices = path.indexOf(scriptingServices);
			result = path.substring(indexOfScriptingServices + scriptingServices.length());
		} else {
			int indexOfWorkspace = path.indexOf(workspace);
			result = path.substring(indexOfWorkspace + workspace.length());
			result = result.replace(scriptingServices, "");
		}
		return result;
	}

	protected abstract String getModuleType(String path);

	private String createResourcePath(String root, String module, String extension) {
		StringBuilder buff = new StringBuilder().append(root).append(IRepository.SEPARATOR).append(module);
		if (extension != null) {
			buff.append(extension);
		}
		String resourcePath = buff.toString();
		return resourcePath;
	}

	public ICollection getCollection(IRepository repository, String repositoryPath)
			throws IOException {
		final ICollection collection = repository.getCollection(repositoryPath);
		if (!collection.exists()) {
			final String logMsg = String.format(THERE_IS_NO_COLLECTION_AT_THE_SPECIFIED_SERVICE_PATH, collection.getName(), repositoryPath); 
			logger.error(logMsg);
			throw new IOException(logMsg);
		}
		return collection;
	}
	
	public IResource getResource(IRepository repository, String repositoryPath)
			throws IOException {
		final IResource resource = repository.getResource(repositoryPath);
		if (!resource.exists()) {
			final String logMsg = String.format(THERE_IS_NO_RESOURCE_AT_THE_SPECIFIED_SERVICE_PATH, resource.getName(), repositoryPath); 
			logger.error(logMsg);
			throw new IOException(logMsg);
		}
		return resource;
	}
}
