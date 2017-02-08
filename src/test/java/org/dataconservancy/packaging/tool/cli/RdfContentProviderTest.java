/*
 * Copyright 2016 Johns Hopkins University
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

package org.dataconservancy.packaging.tool.cli;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.dataconservancy.packaging.tool.api.RulesEngine;
import org.dataconservancy.packaging.tool.impl.RulesEngineImpl;
import org.junit.Test;

import org.dataconservancy.packaging.shared.IpmPackager;
import org.dataconservancy.packaging.tool.api.PackageDescriptionCreator;
import org.dataconservancy.packaging.tool.impl.GeneralPackageDescriptionCreator;
import org.dataconservancy.packaging.tool.impl.RulesEngineImpl;
import org.dataconservancy.packaging.tool.model.builder.xstream.JaxbPackageDescriptionRulesBuilder;
import org.dataconservancy.packaging.tool.model.PackageDescription;
import org.dataconservancy.packaging.tool.model.PackageDescriptionRulesBuilder;
import org.dataconservancy.packaging.tool.model.rules.RulesSpec;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * Tests for validating RDF-sourced content in a DC package.
 * @author Ben Trumbore (wbt3@cornell.edu).
 */
public class RdfContentProviderTest {

    // Create a Jena model, package metadata and generation parameters
    // to pass to an IpmPackager to create a package.
    // Compare the package contents to that of the original source.
    //@Test
    public void testCreateRdfPackageFromJenaFile() throws Exception {
        final String dataResource = "/testCreateRdfPackage/";
        final String jenaResource = dataResource + "state/DOMAIN_OBJECTS";
        final String bagResource  = dataResource + "Hanh-test/data/bin";

        // Read Jena model from sample DOMAIN_OBJECTS file.
        final String jenaFile = RdfContentProviderTest.class.getResource(jenaResource).getPath();
        final Model jenaModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(jenaModel, jenaFile, Lang.TTL);
        assertEquals(148, jenaModel.size());

        // Metadata
        final InputStream metadataStream =
                RdfContentProviderTest.class.getResourceAsStream("/metadata.properties");

        // Read package generation parameters from a resource file.
        final InputStream paramStream =
                RdfContentProviderTest.class.getResourceAsStream("/PackageGenerationParams.properties");

        // Create content provider using local data path
        String contentPath = RdfContentProviderTest.class.getResource(bagResource).getPath();
        contentPath = contentPath.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        final URI contentURI = new URI(contentPath);

        final RdfContentProvider contentProvider = new RdfContentProvider(jenaModel, contentURI);

        // Create the package
        final IpmPackager packager = new IpmPackager();
        final org.dataconservancy.packaging.tool.api.Package pkg =
                packager.buildPackage(contentProvider, metadataStream, paramStream);

        // TODO - Test the package contents
        assertNotNull(pkg);
    }
    // Create a Jena model, package metadata and generation parameters
    // to pass to an IpmPackager to create a package.
    // Compare the package contents to that of the original source.
    @Test
    public void testCreateRdfPackageFromRules() throws Exception {
        final String dataResource = "/testCreateRdfPackage/Hanh-test/data/bin";

        // Rules engine creates Jena model from rules and local data
        InputStream rulesStream =
                RdfContentProviderTest.class.getClassLoader()
                        .getResourceAsStream("default-engine-rules.xml");

        PackageDescriptionRulesBuilder builder = new JaxbPackageDescriptionRulesBuilder();
        RulesSpec rulesSpec = builder.buildPackageDescriptionRules(rulesStream);
        RulesEngine engine = new RulesEngineImpl(rulesSpec);


        String dataPath = RdfContentProviderTest.class.getResource(dataResource).getPath();
        dataPath = dataPath.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        File dataFolder = new File(dataPath);
        Model jenaModel = (Model) engine.generateRdf(dataFolder);
        assertEquals(148, jenaModel.size());

        // Metadata
        final InputStream metadataStream =
                RdfContentProviderTest.class.getResourceAsStream("/metadata.properties");

        // Read package generation parameters from a resource file.
        final InputStream paramStream =
                RdfContentProviderTest.class.getResourceAsStream("/PackageGenerationParams.properties");

        // Create content provider using local data path
        //String contentPath = RdfContentProviderTest.class.getResource(dataResource).getPath();
        //dataPath = dataPath.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        final URI contentURI = new URI(dataPath);

        final RdfContentProvider contentProvider = new RdfContentProvider(jenaModel, contentURI);

        // Create the package
        final IpmPackager packager = new IpmPackager();
        final org.dataconservancy.packaging.tool.api.Package pkg =
                packager.buildPackage(contentProvider, metadataStream, paramStream);

        // TODO - Test the package contents
        assertNotNull(pkg);
    }

}
