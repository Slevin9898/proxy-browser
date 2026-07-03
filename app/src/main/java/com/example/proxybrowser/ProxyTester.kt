package com.example.proxybrowser

import java.net.InetSocketAddress
import java.net.Socket

object ProxyTester {

    // Пробует подключиться через SOCKS5-прокси к тестовому сайту (google.com).
    // Если что-то не так — бросает исключение с понятным текстом ошибки.
    fun test(host: String, port: Int, user: String?, pass: String?) {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), 8000)
        socket.soTimeout = 8000
        try {
            val sin = socket.getInputStream()
            val sout = socket.getOutputStream()

            val useAuth = !user.isNullOrEmpty()
            if (useAuth) {
                sout.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                sout.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            sout.flush()

            val ver = sin.read()
            val method = sin.read()
            if (ver != 0x05) {
                throw Exception("прокси не отвечает по протоколу SOCKS5")
            }

            if (method == 0x02) {
                val u = (user ?: "").toByteArray()
                val p = (pass ?: "").toByteArray()
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
                    throw Exception("неверный логин или пароль прокси")
                }
            } else if (method != 0x00) {
                throw Exception("прокси отклонил подключение")
            }

            val targetHost = "www.google.com"
            val targetPort = 443
            val hostBytes = targetHost.toByteArray()
            val req = ArrayList<Byte>()
            req.add(0x05)
            req.add(0x01)
            req.add(0x00)
            req.add(0x03)
            req.add(hostBytes.size.toByte())
            for (b in hostBytes) req.add(b)
            req.add(((targetPort shr 8) and 0xFF).toByte())
            req.add((targetPort and 0xFF).toByte())
            sout.write(req.toByteArray())
            sout.flush()

            sin.read()
            val rep = sin.read()
            sin.read()
            val atyp = sin.read()
            if (rep != 0x00) {
                throw Exception("прокси не смог подключиться к сайту (код ошибки $rep)")
            }
            when (atyp) {
                0x01 -> { for (i in 0 until 4) sin.read() }
                0x03 -> { val len = sin.read(); for (i in 0 until len) sin.read() }
                0x04 -> { for (i in 0 until 16) sin.read() }
            }
            sin.read()
            sin.read()
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }
}
