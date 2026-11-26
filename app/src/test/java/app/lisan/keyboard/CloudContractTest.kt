package app.lisan.keyboard

import app.lisan.keyboard.translation.CloudTranslator
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.TrustManagerFactory

class CloudContractTest {
    private fun line(input:BufferedInputStream):String {
        val output=ByteArrayOutputStream()
        while(output.size()<8192) {
            val b=input.read()
            check(b>=0)
            if(b==10)return output.toString("US-ASCII").trimEnd('\r')
            output.write(b)
        }
        error("oversized test header")
    }
    @Test fun sendsExactConsentPayloadOverTlsAndDoesNotFollowRedirects() = runBlocking {
        val dir=Files.createTempDirectory("lisan-tls-test").toFile()
        val store=dir.resolve("test.p12")
        val pass=UUID.randomUUID().toString()
        val oldFactory=HttpsURLConnection.getDefaultSSLSocketFactory()
        var server:SSLServerSocket?=null
        var worker:Thread?=null
        try {
            val keytool=java.io.File(System.getProperty("java.home"),"bin/keytool").path
            val command=ProcessBuilder(keytool,"-genkeypair","-alias","lisan-test","-keystore",store.path,
                "-keyalg","EC","-storetype","PKCS12","-storepass",pass,"-dname","CN=localhost",
                "-ext","SAN=dns:localhost","-validity","1","-noprompt")
                .redirectErrorStream(true).redirectOutput(dir.resolve("keytool.log")).start()
            if(!command.waitFor(25,TimeUnit.SECONDS)) {command.destroyForcibly();error("TLS setup timed out")}
            check(command.exitValue()==0)
            val keys=KeyStore.getInstance("PKCS12").apply { store.inputStream().use{load(it,pass.toCharArray())} }
            val kms=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys,pass.toCharArray()) }
            val tms=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
            val ssl=SSLContext.getInstance("TLS").apply { init(kms.keyManagers,tms.trustManagers,null) }
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.socketFactory)
            val actual=AtomicReference<Pair<String,JSONObject>>()
            val hits=AtomicInteger()
            val listening=(ssl.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
                bind(InetSocketAddress("127.0.0.1",0))
            }
            server=listening
            val failure=AtomicReference<Throwable?>()
            worker=kotlin.concurrent.thread(isDaemon=true) {
                try {
                    repeat(2) {
                        listening.accept().use { client ->
                            client.soTimeout=5000
                            val input=client.getInputStream().buffered()
                            val start=line(input).split(' ')
                            val headers=generateSequence { line(input).takeIf{it.isNotEmpty()} }.take(30).toList()
                            val size=headers.first { it.substringBefore(':').equals("Content-Length",true) }
                                .substringAfter(':').trim().toInt()
                            check(size in 1..10_000)
                            val request=ByteArray(size)
                            java.io.DataInputStream(input).readFully(request)
                            val out=client.getOutputStream()
                            if(start[1]=="/translate") {
                                hits.incrementAndGet()
                                actual.set(start[0] to JSONObject(request.toString(Charsets.UTF_8)))
                                val reply="""{"translatedText":"Good morning","detectedLanguage":{"language":"ar"}}""".toByteArray()
                                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"+
                                    "Content-Length: ${reply.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(reply)
                            } else {
                                check(start[1]=="/redirect")
                                out.write("HTTP/1.1 307 Temporary Redirect\r\nLocation: /translate\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            }
                            out.flush()
                        }
                    }
                } catch(e:Throwable) { failure.set(e) }
            }
            val base="https://localhost:${listening.localPort}"
            val sample="صباح الخير\nهذا اختبار"
            val result=CloudTranslator.translate(checkNotNull(CloudTranslator.endpoint("$base/translate")),sample,"auto","en")
            assertEquals("Good morning",result.text)
            assertEquals("ar",result.source)
            val (method,body)=actual.get()
            assertEquals("POST",method)
            assertEquals(sample,body.getString("q"))
            assertEquals("auto",body.getString("source"))
            assertEquals("en",body.getString("target"))
            assertEquals("text",body.getString("format"))
            assertEquals(4,body.length())
            assertTrue(runCatching {
                CloudTranslator.translate(checkNotNull(CloudTranslator.endpoint("$base/redirect")),sample,"auto","en")
            }.isFailure)
            assertEquals(1,hits.get())
            worker.join(2000)
            assertFalse(worker.isAlive)
            assertNull(failure.get())
        } finally {
            server?.close()
            worker?.join(2000)
            HttpsURLConnection.setDefaultSSLSocketFactory(oldFactory)
            dir.deleteRecursively()
        }
    }
}
