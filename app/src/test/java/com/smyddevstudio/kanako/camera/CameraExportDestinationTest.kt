package com.smyddevstudio.kanako.camera

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraExportDestinationTest {
    @Test fun publishesOnlyAfterWritingAndClosingTheStream() = runBlocking {
        val destination = Destination()
        val result = writeCameraExport(destination) {
            destination.events += "write"
            it.write(byteArrayOf(1, 2, 3))
        }
        assertEquals("image", result)
        assertEquals(listOf("create", "open", "write", "close", "publish"), destination.events)
        assertArrayEquals(byteArrayOf(1, 2, 3), destination.bytes.toByteArray())
    }

    @Test fun failedCreationDoesNotDeleteAnUnownedItem() = runBlocking {
        val destination = Destination(failure = "create")
        assertTrue(runCatching { writeCameraExport(destination) {} }.exceptionOrNull() is IOException)
        assertEquals(listOf("create"), destination.events)
    }

    @Test fun missingOutputStreamRemovesTheNewItem() = runBlocking {
        val destination = Destination(missingOutput = true)
        assertTrue(runCatching { writeCameraExport(destination) {} }.isFailure)
        assertEquals(listOf("create", "open", "delete"), destination.events)
    }

    @Test fun failedRenderingClosesAndRemovesTheItemWithoutPublishing() = runBlocking {
        val destination = Destination()
        val error = runCatching {
            writeCameraExport(destination) { throw IOException("render failed") }
        }.exceptionOrNull()
        assertEquals("render failed", error?.message)
        assertEquals(listOf("create", "open", "close", "delete"), destination.events)
    }

    @Test fun failedStreamCloseDoesNotPublishAPartialImage() = runBlocking {
        val destination = Destination(failure = "close")
        assertTrue(runCatching { writeCameraExport(destination) {} }.isFailure)
        assertEquals(listOf("create", "open", "close", "delete"), destination.events)
    }

    @Test fun failedPublicationRemovesTheWrittenItem() = runBlocking {
        val destination = Destination(failure = "publish")
        assertTrue(runCatching { writeCameraExport(destination) {} }.isFailure)
        assertEquals(listOf("create", "open", "close", "publish", "delete"), destination.events)
    }

    @Test fun cancellationDuringCreationStillRemovesTheInsertedItem() = runBlocking {
        lateinit var destination: Destination
        val error = supervisorScope {
            val export = async {
                val operation = currentCoroutineContext().job
                destination = Destination(onCreate = { operation.cancel() })
                writeCameraExport(destination) {}
            }
            runCatching { export.await() }.exceptionOrNull()
        }
        assertTrue(error is CancellationException)
        assertEquals(listOf("create", "delete"), destination.events)
    }

    @Test fun cancellationDuringWritingClosesTheStreamAndNeverPublishes() = runBlocking {
        val destination = Destination()
        val error = supervisorScope {
            val export = async {
                val operation = currentCoroutineContext().job
                writeCameraExport(destination) { operation.cancel() }
            }
            runCatching { export.await() }.exceptionOrNull()
        }
        assertTrue(error is CancellationException)
        assertEquals(listOf("create", "open", "close", "delete"), destination.events)
    }

    @Test fun cancellationDuringPublicationRemovesTheItemInsteadOfReportingSuccess() = runBlocking {
        lateinit var destination: Destination
        val error = supervisorScope {
            val export = async {
                val operation = currentCoroutineContext().job
                destination = Destination(onPublish = { operation.cancel() })
                writeCameraExport(destination) {}
            }
            runCatching { export.await() }.exceptionOrNull()
        }
        assertTrue(error is CancellationException)
        assertEquals(listOf("create", "open", "close", "publish", "delete"), destination.events)
    }

    @Test fun cleanupFailurePreservesTheOriginalExportError() = runBlocking {
        val destination = Destination(failure = "delete")
        val error = runCatching {
            writeCameraExport(destination) { throw IOException("render failed") }
        }.exceptionOrNull()
        assertEquals("render failed", error?.message)
        assertEquals(listOf("create", "open", "close", "delete"), destination.events)
    }

    private class Destination(
        private val failure: String? = null,
        private val missingOutput: Boolean = false,
        private val onCreate: () -> Unit = {},
        private val onPublish: () -> Unit = {}
    ) : CameraExportDestination<String> {
        val events = mutableListOf<String>()
        val bytes = ByteArrayOutputStream()

        private fun record(event: String) {
            events += event
            if (failure == event) throw IOException(event)
        }

        override fun create(): String {
            record("create")
            onCreate()
            return "image"
        }

        override fun open(item: String): OutputStream? {
            record("open")
            if (missingOutput) return null
            return object : OutputStream() {
                override fun write(value: Int) = bytes.write(value)
                override fun close() = record("close")
            }
        }

        override fun publish(item: String) {
            record("publish")
            onPublish()
        }

        override fun delete(item: String) = record("delete")
    }
}
