/*
* Copyright (C) 2020 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.insight;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.module.rest.RESTUtils;
import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebApplicationUtils;
import be.nabu.libs.authentication.api.Authenticator;
import be.nabu.libs.authentication.api.Device;
import be.nabu.libs.authentication.api.PermissionHandler;
import be.nabu.libs.authentication.api.PotentialPermissionHandler;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.glue.GlueListener;
import be.nabu.libs.http.glue.GlueListener.PathAnalysis;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.binding.api.MarshallableBinding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class InsightListener implements EventHandler<HTTPRequest, HTTPResponse> {

	private String parentPath;
	private String childPath;
	private Charset charset;
	private InsightArtifact artifact;
	private WebApplication application;
	private PathAnalysis pathAnalysis;

	public InsightListener(WebApplication application, InsightArtifact insightArtifact, String parentPath, String childPath, Charset charset) {
		this.application = application;
		this.artifact = insightArtifact;
		this.parentPath = parentPath;
		this.childPath = childPath;
		this.charset = charset;
		if (childPath.startsWith("/")) {
			childPath = childPath.substring(1);
		}
		this.pathAnalysis = GlueListener.analyzePath(childPath);
	}

	@Override
	public HTTPResponse handle(HTTPRequest request) {
		// stop fast if wrong method
		if (!"GET".equalsIgnoreCase(request.getMethod())) {
			return null;
		}
		Token token = null;
		Device device = null;
		try {
			ServiceRuntime.setGlobalContext(new HashMap<String, Object>());
			
			URI uri = HTTPUtils.getURI(request, false);
			String path = URIUtils.normalize(uri.getPath());
			// not in this web artifact
			if (!path.startsWith(parentPath)) {
				return null;
			}
			path = path.substring(parentPath.length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			Map<String, String> pathParameters = pathAnalysis.analyze(path);
			// not in this rest path
			if (pathParameters == null) {
				return null;
			}
			
			// if we have chosen this rest service, check if the server is offline
			WebApplicationUtils.checkOffline(application, request);
			
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			
			token = WebApplicationUtils.getToken(application, request);
			device = WebApplicationUtils.getDevice(application, request, token);

			ServiceRuntime.getGlobalContext().put("device", device);
			ServiceRuntime.getGlobalContext().put("service.context", application.getId());
			
			if (artifact.getConfig().getRole() != null) {
				WebApplicationUtils.checkRole(application, token, artifact.getConfig().getRole());
			}
			
//			HTTPResponse checkRateLimits = WebApplicationUtils.checkRateLimits(application, token, device, artifact.getConfig().getListPermission() == null ? service.getId() : artifact.getConfig().getListPermission(), null, request);
			HTTPResponse checkRateLimits = WebApplicationUtils.checkRateLimits(application, token, device, artifact.getId(), null, request);
			if (checkRateLimits != null) {
				return checkRateLimits;
			}
			
			Header contentTypeHeader = MimeUtils.getHeader("Content-Type", request.getContent().getHeaders());
			String contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue().trim().replaceAll(";.*$", "");

			PermissionHandler permissionHandler = application.getPermissionHandler();
			String action = artifact.getPermissionAction();
			String context = null;
			String parentQueryName = null;
			if (artifact.getConfig().getFilters() != null) {
				Element<?> securityContext = artifact.getSecurityContext();
				if (securityContext != null) {
					for (CRUDFilter filter : artifact.getConfig().getFilters()) {
						if (filter != null && filter.getKey() != null) {
							if (securityContext.getName().equals(filter.getKey())) {
								parentQueryName = filter.getAlias() == null ? filter.getKey() : filter.getAlias();
								break;
							}
						}
					}
				}
			}
			if (parentQueryName != null) {
				context = pathParameters.get("contextId");
			}
			if (permissionHandler != null) {
				if (parentQueryName == null && artifact.hasSecurityContextFilter()) {
					throw new HTTPException(400, "A security context id is required");
				}
				
				if (action != null && !permissionHandler.hasPermission(token, context, action)) {
					boolean allowed = false;
					// if you specifically did not select a security field, we can check the potential permissions as well
					if (artifact.getConfig().getSecurityContextField() == null) {
						PotentialPermissionHandler potentialPermissionHandler = application.getPotentialPermissionHandler();
						if (potentialPermissionHandler != null) {
							allowed = potentialPermissionHandler.hasPotentialPermission(token, action);
						}
					}
					if (!allowed) {
						throw new HTTPException(token == null ? 401 : 403, "User does not have permission to execute the rest service", "User '" + (token == null ? Authenticator.ANONYMOUS : token.getName()) + "' does not have permission to run the CRUD service: " + artifact.getId(), token);
					}
				}
			}
			
			List<Header> headers = new ArrayList<Header>();
			
			ComplexContent input = artifact.getServiceInterface().getInputDefinition().newInstance();
			// limit to the user if we have a permission handler
			// if we don't have one configured, it is not enforced on the other actions either and it could backfire trying to force it here
			List<String> limit = queryProperties.get("limit");
			if (limit != null && !limit.isEmpty()) {
				input.set("limit", limit.get(0));
			}
			List<String> offset = queryProperties.get("offset");
			if (offset != null && !offset.isEmpty()) {
				input.set("offset", offset.get(0));
			}
			List<String> orderBy = queryProperties.get("orderBy");
			if (orderBy != null && !orderBy.isEmpty()) {
				input.set("orderBy", orderBy);
			}
			if (artifact.getConfig().getFilters() != null) {
				for (CRUDFilter filter : artifact.getConfig().getFilters()) {
					if (filter != null && filter.isInput() && filter.getKey() != null) {
						List<String> list = queryProperties.get(filter.getAlias() == null ? filter.getKey() : filter.getAlias());
						if (list != null && !list.isEmpty()) {
							input.set("filter/" + (filter.getAlias() == null ? filter.getKey() : filter.getAlias()), list);
						}
					}
				}
			}
			if (parentQueryName != null) {
				input.set("filter/" + parentQueryName + "[0]", context);
			}
			
			ExecutionContext executionContext = application.getRepository().newExecutionContext(token);
			ServiceRuntime runtime = new ServiceRuntime(artifact, executionContext);
			// we set the service context to the web application, rest services can be mounted in multiple applications
			ServiceUtils.setServiceContext(runtime, application.getId());
			runtime.getContext().put("webApplicationId", application.getId());
			ComplexContent output = runtime.run(input);
			
			if (output != null) {
				if (artifact.getConfig().isAllowHeaderAsQueryParameter()) {
					WebApplicationUtils.queryToHeader(request, queryProperties);
				}
				
				MarshallableBinding binding = RESTUtils.getOutputBinding(request, output.getType(), charset, "application/json", false, false);
				
				if (binding == null) {
					throw new HTTPException(500, "Unsupported response content types: " + MimeUtils.getAcceptedContentTypes(request.getContent().getHeaders()));
				}
				contentType = RESTUtils.getContentTypeFor(binding);
				
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				binding.marshal(content, (ComplexContent) output);
				byte[] byteArray = content.toByteArray();
				headers.add(new MimeHeader("Content-Length", "" + byteArray.length));
				headers.add(new MimeHeader("Content-Type", contentType + "; charset=" + charset.name()));
				
				Map<String, String> values = MimeUtils.getHeaderAsValues("Accept-Content-Disposition", request.getContent().getHeaders());
				// we are asking for an attachment download
				if (values.get("value") != null && values.get("value").equalsIgnoreCase("attachment")) {
					String fileName = values.get("filename");
					if (fileName != null) {
						fileName = fileName.replaceAll("[^\\w.-]+", "");
					}
					else {
						fileName = "unnamed";
					}
					headers.add(new MimeHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\""));
				}
				
				PlainMimeContentPart part = new PlainMimeContentPart(null,
					IOUtils.wrap(byteArray, true),
					headers.toArray(new Header[headers.size()])
				);
				return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), part);
			}
			else {
				return new DefaultHTTPResponse(request, 200, HTTPCodes.getMessage(200), new PlainMimeEmptyPart(null, 
					new MimeHeader("Content-Length", "0")));
			}
		}
		catch (HTTPException e) {
			if (e.getToken() == null) {
				e.setToken(token);
			}
			if (e.getDevice() == null) {
				e.setDevice(device);
			}
			e.getContext().addAll(Arrays.asList(artifact.getId()));
			throw e;
		}
		catch (Exception e) {
			HTTPException httpException = new HTTPException(500, "Could not execute service", "Could not execute service: " + artifact.getId(), e, token);
			httpException.getContext().addAll(Arrays.asList(artifact.getId()));
			httpException.setDevice(device);
			throw httpException;
		}
		finally {
			ServiceRuntime.setGlobalContext(null);
		}
	}

}
