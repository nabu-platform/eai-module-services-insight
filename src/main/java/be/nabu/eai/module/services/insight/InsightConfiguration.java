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
}
