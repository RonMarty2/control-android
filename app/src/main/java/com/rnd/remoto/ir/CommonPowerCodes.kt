package com.rnd.remoto.ir

/**
 * NEC power-toggle codes decoded from public Amlogic Android TV box remote configs
 * (github.com/CoreELEC/remotes, AmRemote/<model>/remote.conf `factory_code` field - the top
 * 16 bits are the two address bytes, sent low-byte-first; the per-key byte is the raw NEC
 * command). Two formats appear here:
 * - "address,command": standard 8-bit NEC, where the two address bytes happen to be
 *   complements of each other, so [IrController.sendHexPair]'s normal 2-value format covers it.
 * - "addressLow,addressHigh,command": extended NEC, where the address bytes are NOT
 *   complements of each other and must be sent exactly as captured.
 *
 * Ordered roughly by how many distinct box models were seen reusing the same code (most
 * reused first), since those are the best bets for an unlabeled generic/no-name box.
 */
object CommonPowerCodes {
    val GENERIC_TV_BOX: List<String> = listOf(
        "01,40",       // HK1RBox, M8, M8S, X96 family (7 variants), T8, Matricon Gbox Q - 14+ models
        "40,40,4d",    // Bqeel M9C/T10 Max, Tanix, Magicsee N5, Eweat EW S802, MXIII - "MXIII" family
        "00,df,1c",    // A95X F3 Air, NexBox A95X, Abox A1 Max, Akaso HM8, 3GO APLAY 4 - "A95X" family
        "01,18",       // Minix Neo T5/U8K Ultra/U9-H/X8-H Plus/Z64A
        "00,14",       // Ugoos AM6/UR-01, Khadas Vim (variants 1 and 2)
        "00,59",       // MeCOOL M8S Pro, MeCOOL KM9 Pro
        "80,51",       // Beelink BT Remote / MXIII II / Mini M8S II
        "80,43",       // Abox A2 (power-menu variant)
        "01,dc",       // Jesurun A20
        "10,1a",       // Inphic SPOT i7
        "19,74",       // Ugoos UR-02
        "88,21",       // Homatics Box R 4K Plus
        "02,bd,45",    // MyGica ATV 1900AC
        "01,fd,dc",    // Tencent-brand box
        "84,79,12",    // Venz V10 Pro
        "cc,1d,00",    // COOWELL V2 (shutdown-menu variant)
        "b2,dc"        // Hardkernel/ODROID remote
    )
}
