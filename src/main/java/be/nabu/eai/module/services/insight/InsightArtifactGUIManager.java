package be.nabu.eai.module.services.insight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.crud.CRUDArtifactGUIManager;
import be.nabu.eai.module.services.crud.CRUDArtifactGUIManager.Redrawer;
import be.nabu.eai.module.services.crud.CRUDConfiguration.ForeignNameField;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
	protected void display(InsightArtifact instance, Pane pane) {
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
		pane.getChildren().add(root);
	}
	
	private void drawFields(VBox target, InsightArtifact insight) {
		target.getChildren().clear();
		for (InsightField field : insight.getConfig().getFields()) {
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
			
			HBox.setMargin(aggregate, new Insets(0, 0, 0, 5));
			HBox.setMargin(fields, new Insets(0, 0, 0, 5));
			
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
	
}
