/*
 * Copyright 2017 Johns Hopkins University
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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import org.dataconservancy.packaging.shared.AbstractContentProvider;
import org.dataconservancy.packaging.tool.model.ipm.FileInfo;
import org.dataconservancy.packaging.tool.model.ipm.Node;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of ContentProvider for RDF sourced data.
 * @author Ben Trumbore (wbt3@cornell.edu).
 */
@SuppressWarnings("unused")
public class RdfContentProvider extends AbstractContentProvider {

    private Logger  logger = (Logger)LoggerFactory.getLogger(RdfContentProvider.class);
    private Model   domainObjects = null;
    private URI     contentPath;

    // Must use an Atomic Reference to allow root node to be set inside lambda function below.
    private final AtomicReference<Node> root = new AtomicReference<Node>();

    /**
     * Create a content provider that can be passed to
     * {@link org.dataconservancy.packaging.shared.IpmPackager#buildPackage}.
     * @param domainObjs The RDF model of the domain objects.
     * @param contentURI A URL to the files corresponding to the domain model.
     */
    public RdfContentProvider(final Model domainObjs, final URI contentURI) {
        domainObjects = domainObjs;
        contentPath = contentURI;
    }

    /**
     * Returns the domain model provided in the constructor.
     * @return The Model representing the domain objects
     */
    public Model getDomainModel() {
        return domainObjects;
    }

    // Define property names that are found on some RDF nodes.
    private static final String HAS_TYPE        = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String HAS_TITLE       = "http://dataconservancy.org/business-object-model#hasTitle";
    private static final String METADATA_FOR    = "http://dataconservancy.org/business-object-model#metadataFor";
    private static final String IS_METADATA_FOR = "http://dataconservancy.org/business-object-model#isMetadataFor";
    private static final String IS_MEMBER_OF    = "http://dataconservancy.org/business-object-model#isMemberOf";

    // Define properties that will be inspected for some RDF nodes.
    private static final Property hasType       = ResourceFactory.createProperty(HAS_TYPE);
    private static final Property hasTitle      = ResourceFactory.createProperty(HAS_TITLE);
    private static final Property metadataFor   = ResourceFactory.createProperty(METADATA_FOR);
    private static final Property isMetadataFor = ResourceFactory.createProperty(IS_METADATA_FOR);
    private static final Property isMemberOf    = ResourceFactory.createProperty(IS_MEMBER_OF);

    /**
     * We manually build the IPM tree here. Fundamentally, we're doing three things:
     * 1) Creating "directory" nodes that correspond to a domain object.
     * 2) Creating "content" nodes that correspond to a domain object that describes associated content.
     * 3) Arranging these nodes into a tree structure of our liking.
     * @return The root node of the IPM model for this content provider.
     */
    public Node getIpmModel() {
        // For each subject resource that is not anonymous, find or create a node.
        // The root node is remembered when it is created, then returned from this method.
        domainObjects.listSubjects().forEachRemaining(subject -> {
            if (subject.isAnon()) {
                logger.debug("Skipping IPM node creation for anonymous resource '{}'", subject.getId().toString());
                return;
            }

            final List<Statement> statements = subject.listProperties().toList();
            final Statement s = subject.getProperty(isMemberOf);
            final String parent = (s == null) ? "none" : s.getObject().toString();

            logger.debug(String.format("%25s  %s  %s  %s",
                    subject.getProperty(hasTitle).getObject().toString(), subject.toString(),
                    parent, subject.getProperty(hasType).getObject().toString()));

            // Is this node for a folder?
            final String  rdfType  = subject.getProperty(hasType ).getObject().toString();
            final boolean isFolder = rdfType.equals("http://dataconservancy.org/business-object-model#DataItem") ||
                                     rdfType.equals("http://dataconservancy.org/business-object-model#Collection") ||
                                     rdfType.equals("DataItem") ||
                                     rdfType.equals("Collection");

            // Find or create a node for the RDF subject.  Any missing parent nodes are recursively created.
            if (isFolder) {
                findOrCreateFolderNode(subject);
            } else {
                createFileNode(subject, rdfType);
            }
        });

        return root.get();
    }


    /**
     * Create a file node and, recursively, any missing folder nodes on its path.
     * @param subject The RDF resource for the file
     * @param type The type of the RDF resource
     * @return The newly created file node
     */
    private Node createFileNode(final Resource subject, final String type) {
        // File type determines which property is used to access its parent.
        final boolean isImage1 = type.equals("http://dataconservancy.org/business-object-model#Metadata");
        final boolean isImage2 = type.equals("MetadataFile");
        final boolean isFile   = type.equals("http://dataconservancy.org/business-object-model#File") ||
                                 type.equals("http://dataconservancy.org/business-object-model#DataFile") ||
                                 type.equals("DataFile");

        final Property prop = isImage1 ? metadataFor : isImage2 ? isMetadataFor : isMemberOf;
        final String parentID = subject.getProperty(prop).getObject().toString();
        final Resource parent = domainObjects.getResource(parentID);

        // Create a node for the file.
        final URI uri = URI.create(subject.getURI());
        final Node node = new Node(uri);
        node.setDomainObject(uri);

        // Recursively find the node's parent node.  If the parent is a "synthesized" DataItem node,
        // use its parent instead.  Add the new node to the true parent node.
        final Node parentNode = findOrCreateFolderNode(parent);
        final Node ancestorNode = (parentNode.getFileInfo() == null) ? parentNode.getParent() : parentNode;
        final String fileName = subject.getProperty(hasTitle).getObject().toString();
        assignFileInfo(fileName, ancestorNode.getDomainObject().toString(), contentPath, node, true);
        parentNode.addChild(node);

        logger.debug(String.format("Creating file named '%s' for object %s", fileName, uri.toString()));
        return node;
    }


    /**
     * Find an existing node for the supplied RDF folder resource, or create one.
     * Recursively create any folders on the path to this folder.
     * @param subject The RDF resource for the folder
     * @return The node that was found or created for the folder
     */
    private Node findOrCreateFolderNode(final Resource subject) {
        final String msgFmt = "Creating %s named '%s' for object %s";
        final String title = subject.getProperty(hasTitle).getObject().toString();
        final String nodeUri = subject.getURI();

        // If this node has no parent, it is the root node
        final Statement prop = subject.getProperty(isMemberOf);
        if (prop == null) {
            // If the root node doesn't exist, create and remember it.
            if (root.get() == null) {
                final URI uri = URI.create(subject.getURI());
                final Node node = new Node(uri);
                node.setDomainObject(uri);
                assignFileInfo(title, null, contentPath, node, false);
                root.set(node);
                logger.debug(String.format(msgFmt, "root folder", title, nodeUri));
            }
            return root.get();
        } else {
            // This is not the root node.  Recursively find or create its parent node.
            final String parentID = prop.getObject().toString();
            final Resource parent = domainObjects.getResource(parentID);
            final Node parentNode = findOrCreateFolderNode(parent);

            // See if this node already exists as a child of its parent node.
            Node node = null;
            final List<Node> children = parentNode.getChildren();
            if (children != null) {
                for (Node child : children) {
                    if (nodeUri.equals(child.getIdentifier().toString())) {
                        node = child;
                        break;
                    }
                }
            }

            if (node == null) {
                // There is no existing node for this subject, so create one.
                final URI uri = URI.create(subject.getURI());
                node = new Node(uri);
                node.setDomainObject(uri);
                parentNode.addChild(node);

                // If this is a "synthesized" node for a DataItem, do not assign it a FileInfo.
                final String localPath = getPath(title, parentID);
                final Path fullPath = Paths.get(contentPath + "/" + localPath);
                final File testFile = new File(fullPath.toString());
                if (testFile.isFile()) {
                    // Without a FileInfo, no bin file will be created in the package.
                    // The node's URI will be used as the base name for the .ttl file.
                    logger.debug(String.format(msgFmt, "synthetic folder", title, nodeUri));
                } else {
                    assignFileInfo(title, parentID, contentPath, node, false);
                    logger.debug(String.format(msgFmt, "folder", title, nodeUri));
                }
            }

            return node;
        }
    }


    /**
     * Create a FileInfo that points to file/folder content found in the content tree path.
     * @param fileName The name of the file or folder to be included.
     * @param parentID ID of the node's parent in the domain model.
     * @param contentPath Path to the root of the local package data tree.
     * @param node The node for which the FileInfo is being created.
     * @param isFile True if the node represents a file, False if it is a folder.
     */
    private void assignFileInfo(final String fileName, final String parentID, final URI contentPath,
                                final Node node, final boolean isFile) {
        final String localPath = (parentID == null) ? fileName : getPath(fileName, parentID);
        final Path fullPath = Paths.get(contentPath + "/" + localPath);

        // Empty folders will not be included in the content tree so we must add them instead of reading their info.
        final FileInfo info;
        final File file = new File(fullPath.toString());
        if (file.exists()) {
            info = new FileInfo(fullPath);
        } else {
            info = new FileInfo(fullPath.toUri(), fileName);
            file.mkdir();
        }

        if (isFile) {
            info.setIsFile(true);
        } else {
            info.setIsDirectory(true);
        }
        node.setFileInfo(info);
    }


    /**
     * Creates a complete path from the tail end of a path up to the domain model root.
     * Prepends the current path's parent name and recurses until the domain model does not
     * contain a higher parent.
     * @param pathTail When first called, the leaf file/folder of the desired path.
     *                 During recursion, the accumulated path from the leaf upwards.
     * @param parentID The ID of the domain model node that is the parent of the pathTail.
     * @return The complete path from domain model root to the provided leaf.
     */
    private String getPath(final String pathTail, final String parentID) {
        final Resource parent = domainObjects.getResource(parentID);
        final String parentName = parent.getProperty(hasTitle).getObject().toString();
        final String newPath  = parentName + "/" + pathTail;

        final Statement grandparent = parent.getProperty(isMemberOf);
        if (grandparent != null) {
            return getPath(newPath, grandparent.getObject().toString());
        } else {
            return newPath;
        }
    }

}
