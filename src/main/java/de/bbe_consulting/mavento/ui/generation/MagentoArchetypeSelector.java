/**
 * Copyright 2011-2012 BBe Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.bbe_consulting.mavento.ui.generation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.archetype.ui.generation.ArchetypeSelector;
import org.apache.maven.archetype.ui.generation.ArchetypeSelectorUtils;
import org.apache.maven.archetype.ui.generation.ArchetypeSelectionQueryer;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.exception.ArchetypeNotDefined;
import org.apache.maven.archetype.exception.ArchetypeSelectionFailure;
import org.apache.maven.archetype.exception.UnknownArchetype;
import org.apache.maven.archetype.exception.UnknownGroup;
import org.apache.maven.archetype.ui.ArchetypeDefinition;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component(role = ArchetypeSelector.class)
public class MagentoArchetypeSelector extends AbstractLogEnabled implements
        ArchetypeSelector {
    static final String DEFAULT_ARCHETYPE_GROUPID = "de.bbe-consulting.maven.archetype";

    static final String DEFAULT_ARCHETYPE_VERSION = "0.9.8.2";

    static final String DEFAULT_ARCHETYPE_ARTIFACTID = "magento-sample-module-archetype";

    static final String DEFAULT_SNIPPET_GROUPID = "de.bbe-consulting.maven.archetype.snippet";

    static final String DEFAULT_SNIPPET_VERSION = "0.9.8.2";

    static final String DEFAULT_SNIPPET_ARTIFACTID = "magento-simple-model-snippet";

    @Requirement
    private ArchetypeSelectionQueryer archetypeSelectionQueryer;

    @Requirement
    private ArchetypeManager archetypeManager;

    private String archetypeIdentifier = "archetype";

    private String archetypeMode = "Archetype";

    private Properties props;

    public void selectArchetype(ArchetypeGenerationRequest request,
            Boolean interactiveMode, String catalogs)
            throws ArchetypeNotDefined, UnknownArchetype, UnknownGroup,
            IOException, PrompterException, ArchetypeSelectionFailure {

        props = request.getProperties();
        if ("snippet".equals(props.getProperty("magentoArchetypeIdentifier"))) {
            archetypeMode = "Snippet";
        }

        ArchetypeDefinition definition = new ArchetypeDefinition(request);

        if (definition.isDefined()
                && StringUtils.isNotEmpty(request.getArchetypeRepository())) {
            getLogger().info(archetypeMode + " defined by properties");
            return;
        }

        Map<String, List<Archetype>> archetypes = getArchetypesByCatalog(catalogs);

        if (StringUtils.isNotBlank(request.getFilter())) {
            // applying some filtering depending on filter parameter
            archetypes = ArchetypeSelectorUtils.getFilteredArchetypesByCatalog(
                    archetypes, request.getFilter());
            if (archetypes.isEmpty()) {
                getLogger().info(
                        "Your filter doesn't match any " + archetypeMode
                                + ", so try again with another value.");
                return;
            }
        }

        if (definition.isDefined()
                && StringUtils.isEmpty(request.getArchetypeRepository())) {
            Map.Entry<String, Archetype> found = findArchetype(archetypes,
                    request.getArchetypeGroupId(),
                    request.getArchetypeArtifactId());

            if (found != null) {
                String catalogKey = found.getKey();
                Archetype archetype = found.getValue();

                updateRepository(definition, archetype, catalogKey);

                getLogger()
                        .info(archetypeMode
                                + " repository missing. Using the one from "
                                + archetype + " found in catalog " + catalogKey);
            } else {
                getLogger()
                        .warn(archetypeMode
                                + " not found in any catalog. Falling back to central repository (http://repo1.maven.org/maven2).");
                getLogger()
                        .warn("Use -DarchetypeRepository=<your repository> if "
                                + archetypeMode + "'s repository is elsewhere.");

                definition.setRepository("http://repo1.maven.org/maven2");
            }
        }

        if (!definition.isDefined() && definition.isPartiallyDefined()) {
            Map.Entry<String, Archetype> found = findArchetype(archetypes,
                    request.getArchetypeGroupId(),
                    request.getArchetypeArtifactId());

            if (found != null) {
                String catalogKey = found.getKey();
                Archetype archetype = found.getValue();

                updateDefinition(definition, archetype, catalogKey);

                getLogger().info(
                        archetypeMode + " " + archetype + " found in catalog "
                                + catalogKey);
            } else {
                getLogger().warn(
                        "Specified " + archetypeMode.toLowerCase()
                                + " not found.");
                if (interactiveMode.booleanValue()) {
                    definition.setVersion(null);
                    definition.setGroupId(null);
                    definition.setArtifactId(null);
                }
            }
        }

        // set the defaults - only group and version can be auto-defaulted
        if (definition.getGroupId() == null) {
            if ("snippet".equals(props
                    .getProperty("magentoArchetypeIdentifier"))) {
                definition.setGroupId(DEFAULT_SNIPPET_GROUPID);
            } else {
                definition.setGroupId(DEFAULT_ARCHETYPE_GROUPID);
            }
        }
        if (definition.getVersion() == null) {
            if ("snippet".equals(props
                    .getProperty("magentoArchetypeIdentifier"))) {
                definition.setVersion(DEFAULT_SNIPPET_VERSION);
            } else {
                definition.setVersion(DEFAULT_ARCHETYPE_VERSION);
            }
        }

        if (!definition.isPartiallyDefined()) {
            // if artifact ID is set to its default, we still prompt to confirm
            if (definition.getArtifactId() == null) {
                if ("snippet".equals(props
                        .getProperty("magentoArchetypeIdentifier"))) {
                    getLogger().info(
                            "No snippet defined. Using "
                                    + DEFAULT_SNIPPET_ARTIFACTID + " ("
                                    + definition.getGroupId() + ":"
                                    + DEFAULT_SNIPPET_ARTIFACTID + ":"
                                    + definition.getVersion() + ")");
                    definition.setArtifactId(DEFAULT_SNIPPET_ARTIFACTID);
                } else {
                    getLogger().info(
                            "No archetype defined. Using "
                                    + DEFAULT_ARCHETYPE_ARTIFACTID + " ("
                                    + definition.getGroupId() + ":"
                                    + DEFAULT_ARCHETYPE_ARTIFACTID + ":"
                                    + definition.getVersion() + ")");
                    definition.setArtifactId(DEFAULT_ARCHETYPE_ARTIFACTID);
                }
            }

            if (interactiveMode.booleanValue() && (archetypes.size() > 0)) {
                Archetype selectedArchetype = archetypeSelectionQueryer
                        .selectArchetype(archetypes, definition);

                String catalogKey = getCatalogKey(archetypes, selectedArchetype);

                updateDefinition(definition, selectedArchetype, catalogKey);
            }

            // Make sure the groupId and artifactId are valid, the version may
            // just default to
            // the latest release.
            if (!definition.isPartiallyDefined()) {
                throw new ArchetypeSelectionFailure("No valid "
                        + archetypeMode.toLowerCase()
                        + "s could be found to choose.");
            }
        }

        // finally update the request with gathered information
        definition.updateRequest(request);
    }

    private Map<String, List<Archetype>> getArchetypesByCatalog(String catalogs) {
        if (catalogs == null) {
            throw new NullPointerException("catalogs cannot be null");
        }

        Map<String, List<Archetype>> archetypes = new LinkedHashMap<String, List<Archetype>>();

        for (String catalog : StringUtils.split(catalogs, ",")) {
            if ("internal".equalsIgnoreCase(catalog)) {
                archetypes.put("internal", archetypeManager
                        .getInternalCatalog().getArchetypes());
            } else if ("local".equalsIgnoreCase(catalog)) {
                Iterator<Archetype> ite = archetypeManager
                        .getDefaultLocalCatalog().getArchetypes().iterator();
                List<Archetype> localArchetypes = new LinkedList<Archetype>();
                while (ite.hasNext()) {
                    Archetype a = ite.next();
                    if (a.getArtifactId().toLowerCase().contains("magento")
                            && a.getArtifactId()
                                    .toLowerCase()
                                    .contains(
                                            props.getProperty("magentoArchetypeIdentifier"))) {
                        localArchetypes.add(a);
                    }
                }
                getLogger().info(
                        "Found " + localArchetypes.size() + " Magento "
                                + archetypeMode + "s in repository " + catalog);
                archetypes.put("local", localArchetypes);
            } else if ("remote".equalsIgnoreCase(catalog)) {
                List<Archetype> archetypesFromRemote = archetypeManager
                        .getRemoteCatalog().getArchetypes();
                if (archetypesFromRemote.size() > 0) {
                    Iterator<Archetype> ite = archetypesFromRemote.iterator();
                    List<Archetype> remoteArchetypes = new LinkedList<Archetype>();
                    while (ite.hasNext()) {
                        Archetype a = ite.next();
                        if (a.getArtifactId().toLowerCase().contains("magento")
                                && a.getArtifactId()
                                        .toLowerCase()
                                        .contains(
                                                props.getProperty("magentoArchetypeIdentifier"))) {
                            remoteArchetypes.add(a);
                        }
                    }
                    getLogger().info(
                            "Found " + remoteArchetypes.size() + " Magento "
                                    + archetypeMode + "s in repository "
                                    + catalog);
                    archetypes.put("remote", remoteArchetypes);
                } else {
                    getLogger()
                            .warn("No "
                                    + archetypeMode.toLowerCase()
                                    + " found in remote catalog. Defaulting to internal catalog");
                    archetypes.put("internal", archetypeManager
                            .getInternalCatalog().getArchetypes());
                }
            } else if (catalog.startsWith("file://")) {
                String path = catalog.substring(7);
                archetypes.put(catalog, archetypeManager.getLocalCatalog(path)
                        .getArchetypes());
            } else if (catalog.startsWith("http://")
                    || catalog.startsWith("https://")) {
                Iterator<Archetype> ite = archetypeManager
                        .getRemoteCatalog(catalog).getArchetypes().iterator();
                List<Archetype> httpArchetypes = new LinkedList<Archetype>();
                while (ite.hasNext()) {
                    Archetype a = ite.next();
                    if (a.getArtifactId().toLowerCase().contains("magento")
                            && a.getArtifactId()
                                    .toLowerCase()
                                    .contains(
                                            props.getProperty("magentoArchetypeIdentifier"))) {
                        httpArchetypes.add(a);
                    }
                }
                getLogger().info(
                        "Found " + httpArchetypes.size() + " Magento "
                                + archetypeMode + "s in repository " + catalog);
                archetypes.put(catalog, httpArchetypes);
            }
        }

        if (archetypes.size() == 0) {
            getLogger().info("No catalog defined. Using internal catalog");

            archetypes.put("internal", archetypeManager.getInternalCatalog()
                    .getArchetypes());
        }
        return archetypes;
    }

    private void updateRepository(ArchetypeDefinition definition,
            Archetype archetype, String catalogKey) {
        String repository = archetype.getRepository();
        if (StringUtils.isNotEmpty(repository)) {
            definition.setRepository(repository);
        } else if (catalogKey.indexOf(':') > 1) {
            // file: or http:
            int lastIndex = catalogKey.lastIndexOf('/');
            String catalogBase = catalogKey.substring(0,
                    (lastIndex > 7 ? lastIndex : catalogKey.length()));
            definition.setRepository(catalogBase);
        }
    }

    private void updateDefinition(ArchetypeDefinition definition,
            Archetype archetype, String catalogKey) {
        definition.setGroupId(archetype.getGroupId());
        definition.setArtifactId(archetype.getArtifactId());
        definition.setVersion(archetype.getVersion());
        definition.setName(archetype.getArtifactId());
        updateRepository(definition, archetype, catalogKey);
        definition.setGoals(StringUtils.join(archetype.getGoals().iterator(),
                ","));
    }

    public void setArchetypeSelectionQueryer(
            ArchetypeSelectionQueryer archetypeSelectionQueryer) {
        this.archetypeSelectionQueryer = archetypeSelectionQueryer;
    }

    private String getCatalogKey(Map<String, List<Archetype>> archetypes,
            Archetype selectedArchetype) {
        for (Map.Entry<String, List<Archetype>> entry : archetypes.entrySet()) {
            List<Archetype> catalog = entry.getValue();

            if (catalog.contains(selectedArchetype)) {
                return entry.getKey();
            }
        }
        return "";
    }

    private Map.Entry<String, Archetype> findArchetype(
            Map<String, List<Archetype>> archetypes, String groupId,
            String artifactId) {
        Archetype example = new Archetype();
        example.setGroupId(groupId);
        example.setArtifactId(artifactId);

        for (Map.Entry<String, List<Archetype>> entry : archetypes.entrySet()) {
            List<Archetype> catalog = entry.getValue();

            if (catalog.contains(example)) {
                Archetype archetype = catalog.get(catalog.indexOf(example));

                return newMapEntry(entry.getKey(), archetype);
            }
        }

        return null;
    }

    private static <K, V> Map.Entry<K, V> newMapEntry(K key, V value) {
        Map<K, V> map = new HashMap<K, V>(1);
        map.put(key, value);

        return map.entrySet().iterator().next();
    }

    public String getArchetypeIdentifier() {
        return archetypeIdentifier;
    }

    public void setArchetypeIdentifier(String archetypeIdentifier) {
        this.archetypeIdentifier = archetypeIdentifier;
    }
}
