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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import org.dataconservancy.packaging.shared.AbstractContentProvider;
import org.dataconservancy.packaging.tool.model.ipm.FileInfo;
import org.dataconservancy.packaging.tool.model.ipm.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of ContentProvider for RDF sourced data.
 * @author Ben Trumbore (wbt3@cornell.edu).
 */
public class RdfContentProvider extends AbstractContentProvider {

    private Logger  LOG = LoggerFactory.getLogger(RdfContentProvider.class);
    private Model   domainObjects = null;
    private URI     contentPath;

    /**
     * Create a content provider that can be passed to
     * {@link org.dataconservancy.packaging.shared.IpmPackager#buildPackage}.
     * @param domainObjs The RDF model of the domain objects.
     * @param contentURI A URL to the files corresponding to the domain model.
     */
    public RdfContentProvider(Model domainObjs, URI contentURI) {
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
    private static final String HAS_TYPE      = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String HAS_TITLE     = "http://dataconservancy.org/business-object-model#hasTitle";
    private static final String METADATA_FOR  = "http://dataconservancy.org/business-object-model#metadataFor";
    private static final String IS_MEMBER_OF  = "http://dataconservancy.org/business-object-model#isMemberOf";

    // Define properties that will be inspected for some RDF nodes.
    private static final Property hasType     = ResourceFactory.createProperty(HAS_TYPE);
    private static final Property hasTitle    = ResourceFactory.createProperty(HAS_TITLE);
    private static final Property metadataFor = ResourceFactory.createProperty(METADATA_FOR);
    private static final Property isMemberOf  = ResourceFactory.createProperty(IS_MEMBER_OF);

    /*
     * TODO - Update doc throughout this method
     * We manually build the IPM tree here. Fundamentally, we're doing three things:
     * 1) Creating "directory" nodes that correspond to a domain object.
     * 2) Creating "content" nodes that correspond to a domain object that
     *    describes associated content.
     * 3) Arranging these nodes into a tree structure of our liking.
     */
    public Node getIpmModel() {
        // Must use an array to allow root node to be set inside lambda function below.
        final AtomicReference<Node> root = new AtomicReference<Node>();

        String msgFmt = "Creating %s IPM node named '%s' for domain object %s";

        // For each subject resource that is not anonymous, create a node.
        // - If the node type is an Image or File, then it will be a content node.
        // - Otherwise, make a directory node.
        // It is assumed that all folders will precede their child nodes/folders.
        domainObjects.listSubjects().forEachRemaining(subject -> {
            if (subject.isAnon()) {
                LOG.debug("Skipping IPM node creation for anonymous resource '{}'", subject.getId().toString());
                return;
            }

            URI u = URI.create(subject.getURI());

            // Hash URIs do not get their own node; they will be considered to be a single node.
            // TODO - Is this needed for JenaModel input? (copied from buildModelTree)
            if (u.getFragment() != null) {
                LOG.debug("Skipping IPM node creation for hash URI resource '{}'", subject.getURI());
                return;
            }

            // What type of node is this?  What is its name?
            String type  = subject.getProperty(hasType ).getObject().toString();
            String title = subject.getProperty(hasTitle).getObject().toString();
            boolean isImage  = type.equals("http://dataconservancy.org/business-object-model#Metadata");
            boolean isFile   = type.equals("http://dataconservancy.org/business-object-model#File");
            boolean isFolder = type.equals("http://dataconservancy.org/business-object-model#DataItem");

            // Create the new package tree node and insert it into the tree.
            Node n = new Node(u);
            n.setDomainObject(u);

            // Assign node file info and parent depending on node type.
            // Note that file info must be assigned before finding/assigning parent.
            if (isImage || isFile) {
                LOG.info(String.format(msgFmt, "file", title, subject.getURI()));
                Property prop = isImage ? metadataFor : isMemberOf;
                String parentID = subject.getProperty(prop).getObject().toString();
                assignFileInfo(title, parentID, contentPath, domainObjects, n, true);
                assignParentNode(n, root.get());
            }
            else if (isFolder) {
                LOG.info(String.format(msgFmt, "directory", title, subject.getURI()));
                String parentID = subject.getProperty(isMemberOf).getObject().toString();
                assignFileInfo(title, parentID, contentPath, domainObjects, n, false);
                assignParentNode(n, root.get());
            }
            else { // root folder, type == "Collection"
                LOG.info(String.format(msgFmt, "root directory", title, subject.getURI()));
                assignFileInfo(title, null, contentPath, domainObjects, n, false);
                root.set(n);
            }
        });

        return root.get();
    }

    /**
     * Create a FileInfo that points to file/folder content found in content tree path.
     *
     * @param fileName The name of the file or folder to be included.
     * @param parentID ID of the node's parent in the domain model.
     * @param contentPath Path to the root of the local package data tree.
     * @param domainObjects The domain model tree.
     * @param node The node for which the FileInfo is being created.
     * @param isFile True if the node represents a file, False if it is a folder.
     */
    private void assignFileInfo(String fileName, String parentID, URI contentPath,
                                       Model domainObjects, Node node, boolean isFile) {
        String localPath = (parentID == null)
                ? fileName
                : getPath(fileName, parentID, domainObjects);
        Path fullPath = Paths.get(contentPath + "/" + localPath);

        FileInfo info = new FileInfo(fullPath);
        if (isFile) {
            info.setIsFile(true);
        } else {
            info.setIsDirectory(true);
        }
        node.setFileInfo(info);
    }

    /**
     * Creates a complete path from the tail end of a path up to the domain model root.
     * Prepends the current path's parent name and
     * recurses until the domain model does not contain a higher parent.
     * @param pathTail When first called, the leaf file/folder of the desired path.
     *                 During recursion, the accumulated path from the leaf upwards.
     * @param parentID The ID of the domain model node that is the parent of the pathTail.
     * @param domainObjects The domain model.
     * @return The complete path from domain model root to the provided leaf.
     */
    private String getPath(String pathTail, String parentID, Model domainObjects) {
        Resource parent = domainObjects.getResource(parentID);
        String parentName = parent.getProperty(hasTitle).getObject().toString();
        String newPath  = parentName + "/" + pathTail;

        Statement grandparent = parent.getProperty(isMemberOf);
        if (grandparent != null) {
            return getPath(newPath, grandparent.getObject().toString(), domainObjects);
        } else {
            return newPath;
        }
    }

    /**
     * Finds and assigns the parent IPM tree node of the given node.
     * @param node The node for which a parent is found and assigned.
     * @param root The root node of the IPM tree.
     */
    private void assignParentNode(Node node, Node root) {
        // Find the file system paths to the provided root and target nodes.
        String nodeLoc = node.getFileInfo().getLocation().getPath();
        nodeLoc = nodeLoc.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        Path nodePath = Paths.get(nodeLoc);
        String rootLoc = root.getFileInfo().getLocation().getPath();
        rootLoc = rootLoc.replaceFirst("^/(.:/)", "$1"); // Remove leading "/" on Windows
        Path rootPath = Paths.get(rootLoc);

        // Find the relative file system path between the root and target nodes.
        Path relPath  = rootPath.relativize(nodePath);

        // Walk the tree from the root node down through its children
        // until the target node is reached.  At each step find the child whose name
        // matches the first name in the remaining relative path.
        Node parent = root;
        while (relPath.getNameCount() > 1) {
            String name = relPath.getName(0).toString();
            List<Node> children = parent.getChildren();
            Node match = null;
            for (Node child : children) {
                if (child.getFileInfo().getName().equals(name)) {
                    match = child;
                    break;
                }
            }
            if (match == null)
                throw new RuntimeException("Expected IPM model folder '" + name + "' does not exist!");
            parent = match;
            relPath = relPath.subpath(1, relPath.getNameCount());
        }

        // Assign the target node's discovered parent.
        parent.addChild(node);
    }

}
