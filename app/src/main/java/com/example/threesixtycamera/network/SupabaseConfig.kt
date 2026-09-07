package com.example.threesixtycamera.network

import com.example.threesixtycamera.BuildConfig
/**
 * Supabase configuration.
 *
 * Keys come from local.properties -> BuildConfig at build time — nothing
 * sensitive is hardcoded here. Only use the "anon" public key, protected
 * by Row Level Security policies on the bucket. NEVER put the
 * service_role key in a client app.
 */
object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
    const val BUCKET_NAME = "panorama-images"
}