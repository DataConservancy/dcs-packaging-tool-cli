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
import org.junit.Test;

import org.dataconservancy.packaging.shared.IpmPackager;

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
    @Test
    public void testCreateRdfPackage() throws Exception {
        String dataResource = "/testCreateRdfPackage/";
        String jenaResource = dataResource + "state/DOMAIN_OBJECTS";
        String bagResource  = dataResource + "Hanh-test/data/bin";

        // Read Jena model from sample DOMAIN_OBJECTS file.
        String jenaFile = RdfContentProviderTest.class.getResource(jenaResource).getPath();
        Model jenaModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(jenaModel, jenaFile, Lang.TTL);
        assertEquals(148, jenaModel.size());

        // Metadata
        InputStream metadataStream =
                RdfContentProviderTest.class.getResourceAsStream("/metadata.properties");

        // Read package generation parameters from a resource file.
        InputStream paramStream =
                RdfContentProviderTest.class.getResourceAsStream("/PackageGenerationParams.properties");

        String contentPath = RdfContentProviderTest.class.getResource(bagResource).getPath();
        contentPath = contentPath.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        URI contentURI = new URI(contentPath);

        RdfContentProvider contentProvider = new RdfContentProvider(jenaModel, contentURI);

        // Create the package
        IpmPackager packager = new IpmPackager();
        org.dataconservancy.packaging.tool.api.Package pkg =
                packager.buildPackage(contentProvider, metadataStream, paramStream);

        // TODO - Test the package contents
        assertNotNull(pkg);
    }

}
