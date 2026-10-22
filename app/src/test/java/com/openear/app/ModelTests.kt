package com.openear.app

import com.openear.app.model.ModelCatalog
import com.openear.app.model.ModelDownloader
import com.openear.app.model.ModelInfo
import com.openear.app.model.ModelState
import com.openear.app.model.ModelStore
import com.openear.app.model.Sha256
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class ModelCatalogTest {
    @Test fun catalogEntriesAreWellFormed() {
        val hex64 = Regex("^[0-9a-f]{64}$")
        ModelCatalog.all.forEach {
            assertTrue("${it.id} url must be https", it.url.startsWith("https://"))
            assertTrue("${it.id} sha256 must be 64 hex chars", hex64.matches(it.sha256))
            assertTrue("${it.id} size", it.sizeBytes > 1_000_000)
            assertTrue("${it.id} pinned revision", it.url.contains(ModelCatalog.UPSTREAM_REVISION))
        }
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
    }

    @Test fun defaultModelIsInTheCatalogAndEnglish() {
        val m = ModelCatalog.byId(com.openear.app.settings.Settings.DEFAULT_MODEL_ID)
        assertNotNull(m)
        assertTrue(m!!.fileName.contains(".en"))
    }

    @Test fun multilingualModelsRemainAvailableForHindi() {
        assertNotNull(ModelCatalog.byId("base"))
        assertNotNull(ModelCatalog.byId("small"))
    }

    @Test fun storageCheck() {
        val mb = 1024L * 1024
        assertTrue(ModelStore.hasEnoughSpace(100 * mb, 200 * mb))
        assertFalse(ModelStore.hasEnoughSpace(100 * mb, 120 * mb)) // margin not met
        assertFalse(ModelStore.hasEnoughSpace(-1, 1_000 * mb))
    }
}

class ModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fakeModel(content: ByteArray = "0123456789".toByteArray()): ModelInfo {
        val sha = Sha256.hex(ByteArrayInputStream(content))
        return ModelInfo("fake", "Fake", "ggml-fake.bin", "https://example.invalid/m.bin", sha, content.size.toLong(), "")
    }

    @Test fun sha256OfKnownInput() {
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex(ByteArrayInputStream("abc".toByteArray())),
        )
    }

    @Test fun stateTransitionsMissingPartialReady() {
        val store = ModelStore(tmp.newFolder("m"))
        val info = fakeModel()
        assertEquals(ModelState.NotInstalled, store.state(info))
        store.partFile(info).writeBytes("01234".toByteArray())
        assertEquals(ModelState.Partial(5, 10), store.state(info))
        store.partFile(info).writeBytes("0123456789".toByteArray())
        assertEquals(ModelStore.InstallResult.Installed, store.verifyAndInstall(info))
        assertEquals(ModelState.Ready, store.state(info))
    }

    @Test fun corruptDownloadIsRejectedAndDeleted() {
        val store = ModelStore(tmp.newFolder("m"))
        val info = fakeModel()
        store.partFile(info).writeBytes("012345678X".toByteArray()) // right size, wrong content
        assertEquals(ModelStore.InstallResult.ChecksumMismatch, store.verifyAndInstall(info))
        assertFalse(store.isInstalled(info))
        assertFalse(store.partFile(info).exists())
    }

    @Test fun wrongSizeIsRejected() {
        val store = ModelStore(tmp.newFolder("m"))
        val info = fakeModel()
        store.partFile(info).writeBytes("012".toByteArray())
        assertEquals(ModelStore.InstallResult.WrongSize, store.verifyAndInstall(info))
        assertFalse(store.isInstalled(info))
    }

    @Test fun fileWithoutVerifiedMarkerIsNotReady() {
        val store = ModelStore(tmp.newFolder("m"))
        val info = fakeModel()
        store.modelFile(info).writeBytes("0123456789".toByteArray())
        assertFalse(store.isInstalled(info))
    }

    @Test fun deleteReclaimsStorage() {
        val store = ModelStore(tmp.newFolder("m"))
        val info = fakeModel()
        store.partFile(info).writeBytes("0123456789".toByteArray())
        store.verifyAndInstall(info)
        assertTrue(store.usedBytes() > 0)
        store.delete(info)
        assertEquals(0L, store.usedBytes())
        assertFalse(store.isInstalled(info))
    }

    @Test fun importAcceptsOnlyGgmlFiles() {
        val store = ModelStore(tmp.newFolder("m"))
        val notModel = ByteArrayInputStream("hello world".toByteArray())
        assertEquals(ModelStore.ImportResult.NotAModel, store.importFrom(notModel))
        assertFalse(store.isImportedReady())

        val ggml = byteArrayOf(0x6c, 0x6d, 0x67, 0x67, 1, 2, 3, 4)
        assertEquals(ModelStore.ImportResult.Imported, store.importFrom(ByteArrayInputStream(ggml)))
        assertTrue(store.isImportedReady())
        assertNotNull(store.resolve(ModelCatalog.IMPORTED_ID))
        store.deleteImported()
        assertNull(store.resolve(ModelCatalog.IMPORTED_ID))
    }

    @Test fun resolveNeverReturnsUninstalledModels() {
        val store = ModelStore(tmp.newFolder("m"))
        assertNull(store.resolve("base"))
        assertNull(store.resolve("does-not-exist"))
    }
}

class ModelDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun info(url: String) = ModelInfo("x", "X", "ggml-x.bin", url, "0".repeat(64), 100_000_000L, "")

    @Test fun insecureUrlIsRefusedBeforeAnyNetworkCall() = runBlocking {
        val d = ModelDownloader(ModelStore(tmp.newFolder("a")), freeSpace = { Long.MAX_VALUE })
        val r = d.download(info("http://example.invalid/m.bin"), { _, _ -> }, {})
        assertEquals(ModelDownloader.Outcome.Failure(ModelDownloader.Reason.InsecureUrl), r)
    }

    @Test fun insufficientStorageIsReportedBeforeDownloading() = runBlocking {
        val d = ModelDownloader(ModelStore(tmp.newFolder("b")), freeSpace = { 1_000L })
        val r = d.download(info("https://example.invalid/m.bin"), { _, _ -> }, {})
        assertEquals(ModelDownloader.Outcome.Failure(ModelDownloader.Reason.InsufficientStorage), r)
    }
}
