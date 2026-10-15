package com.apfshfsview.core.fs

import java.text.Normalizer

fun normalizeName(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
