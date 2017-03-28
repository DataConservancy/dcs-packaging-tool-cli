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

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.jena.rdf.model.Model;

import org.dataconservancy.packaging.shared.IpmPackager;
import org.dataconservancy.packaging.tool.api.PackagingFormat;
import org.dataconservancy.packaging.tool.api.RulesEngine;
import org.dataconservancy.packaging.tool.api.RulesEngineException;
import org.dataconservancy.packaging.tool.impl.RulesEngineImpl;
import org.dataconservancy.packaging.tool.model.BagItParameterNames;
import org.dataconservancy.packaging.tool.model.GeneralParameterNames;
import org.dataconservancy.packaging.tool.model.PackageDescriptionRulesBuilder;
import org.dataconservancy.packaging.tool.model.PackageGenerationParameters;
import org.dataconservancy.packaging.tool.model.PackageToolException;
import org.dataconservancy.packaging.tool.model.PackagingToolReturnInfo;
import org.dataconservancy.packaging.tool.model.ParametersBuildException;
import org.dataconservancy.packaging.tool.model.PropertiesConfigurationParametersBuilder;
import org.dataconservancy.packaging.tool.model.builder.xstream.JaxbPackageDescriptionRulesBuilder;
import org.dataconservancy.packaging.tool.model.rules.RulesSpec;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Command line interface to create a DC package as directed by the rules engine.
 * @author jrm - initial framework.
 * @author Ben Trumbore (wbt3@cornell.edu) - connect CLI to functionality.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AutomatedPackageTool {

    private static final String rulesFileName                        = "apt-rules.xml";
    private static final String packageGenerationsParametersFileName = "packageGenerationParameters";
    private static final String defaultResourceConfigPath            = "/org/dataconservancy/apt/config/";
    private static final String packageNameKey                       = "Package-Name";
    private static final String defaultPackageName                   = "MyPackage";

    private static final File   userDcDir = new File(System.getProperty("user.home") + "/.dataconservancy");
    private static final String javaTemp  = System.getProperty("java.io.tmpdir").replace(File.separatorChar, '/');

    private final Logger log = (Logger)LoggerFactory.getLogger(this.getClass());

    private PackageGenerationParameters packageParams = new PackageGenerationParameters();

    /*
    * Arguments
    */
    @Argument(required = true, index = 0, metaVar = "<path>", usage = "content root directory")
    private static File contentRootFile;

    @Argument(required = true, index = 1, metaVar = "<file>", usage = "package metadata file location")
    private static File packageMetadataFile;

    /*
    * General Options
    */
    /** Request for help/usage documentation */
    @Option(name = "-h", aliases = { "-help", "--help" }, usage = "print help message")
    private boolean help = false;

    /** Requests the current version number of the cli application. */
    @Option(name = "-v", aliases = { "-version", "--version" }, usage = "print version information")
    private boolean version = false;

    /** Requests for debugging info. */
    @Option(name = "-d", aliases = { "-debug", "--debug" }, usage = "print debug information")
    private boolean debug = false;

    /** Requests for parameter info */
    @Option(name = "-i", aliases = { "-info", "--info"}, usage = "print parameter info")
    private boolean info = false;

    /*
    * Package Generation Options
    */
    /** Package Generation Params location **/
    @Option(name = "-g", aliases = { "--generation-params" }, metaVar = "<file>",
            usage = "package generation params file location")
    private static File packageGenerationParamsFile;

    /** Rules file **/
    @Option(name = "-r", aliases = {"--rules", "--rules-file"}, metaVar = "<path>",
            usage = "The location of the rules file")
    private static File rulesFile;

    /** Package Name **/
    @Option(name = "-n", aliases = { "--name", "--package-name"}, metaVar = "<name>",
            usage = "The package name, which also determines the output filename.  " +
                    "Will override value in Package Generation Parameters file.")
    private static String packageName;

    /** Package output location **/
    @Option(name = "-o", aliases = { "--location", "--output-location"}, metaVar = "<path>",
            usage = "The output directory to which the package file will be written.  " +
                    "Will override value in Package Generation Parameters file.")
    private static File outputLocation;

    /** Force overwrite of target file **/
    @Option(name = "--overwrite", aliases = { "--force" },
            usage = "If specified, will overwrite if the destination package file already exists without prompting.")
    private static boolean overwriteIfExists = false;

    /** Package staging location **/
    @Option(name = "--stage", aliases = { "--staging", "--staging-location", "--package-staging-location"},
            metaVar = "<path>", usage = "The directory to which the package will be staged before building.  " +
            "Will override value in Package Generation Parameters file.")
    private String packageStagingLocation = javaTemp + "DCS-PackageToolStaging";

    /** Packaging format **/
    @Option(name = "-f", aliases = { "--format" }, usage = "packaging format to use")
    private PackagingFormat pkgFormat = PackagingFormat.BOREM;

    /** Archive format **/
    @Option(name = "-a", aliases = { "--archiving-format"}, metaVar = "tar|zip",
            usage = "Archive format to use when creating the package.  Defaults to tar")
    private String archiveFormat;

    /** Compression format for tar archives **/
    @Option(name = "-c", aliases = { "--compression-format"}, metaVar = "gz|none",
            usage = "Compression format, if archive type is tar.  " +
                    "If not specified, no compression is used.  Ignored if non-tar archive is used.")
    private String compressionFormat;

    /** Checksum algorithms **/
    @Option(name = "-s", aliases = { "--checksum"}, metaVar = "md5|sha1",
            usage = "Checksum algorithms to use.  If none specified, will use md5.  Can be specified multiple times")
    private List<String> checksums;

    /** Serialization Format **/
    @Option(name = "-z", aliases = { "--serialization", "--serialization-format"}, metaVar = "JSONLD|TURTLE|XML",
            usage = "Serialization format for the ORE-ReM file")
    private String serializationFormat;

    /** Serialization Format **/
    @Option(name = "-eid", aliases = { "--external-project-id"},
            usage = "External project ID to associate with the package")
    private String externalID;


    /**
     * Entry point for the application
     * @param args command line arguments
     */
    public static void main(final String[] args) {

        final AutomatedPackageTool application = new AutomatedPackageTool();

        final CmdLineParser parser = new CmdLineParser(application);
        parser.getProperties().withUsageWidth(80);

        try {
            parser.parseArgument(args);

            // Handle general options such as help, version.
            if (application.help) {
                parser.printUsage(System.err);
                System.err.println();
                System.exit(0);
            } else if (application.version) {
                System.err.println(AutomatedPackageTool.class.getPackage().getImplementationVersion());
                System.exit(0);
            }

            if (!contentRootFile.exists() || !contentRootFile.isDirectory()) {
                System.err.println("Supplied content directory " + contentRootFile.getCanonicalPath() +
                        " does not exist or is not a directory.");
                System.exit(1);
            }

            if (outputLocation != null && (!outputLocation.exists() || !outputLocation.isDirectory())) {
                System.err.println("Supplied output file directory " + outputLocation.getCanonicalPath() +
                        " does not exist or is not a directory.");
                System.exit(1);
            }

            if (packageName != null && !(packageName.length() > 0)) {
                System.err.println("Bag name must have positive length.");
                System.exit(1);
            }

            if (!packageMetadataFile.exists() || !packageMetadataFile.isFile()) {
                System.err.println("Supplied package metadata file " + packageMetadataFile.getCanonicalPath() +
                        " does not exist or is not a file.");
                System.exit(1);
            }

            if (rulesFile != null && (!rulesFile.exists() || !rulesFile.isFile())) {
                System.err.println("Supplied rules file " + rulesFile.getCanonicalPath() +
                        " does not exist or is not a file.");
                System.exit(1);
            }

            // Run the package generation application proper.
            application.run();

        } catch (CmdLineException e) {
            // This is an error in command line args, just print out usage data and description of the error.
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }


    private void run() throws Exception {
        // Control the logger output level.
        final Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (debug) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.WARN); // TODO - Change to INFO when some info logging is moved to debug.
        }

        // Get the inputs to the packager.
        final RdfContentProvider contentProvider = createContentProvider();
        final InputStream paramsStream = collectPackagingParameters();
        final InputStream metadataStream = createPackageMetadata();

        // Create the package and copy it to the output folder
        if (contentProvider != null) {
            final IpmPackager packager = new IpmPackager();
            packager.setPackageName(packageName);
            final org.dataconservancy.packaging.tool.api.Package pkg =
                    packager.buildPackage(contentProvider, metadataStream, paramsStream);
            copyPackage(pkg);
        }
    }


    private RdfContentProvider createContentProvider() {
        final InputStream rulesStream = getPackagingRules();
        final Model jenaModel = createJenaModel(rulesStream);

        // Create content provider using local data path
        final String rootPath;
        try {
            rootPath = contentRootFile.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        RdfContentProvider contentProvider = null;
        try {
            final String parentFolder = new File(rootPath).getParent();
            // Handle Windows path separators
            final URI contentURI = new URI(parentFolder.replace(File.separatorChar, '/'));
            contentProvider = new RdfContentProvider(jenaModel, contentURI);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return contentProvider;
    }


    // Resolve the rules file location. Priority is given to a command line file path,
    // then to the user's default location, then to the application default.
    private InputStream getPackagingRules() {
        InputStream rulesStream = null;
        try {
            if (rulesFile == null) {
                final File userRulesFile = new File(userDcDir, rulesFileName);
                if (userRulesFile.exists()) {
                    rulesStream = new FileInputStream(userRulesFile);
                } else {
                    // Get the default rules file supplied with the app
                    rulesStream = getClass().getResourceAsStream(defaultResourceConfigPath + rulesFileName);
                }
            } else {
                rulesStream = new FileInputStream(rulesFile);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return rulesStream;
    }


    private Model createJenaModel(final InputStream rulesStream) {
        // Rules engine creates Jena model from rules and local data
        final PackageDescriptionRulesBuilder builder = new JaxbPackageDescriptionRulesBuilder();
        final RulesSpec rulesSpec = builder.buildPackageDescriptionRules(rulesStream);
        final RulesEngine engine = new RulesEngineImpl(rulesSpec);

        final String dataPath;
        try {
            dataPath = contentRootFile.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        final File dataFolder = new File(dataPath);
        Model jenaModel = null;
        try {
            jenaModel = engine.generateRdf(dataFolder);
        } catch (RulesEngineException e) {
            e.printStackTrace();
        }

        return jenaModel;
    }


    // Load parameters first from application default, then override with file of user defaults,
    // then override with a specified parameters file (if given), then with command line options.
    private InputStream collectPackagingParameters() {
        // Get default parameters from a source code resource.
        try {
            packageParams = new PropertiesConfigurationParametersBuilder().
                    buildParameters(getClass().getResourceAsStream(defaultResourceConfigPath +
                            packageGenerationsParametersFileName));
            updateCompression(packageParams);
        } catch (ParametersBuildException e) {
            throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_PARAM_BUILD_EXCEPTION, e);
        }

        // Override any parameters defined in the user's default parameter file.
        final File userParamsFile = new File(userDcDir, packageGenerationsParametersFileName);
        if (userParamsFile.exists()) {
            try {
                final PackageGenerationParameters homeParams = new PropertiesConfigurationParametersBuilder().
                        buildParameters(new FileInputStream(userParamsFile));

                System.err.println("Overriding generation parameters with values from standard '" +
                        packageGenerationsParametersFileName + "'");
                updateCompression(homeParams);
                packageParams.overrideParams(homeParams);
            } catch (FileNotFoundException e) {
                // Do nothing, it's ok to not have this file
            } catch (ParametersBuildException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_PARAM_BUILD_EXCEPTION, e);
            }
        }

        // Override any parameters defined in a file provided as a command line option.
        if (packageGenerationParamsFile != null) {
            try {
                final PackageGenerationParameters fileParams = new PropertiesConfigurationParametersBuilder().
                        buildParameters(new FileInputStream(packageGenerationParamsFile));

                System.err.println("Overriding generation parameters with values from " +
                        packageGenerationParamsFile + " specified on command line");
                updateCompression(fileParams);
                packageParams.overrideParams(fileParams);
            } catch (ParametersBuildException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_PARAM_BUILD_EXCEPTION, e);
            } catch (FileNotFoundException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION, e);
            }
            if (debug) {
                log.debug("Parameters resulted from parsing file "
                        + packageGenerationParamsFile.getAbsoluteFile() + ": \n" + packageParams.toString());
            }
        }

        // Finally, override any parameters specified with individual command line options.
        final PackageGenerationParameters flagParams = createCommandLinePrefs();
        if (!flagParams.getKeys().isEmpty()) {
            System.err.println("Overriding generation parameters using command line flags");
            updateCompression(flagParams);
            packageParams.overrideParams(flagParams);
        }

        // We need to validate any specified file locations in the package generation parameters
        // to make sure they exist.
        if (packageParams.getParam(GeneralParameterNames.PACKAGE_LOCATION) == null) {
            packageParams.addParam(GeneralParameterNames.PACKAGE_LOCATION, javaTemp);
        }

        validateLocationParameters(packageParams);

        // Print the parameter info
        if (info) {
            System.err.println("\nPARAMETERS");
            final String params = packageParams.toParametersString();
            System.err.println(params);
        }

        final String params = packageParams.toParametersString();
        return new ByteArrayInputStream(params.getBytes(StandardCharsets.UTF_8));
    }


   /**
     * Update the compression format for the parameters, if necessary.
     * Basically, if the archive format is "zip", it should set the compression
     * format to "none" unless another format is explicitly set.
     * @param params The package generation params, used to get the file needed
     */
    private void updateCompression(final PackageGenerationParameters params) {
        final String archive = params.getParam(GeneralParameterNames.ARCHIVING_FORMAT, 0);
        final String compress = params.getParam(GeneralParameterNames.COMPRESSION_FORMAT, 0);

        //manually set the compression to none if archive is ZIP and no compression
        // is specifically set in this object, or if archive is exploded
        if (archive != null &&
            ((archive.equals(ArchiveStreamFactory.ZIP) && compress == null) || archive.equals("exploded"))) {
            params.addParam(GeneralParameterNames.COMPRESSION_FORMAT, "none");
        }
    }


    /**
     * Create a PackageGenerationParameter for command line flags
     * @return a PackageGenerationParameter object with any command line overrides
     */
    private PackageGenerationParameters createCommandLinePrefs() {
        final PackageGenerationParameters params = new PackageGenerationParameters();

        if (pkgFormat != null) {
            params.addParam(GeneralParameterNames.PACKAGE_FORMAT_ID, pkgFormat.toString());
        }
        if (archiveFormat != null) {
            params.addParam(GeneralParameterNames.ARCHIVING_FORMAT, archiveFormat);
        }
        if (compressionFormat != null) {
            params.addParam(GeneralParameterNames.COMPRESSION_FORMAT, compressionFormat);
        }
        if (packageName != null) {
            params.addParam(GeneralParameterNames.PACKAGE_NAME, packageName);
        }
        if (outputLocation != null) {
            params.addParam(GeneralParameterNames.PACKAGE_LOCATION, outputLocation.getAbsolutePath());
        }
        if (packageStagingLocation != null) {
            params.addParam(GeneralParameterNames.PACKAGE_STAGING_LOCATION, packageStagingLocation);
        }

        if (checksums != null && !checksums.isEmpty()) {
            params.addParam(GeneralParameterNames.CHECKSUM_ALGORITHMS, checksums);
        }
        if (serializationFormat != null) {
            params.addParam(GeneralParameterNames.REM_SERIALIZATION_FORMAT, serializationFormat);
        }

        return params;
    }


    /**
     * we validate locations of files passed as arguments elsewhere, but the locations passed as options
     * eventually end up in the PackageGenerationsParameters. We validate these parameter values only after
     * we finish the process of building the parameters from the various available sources.
     * @param params  the package generation parameters
     */
    private void validateLocationParameters(final PackageGenerationParameters params) {

        // required, cannot be null
        final String packageLocation = params.getParam(GeneralParameterNames.PACKAGE_LOCATION, 0);
        if (packageLocation == null || packageLocation.isEmpty()) {
            throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
        } else {
            final File packageLocationFile = new File(packageLocation);
            if (!packageLocationFile.exists()) {
                System.err.println(packageLocation);
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
        }
    }


    private InputStream createPackageMetadata() {
        final Properties metadata = new Properties();
        if (packageMetadataFile != null) {
            if (!packageMetadataFile.exists()) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
            try (InputStream fileStream = new FileInputStream(packageMetadataFile)) {
                metadata.load(fileStream);
            } catch (FileNotFoundException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION, e);
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
        }

        if (! metadata.containsKey(packageNameKey)) {
            final List<String> name = packageParams.getParam(packageNameKey);
            if (name != null && name.size() > 0) {
                metadata.setProperty(packageNameKey, name.get(name.size() - 1));
            } else {
                metadata.setProperty(packageNameKey, defaultPackageName);
            }
        } else if (packageName == null) {
            packageName = metadata.getProperty(packageNameKey);
        } else {
            metadata.setProperty(packageNameKey, packageName);
        }

        if (externalID != null) {
            metadata.setProperty(GeneralParameterNames.EXTERNAL_PROJECT_ID, externalID);
        }

        metadata.setProperty(BagItParameterNames.BAGGING_DATE, LocalDate.now().toString());

        final ByteArrayOutputStream metaDataOut = new ByteArrayOutputStream();
        InputStream metadataStream = null;
        try {
            metadata.store(metaDataOut, null);
            metaDataOut.close();
            metadataStream = new ByteArrayInputStream(metaDataOut.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return metadataStream;
    }


    private void copyPackage(final org.dataconservancy.packaging.tool.api.Package pkg) {
        // Determine the file to copy
        final String sourceName = pkg.getPackageName();
        final Path sourcePath = Paths.get(packageStagingLocation, sourceName);

        // If no output location is specified, don't copy the package
        if (outputLocation == null) {
            System.err.printf("Package '%s' has been created.\n", sourcePath);
            return;
        }

        // Determine the file to create
        Path destPath = Paths.get(outputLocation.getAbsolutePath(), sourceName);
        File dest = new File(destPath.toString());

        // If the file already exists and we can't overwrite it, find a new name
        if (! overwriteIfExists) {
            int index = 0;
            final String extension = sourceName.substring(packageName.length());
            while (dest.exists()) {
                // Append (#) to the root of the file name to avoid the conflict
                index++;
                final String destName = String.format("%s (%d)%s", packageName, index, extension);
                destPath = Paths.get(outputLocation.getAbsolutePath(), destName);
                dest = new File(destPath.toString());
            }
        }

        // Copy the file
        try {
            Files.copy(sourcePath, destPath, REPLACE_EXISTING);
            System.err.printf("Package '%s' has been created.\n", destPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
