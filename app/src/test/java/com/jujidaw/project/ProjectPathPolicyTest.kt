package com.jujidaw.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectPathPolicyTest {
    private val projectsDirectory = File("/tmp/juji-projects")
    private val projectDirectory = File(projectsDirectory, "demo")

    @Test
    fun projectDirectoryRejectsTraversalAndSeparators() {
        assertNull(ProjectPathPolicy.projectDirectory(projectsDirectory, "../escape"))
        assertNull(ProjectPathPolicy.projectDirectory(projectsDirectory, "nested/project"))
        assertNull(ProjectPathPolicy.projectDirectory(projectsDirectory, ""))
    }

    @Test
    fun audioFileAcceptsOnlyPathsContainedByProject() {
        val relative = ProjectPathPolicy.audioFile(projectDirectory, "samples/kick.wav")
        val absoluteInside = ProjectPathPolicy.audioFile(projectDirectory, relative!!.absolutePath)

        assertEquals(relative.canonicalFile, absoluteInside?.canonicalFile)
        assertNull(ProjectPathPolicy.audioFile(projectDirectory, "../../escape.wav"))
        assertNull(ProjectPathPolicy.audioFile(projectDirectory, "/tmp/escape.wav"))
        assertNull(ProjectPathPolicy.audioFile(projectDirectory, "."))
    }

    @Test
    fun appAudioFileResolvesRelativePathsOnlyForTheSelectedProject() {
        val appFilesDirectory = File("/tmp/juji-app")

        assertEquals(
            File(appFilesDirectory, "projects/demo/samples/kick.wav").canonicalFile,
            ProjectPathPolicy.appAudioFile(appFilesDirectory, "demo", "samples/kick.wav")?.canonicalFile,
        )
        assertEquals(
            File(appFilesDirectory, "projects/demo/samples/kick.wav").canonicalFile,
            ProjectPathPolicy.appAudioFile(
                appFilesDirectory,
                "demo",
                File(appFilesDirectory, "projects/demo/samples/kick.wav").absolutePath,
            )?.canonicalFile,
        )
        assertNull(ProjectPathPolicy.appAudioFile(appFilesDirectory, null, "samples/kick.wav"))
        assertNull(ProjectPathPolicy.appAudioFile(appFilesDirectory, "demo", "../../escape.wav"))
        assertNull(
            ProjectPathPolicy.appAudioFile(
                appFilesDirectory,
                "demo",
                File(appFilesDirectory, "projects/other/samples/kick.wav").absolutePath,
            ),
        )
    }
}
