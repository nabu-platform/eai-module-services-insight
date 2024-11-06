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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.repository.EAINode;
import be.nabu.eai.repository.api.ArtifactRepositoryManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ModifiableEntry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.eai.repository.resources.MemoryEntry;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.types.structure.DefinedStructure;

public class InsightArtifactManager extends JAXBArtifactManager<InsightConfiguration, InsightArtifact> implements ArtifactRepositoryManager<InsightArtifact> {

	public InsightArtifactManager() {
		super(InsightArtifact.class);
	}

	@Override
	protected InsightArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new InsightArtifact(id, container, repository);
	}

	@Override
	public List<Entry> addChildren(ModifiableEntry parent, InsightArtifact artifact) throws IOException {
		List<Entry> entries = new ArrayList<Entry>();
		EAINode node = new EAINode();
		node.setArtifactClass(DefinedStructure.class);
		node.setArtifact(artifact.getResult());
		node.setLeaf(true);
		Entry childEntry = new MemoryEntry(artifact.getId(), parent.getRepository(), parent, node, artifact.getResult().getId(), "results");
		node.setEntry(childEntry);
		parent.addChildren(childEntry);
		entries.add(childEntry);
		return entries;
	}
	
	@Override
	public List<Entry> removeChildren(ModifiableEntry parent, InsightArtifact artifact) throws IOException {
		List<Entry> entries = new ArrayList<Entry>();
		List<String> toRemove = new ArrayList<String>();
		for (Entry child : parent) {
			entries.add(child);
			toRemove.add(child.getName());
		}
		parent.removeChildren(toRemove.toArray(new String[toRemove.size()]));
		return entries;
	}

}
