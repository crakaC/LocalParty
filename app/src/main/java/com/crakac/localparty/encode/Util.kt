package com.crakac.localparty.encode

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

fun createSingleThreadScope(name: String) = CoroutineScope(
    Executors.newSingleThreadExecutor().asCoroutineDispatcher() +
            CoroutineName(name) +
            CoroutineExceptionHandler { _, t ->
                Log.w(name, t.stackTraceToString())
            }
)