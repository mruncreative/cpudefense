package com.example.cpudefense

import android.graphics.*
import androidx.core.graphics.createBitmap
import com.example.cpudefense.gameElements.*
import com.example.cpudefense.networkmap.Link
import com.example.cpudefense.networkmap.Network
import com.example.cpudefense.networkmap.Track
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.utils.blur
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.random.nextULong

class Stage(var theGame: Game) {

    lateinit var network: Network
    var sizeX = 0
    var sizeY = 0

    var chips = hashMapOf<Int, Chip>()
    var tracks = hashMapOf<Int, Track>()
    var waves = CopyOnWriteArrayList<Wave>()

    enum class Type { REGULAR, FINAL }
    var type = Type.REGULAR

    data class Data (
        var level: Int = 0,
        var gridSizeX: Int = 1,
        var gridSizeY: Int = 1,
        var maxWaves: Int = 0,
        var countOfWaves: Int = 0,
        var chips: HashMap<Int, Chip.Data> = hashMapOf(),
        var links: HashMap<Int, Link.Data> = hashMapOf(),
        var tracks: HashMap<Int, Track.Data> = hashMapOf(),
        var waves: CopyOnWriteArrayList<Wave.Data> = CopyOnWriteArrayList<Wave.Data>(),
        var attackers: CopyOnWriteArrayList<Attacker.Data> = CopyOnWriteArrayList<Attacker.Data>(),
        var chipsAllowed: Set<Chip.ChipUpgrades> = setOf()
    )
    var data = Data()

    data class Summary(
        var coinsMaxAvailable: Int = 0,
        var coinsGot: Int = 0,
        var won: Boolean = false
    )
    lateinit var summary: Summary
    var rewardCoins = 0  // number of coins that can be obtained by completing the level

    fun calculateRewardCoins(previousSummary: Summary?)
    {
        summary = previousSummary ?: Summary()
        summary.coinsMaxAvailable = rewardCoins
    }

    fun provideData(): Data
            /** serialize all objects that belong to this stage
             * and return the data object
             * for saving and restoring the game.
             */
    {
        data.gridSizeX = network.data.gridSizeX
        data.gridSizeY = network.data.gridSizeY
        data.chips.clear()
        chips.forEach()
        { (key, value) -> data.chips[key] = value.chipData }
        data.links.clear()
        network.links.forEach()
        { (key, value) -> data.links[key] = value.data }
        data.tracks.clear()
        tracks.forEach()
        { (key, track) -> data.tracks[key] = track.data }
        data.waves.clear()
        waves.forEach()
        { data.waves.add(it.data) }
        data.attackers.clear()
        network.vehicles.forEach()
        { data.attackers.add((it as Attacker).provideData())}
        return data
    }

    companion object {
        fun createStageFromData(game: Game, stageData: Data?): Stage?
        {
            var data = stageData ?: return null
            var stage = Stage(game)
            stage.data = data
            stage.sizeX = data.gridSizeX
            stage.sizeY = data.gridSizeY
            stage.network = Network(game, stage.sizeX, stage.sizeY)
            for ((id, chipData) in stage.data.chips)
            {
                val chip = Chip.createFromData(stage.network, chipData)
                stage.network.addNode(chip, id)
                stage.chips[id] = chip
            }
            for ((id, linkData) in stage.data.links)
            {
                val link = Link.createFromData(stage.network, linkData)
                stage.network.addLink(link, id)
            }
            for ((id, trackData) in stage.data.tracks)
            {
                val track = Track.createFromData(stage, trackData)
                stage.tracks[id] = track
            }
            for (waveData in stage.data.waves)
            {
                val wave = Wave.createFromData(game, waveData)
                stage.waves.add(wave)
            }
            for (attackerData in stage.data.attackers)
            {
                val attacker = Attacker.createFromData(stage, attackerData)
                stage.network.addVehicle(attacker)
            }
            return stage
        }
    }

    fun createNewAttacker(maxNumber: Int, speed: Float, isCoin: Boolean = false)
    {
        val attacker = if (isCoin)
            Cryptocoin(network, Random.nextULong((maxNumber*1.5).toULong()), speed )
        else
            Attacker(network, Attacker.Representation.BINARY,
            Random.nextULong((maxNumber+1).toULong()), speed)
        network.addVehicle(attacker)

        if (tracks.size > 0)
            attacker.setOntoTrack(tracks[Random.nextInt(tracks.size)])
    }

    fun attackerCount(): Int
    {
        return network.vehicles.size
    }

    fun nextWave(): Wave?
    {
        if (theGame.data.state != Game.GameState.RUNNING)
            return null
        else if (waves.size == 0)
        {
            theGame.onEndOfStage()
            return null
        }
        else {
            data.countOfWaves++
            return waves.removeFirst()
        }
    }

    /* methods for creating and setting up the stage */

    private fun initializeNetwork(dimX: Int, dimY: Int)
    /** creates an empty network with the given grid dimensions */
    {
        sizeX = dimX
        sizeY = dimY
        network = Network(theGame, sizeX, sizeY)
        theGame.viewport.setViewportSize(sizeX, sizeY)
    }

    private fun createChip(gridX: Int, gridY: Int, ident: Int = -1, type: Chip.ChipType = Chip.ChipType.EMPTY): Chip
            /**
             * creates a chip at the given position. If an ident is given, it is used,
             * otherwise a new ident is created.
             * @param gridX Position in grid coordinates
             * @param gridY Position in grid coordinates
             * @param ident Node ident, or -1 to choose a new one
             */
    {
        var id = ident
        lateinit var chip: Chip
        when (type)
        {
            Chip.ChipType.ENTRY -> {
                chip = EntryPoint(network, gridX, gridY)
                id = 0
            }
            Chip.ChipType.CPU -> {
                chip = Cpu(network, gridX, gridY)
                id = 999
            }
            else -> { chip = Chip(network, gridX, gridY) }
        }
        id = network.addNode(chip, id)
        chips[id] = chip
        chip.setIdent(id)
        return chip
    }

    private fun createLink(from: Int, to: Int, ident: Int)
    /** adds a link between two existing nodes, referenced by ID
     * @param from Ident of first node
     * @param to Ident of 2nd node
     * @param ident Ident of the link, or -1 to choose a new one
     * */
    {
        val node1 = network.nodes[from] ?: return
        val node2 = network.nodes[to] ?: return
        val link = Link(network, node1, node2, ident)
        network.addLink(link, ident)
    }

    private fun createTrack(linkIdents: List<Int>, ident: Int)
            /** adds a track of connected links
             * @param linkIdents List of the link idents in the track
             * */
    {
        val track = network.createTrack(linkIdents, false)
        tracks[ident] = track
    }
    
    private fun createWave(attackerCount: Int, attackerStrength: Int, attackerFreqency: Float, attackerSpeed: Float, coins: Int = 0)
    {
        var waveData = Wave.Data(attackerCount, attackerStrength, attackerFreqency, attackerSpeed, coins, currentCount = attackerCount)
        waves.add(Wave(theGame, waveData))
    }

    private fun createAttacker(data: Attacker.Data)
    {
        var attacker = Attacker.createFromData(this, data)
        network.addVehicle(attacker)
    }

    fun takeSnapshot(size: Int): Bitmap
            /** gets a miniature picture of the current level
             * @param size snapshot size in pixels (square)
             * @return the bitmap that holds the snapshot
             */
    {
        var p: Viewport = theGame.viewport
        var bigSnapshot = createBitmap(p.screenWidth, p.screenHeight)
        network.display(Canvas(bigSnapshot), p)
        // var smallSnapshot = createBitmap(size, size)

        /* blur the image */
        bigSnapshot = bigSnapshot.blur(theGame.gameActivity, 3f) ?: bigSnapshot

        var smallSnapshot = Bitmap.createScaledBitmap(bigSnapshot, size, size, true)
        return smallSnapshot
    }

    fun createNetwork(level: Int): Network
    {
        this.data.level = level
        waves.clear()
        type = Type.REGULAR
        data.chipsAllowed = setOf(Chip.ChipUpgrades.AND, Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.SHIFT, Chip.ChipUpgrades.POWERUP)

        when (level)
        {
            1 ->
            {
                initializeNetwork(40, 40)

                createChip(20, 1, type = Chip.ChipType.ENTRY)
                createChip(15, 20, 1)
                createChip(20, sizeY-2, type = Chip.ChipType.CPU)

                createLink(0, 1, 0)
                createLink(1, 999, 1)

                createTrack(listOf(0, 1), 0)
                
                createWave(4, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB)
            }
            2 ->
            {
                initializeNetwork(40, 40)

                createChip(20, 1, type = Chip.ChipType.ENTRY)
                createChip(15, 20, 1)
                createChip(20, sizeY-2, type = Chip.ChipType.CPU)

                createLink(0, 1, 0)
                createLink(1, 999, 1)

                createTrack(listOf(0, 1), 0)

                createWave(8, 1, .075f, 1f)
                createWave(8, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB)
            }
            3 ->
            {
                initializeNetwork(50, 50)

                createChip(1, 1, type = Chip.ChipType.ENTRY)
                createChip(20, 12, 1)
                createChip(20, 20, 2)
                createChip(45, 30, 3)
                createChip(45, 45, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 999, 4)

                createTrack(listOf(1, 2, 3, 4), 0)

                createWave(4, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP)
            }
            4 ->
            {
                initializeNetwork(50, 50)

                createChip(1, 1, type = Chip.ChipType.ENTRY)
                createChip(10, 12, 1)
                createChip(15, 20, 2)
                createChip(30, 30, 3)
                createChip(35, 45, 4)
                createChip(20, 35, 5)
                createChip(45, 45, type = Chip.ChipType.CPU)
                
                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 4, 4)
                createLink(2, 5, 5)
                createLink(5, 4, 6)
                createLink(4, 999, 7)

                createTrack(listOf(1, 2, 3, 4, 7), 0)
                createTrack(listOf(1, 2, 5, 6, 7), 1)

                createWave(10, 1, .1f, 1f)
                createWave(10, 2, .1f, 1f)
                createWave(10, 2, .1f, 1f)
                createWave(10, 3, .15f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP)
            }
            5 ->
            {
                initializeNetwork(50, 50)

                createChip(1, 15, type = Chip.ChipType.ENTRY)
                createChip(25, 15, 1)
                createChip(40, 15, 2)
                createChip(40, 5, 3)
                createChip(25,  5, 4)
                createChip(25,  25, 5)
                createChip(25,  35, 6)
                createChip(40,  35, 7)
                createChip(40,  25, 8)
                createChip(5, 25, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 4, 4)
                createLink(4, 1, 5)
                createLink(1, 5, 6)
                createLink(5, 6, 7)
                createLink(6, 7, 8)
                createLink(7, 8, 9)
                createLink(8, 5, 10)
                createLink(5, 999, 11)

                createTrack((1..11).toList(), 0)

                createWave(10, 2, .125f, 1.2f)
                createWave(15, 3, .1250f, 1.1f)
                createWave(15, 3, .1f, 1.1f)
                createWave(20, 4, .1f, 1.1f)
                createWave(20, 7, .050f, 1f)
                createWave(15, 15, .050f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
            }
            6 ->
            {
                initializeNetwork(50, 50)

                createChip(1, 5, type = Chip.ChipType.ENTRY)
                createChip(25, 15, 1)
                createChip(40, 15, 2)
                createChip(40, 5, 3)
                createChip(25,  5, 4)
                createChip(25,  25, 5)
                createChip(25,  35, 6)
                createChip(40,  35, 7)
                createChip(40,  25, 8)
                createChip(5, 35, type = Chip.ChipType.CPU)

                createLink(0, 4, 1)
                createLink(4, 3, 2)
                createLink(4, 1, 3)
                createLink(3, 2, 4)
                createLink(1, 2, 5)
                createLink(1, 5, 6)
                createLink(2, 8, 7)
                createLink(5, 8, 8)
                createLink(5, 6, 9)
                createLink(8, 7, 10)
                createLink(6, 7, 11)
                createLink(6, 999, 12)

                createTrack(listOf(1, 2, 4, 5 ,6, 8, 10, 11, 12), 0)
                createTrack(listOf(1, 3, 5, 7, 8 ,9, 12), 1)

                createWave(10, 2, .125f, 1.2f)
                createWave(15, 3, .120f, 1.1f)
                createWave(15, 3, .110f, 1.1f)
                createWave(20, 4, .110f, 1.1f)
                createWave(20, 7, .050f, 1f, coins = 1)
                createWave(15, 15, .050f, 1f, coins = 1)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 2
            }
            7 ->
            {
                initializeNetwork(50, 50)

                createChip(25, 5, type = Chip.ChipType.ENTRY)
                createChip(10, 5, 1)
                createChip(40, 5, 2)
                createChip(10, 20, 3)
                createChip(25, 20, 4)
                createChip(40, 20, 5)
                createChip(10, 35, 6)
                createChip(25, 35, 7, type = Chip.ChipType.SUB)
                createChip(40, 35, 8)
                createChip(25, 42, type = Chip.ChipType.CPU)
                chips[7]?.setType(Chip.ChipType.SUB)

                createLink(0, 1, 1)
                createLink(0, 2, 2)
                createLink(1, 3, 3)
                createLink(0, 4, 4)
                createLink(2, 5, 5)
                createLink(3, 6, 6)
                createLink(4, 7, 7)
                createLink(5, 8, 8)
                createLink(6, 999, 9)
                createLink(7, 999, 10)
                createLink(8, 999, 11)

                createTrack(listOf(1, 3, 6, 9), 0)
                createTrack(listOf(4, 7, 10), 1)
                createTrack(listOf(2, 5, 8, 11), 2)

                createWave(10, 1, .125f, 1.2f)
                createWave(10, 1, .125f, 1.1f)
                createWave(15, 2, .100f, 1.0f)
                createWave(20, 3, .100f, 1.0f)
                createWave(20, 4, .100f, 1.0f)
                createWave(20, 5, .125f, 1.0f)
                createWave(20, 6, .05f, 1f, coins = 1)
                createWave(15, 8, .05f, 1f, coins = 1)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 2

                type = Type.FINAL
            }
        }

        data.maxWaves = waves.size
        return network
    }
}