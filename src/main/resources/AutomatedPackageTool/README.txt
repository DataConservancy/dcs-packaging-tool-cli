 usage: AutomatedPackageTool [options] <content path> <metadata file>
 
 <content path>                         : content root directory
 <metadata file>                        : package metadata file location
 --overwrite (--force)                  : If specified, will overwrite if the
                                          destination package file already
                                          exists without prompting. (default:
                                          true)
 --stage (--staging,                    : The directory to which the package
    --staging-location,                   will be staged before building.  Will
    --package-staging-location) <path>    override value in Package Generation
                                          Parameters file. (default:
                                          c:/temp/staging)
 -a (--archiving-format) tar|zip        : Archive format to use when creating
                                          the package.  Defaults to tar
 -c (--compression-format) gz|none      : Compression format, if archive type
                                          is tar.  If not specified, no
                                          compression is used.  Ignored if
                                          non-tar archive is used.
 -d (-debug, --debug)                   : print debug information (default:
                                          false)
 -eid (--external-project-id) VAL       : External project ID to associate with
                                          the package
 -f (--format) [BOREM | TEST]           : packaging format to use (default:
                                          BOREM)
 -g (--generation-params) <file>        : package generation params file
                                          location
 -h (-help, --help)                     : print help message (default: true)
 -i (-info, --info)                     : print parameter info (default: false)
 -n (--name, --package-name) <name>     : The package name, which also
                                          determines the output filename.  Will
                                          override value in Package Generation
                                          Parameters file. (default: FooBar)
 -o (--location, --output-location)     : The output directory to which the
    <path>                                package file will be written.  Will
                                          override value in Package Generation
                                          Parameters file. (default: C:\temp)
 -r (--rules, --rules-file) <path>      : The location of the rules file
 -s (--checksum) md5|sha1               : Checksum algorithms to use.  If none
                                          specified, will use md5.  Can be
                                          specified multiple times
 -v (-version, --version)               : print version information (default:
                                          false)
 -z (--serialization,                   : Serialization format for the ORE-ReM
    --serialization-format)               file
    JSONLD|TURTLE|XML