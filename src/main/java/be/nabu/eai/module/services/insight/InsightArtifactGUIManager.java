package be.nabu.eai.module.services.insight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.crud.CRUDArtifactGUIManager;
import be.nabu.eai.module.services.crud.CRUDArtifactGUIManager.Redrawer;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.validator.api.Validation;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Insight foreign fields are slightly trickier
 * You mostly want to aggregate results, but any fields you have with a foreign key are likely pointing to a 1-sided relation from your *
 * For example suppose you want to aggregate over order lines and you have an order id foreign key
 * If you import that remote field, you _have_ to group by it?
 */
public class InsightArtifactGUIManager extends BaseJAXBGUIManager<InsightConfiguration, InsightArtifact> {

	private VBox chart;
	private InsightArtifact instance;

	public InsightArtifactGUIManager() {
		super("Insight", InsightArtifact.class, new InsightArtifactManager(), InsightConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(new SimpleProperty<DefinedType>("Type", DefinedType.class, true));
	}

	@Override
	protected InsightArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		InsightArtifact artifact = new InsightArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		if (values != null && values.length > 0) {
			for (Value<?> value : values) {
				if (value.getValue() instanceof DefinedType) {
					artifact.getConfig().setCoreType((DefinedType) value.getValue());
				}
			}
		}
		if (artifact.getConfig().getCoreType() == null) {
			throw new IllegalStateException("You need to define a type");
		}
		return artifact;
	}

	@Override
	public String getCategory() {
		return "Services";
	}
	

	@Override
	protected BaseArtifactGUIInstance<InsightArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<InsightArtifact>(this, entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				List<Validation<?>> save = super.save();
				MainController.getInstance().submitTask("Redraw chart", "Redrawing chart for " + entry.getId(), new Runnable() {
					@Override
					public void run() {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								drawChart(chart, instance);
							}
						});
					}
				}, 1000);
				return save;
			}
		};
	}

	@Override
	protected void display(InsightArtifact instance, Pane pane) {
		this.instance = instance;
		SplitPane split = new SplitPane();
		// give most screen real estate to the settings
		split.setDividerPositions(0.7);
		
		AnchorPane.setBottomAnchor(split, 0d);
		AnchorPane.setTopAnchor(split, 0d);
		AnchorPane.setLeftAnchor(split, 0d);
		AnchorPane.setRightAnchor(split, 0d);
		
		ScrollPane left = new ScrollPane();
		left.setFitToWidth(true);
		left.setFitToHeight(true);
		
		ScrollPane right = new ScrollPane();
		right.setFitToWidth(true);
		right.setFitToHeight(true);
		right.setHbarPolicy(ScrollBarPolicy.NEVER);
		
		VBox root = new VBox();
		root.setPadding(new Insets(20));
		
		VBox filters = new VBox();
		CRUDArtifactGUIManager.drawFilters(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), instance.getConfig().getFilters(), filters, new Redrawer() {
			@Override
			public void redraw() {
				pane.getChildren().clear();
				display(instance, pane);
			}
		}, false);
		root.getChildren().add(filters);
		VBox.setMargin(filters, new Insets(0, 0, 10, 0));
		
		VBox fields = new VBox();
		fields.getStyleClass().addAll("section", "block");
		Label label = new Label("Aggregated Fields");
		label.getStyleClass().addAll("section-title", "h1");
		fields.getChildren().add(label);
		
		VBox list = new VBox();
		drawFields(list, instance);
		fields.getChildren().add(list);
		
		// have a button to add a filter
		Button add = new Button("Field");
		add.setGraphic(MainController.loadFixedSizeGraphic("icons/add.png", 12));
		add.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				instance.getConfig().getFields().add(new InsightField());
				// redraw this section
				list.getChildren().clear();
				drawFields(list, instance);
				MainController.getInstance().setChanged();
			}
		});
		HBox buttons = new HBox();
		buttons.getStyleClass().add("buttons");
		buttons.getChildren().addAll(add);
		fields.getChildren().add(buttons);
		
		VBox foreign = new VBox();
		CRUDArtifactGUIManager.drawForeignNameFields(instance.getConfig().getForeignFields(), instance.getConfig().getCoreType(), instance.getRepository(), foreign);
		
		root.getChildren().addAll(fields, foreign);
		left.setContent(root);
		split.getItems().addAll(left, right);
		pane.getChildren().add(split);
		
		chart = new VBox();
		right.setContent(chart);
		drawChart(chart, instance);
	}
	
	private void drawFields(VBox target, InsightArtifact insight) {
		target.getChildren().clear();
		List<InsightField> insightFields = insight.getConfig().getFields();
		for (InsightField field : insightFields) {
			ComboBox<String> fields = new ComboBox<String>();
			// we don't allow you to select the foreign fields, they will always be added and a "group by" will be done on them (for now)
			fields.getItems().addAll(fields(insight, true));
			
			if (field.getKey() != null && fields.getItems().contains(field.getKey())) {
				fields.getSelectionModel().select(field.getKey());
			}
			fields.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					field.setKey(arg2);
					MainController.getInstance().setChanged();
				}
			});
			
			HBox row = new HBox();
			
			TextField alias = new TextField();
			alias.setPromptText("Field Alias");
			row.getChildren().add(alias);
			alias.setText(field.getAlias());
			alias.textProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					field.setAlias(arg2 != null && !arg2.trim().isEmpty() ? arg2.trim() : null);
					MainController.getInstance().setChanged();
				}
			});
			
			ComboBox<String> aggregate = new ComboBox<String>();
			// we might want to get the available aggregates from the dialect for the current connection?
			// we need three things: a label, an actual function and an optional description (for more complex functions)
			// the function is what needs to end up in the insight field, in the future we can use a complex object with a label for the dropdown while still remaining backwards compatible by putting the function in the field
			// especially postgres has a number of additional aggregates that can be nice to use but definitely not portable
			// STDEV (mssql), stddev (postgres, oracle) is also rather widely available, stddev in postgres is an alias for STDDEV_SAMP which also exists in h2
			aggregate.getItems().addAll("avg", "count", "group by", "max", "min", "sum");
			aggregate.getSelectionModel().select(field.getAggregate());
			row.getChildren().add(aggregate);
			
			aggregate.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					field.setAggregate(arg2);
					MainController.getInstance().setChanged();
				}
			});
			
			row.getChildren().add(fields);
			
			HBox buttons = new HBox();
			Button remove = new Button();
			remove.setGraphic(MainController.loadFixedSizeGraphic("icons/delete.png", 12));
			remove.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					insightFields.remove(field);
					// redraw this section
					drawFields(target, insight);
					MainController.getInstance().setChanged();
				}
			});
			Button up = new Button();
			up.setGraphic(MainController.loadFixedSizeGraphic("move/up.png", 12));
			up.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					int indexOf = insightFields.indexOf(field);
					if (indexOf > 0) {
						insightFields.remove(indexOf);
						insightFields.add(indexOf - 1, field);
					}
					// redraw this section
					drawFields(target, insight);
					MainController.getInstance().setChanged();
				}
			});
			
			Button down = new Button();
			down.setGraphic(MainController.loadFixedSizeGraphic("move/down.png", 12));
			down.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					int indexOf = insightFields.indexOf(field);
					if (indexOf < insightFields.size() - 1) {
						insightFields.remove(indexOf);
						insightFields.add(indexOf + 1, field);
					}
					// redraw this section
					drawFields(target, insight);
					MainController.getInstance().setChanged();
				}
			});
			
			buttons.getChildren().addAll(up, down, remove);
			row.getChildren().add(buttons);
			
			HBox.setMargin(aggregate, new Insets(0, 0, 0, 10));
			HBox.setMargin(fields, new Insets(0, 0, 0, 10));
			HBox.setMargin(buttons, new Insets(0, 0, 0, 10));
			VBox.setMargin(row, new Insets(10, 0, 0, 0));
			
			target.getChildren().add(row);
		}
	}
	
	private List<String> fields(InsightArtifact instance, boolean includeForeignFields) {
		List<String> list = new ArrayList<String>();
		for (Element<?> child : TypeUtils.getAllChildren((ComplexType) instance.getConfig().getCoreType())) {
			if (child.getType() instanceof SimpleType) {
				list.add(child.getName());
			}
		}
		if (includeForeignFields && instance.getConfig().getForeignFields() != null) {
			for (ForeignNameField foreign : instance.getConfig().getForeignFields()) {
				if (foreign.getLocalName() != null) {
					list.add(foreign.getLocalName());
				}
			}
		}
		Collections.sort(list);
		return list;
	}
	
	private void drawChart(VBox container, InsightArtifact artifact) {
		container.getChildren().clear();
		if (artifact.getConfig().getFields() != null && !artifact.getConfig().getFields().isEmpty()) {
			try {
				ComplexContent input = artifact.getServiceInterface().getInputDefinition().newInstance();
				Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(artifact, EAIResourceRepository.getInstance().newExecutionContext(SystemPrincipal.ROOT), input);
				ServiceResult serviceResult = run.get();
				if (serviceResult.getException() != null) {
					throw serviceResult.getException();
				}
				final CategoryAxis xAxis = new CategoryAxis();
		        final NumberAxis yAxis = new NumberAxis();
		        final BarChart<String,Number> barChart = new BarChart<String,Number>(xAxis,yAxis);
		        barChart.setTitle("Chart");
		        barChart.setCategoryGap(10);
		        barChart.setBarGap(2);
		        xAxis.setLabel("Group");       
		        yAxis.setLabel("Value");
		        container.getChildren().add(barChart);
		        Map<String, XYChart.Series<String, Number>> series = new LinkedHashMap<String, XYChart.Series<String, Number>>();
		        List<String> keyFields = new ArrayList<String>();
		        List<String> valueFields = new ArrayList<String>();
		        for (InsightField field : artifact.getConfig().getFields()) {
		        	if (field.getAggregate() == null || "group by".equals(field.getAggregate())) {
		        		keyFields.add(field.getAlias() == null ? field.getKey() : field.getAlias());
		        	}
		        	else {
		        		String valueField = field.getAlias() == null ? field.getKey() : field.getAlias();
						String cleanedUpValueField = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.UNDERSCORE.apply(valueField));
						valueFields.add(cleanedUpValueField);
		        		Series<String, Number> serie = new XYChart.Series<String, Number>();
		        		serie.setName(NamingConvention.UPPER_TEXT.apply(NamingConvention.UNDERSCORE.apply(valueField)));
						series.put(cleanedUpValueField, serie);
		        		barChart.getData().add(serie);
		        	}
		        }
				List<Object> list = (List<Object>) serviceResult.getOutput().get("results");
				if (list != null) {
					for (int i = 0; i < list.size(); i++) {
						Object object = list.get(i);
						ComplexContent content = (ComplexContent) object;
						if (content != null) {
							String key = "";
							// if there are no key fields, you are not grouping at all
							if (keyFields.isEmpty()) {
								key = "All";
							}
							else {
								for (String keyField : keyFields) {
									Object result = content.get(keyField);
									if (result != null) {
										if (!key.isEmpty()) {
											key += ", ";
										}
										key += result;
									}
								}
							}
							if (!key.isEmpty()) {
								for (String valueField : valueFields) {
									Object result = content.get(valueField);
									if (result instanceof Number) {
										Data<String, Number> data = new XYChart.Data<String, Number>(key, (Number) result);
										series.get(valueField).getData().add(data);
										new CustomTooltip(key + " = " + result).install(data.getNode());
									}
								}
							}
						}
					}
				}
			}
			catch (Exception e) {
				MainController.getInstance().notify(e);
			}
		}
	}
}
