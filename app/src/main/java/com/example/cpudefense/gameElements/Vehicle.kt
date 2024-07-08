package com.example.cpudefense.gameElements

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.cpudefense.networkmap.*
import com.example.cpudefense.utils.setCenter

open class Vehicle(val network: Network): GameElement()
/** generic class that represents any vehicle moving on a network.
 * As a special case, the attackers moving on an electric circuit are derived from this class.
 */
{

    enum class State { ACTIVE, GONE}

    data class Data
        (
        /** speed with respect to the virtual grid, without global speed modifier */
        var speed: Float,
        /** temporary modifier of the speed */
        var speedModifier: Float,
        var speedModificationTimer: Float,
        /** position on the virtual grid */
        var gridPos: Pair<Float, Float>,
        /** distance travelled from last node */
        var distanceTravelledOnLink: Float,
        /** the link we're currently on */
        var linkId: Int,
        var startNodeId: Int,
        var endNodeId: Int,
        var trackId: Int,
        var sizeOnScreenInDp: Float,
        /** the state of the vehicle determines whether it is to be considered active by the network */
        var state: Vehicle.State,
        )

    var data = Data(
        speed = 1f,
        speedModifier = 1.0f,
        speedModificationTimer = 0.0f,
        gridPos = Pair(0f, 0f),
        distanceTravelledOnLink = 0.0f,
        linkId = -1,
        startNodeId = -1,
        endNodeId = -1,
        trackId = -1,
        sizeOnScreenInDp = 0.0f,
        state = State.ACTIVE,
    )

    var posOnGrid: Coord? = null
    var onLink: Link? = null
    var onTrack: Track? = null
    var startNode: Node? = null
    var endNode: Node? = null

    var distanceFromLastNode = 0.0f
    var distanceToNextNode = 0.0f

    override fun update() {
        /** determine the current speed of the vehicle, including modifiers and buffs */
        var currentSpeed: Float = data.speed * 0.16f
        if (currentSpeed < Network.minVehicleSpeed)
            currentSpeed = Network.minVehicleSpeed // stopgap. Impose minimal speed lest vehicles get stuck somewhere.

        if (data.speedModificationTimer > 0)
            currentSpeed *= data.speedModifier

        onLink?.let {
            /* determine which is start and which is end node */
            val startNode = this.startNode ?: it.node1
            val endNode = this.endNode ?: it.node2
            data.distanceTravelledOnLink += currentSpeed * network.theGame.globalSpeedFactor()
            posOnGrid = it.getPositionOnGrid(data.distanceTravelledOnLink, startNode)
            if (posOnGrid == endNode.posOnGrid) // reached end of link
            {
                startNode.notify(this, direction = Node.VehicleDirection.GONE) // out of reach, so stop notification
                setOntoLink(onTrack?.nextLink(it), endNode) // get next link on track, if any
                data.distanceTravelledOnLink = 0f // and start at the beginning of the link
            }
            setCurrentDistanceOnLink(it)
        }
     }

    override fun display(canvas: Canvas, viewport: Viewport)
    {
        posOnGrid?.let {
            var actualRect = Rect(0, 0, 20, 20)
            actualRect.setCenter(viewport.gridToViewport(it))
            var paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.FILL
            canvas.drawRect(actualRect, paint)
        }
    }

    fun setOntoLink(link: Link?, node: Node?)
            /** starts voyage on the link at the given node.
             * @param link The link to travel on
             * @param node if given, start at this node. Must be one of the link's end points
             */
    {
        if (link == null)
            return
        if (link.node2 == node) {
            startNode = node
            endNode = link.node1
        }
        else
        {
            startNode = link.node1
            endNode = link.node2
        }
        data.startNodeId = startNode?.data?.ident ?: -1
        data.endNodeId = endNode?.data?.ident ?: -1
        onLink = link
        setCurrentDistanceOnLink(link)
    }

    fun setCurrentDistanceOnLink(link: Link)
    {
        distanceFromLastNode = data.distanceTravelledOnLink
        distanceToNextNode = link.lengthOnGrid - data.distanceTravelledOnLink
    }

    fun setOntoTrack(track: Track?)
    {
        if (track != null)
        {
            onTrack = track
            data.trackId = track.data.ident
            setOntoLink(track.links[0], null)
            data.state = State.ACTIVE
        }
    }
}