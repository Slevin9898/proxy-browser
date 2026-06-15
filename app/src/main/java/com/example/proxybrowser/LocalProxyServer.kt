package com.example.proxybrowser

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class LocalProxyServer(
private val proxyHost: String,
private val proxyPort: Int,
private val proxyUser: String?,
private val proxyPass: String?
) {
private var serverSocket: ServerSocket? = null
var localPort: Int = 0
private set

fun start(): Int {
    val ss = ServerSocket()
    ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    serverSocket = ss
    localPort = ss.localPort
    Thread {
        while (true) {
            try {
                val client = ss.accept()
                Thread { handle(client) }.start()
            } catch (e: Exception) {
                break
            }
        }
    }.start()
    return localPort
}

fun stop() {
    try { serverSocket?.close() } catch (e: Exception) {}
}

private fun handle(client: Socket) {
    var remote: Socket? = null
    try {
        val cin = client.getInputStream()
        val cout = client.getOutputStream()

        val requestLine = readLine(cin)
        if (requestLine == null) { client.close(); return }
        val headers = ArrayList<String>()
        while (true) {
            val line = readLine(cin) ?: break
            if (line.isEmpty()) break
            headers.add(line)
        }

        val parts = requestLine.split(" ")
        val method = parts[0]

        if (method.equals("CONNECT", true)) {
            val hostPort = parts[1].split(":")
            val host = hostPort[0]
            val port = if (hostPort.size > 1) hostPort[1].toInt() else 443
            remote = socks5Connect(host, port)
            cout.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
            cout.flush()
        } else {
            var host = ""
            for (h in headers) {
                if (h.lowercase().startsWith("host:")) {
                    host = h.substring(5).trim()
                }
            }
            var port = 80
            if (host.contains(":")) {
                val hp = host.split(":")
                host = hp[0]
                port = hp[1].toInt()
            }
            remote = socks5Connect(host, port)
            val rout0 = remote.getOutputStream()
            rout0.write((requestLine + "\r\n").toByteArray())
            for (h in headers) rout0.write((h + "\r\n").toByteArray())
            rout0.write("\r\n".toByteArray())
            rout0.flush()
        }

        val rin = remote.getInputStream()
        val rout = remote.getOutputStream()

        val t1 = Thread { pipe(cin, rout) }
        t1.start()
        pipe(rin, cout)
        t1.join()
    } catch (e: Exception) {
    } finally {
        try { client.close() } catch (e: Exception) {}
        try { remote?.close() } catch (e: Exception) {}
    }
}

private fun pipe(input: InputStream, output: OutputStream) {
    val buf = ByteArray(8192)
    try {
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            output.write(buf, 0, n)
            output.flush()
        }
    } catch (e: Exception) {
    } finally {
        try { output.close() } catch (e: Exception) {}
        try { input.close() } catch (e: Exception) {}
    }
}

private fun readLine(input: InputStream): String? {
    val sb = StringBuilder()
    while (true) {
        val c = input.read()
        if (c == -1) {
            if (sb.isEmpty()) return null else break
        }
        if (c == '\n'.code) break
        if (c != '\r'.code) sb.append(c.toChar())
    }
    return sb.toString()
}

private fun socks5Connect(host: String, port: Int): Socket {
    val socket = Socket()
    socket.connect(InetSocketAddress(proxyHost, proxyPort), 15000)
    val sin = socket.getInputStream()
    val sout = socket.getOutputStream()

    val useAuth = !proxyUser.isNullOrEmpty()
    if (useAuth) {
        sout.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
    } else {
        sout.write(byteArrayOf(0x05, 0x01, 0x00))
    }
    sout.flush()

    sin.read()
    val method = sin.read()
    if (method == 0x02) {
        val u = proxyUser!!.toByteArray()
        val p = (proxyPass ?: "").toByteArray()
        val auth = ArrayList<Byte>()
        auth.add(0x01)
        auth.add(u.size.toByte())
        for (b in u) auth.add(b)
        auth.add(p.size.toByte())
        for (b in p) auth.add(b)
        sout.write(auth.toByteArray())
        sout.flush()
        sin.read()
        val authStatus = sin.read()
        if (authStatus != 0x00) {
            socket.close()
            throw Exception("SOCKS5 auth failed")
        }
    } else if (method != 0x00) {
        socket.close()
        throw Exception("SOCKS5 no acceptable methods")
    }

    val hostBytes = host.toByteArray()
    val req = ArrayList<Byte>()
    req.add(0x05)
    req.add(0x01)
    req.add(0x00)
    req.add(0x03)
    req.add(hostBytes.size.toByte())
    for (b in hostBytes) req.add(b)
    req.add(((port shr 8) and 0xFF).toByte())
    req.add((port and 0xFF).toByte())
    sout.write(req.toByteArray())
    sout.flush()

    sin.read()
    val rep = sin.read()
    sin.read()
    val atyp = sin.read()
    if (rep != 0x00) {
        socket.close()
        throw Exception("SOCKS5 connect failed: " + rep)
    }
    when (atyp) {
        0x01 -> { for (i in 0 until 4) sin.read() }
        0x03 -> { val len = sin.read(); for (i in 0 until len) sin.read() }
        0x04 -> { for (i in 0 until 16) sin.read() }
    }
    sin.read()
    sin.read()

    return socket
}
}
