// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/HttpClientFactory.kt
// File: HttpClientFactory.kt
// ========================================
package com.Anichin

import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Factory untuk membuat OkHttpClient dengan konfigurasi optimal untuk video streaming.
 * 
 * Masalah yang diperbaiki:
 * - OkHttpClient tanpa konfigurasi menyebabkan timeout saat buffering
 * - Tidak ada connection pooling → setiap request buat koneksi baru
 * - Tidak ada retry logic → gagal saat network fluktuasi
 * - User-Agent tidak konsisten → trigger bot detection
 * 
 * @author CloudStream Extension Team
 * @since 2026-03-24
 */
object HttpClientFactory {
    
    // Singleton OkHttpClient untuk semua extractor - reuse connection pool
    @Volatile
    private var instance: OkHttpClient? = null
    
    // Session-based User-Agent untuk konsistensi per domain
    private val sessionUserAgents = ConcurrentHashMap<String, String>()
    
    // User-Agent pool dengan browser modern
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )
    
    /**
     * Mendapatkan OkHttpClient singleton dengan konfigurasi optimal.
     * 
     * Konfigurasi:
     * - Connect timeout: 15s (cukup untuk handshake SSL)
     * - Read timeout: 30s (penting untuk video streaming)
     * - Write timeout: 15s (untuk upload/POST requests)
     * - Connection pool: 10 connections, keep-alive 5 menit
     * - Retry on connection failure: true
     * - Custom interceptors untuk User-Agent dan headers
     */
    fun getClient(): OkHttpClient {
        return instance ?: synchronized(this) {
            instance ?: createClient().also { instance = it }
        }
    }
    
    /**
     * Reset client instance (untuk testing atau reinitialization)
     */
    fun resetClient() {
        synchronized(this) {
            instance?.connectionPool()?.evictAll()
            instance = null
        }
    }
    
    /**
     * Mendapatkan User-Agent yang konsisten untuk domain tertentu.
     * Ini penting untuk menghindari bot detection.
     */
    fun getUserAgentForDomain(domain: String): String {
        return sessionUserAgents.getOrPut(domain) {
            USER_AGENTS[(domain.hashCode() and Int.MAX_VALUE) % USER_AGENTS.size]
        }
    }
    
    /**
     * Mendapatkan default headers untuk semua requests.
     */
    fun getDefaultHeaders(domain: String? = null): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Sec-Ch-Ua" to """"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"""",
            "Sec-Ch-Ua-Mobile" to "?0",
            "Sec-Ch-Ua-Platform" to ""Windows"",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site"
        )
        
        if (domain != null) {
            headers["User-Agent"] = getUserAgentForDomain(domain)
        } else {
            headers["User-Agent"] = USER_AGENTS[0]
        }
        
        return headers
    }
    
    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // Timeout configuration - optimal untuk video streaming
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            
            // Connection pooling - reuse connections untuk performa lebih baik
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            
            // Retry on connection failure
            .retryOnConnectionFailure(true)
            
            // Add interceptors
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(HeadersInterceptor())
            .addNetworkInterceptor(NetworkPerformanceInterceptor())
            
            // Enable HTTP/2 support
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            
            // Build client
            .build()
    }
    
    /**
     * Interceptor untuk menambahkan User-Agent yang konsisten per domain.
     */
    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val host = originalRequest.url.host
            
            // Gunakan User-Agent yang konsisten untuk domain yang sama
            val userAgent = HttpClientFactory.getUserAgentForDomain(host)
            
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .build()
            
            return chain.proceed(newRequest)
        }
    }
    
    /**
     * Interceptor untuk menambahkan default headers.
     */
    private class HeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val host = originalRequest.url.host
            
            val newRequest = originalRequest.newBuilder()
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                .header("Connection", "keep-alive")
                .header("DNT", "1")
                .header("Sec-Ch-Ua", """"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"""")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", ""Windows"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                .build()
            
            return chain.proceed(newRequest)
        }
    }
    
    /**
     * Network performance interceptor untuk monitoring dan debugging.
     * Hanya aktif saat debug mode.
     */
    private class NetworkPerformanceInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            try {
                val response = chain.proceed(request)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                // Log hanya untuk request lambat (> 2s)
                if (duration > 2000) {
                    android.util.Log.d(
                        "HttpClientFactory",
                        "SLOW REQUEST: ${request.method} ${request.url} - ${duration}ms - Status: ${response.code}"
                    )
                }
                
                return response
            } catch (e: IOException) {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                android.util.Log.e(
                    "HttpClientFactory",
                    "NETWORK ERROR: ${request.method} ${request.url} - ${duration}ms - Error: ${e.message}"
                )
                
                throw e
            }
        }
    }
    
    /**
     * Helper class untuk thread-safe concurrent operations.
     */
    private class ConcurrentHashMap<K, V> {
        private val delegate = java.util.concurrent.ConcurrentHashMap<K, V>()
        
        fun getOrPut(key: K, defaultValue: () -> V): V {
            return delegate.getOrPut(key, defaultValue)
        }
        
        fun get(key: K): V? = delegate[key]
        fun put(key: K, value: V): V? = delegate.put(key, value)
        fun remove(key: K): V? = delegate.remove(key)
        fun clear() = delegate.clear()
    }
}
