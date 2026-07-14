package com.rnd.remoto.premium

/**
 * TESTING SWITCH: while there's no Google Play Developer account / Play Console product yet,
 * this forces every premium feature open so it can be tested end to end. Flip this to `false`
 * once Play Billing is actually configured (see BillingManager's docs) so the real purchase
 * gates everything again.
 */
object DebugConfig {
    const val FORCE_PREMIUM = true
}
