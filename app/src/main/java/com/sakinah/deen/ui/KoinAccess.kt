package com.sakinah.deen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.core.context.GlobalContext

/** Resolves a singleton from Koin without the optional Koin-Compose artifact. */
@Composable
inline fun <reified T : Any> rememberInject(): T = remember { GlobalContext.get().get<T>() }
