package tv.ninekpro.app.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.RandomAccessFile
import javax.crypto.Cipher

/** ExoPlayer data source for stv://<id> files: reads the encrypted file and decrypts on the fly (AES-CTR → free seeking). */
@UnstableApi
class CryptDataSource(private val ctx: Context) : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null; private var cipher: Cipher? = null; private var uri: Uri? = null; private var remaining = 0L
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri; val id = dataSpec.uri.host?.toLongOrNull() ?: dataSpec.uri.toString().removePrefix("stv://").toLongOrNull() ?: throw java.io.IOException("bad stv uri")
        val f = DlStore.file(ctx, id); if (!f.exists()) throw java.io.IOException("file missing")
        val r = RandomAccessFile(f, "r"); r.seek(dataSpec.position); raf = r
        cipher = DlStore.cipherAt(ctx, id, dataSpec.position)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else f.length() - dataSpec.position
        transferInitializing(dataSpec); transferStarted(dataSpec)
        return remaining
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0; if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = raf!!.read(buffer, offset, minOf(length.toLong(), remaining).toInt()); if (n <= 0) return C.RESULT_END_OF_INPUT
        val dec = cipher!!.update(buffer, offset, n, buffer, offset); remaining -= n; bytesTransferred(n); return if (dec > 0) dec else n
    }
    override fun getUri(): Uri? = uri
    override fun close() { try { raf?.close() } catch (e: Exception) {}; raf = null; cipher = null; if (uri != null) { uri = null; transferEnded() } }

    /** Routes stv:// to the decrypting source and everything else to the given (http) factory. */
    class RoutingFactory(private val ctx: Context, private val other: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = object : DataSource {
            private var d: DataSource? = null
            override fun open(dataSpec: DataSpec): Long { d = if (dataSpec.uri.scheme == "stv") CryptDataSource(ctx) else other.createDataSource(); return d!!.open(dataSpec) }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = d!!.read(buffer, offset, length)
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
            override fun getUri(): Uri? = d?.uri
            override fun close() { d?.close() }
        }
    }
}
