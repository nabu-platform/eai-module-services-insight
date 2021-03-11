package be.nabu.eai.module.services.insight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.module.services.crud.CRUDArtifactManager;
import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.CRUDService;
import be.nabu.eai.module.services.crud.Page;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.util.Filter;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.Scope;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.CalculationProperty;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.ForeignNameProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.RestrictProperty;
import be.nabu.libs.types.properties.ScopeProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;
import nabu.services.jdbc.Services;
import nabu.services.jdbc.Services.JDBCSelectResult;

public class InsightArtifact extends JAXBArtifact<InsightConfiguration> implements DefinedService {

	private DefinedStructure result;
	private Structure foreign;
	private Structure input, output;
	
	public InsightArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "insight.xml", InsightConfiguration.class);
	}

	Structure getForeign() {
		if (foreign == null) {
			synchronized(this) {
				if (foreign == null) {
					Structure foreign = new Structure();
					// we inject it into a placeholder but only use it if we actually add it
					CRUDArtifactManager.injectForeignFields(getConfig().getForeignFields(), getConfig().getCoreType(), getRepository(), foreign);
					
					for (Element<?> element : foreign) {
						Value<String> property = element.getProperty(ForeignNameProperty.getInstance());
						if (property != null && property.getValue() != null) {
							element.setProperty(new ValueImpl<String>(ForeignNameProperty.getInstance(), property.getValue() + "@" + getConfig().getCoreType().getId()));
						}
					}
					this.foreign = foreign;
				}
			}
		}
		return foreign;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Structure getInput() {
		if (input == null) {
			synchronized(this) {
				if (input == null) {
					Structure input = new Structure();
					input.setName("input");
					input.add(new SimpleElementImpl<String>("connectionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
							new ValueImpl<Scope>(ScopeProperty.getInstance(), Scope.PRIVATE)));
					input.add(new SimpleElementImpl<String>("transactionId", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
							new ValueImpl<Scope>(ScopeProperty.getInstance(), Scope.PRIVATE)));
					input.add(new SimpleElementImpl<Integer>("limit", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Long>("offset", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<String>("orderBy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					if (getConfig().getFilters() != null) {
						Structure filters = new Structure();
						filters.setName("filter");
						for (CRUDFilter filter : getConfig().getFilters()) {
							if (filter.isInput()) {
								Element<?> element = ((ComplexType) getConfig().getCoreType()).get(filter.getKey());
								// if the element does not exist in the core type, it may have been added via foreign fields
								if (element == null) {
									element = getForeign().get(filter.getKey());
								}
								// old filters might still have outdated values
								if (element != null) {
									// in most cases, the input type is the same as the element type _except_ when we are doing "is null" or "is not null" checks
									// in such scenarios, the resulting input field (if it is indeed marked as an input) is a boolean indicating whether or not you want the additional query to be active
									SimpleElementImpl childElement;
									// every other "new operator" that you typed is also considered to be an "is" check, like if you manually type "> current_timestamp"
									if ("is null".equals(filter.getOperator()) || "is not null".equals(filter.getOperator()) || !CRUDService.operators.contains(filter.getOperator())) {
										childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<Boolean>) SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class), filters, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));	
									}
									else {
										childElement = new SimpleElementImpl(filter.getAlias() == null ? filter.getKey() : filter.getAlias(), (SimpleType<?>) element.getType(), filters, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
									}
									// only for some filters do we support the list entries
									if ("=".equals(filter.getOperator()) || "<>".equals(filter.getOperator())) {
										// for boolean data types a list also makes little sense
										if (!Boolean.class.isAssignableFrom(((SimpleType<?>) childElement.getType()).getInstanceClass())) {
											childElement.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
										}
									}
									filters.add(childElement);
								}
							}
						}
						input.add(new ComplexElementImpl("filter", filters, input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					}
					this.input = input;
				}
			}
		}
		return input;
	}
	
	private Structure getOutput() {
		if (output == null) {
			synchronized(this) {
				if (output == null) {
					Structure output = new Structure();
					output.setName("output");
					DefinedType resolve = BeanResolver.getInstance().resolve(Page.class);
					output.add(new ComplexElementImpl("results", getResult(), output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0), new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					output.add(new ComplexElementImpl("page", (ComplexType) resolve, output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					this.output = output;
				}
			}
		}
		return output;
	}
	
	@Override
	public ServiceInterface getServiceInterface() {
		return new ServiceInterface() {
			@Override
			public ComplexType getInputDefinition() {
				return getInput();
			}

			@Override
			public ComplexType getOutputDefinition() {
				return getOutput();
			}
			@Override
			public ServiceInterface getParent() {
				return null;
			}
		};
	}

	@Override
	public ServiceInstance newInstance() {
		return new ServiceInstance() {
			@Override
			public Service getDefinition() {
				return InsightArtifact.this;
			}
			@SuppressWarnings("unchecked")
			@Override
			public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
				String connectionId = input == null ? null : (String) input.get("connectionId");
				String transactionId = input == null ? null : (String) input.get("transactionId");
				
				// if we have configured a connection id, use that
				if (connectionId == null && getConfig().getConnection() != null) {
					connectionId = getConfig().getConnection().getId();
				}
				
				List<Filter> filters = new ArrayList<Filter>();
				if (getConfig().getFilters() != null) {
					CRUDService.transformFilters(getConfig().getFilters(), input, filters);
				}
				
				List<String> groupBy = new ArrayList<String>();
				for (InsightField field : getConfig().getFields()) {
					if ("group by".equals(field.getAggregate()) || field.getAggregate() == null) {
						groupBy.add(field.getAlias() == null ? field.getKey() : field.getAlias());
					}
				}
				
				JDBCSelectResult selectFiltered = Services.selectFiltered(
					connectionId, 
					transactionId, 
					getResult().getId(), 
					input == null ? null : (Long) input.get("offset"), 
					input == null ? null : (Integer) input.get("limit"), 
					input == null ? null : (List<String>) input.get("orderBy"), 
					input == null ? null : (Boolean) input.get("totalRowCount"), 
					false, 
					filters, 
					null,
					executionContext,
					groupBy,
					null,
					null
				);
				
				ComplexContent output = getServiceInterface().getOutputDefinition().newInstance();
				output.set("results", selectFiltered.getResults());
				if (selectFiltered.getTotalRowCount() != null) {
					output.set("page", Page.build(selectFiltered.getTotalRowCount(), input == null ? null : (Long) input.get("offset"), input == null ? null : (Integer) input.get("limit")));
				}
				return output;
			}
		};
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	DefinedStructure getResult() {
		if (result == null) {
			synchronized(this) {
				if (result == null) {
					DefinedStructure result = new DefinedStructure();
					result.setSuperType(getConfig().getCoreType());
					// we default restrict all fields! but we want the extension for further lookups
					String restrict = null;
					for (Element<?> child : TypeUtils.getAllChildren((ComplexType) getConfig().getCoreType())) {
						if (restrict == null) {
							restrict = "";
						}
						else {
							restrict += ",";
						}
						restrict += child.getName();
					}
					result.setProperty(new ValueImpl<String>(RestrictProperty.getInstance(), restrict));
					
					buildStructure(result, this);
					String id = getId() + ".results";
					result.setId(id);
					result.setName(getConfig().getCoreType() == null ? "result" : getConfig().getCoreType().getName());
					String collectionName = getConfig().getCoreType() == null ? null : ValueUtils.getValue(CollectionNameProperty.getInstance(), getConfig().getCoreType().getProperties());
					if (collectionName != null) {
						result.setProperty(new ValueImpl<String>(CollectionNameProperty.getInstance(), collectionName));
					}
					this.result = result;
				}
			}
		}
		return result;
	}
	
	private void buildStructure(Structure structure, InsightArtifact artifact) {
		Structure placeholder = artifact.getForeign();
		
		for (InsightField field : artifact.getConfig().getFields()) {
			String alias = field.getAlias();
			if (alias == null) {
				alias = field.getKey();
			}
			else {
				alias = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.UNDERSCORE.apply(alias));
			}
			
			String aggregate = field.getAggregate();
			
			boolean isForeign = false;
			Element<?> element = ((ComplexType) artifact.getConfig().getCoreType()).get(field.getKey());
			if (element == null) {
				element = placeholder.get(field.getKey());
				isForeign = true;
			}
					
			if (element != null) {
				Element<?> clone = clone(element, structure, field.getAggregate());
				clone.setProperty(new ValueImpl<String>(NameProperty.getInstance(), alias));
				// if you set an alias, we need the original field for aggregation
				if (!alias.equals(field.getKey()) && !isForeign) {
					clone.setProperty(new ValueImpl<String>(ForeignNameProperty.getInstance(), field.getKey() + "@" + getConfig().getCoreType().getId()));
				}
				// if we have an aggregate that is not a group by, add it
				if (aggregate != null && !"group by".equals(aggregate)) {
					clone.setProperty(new ValueImpl<String>(CalculationProperty.getInstance(), aggregate));
				}
				structure.add(clone);
			}
		}
	}
	
	private Element<?> clone(Element<?> element, Structure structure, String aggregate) {
		// a count always transform into a number, the rest inherit the type of the original
		if ("count".equals(aggregate)) {
			return new SimpleElementImpl<Long>(element.getName(), SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), structure, element.getProperties());
		}
		else {
			return TypeBaseUtils.clone(element, structure);
		}
	}

}
