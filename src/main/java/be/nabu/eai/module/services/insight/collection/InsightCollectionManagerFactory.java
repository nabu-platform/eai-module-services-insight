package be.nabu.eai.module.services.insight.collection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.CollectionActionImpl;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionAction;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.api.CollectionManagerFactory;
import be.nabu.eai.developer.api.EntryAcceptor;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.services.insight.InsightArtifact;
import be.nabu.eai.module.services.insight.InsightArtifactManager;
import be.nabu.eai.repository.CollectionImpl;
import be.nabu.eai.repository.api.Collection;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.DataSourceProviderArtifact;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.SynchronizableTypeRegistry;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeRegistry;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class InsightCollectionManagerFactory implements CollectionManagerFactory {

	@Override
	public CollectionManager getCollectionManager(Entry entry) {
		if (entry.isNode() && InsightArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			return new InsightCollectionManager(entry);
		}
		return null;
	}
	
	public static class ComboItem {
		private String display;
		private Object content;
		public ComboItem(String display, Object content) {
			this.display = display;
			this.content = content;
		}
		public String getDisplay() {
			return display;
		}
		public void setDisplay(String display) {
			this.display = display;
		}
		public Object getContent() {
			return content;
		}
		public void setContent(Object content) {
			this.content = content;
		}
		@Override
		public String toString() {
			return display;
		}
	}

	@Override
	public List<CollectionAction> getActionsFor(Entry entry) {
		List<CollectionAction> actions = new ArrayList<CollectionAction>();
		// if it is a valid application, we want to be able to add to it
		if (MainController.getInstance().newCollectionManager(entry) instanceof ApplicationManager) {
			actions.add(new CollectionActionImpl(EAICollectionUtils.newActionTile("insight-large.png", "Add Insight", "Interpret the data you have collected."), build(entry), new EntryAcceptor() {
				@Override
				public boolean accept(Entry entry) {
					Collection collection = entry.getCollection();
					return collection != null && "folder".equals(collection.getType()) && "insights".equals(collection.getSubType());
				}
			}));
		}
		return actions;
	}
	
	private EventHandler<ActionEvent> build(Entry entry) {
		return new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				Map<String, List<TypeRegistry>> registries = new HashMap<String, List<TypeRegistry>>();
				ComboBox<ComboItem> databases = new ComboBox<ComboItem>();
				Entry project = EAICollectionUtils.getProject(entry);
				for (DataSourceProviderArtifact database : project.getRepository().getArtifacts(DataSourceProviderArtifact.class)) {
					// in this project
					if (database.getId().startsWith(project.getId() + ".")) {
						// we expect a data model to be nearby, otherwise we can't really do much
						Entry databaseEntry = entry.getRepository().getEntry(database.getId());
						boolean hasRegistrySibling = false;
						for (Entry child : databaseEntry.getParent()) {
							try {
								if (child.isNode() && TypeRegistry.class.isAssignableFrom(child.getNode().getArtifactClass())) {
									hasRegistrySibling = true;
									if (!registries.containsKey(database.getId())) {
										registries.put(database.getId(), new ArrayList<TypeRegistry>());
									}
									registries.get(database.getId()).add((TypeRegistry) child.getNode().getArtifact());
									hasRegistrySibling = true;
								}
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}
						if (hasRegistrySibling) {
							databases.getItems().add(new ComboItem(EAICollectionUtils.getPrettyName(databaseEntry.getParent()), databaseEntry));
						}
					}
				}
				
				Button create = new Button("Create");
				create.setDisable(true);
				create.getStyleClass().add("primary");
				
				Button cancel = new Button("Cancel");
											
				HBox buttons = new HBox();
				buttons.getStyleClass().add("buttons");
				buttons.getChildren().addAll(create, cancel);
				
				VBox root = new VBox();
				Stage stage = EAIDeveloperUtils.buildPopup("Create Insight", root, MainController.getInstance().getActiveStage(), StageStyle.DECORATED, false);
				
				// we want to be able to add multiple CRUD at once, we use checkboxes
				// if you already have a CRUD with the type name, we assume you generated it properly and we don't offer it as a possibility anymore
				VBox options = new VBox();
				Map<String, CheckBox> boxes = new HashMap<String, CheckBox>();
				
				databases.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ComboItem>() {
					@Override
					public void changed(ObservableValue<? extends ComboItem> arg0, ComboItem arg1, ComboItem arg2) {
						options.getChildren().clear();
						if (arg2 != null) {
							List<String> alreadyTaken = new ArrayList<String>();
							// first we discover what we already have
							Entry databaseEntry = (Entry) arg2.getContent();
							boxes.clear();
							// now we loop over the available type in the available registries and suggest those that have not been crudded yet!
							for (TypeRegistry typeRegistry : registries.get(databaseEntry.getId())) {
								for (String namespace : typeRegistry.getNamespaces()) {
									for (ComplexType type : typeRegistry.getComplexTypes(namespace)) {
										// we only bother with defined types (for now?)
										if (type instanceof DefinedType) {
											// if we can synchronize it, do it!
											if (!(typeRegistry instanceof SynchronizableTypeRegistry) || ((SynchronizableTypeRegistry) typeRegistry).isSynchronizable(type)) {
												// it musn't already exist
												if (!alreadyTaken.contains(((DefinedType) type).getId())) {
													CheckBox checkBox = new CheckBox(EAICollectionUtils.getPrettyName(type));
													boxes.put(((DefinedType) type).getId(), checkBox);
													options.getChildren().add(checkBox);
												}
											}
										}
									}
								}
							}
							if (boxes.isEmpty()) {
								Label label = new Label("There are no types available");
								label.getStyleClass().add("p");
								options.getChildren().add(label);
								create.setDisable(true);
							}
							else {
								create.setDisable(false);
							}
						}
						stage.sizeToScene();
					}
				});
				
				root.getStyleClass().add("popup-form");
				Label label = new Label("Create Insight");
				label.getStyleClass().add("h1");
				root.getChildren().addAll(label);
				
				TextField nameText = new TextField();
				if (databases.getItems().isEmpty()) {
					Label noDatabase = new Label("You don't have a database yet in this project, add one first");
					noDatabase.getStyleClass().add("p");
					root.getChildren().add(noDatabase);
				}
				else {
					root.getChildren().add(EAIDeveloperUtils.newHBox("Name", nameText));
					root.getChildren().add(EAIDeveloperUtils.newHBox("Database", databases));
				}
				
				ScrollPane scroll = new ScrollPane();
				scroll.setContent(options);
				scroll.setMaxHeight(400);
				scroll.setFitToWidth(true);
				scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
				
				root.getChildren().addAll(scroll, buttons);
				
				cancel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						stage.hide();
					}
				});

				create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						Entry databaseEntry = (Entry) databases.getSelectionModel().getSelectedItem().getContent();
						try {
							Entry crudDatabaseEntry = getInsightDatabaseEntry((RepositoryEntry) entry, databaseEntry);
							
							for (Map.Entry<String, CheckBox> box : boxes.entrySet()) {
								if (box.getValue().isSelected()) {
									String typeId = box.getKey();
									String prettyName = nameText.getText();
									String name;
									if (prettyName == null || prettyName.trim().isEmpty()) {
										name = typeId.replaceAll("^.*\\.", "");
										prettyName = EAICollectionUtils.getPrettyName((Type) entry.getRepository().resolve(typeId));
									}
									else {
										name = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.UNDERSCORE.apply(prettyName));
									}
									int counter = 1;
									String finalName = name;
									while (crudDatabaseEntry.getChild(finalName) != null) {
										finalName = name + counter++;
									}
									RepositoryEntry crudEntry = ((RepositoryEntry) crudDatabaseEntry).createNode(finalName, new InsightArtifactManager(), true);
									InsightArtifact artifact = new InsightArtifact(crudEntry.getId(), crudEntry.getContainer(), crudEntry.getRepository());
									artifact.getConfig().setCoreType((DefinedType) entry.getRepository().resolve(typeId));
									// best effort set the jdbc connection, this shouldn't fail...?
									try {
										artifact.getConfig().setConnection((DataSourceProviderArtifact) databaseEntry.getNode().getArtifact());
									}
									catch (Exception e) {
										MainController.getInstance().notify(e);
									}
									new InsightArtifactManager().save(crudEntry, artifact);
									if (!prettyName.equals(name)) {
										crudEntry.getNode().setName(prettyName);
										crudEntry.saveNode();
									}
									EAIDeveloperUtils.created(crudEntry.getId());
									// we hard reload the crud entry to make sure we see the new services
									Platform.runLater(new Runnable() {
										@Override
										public void run() {
											MainController.getInstance().getRepository().reload(crudEntry.getId());
											EAIDeveloperUtils.reload(crudEntry.getId(), true);
										}
									});
								}
							}
						}
						catch (Exception e) {
							MainController.getInstance().notify(e);
						}
						stage.hide();
					}
				});
				stage.show();
				stage.sizeToScene();
			}
		};
	}
	
	// the actual database entry?
	private Entry getInsightDatabaseEntry(RepositoryEntry application, Entry databaseEntry) throws IOException {
		Entry crudEntry = getInsightEntry(application);
		Entry parent = databaseEntry.getParent();
		String databaseName = parent.getName();
		
		Entry child = EAIDeveloperUtils.mkdir((RepositoryEntry) crudEntry, databaseName);
		if (!child.isCollection()) {
			Collection parentCollection = parent.getCollection();
			CollectionImpl collection = new CollectionImpl();
			collection.setType("folder");
			if (parentCollection != null) {
				collection.setName(parentCollection.getName());
			}
			collection.setSmallIcon("insight.png");
			collection.setMediumIcon("insight-medium.png");
			collection.setLargeIcon("insight-large.png");
			collection.setSubType("insights");
			((RepositoryEntry) child).setCollection(collection);
			((RepositoryEntry) child).saveCollection();
		}
		return child;
	}
	
	private Entry getInsightEntry(RepositoryEntry application) throws IOException {
		Entry child = EAIDeveloperUtils.mkdir(application, "insights");
		return child;
	}
}
