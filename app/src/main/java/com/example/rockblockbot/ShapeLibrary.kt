package com.example.rockblockbot

/**
 * Shape footprints recovered from Rock Block Saga v1.1.45 asset data.
 * 41 asset-derived fixed-orientation footprints, plus directly observed additions. No reliable appearance probabilities
 * were recovered; these are used for board-flexibility heuristics, not a claim
 * about the game's random-number generator.
 */
object ShapeLibrary {
    val patterns: List<String> = listOf(
        "#",
        "#/#",
        "##",
        ".#/#.",
        "#./.#",
        "#/#/#",
        ".#/##",
        "#./##",
        "##/.#",
        "##/#.",
        "###",
        "..#/.#./#..",
        "#../.#./..#",
        "#/#/#/#",
        "##/##",
        "../##/##",
        ".#/.#/##",
        ".#/##/#.",
        "#./.#/##",
        "##/../##",
        "##/#./.#",
        "##/#./#.",
        "##/##/..",
        ".#./###",
        ".##/#.#",
        ".##/##.",
        "#.#/.##",
        "#.#/#.#",
        "#.#/##.",
        "##./#.#",
        "###/.#.",
        "####",
        "#/#/#/#/#",
        "..#/..#/###",
        "#../#../###",
        "###/..#/..#",
        "###/#../#..",
        "#####",
        "##/##/##",
        "###/###",
        "###/###/###",
        // Confirmed on-screen but absent from the extracted 41-pattern set:
        "###/#..",
        "#./##/.#"
    )
    val pieces: List<Piece> = patterns.map { pattern ->
        Piece(pattern.split("/").flatMapIndexed { r, line ->
            line.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null }
        })
    }

    /** Count shape families with at least one legal placement, plus total options. */
    fun mobility(board: Long): Pair<Int, Int> {
        var families = 0
        var total = 0
        for (p in pieces) {
            var options = 0
            for (r in 0..(8-p.height)) for (c in 0..(8-p.width)) {
                var mask = 0L
                for (cell in p.normalized) mask = mask or (1L shl ((r+cell.r)*8+c+cell.c))
                if (board and mask == 0L) options++
            }
            if (options > 0) families++
            total += options
        }
        return families to total
    }
}
