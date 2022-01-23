package be.nabu.eai.module.services.insight;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.libs.artifacts.api.DataSourceProviderArtifact;
import be.nabu.libs.types.api.DefinedType;

@XmlRootElement(name = "insight")
public class InsightConfiguration {
	// the data type we are wrapping around
	private DefinedType coreType;
	private DataSourceProviderArtifact connection;
	private List<InsightField> fields;
	private List<ForeignNameField> foreignFields;
	private List<CRUDFilter> filters;
	// we can set a custom name
	private String basePath, name;
	// the roles that can run this
	private List<String> role;
	// the field we want to use to check security context
	private String securityContextField;
	private boolean allowHeaderAsQueryParameter = true;
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedType getCoreType() {
		return coreType;
	}
	public void setCoreType(DefinedType coreType) {
		this.coreType = coreType;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DataSourceProviderArtifact getConnection() {
		return connection;
	}
	public void setConnection(DataSourceProviderArtifact connection) {
		this.connection = connection;
	}
	
	public List<InsightField> getFields() {
		if (fields == null) {
			fields = new ArrayList<InsightField>();
		}
		return fields;
	}
	public void setFields(List<InsightField> fields) {
		this.fields = fields;
	}
	public List<ForeignNameField> getForeignFields() {
		if (foreignFields == null) {
			foreignFields = new ArrayList<ForeignNameField>();
		}
		return foreignFields;
	}
	public void setForeignFields(List<ForeignNameField> foreignFields) {
		this.foreignFields = foreignFields;
	}
	
	public List<CRUDFilter> getFilters() {
		if (filters == null) {
			filters = new ArrayList<CRUDFilter>();
		}
		return filters;
	}
	public void setFilters(List<CRUDFilter> filters) {
		this.filters = filters;
	}
	public String getBasePath() {
		return basePath;
	}
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	public String getSecurityContextField() {
		return securityContextField;
	}
	public void setSecurityContextField(String securityContextField) {
		this.securityContextField = securityContextField;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<String> getRole() {
		return role;
	}
	public void setRole(List<String> role) {
		this.role = role;
	}
	public boolean isAllowHeaderAsQueryParameter() {
		return allowHeaderAsQueryParameter;
	}
	public void setAllowHeaderAsQueryParameter(boolean allowHeaderAsQueryParameter) {
		this.allowHeaderAsQueryParameter = allowHeaderAsQueryParameter;
	}
}
