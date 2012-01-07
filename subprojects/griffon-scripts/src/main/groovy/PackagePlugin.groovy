/*
 * Copyright 2004-2011 the original author or authors.
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

import org.codehaus.griffon.artifacts.ArtifactUtils
import org.codehaus.griffon.artifacts.model.Plugin

/**
 * @author Andres Almiray
 */

includeTargets << griffonScript('_PackageArtifact')
includeTargets << griffonScript('_GriffonPackage')
includeTargets << griffonScript('_PackageAddon')
includeTargets << griffonScript('_GriffonDocs')

PLUGIN_RESOURCES = [
        INCLUSIONS: [
                'griffon-app/conf/**',
                'lib/**',
                'scripts/**',
                'src/templates/**',
                'LICENSE*',
                'README*'
        ],
        EXCLUSIONS: [
                'griffon-app/conf/Application.groovy',
                'griffon-app/conf/Builder.groovy',
                'griffon-app/conf/Config.groovy',
                'griffon-app/conf/BuildConfig.groovy',
                'griffon-app/conf/metainf/**',
                '**/.svn/**',
                'test/**',
                '**/CVS/**'
        ]
]

target(packagePlugin: 'Packages a Griffon plugin') {
    depends(checkVersion)

    pluginDescriptor = ArtifactUtils.getPluginDescriptor(basedir)
    if (!pluginDescriptor?.exists()) {
        event('StatusFinal', ['Current directory does not appear to be a Griffon plugin project.'])
        exit(1)
    }

    artifactInfo = loadArtifactInfo(Plugin.TYPE, pluginDescriptor)
    pluginName = artifactInfo.name
    pluginVersion = artifactInfo.version
    packageArtifact()
}

setDefaultTarget(packagePlugin)

target('package_plugin': '') {
    depends(compile, packageAddon, pluginDocs, pluginShared)

    if (griffonSettings.dependencyManager.hasApplicationDependencies()) {
        ant.copy(file: "$basedir/griffon-app/conf/BuildConfig.groovy",
                tofile: "$artifactPackageDirPath/dependencies.groovy", failonerror: false)
    }

    File runtimeJar = new File("${artifactPackageDirPath}/dist/griffon-${name}-${version}-runtime.jar")
    File compileJar = new File("${artifactPackageDirPath}/dist/griffon-${name}-${version}-compile.jar")
    File testJar = new File("${artifactPackageDirPath}/dist/griffon-${name}-${version}-test.jar")

    String compile = ''
    if (runtimeJar.exists()) {
        compile = "compile(group: '${name}', name: 'griffon-${name}', version: '${version}', classifier: 'runtime')"
    }
    if (compileJar.exists()) {
        compile += "\n\t\tcompile(group: '${name}', name: 'griffon-${name}', version: '${version}' classifier: 'compile')"
    }
    String test = ''
    if (testJar.exists()) {
        test = "test(group: '${name}', name: 'griffon-${name}', version: ${version}', classifier: 'test')"
    }

    File dependencyDescriptor = new File("${artifactPackageDirPath}/plugin-dependencies.groovy")
    dependencyDescriptor.text = """
        |griffon.project.dependency.resolution = {
        |    repositories {
        |        flatDir(name: 'plugin ${name}-${version}', dirs: [
        |            '\${pluginDirPath}/dist'
        |        ])
        |    }
        |
        |    dependencies {
        |        ${compile.trim()}
        |        $test
        |    }
        |}""".stripMargin().trim()
}

target('post_package_plugin': '') {
    ant.zip(destfile: "${artifactPackageDirPath}/${artifactZipFileName}", update: true, filesonly: true) {
        fileset(dir: basedir) {
            PLUGIN_RESOURCES.INCLUSIONS.each {
                include(name: it)
            }
            PLUGIN_RESOURCES.EXCLUSIONS.each {
                exclude(name: it)
            }
        }

        if (pluginDescriptor.metaClass.hasProperty(pluginDescriptor, 'pluginIncludes')) {
            def additionalIncludes = pluginDescriptor.pluginIncludes
            if (additionalIncludes) {
                zipfileset(dir: basedir) {
                    additionalIncludes.each { f ->
                        include(name: f)
                    }
                }
            }
        }
    }
}

target(pluginDocs: "Generates and packages plugin documentation") {
    pluginDocDir = "${artifactPackageDirPath}/docs"
    ant.mkdir(dir: pluginDocDir)

    // copy 'raw' docs if they exists
    def srcDocsMisc = new File("${basedir}/src/docs/misc")
    if (srcDocsMisc.exists()) {
        ant.copy(todir: pluginDocDir, failonerror: false) {
            fileset(dir: srcDocsMisc)
        }
    }

    // package sources
    def srcMainDir = new File("${basedir}/src/main")
    def testSharedDir = new File("${basedir}/src/test")
    def testSharedDirPath = new File(griffonSettings.testClassesDir, 'shared')

    boolean hasSrcMain = hasJavaOrGroovySources(srcMainDir)
    boolean hasTestShared = hasJavaOrGroovySources(testSharedDir)
    List sources = []
    List excludedPaths = ['resources', 'i18n', 'conf']
    for (dir in new File("${basedir}/griffon-app").listFiles()) {
        if (!excludedPaths.contains(dir.name) && dir.isDirectory() &&
                ant.fileset(dir: dir, includes: '**/*.groovy, **/*.java').size() > 0) {
            sources << dir.absolutePath
        }
    }
    buildConfig.griffon?.plugin?.pack?.additional?.sources?.each { source ->
        File dir = new File("${basedir}/${source}")
        if (dir.isDirectory() && ant.fileset(dir: dir, excludes: '**/CVS/**, **/.svn/**').size() > 0) {
            sources << dir.absolutePath
        }
    }

    if (isAddonPlugin || hasSrcMain || hasTestShared || sources) {
        String jarFileName = "${artifactPackageDirPath}/dist/griffon-${pluginName}-${pluginVersion}-sources.jar"

        ant.uptodate(property: 'pluginSourceJarUpToDate', targetfile: jarFileName) {
            sources.each { d ->
                srcfiles(dir: d, excludes: '**/CVS/**, **/.svn/**')
            }
            srcfiles(dir: basedir, includes: '*GriffonAddon*')
            if (hasSrcMain) srcfiles(dir: srcMainDir, includes: '**/*')
            if (hasTestShared) srcfiles(dir: testSharedDir, includes: '**/*')
            srcfiles(dir: classesDirPath, includes: '**/*')
            if (hasTestShared) srcfiles(dir: testSharedDirPath, includes: '**/*')
        }
        boolean uptodate = ant.antProject.properties.pluginSourceJarUpToDate
        if (!uptodate) {
            ant.jar(destfile: jarFileName) {
                sources.each { d -> fileset(dir: d, excludes: '**/CVS/**, **/.svn/**') }
                fileset(dir: basedir, includes: '*GriffonAddon*')
                if (hasSrcMain) fileset(dir: srcMainDir, includes: '**/*.groovy, **/*.java')
                if (hasTestShared) fileset(dir: testSharedDir, includes: '**/*.groovy, **/*.java')
            }
        }

        List groovydocSources = []
        sources.each { source ->
            File dir = new File(source)
            if (ant.fileset(dir: dir, includes: '**/*.groovy, **/*.java').size() > 0) {
                groovydocSources << dir
            }
        }

        if (!argsMap.nodoc && (hasSrcMain || hasTestShared || groovydocSources)) {
            File javadocDir = new File("${projectTargetDir}/docs/api")
            invokeGroovydoc(destdir: javadocDir,
                    sourcepath: [srcMainDir, testSharedDir] + groovydocSources,
                    windowtitle: "${pluginName} ${pluginVersion}",
                    doctitle: "${pluginName} ${pluginVersion}")
            if (javadocDir.list()) {
                jarFileName = "${artifactPackageDirPath}/dist/griffon-${pluginName}-${pluginVersion}-javadoc.jar"
                ant.jar(destfile: jarFileName) {
                    fileset(dir: javadocDir)
                }
                ant.delete(dir: javadocDir, quiet: true)
            }
        }
    }
}

target(pluginShared: '') {
    def testSharedDir = new File("${basedir}/src/test")
    def testSharedDirPath = new File(griffonSettings.testClassesDir, 'shared')
    def testResourcesDir = new File("${basedir}/test/resources")
    def testResourcesDirPath = griffonSettings.testResourcesDir

    boolean hasTestShared = hasJavaOrGroovySources(testSharedDir)
    boolean hasTestResources = hasFiles(dir: testResourcesDir, excludes: '**/*.svn/**, **/CVS/**')

    if (hasTestShared || hasTestResources) {
        String jarFileName = "${artifactPackageDirPath}/dist/griffon-${pluginName}-${pluginVersion}-test.jar"

        ant.uptodate(property: 'pluginTestJarUpToDate', targetfile: jarFileName) {
            if (hasTestShared) {
                srcfiles(dir: testSharedDir, includes: "**/*")
                srcfiles(dir: testSharedDirPath, includes: "**/*")
            }
            if (hasTestResources) {
                srcfiles(dir: testResourcesDir, includes: "**/*")
                srcfiles(dir: testResourcesDirPath, includes: "**/*")
            }
        }
        boolean uptodate = ant.antProject.properties.pluginTestJarUpToDate
        if (!uptodate) {
            ant.jar(destfile: jarFileName) {
                if (hasTestShared) fileset(dir: testSharedDirPath, includes: '**/*.class')
                if (hasTestResources) fileset(dir: testResourcesDir)
            }
        }
    }
}