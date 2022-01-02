package com.crakac.localparty.encode

interface Codec {
    fun prepare()
    fun start()
    fun stop()
    fun release()
}