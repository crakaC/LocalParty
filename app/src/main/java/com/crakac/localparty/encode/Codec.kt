package com.crakac.localparty.encode

interface Codec {
    fun configure()
    fun start()
    fun stop()
    fun release()
}