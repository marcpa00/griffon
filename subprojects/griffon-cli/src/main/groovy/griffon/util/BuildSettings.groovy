/*
 * Copyright 2008-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package griffon.util

import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.plugins.repository.TransferEvent
import org.apache.ivy.plugins.repository.TransferListener
import org.apache.ivy.util.DefaultMessageLogger
import org.apache.ivy.util.Message
import org.codehaus.griffon.artifacts.model.Plugin
import org.codehaus.griffon.resolve.GriffonCoreDependencies
import org.codehaus.griffon.resolve.IvyDependencyManager
import org.codehaus.groovy.runtime.StackTraceUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

import static griffon.util.ArtifactSettings.*
import static griffon.util.GriffonExceptionHandler.sanitize
import static griffon.util.GriffonNameUtils.capitalize
import static org.codehaus.griffon.cli.CommandLineConstants.*

/**
 * <p>Represents the project paths and other build settings
 * that the user can change when running the Griffon commands. Defaults
 * are provided for all settings, but the user can override those by
 * setting the appropriate system property or specifying a value for
 * it in the BuildConfig.groovy file.</p>
 * <p><b>Warning</b> The behaviour is poorly defined if you explicitly
 * set some of the project paths (such as {@link #projectWorkDir}),
 * but not others. If you set one of them explicitly, set all of them
 * to ensure consistent behaviour.</p>
 */
class BuildSettings extends AbstractBuildSettings {
    private static final Logger LOG = LoggerFactory.getLogger(BuildSettings)
    private static final String GRIFFON_APP = "griffon-app"

    static final Pattern JAR_PATTERN = ~/^\S+\.jar$/

    /**
     * The base directory of the application
     */
    public static final String APP_BASE_DIR = "base.dir"

    /**
     * The name of the system property for {@link #griffonWorkDir}.
     */
    public static final String WORK_DIR = "griffon.work.dir"

    /**
     * The name of the system property for {@link #projectWorkDir}.
     */
    public static final String PROJECT_WORK_DIR = "griffon.project.work.dir"

    /**
     * The name of the system property for {@link #projectPluginsDir}.
     */
    public static final String PLUGINS_DIR = "griffon.project.plugins.dir"

    /**
     * The name of the system property for {@link #resourcesDir}.
     */
    public static final String PROJECT_RESOURCES_DIR = "griffon.project.resource.dir"

    /**
     * The name of the system property for {@link #sourceDir}.
     */
    public static final String PROJECT_SOURCE_DIR = "griffon.project.source.dir"

    /**
     * The name of the system property for {@link #classesDir}.
     */
    public static final String PROJECT_CLASSES_DIR = "griffon.project.class.dir"

    /**
     * The name of the system property for {@link #testClassesDir}.
     */
    public static final String PROJECT_TEST_CLASSES_DIR = "griffon.project.test.class.dir"

    /**
     * The name of the system property for {@link #testResourcesDir}.
     */
    public static final String PROJECT_TEST_RESOURCES_DIR = "griffon.project.test.resource.dir"

    /**
     * The name of the system property for {@link #testReportsDir}.
     */
    public static final String PROJECT_TEST_REPORTS_DIR = "griffon.project.test.reports.dir"

    /**
     * The name of the system property for {@link #testReportsDir}.
     */
    public static final String PROJECT_DOCS_OUTPUT_DIR = "griffon.project.docs.output.dir"

    /**
     * The name of the system property for {@link #testSourceDir}.
     */
    public static final String PROJECT_TEST_SOURCE_DIR = "griffon.project.test.source.dir"

    /**
     * The name of the system property for {@link #projectTargetDir}.
     */
    public static final String PROJECT_TARGET_DIR = "griffon.project.target.dir"

    /**
     * The name of the system property for multiple {@link #buildListeners}.
     */
    public static final String BUILD_LISTENERS = "griffon.build.listeners"

    /**
     * The base directory for the build, which is normally the root
     * directory of the current project. If a command is run outside
     * of a project, then this will be the current working directory
     * that the command was launched from.
     */
    File baseDir

    /** Location of the current user's home directory - equivalent to "user.home" system property. */
    File userHome

    /**
     * Location of the Griffon distribution as usually identified by
     * the Griffon_HOME environment variable. This value may be
     * <code>null</code> if Griffon_HOME is not set, for example if a
     * project uses the Griffon JAR files directly.
     */
    File griffonHome

    /** The version of Griffon being used for the current script. */
    final String griffonVersion
    final String groovyVersion
    final String antVersion
    final String slf4jVersion
    final String springVersion

    /** The environment for the current script. */
    String griffonEnv

    /**
     * The compiler source level to use
     */
    String compilerSourceLevel = null

    /**
     * The compiler target level to use
     */
    String compilerTargetLevel = null

    String compilerDebug = 'yes'

    /** <code>true</code> if the default environment for a script should be used.  */
    boolean defaultEnv

    /**
     * whether to include source attachments in a resolve
     */
    boolean includeSource
    /**
     * whether to include javadoc attachments in a resolve
     */
    boolean includeJavadoc

    /**
     * Whether the project required build dependencies are externally configured (by Maven for example) or not
     */
    boolean dependenciesExternallyConfigured = false

    /**
     * Whether to enable resolving dependencies
     */
    boolean enableResolve = true

    /** The location of the Griffon working directory where non-project-specific temporary files are stored. */
    File griffonWorkDir

    /** The location of the project working directory for project-specific temporary files. */
    File projectWorkDir

    /** The location of the project target directory where reports, artifacts and so on are output. */
    File projectTargetDir

    /** The location to which Griffon compiles a project's classes. */
    File classesDir

    /** The location to which Griffon compiles a project's test classes. */
    File testClassesDir

    /** The location to which Griffon writes a project's test resources. */
    File testResourcesDir

    /** The location where Griffon keeps temporary copies of a project's resources. */
    File resourcesDir

    /** The location of the plain source. */
    File sourceDir

    /** The location of the test reports. */
    File testReportsDir

    /** The location of the documentation output. */
    File docsOutputDir

    /** The location of the test source. */
    File testSourceDir

    /** Src Encoding  */
    String sourceEncoding = 'UTF-8'

    /** The root loader for the build. This has the required libraries on the classpath. */
    URLClassLoader rootLoader

    /**
     * The settings used to establish the HTTP proxy to use for dependency resolution etc. 
     */
    ConfigObject proxySettings = new ConfigObject()

    /**
     * The file containing the proxy settings 
     */
    File proxySettingsFile;

    /** Implementation of the "griffonScript()" method used in Griffon scripts. */
    Closure griffonScriptClosure

    /** Implementation of the "includePluginScript()" method used in Griffon scripts. */
    Closure includePluginScriptClosure

    /** Implementation of the "includeScript()" method used in Griffon scripts. */
    Closure includeScriptClosure

    /** Implementation of the "resolveResources()" method used in Griffon scripts. */
    Closure resolveResourcesClosure

    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver()

    /**
     * List of jars provided in the applications 'lib' directory
     */
    List applicationJars = []

    List buildListeners = []

    /**
     * Return whether the BuildConfig has been modified
     */
    boolean modified = false

    private List<File> compileDependencies = []
    private boolean defaultCompileDepsAdded = false

    /** List containing the compile-time dependencies of the app as File instances. */
    List<File> getCompileDependencies() {
        if (!defaultCompileDepsAdded) {
            compileDependencies += defaultCompileDependencies
            defaultCompileDepsAdded = true
        }
        return compileDependencies
    }

    /**
     * Sets the compile time dependencies for the project
     */
    void setCompileDependencies(List<File> deps) {
        compileDependencies = deps
    }

    /**
     * The dependency report for all configurations
     */
    @Lazy ResolveReport allDependenciesReport = {
        dependencyManager.resolveAllDependencies()
    }()

    ResolveReport buildResolveReport
    ResolveReport compileResolveReport
    ResolveReport testResolveReport
    ResolveReport runtimeResolveReport

    /** List containing the default (resolved via the dependencyManager) compile-time dependencies of the app as File instances.  */
    private List<File> internalCompileDependencies
    @Lazy List<File> defaultCompileDependencies = {
        if (internalCompileDependencies) return internalCompileDependencies
        LOG.debug "Resolving [compile] dependencies..."
        List<File> jarFiles
        if (shouldResolve()) {
            compileResolveReport = dependencyManager.resolveDependencies(IvyDependencyManager.COMPILE_CONFIGURATION)
            jarFiles = compileResolveReport.getArtifactsReports(null, false).findAll {it.downloadStatus.toString() != 'failed'}.localFile + applicationJars
            LOG.debug("Resolved jars for [compile]: ${{-> jarFiles.join('\n')}}")
        } else {
            jarFiles = []
        }
        return jarFiles
    }()

    private List<File> testDependencies = []
    private boolean defaultTestDepsAdded = false

    /** List containing the test-time dependencies of the app as File instances. */
    List<File> getTestDependencies() {
        if (!defaultTestDepsAdded) {
            testDependencies += defaultTestDependencies
            defaultTestDepsAdded = true
        }
        return testDependencies
    }

    /**
     * Sets the test time dependencies for the project
     */
    void setTestDependencies(List<File> deps) {
        testDependencies = deps
    }

    /** List containing the default test-time dependencies of the app as File instances.  */
    private List<File> internalTestDependencies
    @Lazy List<File> defaultTestDependencies = {
        LOG.debug "Resolving [test] dependencies..."
        if (internalTestDependencies) return internalTestDependencies
        if (shouldResolve()) {
            testResolveReport = dependencyManager.resolveDependencies(IvyDependencyManager.TEST_CONFIGURATION)
            def jarFiles = testResolveReport.getArtifactsReports(null, false).findAll {it.downloadStatus.toString() != 'failed'}.localFile + applicationJars
            LOG.debug("Resolved jars for [test]: ${{-> jarFiles.join('\n')}}")
            return jarFiles
        } else {
            return []
        }
    }()

    private List<File> runtimeDependencies = []
    private boolean defaultRuntimeDepsAdded = false

    /** List containing the runtime dependencies of the app as File instances. */
    List<File> getRuntimeDependencies() {
        if (!defaultRuntimeDepsAdded) {
            runtimeDependencies += defaultRuntimeDependencies
            defaultRuntimeDepsAdded = true
        }
        return runtimeDependencies
    }

    /**
     * Sets the runtime dependencies for the project
     */
    void setRuntimeDependencies(List<File> deps) {
        runtimeDependencies = deps
    }

    /** List containing the default runtime-time dependencies of the app as File instances.  */
    private List<File> internalRuntimeDependencies
    @Lazy List<File> defaultRuntimeDependencies = {
        LOG.debug "Resolving [runtime] dependencies..."
        if (internalRuntimeDependencies) return internalRuntimeDependencies
        if (shouldResolve()) {
            runtimeResolveReport = dependencyManager.resolveDependencies(IvyDependencyManager.RUNTIME_CONFIGURATION)
            def jarFiles = runtimeResolveReport.getArtifactsReports(null, false).findAll {it.downloadStatus.toString() != 'failed'}.localFile + applicationJars
            LOG.debug("Resolved jars for [runtime]: ${{-> jarFiles.join('\n')}}")
            return jarFiles
        }
        return []
    }()

    private List<File> buildDependencies = []
    private boolean defaultBuildDepsAdded = false

    /** List containing the runtime dependencies of the app as File instances.  */
    List<File> getBuildDependencies() {
        if (!defaultBuildDepsAdded) {
            buildDependencies += defaultBuildDependencies
            defaultBuildDepsAdded = true
        }
        return buildDependencies
    }

    /**
     * Sets the runtime dependencies for the project
     */
    void setBuildDependencies(List<File> deps) {
        buildDependencies = deps
    }

    /**
     * List containing the dependencies required for the build system only
     */
    private List<File> internalBuildDependencies
    @Lazy List<File> defaultBuildDependencies = {
        LOG.debug "Resolving [build] dependencies..."
        if (dependenciesExternallyConfigured) {
            return []
        }
        if (internalBuildDependencies) return internalBuildDependencies
        if (shouldResolve()) {
            buildResolveReport = dependencyManager.resolveDependencies(IvyDependencyManager.BUILD_CONFIGURATION)
            def jarFiles = buildResolveReport.getArtifactsReports(null, false).findAll {it.downloadStatus.toString() != 'failed'}.localFile + applicationJars
            LOG.debug("Resolved jars for [build]: ${{-> jarFiles.join('\n')}}")
            return jarFiles
        }
        return []
    }()

    protected boolean shouldResolve() {
        return dependencyManager != null && enableResolve
    }

    /**
     * Manages dependencies and dependency resolution in a Griffon application
     */
    IvyDependencyManager dependencyManager

    /*
     * This is an unclever solution for handling "sticky" values in the
     * project paths, but trying to be clever so far has failed. So, if
     * the values of properties such as "griffonWorkDir" are set explicitly
     * (from outside the class), then they are not overridden by system
     * properties/build config.
     *
     * TODO Sort out this mess. Must decide on what can set this properties,
     * when, and how. Also when and how values can be overridden. This
     * is critically important for the Maven and Ant support.
  */
    private boolean griffonWorkDirSet
    private boolean projectWorkDirSet
    private boolean projectTargetDirSet
    private boolean classesDirSet
    private boolean testClassesDirSet
    private boolean resourcesDirSet
    private boolean testResourcesDirSet
    private boolean sourceDirSet
    private boolean testReportsDirSet
    private boolean docsOutputDirSet
    private boolean testSourceDirSet
    private boolean buildListenersSet
    private boolean verboseCompileSet
    private boolean sourceEncodingSet
    private String resolveChecksum
    private Map resolveCache = new ConcurrentHashMap()
    private boolean readFromCache = false

    final Map<String, String> systemProperties = [:]
    public final PluginSettings pluginSettings
    public final ArtifactSettings artifactSettings

    BuildSettings() {
        this(null, null)
    }

    BuildSettings(String griffonHome) {
        this(new File(griffonHome), null)
    }

    BuildSettings(File griffonHome) {
        this(griffonHome, null)
    }

    BuildSettings(File griffonHome, File baseDir) {
        userHome = new File(System.getProperty("user.home"))
        pluginSettings = new PluginSettings(this)
        artifactSettings = new ArtifactSettings(this)

        if (griffonHome) this.griffonHome = griffonHome

        // Load the 'build.properties' file from the classpath and
        // retrieve the Griffon version from it.
        Properties buildProps = new Properties()
        try {
            loadBuildPropertiesFromClasspath(buildProps)
            griffonVersion = buildProps.'griffon.version'
            groovyVersion = buildProps.'groovy.version'
            antVersion = buildProps.'ant.version'
            slf4jVersion = buildProps.'slf4j.version'
            springVersion = buildProps.'spring.version'
        }
        catch (IOException ex) {
            sanitize(ex).printStackTrace()
            throw new IOException("Unable to find 'build.properties' - make " +
                    "that sure the 'griffon-cli-*.jar' file is on the classpath.")
        }

        // Update the base directory. This triggers some extra config.
        setBaseDir(baseDir)

        if (![Environment.DEVELOPMENT, Environment.TEST].contains(Environment.current)) {
            modified = true
        }

        resolveResourcesClosure = {String pattern ->
            try {
                Resource[] resources = RESOLVER.getResources(pattern)
                // Filter hidden folders from OSX
                if (resources) {
                    List<Resource> tmp = []
                    for (Resource r : resources) {
                        if (r.URL.toString().contains('.DS_Store')) continue
                        tmp.add(r)
                    }
                    resources = tmp.toArray(new Resource[tmp.size()])
                }
                return resources
            } catch (Throwable e) {
                return [] as Resource[]
            }
        }

        // The "griffonScript" closure definition. Returns the location
        // of the corresponding script file if GRIFFON_HOME is set,
        // otherwise it loads the script class using the Gant classloader.
        griffonScriptClosure = {String name ->
            File potentialScript = new File("${griffonHome}/scripts/${name}.groovy")
            potentialScript = potentialScript.exists() ? potentialScript : new File("${griffonHome}/scripts/${name}_.groovy")
            if (potentialScript.exists()) {
                return potentialScript
            } else {
                try {
                    return classLoader.loadClass("${name}_")
                }
                catch (e) {
                    return classLoader.loadClass(name)
                }
            }
        }

        includeScriptClosure = {String name ->
            try {
                return griffonScriptClosure(name)
            } catch (ClassNotFoundException cnfe) {
                Resource[] potentialScripts = pluginSettings.getAvailableScripts(name)
                switch (potentialScripts.size()) {
                    case 1: return potentialScripts[0].file
                    case 0: throw new IllegalArgumentException("No script matches the name $name")
                    default:
                        throw new IllegalArgumentException("Multiple choices available:\n${potentialScripts.file.absolutePath.join('\n')}")
                }
            }
        }

        includePluginScriptClosure = {String pluginName, String scriptName ->
            File pluginHome = artifactSettings.findArtifactDirForName(Plugin.TYPE, pluginName)
            if (!pluginHome) pluginHome = artifactSettings.findArtifactDirForName(Plugin.TYPE, pluginName, true)
            if (!pluginHome) return
            File scriptFile = new File(pluginHome, "/scripts/${scriptName}.groovy")
            if (scriptFile.exists()) includeTargets << scriptFile
        }
    }

    private def loadBuildPropertiesFromClasspath(Properties buildProps) {
        InputStream stream = getClass().classLoader.getResourceAsStream("griffon.build.properties")
        if (stream == null) {
            stream = getClass().classLoader.getResourceAsStream("build.properties")
        }
        if (stream) {
            buildProps.load(stream)
        }
    }

    /**
     * Returns the current base directory of this project.
     */
    File getBaseDir() { baseDir }

    /**
     * <p>Changes the base directory, making sure that everything that
     * depends on it gets refreshed too. If you have have previously
     * loaded a configuration file, you should load it again after
     * calling this method.</p>
     * <p><b>Warning</b> This method resets the project paths, so if
     * they have been set manually by the caller, then that information
     * will be lost!</p>
     */
    void setBaseDir(File newBaseDir) {
        baseDir = newBaseDir ?: establishBaseDir()
        // Initialize Metadata
        Metadata.getInstance(new File(baseDir, "application.properties"))

        // Set up the project paths, using an empty config for now. The
        // paths will be updated if and when a BuildConfig configuration
        // file is loaded.
        config = new ConfigObject()
        establishProjectStructure()

        if (baseDir) {
            // Add the application's libraries.
            def appLibDir = new File(baseDir, "lib")
            if (appLibDir.exists()) {
                appLibDir.eachFileMatch(JAR_PATTERN) {
                    applicationJars << it
                }
            }
        }
    }

    void setGriffonWorkDir(File dir) {
        griffonWorkDir = dir
        griffonWorkDirSet = true
    }

    void setProjectWorkDir(File dir) {
        projectWorkDir = dir
        projectWorkDirSet = true
    }

    void setProjectTargetDir(File dir) {
        projectTargetDir = dir
        projectTargetDirSet = true
    }

    void setClassesDir(File dir) {
        classesDir = dir
        classesDirSet = true
    }

    void setTestClassesDir(File dir) {
        testClassesDir = dir
        testClassesDirSet = true
    }

    void setResourcesDir(File dir) {
        resourcesDir = dir
        resourcesDirSet = true
    }

    void setTestResourcesDir(File dir) {
        testResourcesDir = dir
        testResourcesDirSet = true
    }

    void setSourceDir(File dir) {
        sourceDir = dir
        sourceDirSet = true
    }

    void setTestReportsDir(File dir) {
        testReportsDir = dir
        testReportsDirSet = true
    }

    void setTestSourceDir(File dir) {
        testSourceDir = dir
        testSourceDirSet = true
    }

    void setBuildListeners(buildListeners) {
        this.buildListeners = buildListeners.toList()
        buildListenersSet = true
    }

    Object[] getBuildListeners() { buildListeners.toArray() }

    void setSourceEncoding(String encoding) {
        sourceEncoding = encoding
        sourceEncodingSet = true
    }

    ConfigObject resetConfig() {
        config = new ConfigObject()
        gcl = null
        settingsFileLoaded = false
        loadConfig()
    }

    /**
     * Loads the application's BuildConfig.groovy file if it exists
     * and returns the corresponding config object. If the file does
     * not exist, this returns an empty config.
     */
    ConfigObject loadConfig() {
        loadConfig(new File(baseDir, 'griffon-app/conf/BuildConfig.groovy'))
    }

    /**
     * Loads the given configuration file if it exists and returns the
     * corresponding config object. If the file does not exist, this
     * returns an empty config.
     */
    ConfigObject loadConfig(File configFile) {
        try {
            loadSettingsFile()
            if (configFile.exists()) {
                // To avoid class loader issues, we make sure that the
                // Groovy class loader used to parse the config file has
                // the root loader as its parent. Otherwise we get something
                // like NoClassDefFoundError for Script.
                GroovyClassLoader gcl = obtainGroovyClassLoader()
                ConfigSlurper slurper = createConfigSlurper()

                URL configUrl = configFile.toURI().toURL()
                Script script = gcl.parseClass(configFile)?.newInstance()

                config.setConfigFile(configUrl)
                loadConfig(slurper.parse(script))
            } else {
                postLoadConfig()
            }
        } catch (e) {
            StackTraceUtils.deepSanitize e
            throw e
        }
        config
    }

    ConfigObject loadConfig(ConfigObject config) {
        try {
            this.config.merge(config)
            return this.config
        }
        finally {
            postLoadConfig()
        }
    }

    protected void postLoadConfig() {
        establishProjectStructure()
        parseGriffonBuildListeners()
        flatConfig = config.flatten()
        dependencyManager = configureDependencyManager(config)
    }

    protected boolean settingsFileLoaded = false

    protected ConfigObject loadSettingsFile() {
        if (!settingsFileLoaded) {
            def settingsFile = new File("$userHome/.griffon/settings.groovy")
            def gcl = obtainGroovyClassLoader()
            def slurper = createConfigSlurper()
            if (settingsFile.exists()) {
                Script script = gcl.parseClass(settingsFile)?.newInstance()
                if (script) {
                    config = slurper.parse(script)

                    if (config.containsKey('projects')) {
                        ConfigObject projects = config.remove('projects')
                        String projectName = Metadata.current.getApplicationName()
                        if (projects.containsKey(projectName)) {
                            config.merge(projects.get(projectName))
                        }
                    }
                }
            }

            this.proxySettingsFile = new File("$userHome/.griffon/ProxySettings.groovy")
            if (proxySettingsFile.exists()) {
                slurper = createConfigSlurper()
                try {
                    Script script = gcl.parseClass(proxySettingsFile)?.newInstance()
                    if (script) {
                        proxySettings = slurper.parse(script)
                        def current = proxySettings.currentProxy
                        if (current) {
                            proxySettings[current]?.each { key, value ->
                                System.setProperty(key, value)
                            }
                        }
                    }
                }
                catch (e) {
                    println "WARNING: Error configuring proxy settings: ${e.message}"
                }

            }

            settingsFileLoaded = true
        }
        config
    }

    private GroovyClassLoader gcl

    GroovyClassLoader obtainGroovyClassLoader() {
        if (gcl == null) {
            gcl = rootLoader != null ? new GroovyClassLoader(rootLoader) : new GroovyClassLoader(ClassLoader.getSystemClassLoader())
        }
        return gcl
    }

    IvyDependencyManager configureDependencyManager(ConfigObject config) {
        Message.setDefaultLogger new DefaultMessageLogger(Message.MSG_WARN)

        Metadata metadata = Metadata.current
        def appName = metadata.getApplicationName() ?: "griffon"
        def appVersion = metadata.getApplicationVersion() ?: griffonVersion

        IvyDependencyManager dependencyManager = new IvyDependencyManager(appName,
                appVersion, this, metadata)

        dependencyManager.includeJavadoc = includeJavadoc
        dependencyManager.includeSource = includeSource

        dependencyManager.transferListener = { TransferEvent e ->
            switch (e.eventType) {
                case TransferEvent.TRANSFER_STARTED:
                    def resourceName = e.resource.name
                    resourceName = resourceName[resourceName.lastIndexOf('/') + 1..-1]
                    println "Downloading: ${resourceName}"
                    break
                case TransferEvent.TRANSFER_COMPLETED:
                    println "Download complete."
                    break
            }
        } as TransferListener

        def griffonConfig = config.griffon
        // If griffon.dependency.cache.dir is set, use it for Ivy.
        /*
        if (griffonConfig.dependency.cache.dir) {
            dependencyManager.ivySettings.defaultCache = griffonConfig.dependency.cache.dir as File
        }
        */

        if (!dependenciesExternallyConfigured) {
            GriffonCoreDependencies coreDependencies = new GriffonCoreDependencies(griffonVersion, this)
            griffonConfig.global.dependency.resolution = coreDependencies.createDeclaration()
            def credentials = griffonConfig.project.ivy.authentication
            if (credentials instanceof Closure) {
                dependencyManager.parseDependencies credentials
            }
        } else {
            // Even if the dependencies are handled externally, we still
            // to handle plugin dependencies.
            griffonConfig.global.dependency.resolution = {
                repositories {

                }
            }
        }

        def dependencyConfig = griffonConfig.project.dependency.resolution
        if (!dependencyConfig) {
            dependencyConfig = griffonConfig.global.dependency.resolution
            dependencyManager.inheritsAll = true
        }
        if (dependencyConfig) {
            dependencyManager.parseDependencies dependencyConfig
        }
        dependencyManager
    }

    Closure pluginDependencyHandler() {
        return pluginDependencyHandler(dependencyManager)
    }

    public static final int RESOLUTION_SKIPPED = 0i
    public static final int RESOLUTION_OK = 1i
    public static final int RESOLUTION_ERROR = 2i

    Closure pluginDependencyHandler(IvyDependencyManager dependencyManager) {
        ConfigSlurper pluginSlurper = createConfigSlurper()

        return { File dir, String pluginName, String pluginVersion ->
            String path = dir.absolutePath
            List<File> dependencyDescriptors = [
                    new File("$path/dependencies.groovy"),
                    new File("$path/plugin-dependencies.groovy")
            ]

            int result = RESOLUTION_SKIPPED
            debug("Resolving plugin ${pluginName}-${pluginVersion} JAR dependencies")
            dependencyDescriptors.each { File dependencyDescriptor ->
                if (dependencyDescriptor.exists()) {
                    def gcl = obtainGroovyClassLoader()

                    try {
                        debug("Parsing dependencies from ${dependencyDescriptor}")
                        Script script = gcl.parseClass(dependencyDescriptor)?.newInstance()
                        if (script) {
                            pluginSlurper.binding = [
                                    pluginName: pluginName,
                                    pluginVersion: pluginVersion,
                                    pluginDirPath: path,
                                    griffonVersion: this.griffonVersion,
                                    groovyVersion: this.groovyVersion,
                                    springVersion: this.springVersion,
                                    antVersion: this.antVersion,
                                    slf4jVersion: this.slf4jVersion
                            ]
                            def pluginConfig = pluginSlurper.parse(script)
                            def pluginDependencyConfig = pluginConfig.griffon.project.dependency.resolution
                            if (pluginDependencyConfig instanceof Closure) {
                                dependencyManager.parseDependencies(pluginName, pluginDependencyConfig)
                            }
                        }
                        if (result != RESOLUTION_ERROR) result = RESOLUTION_OK
                    } catch (e) {
                        result = RESOLUTION_ERROR
                        println "WARNING: Dependencies cannot be resolved for plugin [$pluginName] due to error: ${e.message}"
                    }
                }
            }
            return result
        }
    }

    ConfigSlurper createConfigSlurper() {
        ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
        slurper.setBinding(
                basedir: baseDir.path,
                baseFile: baseDir,
                baseName: baseDir.name,
                griffonHome: griffonHome?.path,
                griffonVersion: griffonVersion,
                userHome: userHome,
                griffonSettings: this,
                appName: Metadata.current.getApplicationName(),
                appVersion: Metadata.current.getApplicationVersion())
        return slurper
    }

    File isPluginProject() {
        baseDir.listFiles().find {it.name.endsWith(PLUGIN_DESCRIPTOR_SUFFIX)}
    }

    File isArchetypeProject() {
        baseDir.listFiles().find {it.name.endsWith(ARCHETYPE_DESCRIPTOR_SUFFIX)}
    }

    File isAddonPlugin() {
        baseDir.listFiles().find {
            it.name.endsWith(ADDON_DESCRIPTOR_SUFFIX) ||
                    it.name.endsWith(ADDON_DESCRIPTOR_SUFFIX_JAVA)
        }
    }

    boolean isGriffonProject() {
        baseDir.listFiles().find { it.name == 'application.properties' } &&
                baseDir.listFiles().find { it.name == GRIFFON_APP && it.directory }
    }

    boolean isOfflineMode() {
        getConfigValue(KEY_OFFLINE_MODE, false) as boolean
    }

    private void establishProjectStructure() {
        // The third argument to "getPropertyValue()" is either the
        // existing value of the corresponding field, or if that's
        // null, a default value. This ensures that we don't override
        // settings provided by, for example, the Maven plugin.
        def props = config.toProperties()
        compilerSourceLevel = getPropertyValue(KEY_COMPILER_SOURCE_LEVEL, props, null)
        compilerTargetLevel = getPropertyValue(KEY_COMPILER_TARGET_LEVEL, props, null)
        compilerDebug = getPropertyValue(KEY_COMPILER_DEBUG, props, 'yes')

        // read metadata file
        Metadata.current
        if (!griffonWorkDirSet) griffonWorkDir = new File(getPropertyValue(WORK_DIR, props, "${userHome}/.griffon/${griffonVersion}"))
        if (!projectWorkDirSet) projectWorkDir = new File(getPropertyValue(PROJECT_WORK_DIR, props, "$griffonWorkDir/projects/${baseDir.name}"))
        if (!projectTargetDirSet) projectTargetDir = new File(getPropertyValue(PROJECT_TARGET_DIR, props, "$baseDir/target"))
        if (!classesDirSet) classesDir = new File(getPropertyValue(PROJECT_CLASSES_DIR, props, "$projectWorkDir/classes"))
        if (!testClassesDirSet) testClassesDir = new File(getPropertyValue(PROJECT_TEST_CLASSES_DIR, props, "$projectWorkDir/test-classes"))
        if (!resourcesDirSet) resourcesDir = new File(getPropertyValue(PROJECT_RESOURCES_DIR, props, "$projectWorkDir/resources"))
        if (!testResourcesDirSet) testResourcesDir = new File(getPropertyValue(PROJECT_TEST_RESOURCES_DIR, props, "$projectWorkDir/test-resources"))
        if (!sourceDirSet) sourceDir = new File(getPropertyValue(PROJECT_SOURCE_DIR, props, "$baseDir/src"))
        if (!projectPluginsDirSet) this.@projectPluginsDir = new File(getPropertyValue(PLUGINS_DIR, props, "$projectWorkDir/plugins"))
        if (!testReportsDirSet) testReportsDir = new File(getPropertyValue(PROJECT_TEST_REPORTS_DIR, props, "${projectTargetDir}/test-reports"))
        if (!docsOutputDirSet) docsOutputDir = new File(getPropertyValue(PROJECT_DOCS_OUTPUT_DIR, props, "${projectTargetDir}/docs"))
        if (!testSourceDirSet) testSourceDir = new File(getPropertyValue(PROJECT_TEST_SOURCE_DIR, props, "${baseDir}/test"))
        if (!sourceEncodingSet) sourceEncoding = getPropertyValue(KEY_SOURCE_ENCODING, props, "UTF-8")
    }

    protected void parseGriffonBuildListeners() {
        if (!buildListenersSet) {
            def listenersValue = System.getProperty(BUILD_LISTENERS) ?: config.griffon.build.listeners // Anyway to use the constant to do this?
            if (listenersValue) {
                def add = {
                    if (it instanceof String) {
                        it.split(',').each { this.@buildListeners << it }
                    } else if (it instanceof Class) {
                        this.@buildListeners << it
                    } else {
                        throw new IllegalArgumentException("$it is not a valid value for $BUILD_LISTENERS")
                    }
                }

                (listenersValue instanceof Collection) ? listenersValue.each(add) : add(listenersValue)
            }
            buildListenersSet = true
        }
    }

    private getPropertyValue(String propertyName, Properties props, String defaultValue) {
        // First check whether we have a system property with the given name.
        def value = getValueFromSystemOrBuild(propertyName, props)

        // Return the BuildSettings value if there is one, otherwise
        // use the default.
        return value != null ? value : defaultValue
    }

    private getValueFromSystemOrBuild(String propertyName, Properties props) {
        def value = System.getProperty(propertyName)
        if (value != null) return value

        // Now try the BuildSettings config.
        value = props[propertyName]
        return value
    }

    private File establishBaseDir() {
        def sysProp = System.getProperty(APP_BASE_DIR)
        def baseDir
        if (sysProp) {
            baseDir = sysProp == '.' ? new File("") : new File(sysProp)
        }
        else {
            baseDir = new File("")
            if (!new File(baseDir, GRIFFON_APP).exists()) {
                // be careful with this next step...
                // baseDir.parentFile will return null since baseDir is new File("")
                // baseDir.absoluteFile needs to happen before retrieving the parentFile
                def parentDir = baseDir.absoluteFile.parentFile

                // keep moving up one directory until we find
                // one that contains the griffon-app dir or get
                // to the top of the filesystem...
                while (parentDir != null && !new File(parentDir, GRIFFON_APP).exists()) {
                    parentDir = parentDir.parentFile
                }

                if (parentDir != null) {
                    // if we found the project root, use it
                    baseDir = parentDir
                }
            }

        }
        return baseDir.canonicalFile
    }

    void updateDependenciesFor(String conf, List<File> dependencies) {
        List<File> existingDependencies = this."get${capitalize(conf)}Dependencies"()
        List<File> newDependencies = dependencies.unique() - existingDependencies
        if (newDependencies) {
            if (LOG.debugEnabled) {
                LOG.debug("Adding the following dependencies to $conf: ${newDependencies.name.join(', ')}")
            }
            this."get${capitalize(conf)}Dependencies"().addAll(newDependencies)
            for (File jar : newDependencies) {
                getClass().classLoader.rootLoader.addURL(jar.toURI().toURL())
            }
        }

        // required for testing until we spin tests out to their own process
        if (conf == 'test') {
            updateDependenciesFor('build', dependencies)
        }
    }

    Object getConfigValue(String key, Object defaultValue) {
        Object value = System.getProperty(key)
        if (value != null) return value
        config.flatten([:])[key] ?: defaultValue
    }
}
