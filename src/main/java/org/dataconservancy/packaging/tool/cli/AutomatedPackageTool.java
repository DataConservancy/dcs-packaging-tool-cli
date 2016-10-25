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
package org.dataconservancy.packaging.apt;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.dataconservancy.packaging.tool.api.PackagingFormat;
import org.dataconservancy.packaging.tool.model.GeneralParameterNames;
import org.dataconservancy.packaging.tool.model.PackageGenerationParameters;
import org.dataconservancy.packaging.tool.model.PackageGenerationParametersBuilder;
import org.dataconservancy.packaging.tool.model.PackageToolException;
import org.dataconservancy.packaging.tool.model.PackagingToolReturnInfo;
import org.dataconservancy.packaging.tool.model.ParametersBuildException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
/**
 * Created by jrm on 8/10/16.
 *
 * @author jrm
 */
public class AutomatedPackageTool {

    private final static String rulesFileName = "rules.xsd";
    private final static String packageMetadataFileName = "packageMetadata";
    private final static String packageGenerationsParametersFileName = "packageGenerationParameters";
    private final static File userDataconservancyDirectory = new File(System.getProperty("user.home") + File.pathSeparator + ".dataconservancy");
    private final static String defaultResourceConfigPath = "/org/dataconservancy/apt/config/";

    private final Logger log = LoggerFactory.getLogger(this.getClass());

       /*
	 *
	 * Arguments
	 */
	@Argument(required = true, index = 0, metaVar = "[content]", usage = "content root directory")
    private File contentRootFile = null;

    @Argument(required = false, index = 1, metaVar = "[profile]", usage = "domain profile file")
    private File domainProfileFile = null;

	/*
	 *
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
	 *
	 * Package Generation Options
	 */
	/** Packaging format */
	@Option(name = "-f", aliases = { "--format" }, usage = "packaging format to use")
	private PackagingFormat pkgFormat = PackagingFormat.BOREM;

	/** Package Generation Params location */
	@Option(name = "-g", aliases = { "--generation-params" }, metaVar = "<file>", usage = "package generation params file location")
	private static File packageGenerationParamsFile;

    /** Package Metadata File location */
    @Option(name = "-m", aliases = { "--package-metadata" }, metaVar = "<file>", usage = "package metadata file location")
	private static File packageMetadataFile;

    /** Archive format **/
    @Option(name = "-a", aliases = { "--archiving-format"}, metaVar = "tar|zip", usage = "Archive format to use when creating the package.  Defaults to tar")
    private String archiveFormat;

    /** Compression format for tar archives **/
    @Option(name = "-c", aliases = { "--compression-format"}, metaVar = "gz|none", usage = "Compression format, if archive type is tar.  If not specified, no compression is used.  Ignored if non-tar archive is used.")
    private String compressionFormat;

    /** Checksum algorithms **/
    @Option(name = "-s", aliases = { "--checksum"}, metaVar = "md5|sha1", usage = "Checksum algorithms to use.  If none specified, will use md5.  Can be specified multiple times")
    private List<String> checksums;

    /** Package Name **/
    @Option(name = "-n", aliases = { "--name", "--package-name"}, metaVar = "<name>", usage = "The package name, which also determines the output filename.  Will override value in Package Generation Parameters file.")
    private static String packageName;

    /** Package output location **/
    @Option(name = "-o", aliases = { "--location", "--output-location"}, metaVar = "<path>", usage = "The output directory to which the package file will be written.  Will override value in Package Generation Parameters file.")
    private static File outputLocation;

    /** Package staging location **/
    @Option(name = "--stage", aliases = { "--staging", "--staging-location", "--package-staging-location"}, metaVar = "<path>", usage = "The directory to which the package will be staged before building.  Will override value in Package Generation Parameters file.")
    private String packageStagingLocation;

    /** Force overwrite of target file **/
    @Option(name = "--overwrite", aliases = { "--force" }, usage = "If specified, will overwrite if the destination package file already exists without prompting.")
    private boolean overwriteIfExists = false;

    /** Write to stdout **/
    @Option(name = "--stdout", usage = "Write to stdout, instead of to a file.")
    private boolean stdout = false;

    /** Serialization Format **/
    @Option(name = "-z", aliases = { "--serialization", "--serialization-format"}, metaVar="JSONLD|TURTLE|XML", usage = "Serialization format for the ORE-ReM file")
    private String serializationFormat;

    /** Rules file **/
    @Option(name = "-r", aliases = {"--rules", "--rules-file"}, metaVar = "<path>", usage = "Thelocation of the rules file")
    private static File rulesFile;

    public static void main(String[] args) {

        final AutomatedPackageTool application = new AutomatedPackageTool();

		CmdLineParser parser = new CmdLineParser(application);
		parser.setUsageWidth(80);

		try {
			parser.parseArgument(args);

			/* Handle general options such as help, version */
			if (application.help) {
				parser.printUsage(System.err);
				System.err.println();
				System.exit(0);
			} else if (application.version) {
				System.err.println(AutomatedPackageTool.class.getPackage()
						.getImplementationVersion());
				System.exit(0);
			}

            if (!outputLocation.exists() || !outputLocation.isDirectory()) {
                System.err.println("Supplied output file directory " + outputLocation.getCanonicalPath() + " does not exist or is not a directory.");
                System.exit(1);
            }

            if (!(packageName.length() > 0)) {
                System.err.println("Bag name must have positive length.");
                System.exit(1);
            }

            if (packageMetadataFile != null && (!packageMetadataFile.exists() || !packageMetadataFile.isFile())) {
                System.err.println("Supplied package metadata file " + packageMetadataFile.getCanonicalPath() + " does not exist or is not a file.");
                System.exit(1);
            }

            if(rulesFile != null && (!rulesFile.exists() || !rulesFile.isFile())){
                System.err.println("Supplied rules file " + rulesFile.getCanonicalPath() + " does not exist or is not a file.");
                System.exit(1);
            }

			/* Run the package generation application proper */
			application.run();

		} catch (CmdLineException e) {
			/*
			 * This is an error in command line args, just print out usage data
			 * and description of the error.
			 */
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
         final ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext(
                "classpath*:org/dataconservancy/apt/config/applicationContext.xml",
                "classpath*:org/dataconservancy/config/applicationContext.xml",
                "classpath*:org/dataconservancy/packaging/tool/ser/config/applicationContext.xml");

        boolean useDefaults = true;

        // Prepare parameter builder
        PackageGenerationParametersBuilder parametersBuilder = appContext.getBean("packageGenerationParametersBuilder",
                PackageGenerationParametersBuilder.class);

        // Load parameters first from default, then override with home directory .packageGenerationParameters, then with
        // specified params file (if given).
        PackageGenerationParameters packageParams;

        try {
            packageParams = parametersBuilder.buildParameters(getClass().getResourceAsStream(defaultResourceConfigPath + packageGenerationsParametersFileName));
            updateCompression(packageParams);
        } catch (ParametersBuildException e) {
            throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_PARAM_BUILD_EXCEPTION, e);
        }

        File userParamsFile = new File(userDataconservancyDirectory, packageGenerationsParametersFileName);
        if (userParamsFile.exists()) {
            try {
                PackageGenerationParameters homeParams =
                        parametersBuilder.buildParameters(new FileInputStream(userParamsFile));

                System.err.println("Overriding generation parameters with values from standard '" + packageGenerationsParametersFileName + "'");
                useDefaults = false;
                updateCompression(homeParams);
                packageParams.overrideParams(homeParams);
            } catch (FileNotFoundException e) {
                // Do nothing, it's ok to not have this file
            } catch (ParametersBuildException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_PARAM_BUILD_EXCEPTION, e);
            }
        }

        if (packageGenerationParamsFile != null) {
            try {
                PackageGenerationParameters fileParams = parametersBuilder.
                        buildParameters(new FileInputStream(packageGenerationParamsFile));

                System.err.println("Overriding generation parameters with values from " + packageGenerationParamsFile + " specified on command line");
                useDefaults = false;
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

        // Finally, override with command line options
        // If any options overridden, this will cause useDefaults to become false, if it wasn't already
        PackageGenerationParameters flagParams = createCommandLinePrefs();
        if (!flagParams.getKeys().isEmpty()) {
            useDefaults = false;
            System.err.println("Overriding generation parameters using command line flags");
            updateCompression(flagParams);
            packageParams.overrideParams(flagParams);
        }

        //we need to validate any specified file locations in the package generation params to make sure they exist
        if (packageParams.getParam(GeneralParameterNames.PACKAGE_LOCATION) == null) {
            packageParams.addParam(GeneralParameterNames.PACKAGE_LOCATION, System.getProperty("java.io.tmpdir"));
        }
        validateLocationParameters(packageParams);

        // Resolve the rules file location. Priority is given to a command line file path, then to one in the user's home location, then to the app default
        if(rulesFile == null){
            File userRulesFile = new File(userDataconservancyDirectory, rulesFileName);
            if (userRulesFile.exists()){
                rulesFile = userRulesFile;
            } else {
                //get the default rules file supplied with the app
            }
        }


        System.err.println("MOOOOOOOOOOOOOOOOOOOOOOOO");

    }

     /**
     * we validate locations of files passed as arguments elsewhere, but the locations passed as options
     * eventually end up in the PackageGenerationsParameters. We validate these parameter values only after
     * we finish the process of building the parameters from the various available sources.
     * @param params  the package generation parameters
     */
    private void validateLocationParameters(PackageGenerationParameters params) {

        //required, cannot be null
        String packageLocation = params.getParam(GeneralParameterNames.PACKAGE_LOCATION, 0);
        if (packageLocation == null || packageLocation.isEmpty()) {
            throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
        } else {
            File packageLocationFile = new File(packageLocation);
            if (!packageLocationFile.exists()) {
                System.err.println(packageLocation);
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
        }
    }

   /**
     * Update the compression format for the parameters, if necessary.
     * Basically, if the archive format is "zip", it should set the compression
     * format to "none" unless another format is explicitly set.
     * @param params The package generation params, used to get the file needed
     */
    private void updateCompression(PackageGenerationParameters params) {
        String archive = params.getParam(GeneralParameterNames.ARCHIVING_FORMAT, 0);
        String compress = params.getParam(GeneralParameterNames.COMPRESSION_FORMAT, 0);

        //manually set the compression to none if archive is ZIP and no compression
        // is specifically set in this object, or if archive is exploded
        if (archive != null && ((archive.equals(ArchiveStreamFactory.ZIP) && compress == null) || archive.equals("exploded"))) {
            params.addParam(GeneralParameterNames.COMPRESSION_FORMAT, "none");
        }
    }

    /**
     * Create a PackageGenerationParameter for command line flags
     * @return a PackageGenerationParameter object with any command line overrides
     */
    private PackageGenerationParameters createCommandLinePrefs() {
        PackageGenerationParameters params = new PackageGenerationParameters();

        if (archiveFormat != null) {params.addParam(GeneralParameterNames.ARCHIVING_FORMAT, archiveFormat);}
        if (compressionFormat != null) {params.addParam(GeneralParameterNames.COMPRESSION_FORMAT, compressionFormat);}
        if (packageName != null) {
            params.addParam(GeneralParameterNames.PACKAGE_NAME, packageName);
           // params.addParam(BagItParameterNames.PKG_BAG_DIR, packageName);
        }
        if (outputLocation != null) {params.addParam(GeneralParameterNames.PACKAGE_LOCATION, outputLocation.getAbsolutePath());}
        if (packageStagingLocation != null) {params.addParam(GeneralParameterNames.PACKAGE_STAGING_LOCATION, packageStagingLocation);}

        if (checksums != null && !checksums.isEmpty()) {
            params.addParam(GeneralParameterNames.CHECKSUM_ALGORITHMS, checksums);
        }
        if(serializationFormat != null){
            params.addParam(GeneralParameterNames.REM_SERIALIZATION_FORMAT, serializationFormat);
        }
        return params;
    }


    private LinkedHashMap<String, List<String>> createPackageMetadata(){
        Properties props = new Properties();
        if(packageMetadataFile != null) {
            if(!packageMetadataFile.exists()){
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
            try (InputStream fileStream = new FileInputStream(packageMetadataFile)) {
                props.load(fileStream);
            } catch (FileNotFoundException e) {
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION, e);
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new PackageToolException(PackagingToolReturnInfo.CMD_LINE_FILE_NOT_FOUND_EXCEPTION);
            }
        }
        LinkedHashMap<String, List<String>> metadata = new LinkedHashMap<>();

        List<String> valueList;
        for (String key : props.stringPropertyNames()) {
            valueList = Arrays.asList(props.getProperty(key).trim().split("\\s*,\\s*"));
            metadata.put(key,valueList);
        }

        return metadata;
    }


}
