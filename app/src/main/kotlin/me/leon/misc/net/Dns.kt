package me.leon.misc.net

import kotlinx.coroutines.*
import me.leon.ext.fromJson
import me.leon.ext.readFromNet

/**
 * @author Leon
 * @since 2023-04-10 11:03
 * @email deadogone@gmail.com
 */
fun dnsSolve(urls: List<String>): String =
    dnsSolveResult(urls)
        .groupBy { it.second.ipCloudFlare() }
        .toList()
        .joinToString(System.lineSeparator()) { (k, v) ->
            v.joinToString(
                System.lineSeparator(),
                "# CloudFlare ip ${System.lineSeparator()}".takeIf { k }.orEmpty(),
            ) {
                it.second + "\t" + it.first
            }
        }

fun dnsSolveResult(urls: List<String>): List<Pair<String, String>> = runBlocking {
    val aliOkUrls =
        urls
            .sorted()
            .distinct()
            .map { domain -> async(DISPATCHER) { domain to fastestIp(resolveDomainByAli(domain)) } }
            .awaitAll()
            .filter { it.second != null }
    // if alidns ip is not available, try cloudfare dns
    val cfOkUrls =
        (urls - aliOkUrls.map { it.first }.toSet())
            .also { println("ali dns failed ips: ${it.size}") }
            .map { domain ->
                async(DISPATCHER) { domain to fastestIp(resolveDomainsByCloudfare(domain)) }
            }
            .awaitAll()
            .filter { it.second != null }

    (aliOkUrls + cfOkUrls).map { it.first to it.second!!.first }
}

fun fastestIp(ips: List<String>, timeout: Int = 2000): Pair<String, Long>? {
    return runCatching {
            ips.map { ip ->
                    ip to
                        runCatching { ip.connect(443, timeout) }
                            .getOrElse { ip.connect(80, timeout) }
                }
                .filter { it.second > 0 }
                .minBy { it.second }
        }
        .getOrNull()
}

fun resolveDomainByAli(name: String): List<String> =
    "https://dns.alidns.com/resolve?name=$name&type=1".readFromNet().parseDnsIp()

fun resolveDomainsByCloudfare(name: String): List<String> =
    "https://1.1.1.1/dns-query?name=$name&type=A"
        .readFromNet(headers = mapOf("accept" to "application/dns-json"))
        .parseDnsIp()

private fun String.parseDnsIp() =
    fromJson(DnsResponse::class.java).Answer.filter { it.type == 1 }.map { it.data }
