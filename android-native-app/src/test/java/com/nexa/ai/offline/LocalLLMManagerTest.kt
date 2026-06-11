package com.nexa.ai.offline

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import java.io.File

class LocalLLMManagerTest {

    @Mock
    lateinit var context: Context

    private lateinit var localLLMManager: LocalLLMManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Mock application context and filesDir
        val tempFilesDir = File(System.getProperty("java.io.tmpdir"), "nexa_test_files")
        tempFilesDir.mkdirs()
        `when`(context.filesDir).thenReturn(tempFilesDir)

        localLLMManager = LocalLLMManager(context)
    }

    @Test
    fun loadModelFromAssets_success() = runTest {
        val mockAssetManager = mock(android.content.res.AssetManager::class.java)
        `when`(context.assets).thenReturn(mockAssetManager)
        val mockInputStream = java.io.ByteArrayInputStream("fake gguf bytes".toByteArray())
        `when`(mockAssetManager.open("llama3-8b-q4.gguf")).thenReturn(mockInputStream)

        val loaded = localLLMManager.loadModelFromAssets()
        assertTrue(loaded)
    }

    @Test
    fun generateText_returnsResponse() = runTest {
        val mockAssetManager = mock(android.content.res.AssetManager::class.java)
        `when`(context.assets).thenReturn(mockAssetManager)
        val mockInputStream = java.io.ByteArrayInputStream("fake gguf bytes".toByteArray())
        `when`(mockAssetManager.open("llama3-8b-q4.gguf")).thenReturn(mockInputStream)

        localLLMManager.loadModelFromAssets()
        val response = localLLMManager.generateText("Hola")
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }
}
