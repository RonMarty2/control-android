package com.rnd.remoto.ir

/**
 * NEC (address,command) power-toggle codes documented for generic/no-name Amlogic-based Android
 * TV boxes (found in public remote configs such as CoreELEC/remotes and Amlogic IR-wakeup docs).
 * Intentionally short for now - only entries actually sourced from those docs are included here;
 * more get added as they're found, rather than guessing plausible-looking hex pairs.
 */
object CommonPowerCodes {
    val GENERIC_TV_BOX: List<String> = listOf(
        "01,40",
        "00,59"
    )
}
