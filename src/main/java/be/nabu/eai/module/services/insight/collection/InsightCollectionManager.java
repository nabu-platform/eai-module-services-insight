package be.nabu.eai.module.services.insight.collection;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.repository.api.Entry;
import javafx.scene.Node;
import javafx.scene.control.Button;

public class InsightCollectionManager implements CollectionManager {

	private Entry entry;

	public InsightCollectionManager(Entry entry) {
		this.entry = entry;
	}

	@Override
	public Entry getEntry() {
		return entry;
	}

	@Override
	public boolean hasSummaryView() {
		return true;
	}

	@Override
	public Node getSummaryView() {
		List<Button> buttons = new ArrayList<Button>();
		buttons.add(EAICollectionUtils.newViewButton(entry));
		buttons.add(EAICollectionUtils.newDeleteButton(entry, null));
		return EAICollectionUtils.newSummaryTile(entry, "insight-large.png", buttons.toArray(new Button[buttons.size()]));
	}
	
}
