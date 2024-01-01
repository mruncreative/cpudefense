package com.example.cpudefense.gameElements

import android.graphics.*
import com.example.cpudefense.*
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.utils.*
import java.lang.StrictMath.min
import kotlin.math.min

class ScoreBoard(val game: Game): GameElement() {
    // default or min sizes
    var margin = 4   // between LED area and edge
    var preferredSizeOfLED = 10 // horizontal size of LEDs, can be smaller if there is too little space

    var area = Rect()
    var information = Information()
    var waves = Waves()
    var lives = Lives()
    var coins = Coins()
    var temperature = Temperature()
    var debugStatusLine: DebugStatusLine? = null
    var myColor = Color.WHITE
    var divider = 0  // between the display title and the actual display

    val fractionOfScoreBoardUsedForInf = 0.3f
    private val scoreboardBorderWidth = 4.0f

    fun setSize(area: Rect)
            /** sets the size of the score board and determines the dimensions of all components.
             * @param area The rectangle that the score board shall occupy
              */
    {
        this.area = Rect(area)
        // divider between title line and actual status indicators
        divider = /* this.area.top + */ this.area.height() * 32 / 100
        var areaRemaining = Rect(area).inflate(-scoreboardBorderWidth.toInt())
        areaRemaining = information.setSize(areaRemaining, divider)
        areaRemaining = waves.setSize(areaRemaining, divider)
        areaRemaining = coins.setSize(areaRemaining, divider)
        areaRemaining = lives.setSize(areaRemaining, divider)
        areaRemaining = temperature.setSize(areaRemaining, divider)
        if (game.gameActivity.settings.showFramerate) {
            debugStatusLine = DebugStatusLine()
            debugStatusLine?.setSize(area, divider)
        }
        recreateBitmap()
    }

    fun addCash(amount: Int) {
        game.state.cash += amount
    }

    fun informationToString(number: Int): String {
        if (number < 512 && number > -512)
            return "%d bit".format(number)
        var bytes = number/8
        if (bytes < 800 && bytes > -800)
            return "%d B".format(bytes)
        val kiB = bytes.toFloat()/1024.0
        if (kiB < 800 && bytes > -1000)
            return "%0.1f KiB".format(kiB)
        val MiB = kiB/1024.0
        if (MiB < 800 && MiB > -800)
            return "%0.1f MiB".format(MiB)
        val  GiB = MiB/1024.0
        return "%0.1f GiB".format(GiB)
    }

    override fun update() {
    }

    override fun display(canvas: Canvas, viewport: Viewport) {
        val paint = Paint()
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(area, paint)
        paint.color = myColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scoreboardBorderWidth
        canvas.drawRect(area, paint)
        game.currentStage?.let { if (it.getSeries() > 1 || it.getLevel() > 2)
            information.display(canvas) }
        waves.display(canvas)
        lives.display(canvas)
        coins.display(canvas)
        game.currentStage?.let { if (it.getSeries() > 1 || it.getLevel() > 27)
            temperature.display(canvas) }
        if (game.currentStage?.getSeries() ?: 1 > 1)
            temperature.display(canvas)
        debugStatusLine?.display(canvas)
    }

    fun displayHeader(canvas: Canvas, area: Rect, text: String, centered: Boolean = true)
            /**
             * Display text in 'header' text size
             *
             * @param canvas Where to paint on
             * @param area The rectangle where the header text should be placed
             * @param text The actual string to be displayed
             */
    {
        // TODO: Problem is that text with letters below the line (such as 'Temp') is slightly off-center vertically
        // due to calculation in rect.displayTextCenteredInRect
        var rect = Rect(area)
        rect.bottom = divider
        val paint = Paint()
        paint.color = game.resources.getColor(R.color.scoreboard_text)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = Game.scoreHeaderSize * game.resources.displayMetrics.scaledDensity
        if (centered)
            rect.displayTextCenteredInRect(canvas, text, paint)
        else
            rect.displayTextLeftAlignedInRect(canvas, text, paint)
    }

    fun recreateBitmap()
            /**
             * Recreate all parts of the score board. Called when resuming the game.
             */
    {
        if (area.width()>0 && area.height()>0) {
            information.recreateBitmap()
            waves.recreateBitmap()
            lives.recreateBitmap()
            coins.recreateBitmap()
            temperature.recreateBitmap()
        }
    }

    inner class Information()
    /** display of current amount of information ('cash') */
    {
        var area = Rect()
        var divider = 0

        var lastValue = -1   // used to detect value changes
        lateinit var bitmap: Bitmap
        val paint = Paint()

        fun setSize(area: Rect, divider: Int): Rect
                /** sets the area that is taken up by the information count.
                 * @param area The whole area of the score board
                 * @divider hieght of the line between header and contents
                 * @return The rectangle that remains (original area minus occupied area)
                  */
        {
            this.area = Rect(area.left, area.top, (area.width()*fractionOfScoreBoardUsedForInf).toInt(), area.bottom)
            bitmap = Bitmap.createBitmap(this.area.width(), this.area.height(), Bitmap.Config.ARGB_8888)
            this.divider = divider
            return Rect(this.area.right, area.top, area.right, area.bottom)
        }

        fun display(canvas: Canvas)
        {
            if (game.state.cash != lastValue)
            {
                /* only render the display if value has changed, otherwise re-use bitmap */
                lastValue = game.state.cash
                recreateBitmap()
            }
            canvas.drawBitmap(bitmap, null, area, paint)
        }

        fun recreateBitmap()
        {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rect = Rect(0, divider, area.width(), area.height())
            val text = informationToString(game.state.cash)
            val paint = Paint()
            paint.color = myColor
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = Game.scoreTextSize * game.resources.displayMetrics.scaledDensity
            rect.displayTextCenteredInRect(canvas, text, paint)
            displayHeader(canvas, Rect(0,0, area.width(), area.height()), game.resources.getString(R.string.scoreboard_inf))
        }
    }

    inner class Waves()
    {
        var area = Rect()
        var divider = 0

        var lastValue = -1   // used to detect value changes
        lateinit var bitmap: Bitmap
        val paint = Paint()

        fun setSize(area: Rect, divider: Int): Rect
        {
            this.area = Rect(area.left, area.top, (area.left+area.width()*0.25).toInt(), area.bottom)
            bitmap = Bitmap.createBitmap(this.area.width(), this.area.height(), Bitmap.Config.ARGB_8888)
            this.divider = divider
            return Rect(this.area.right, area.top, area.right, area.bottom)
        }

        fun display(canvas: Canvas)
        {
            if (game.currentStage?.data?.countOfWaves != lastValue)
            {
                /* only render the display if value has changed, otherwise re-use bitmap */
                lastValue = game.currentStage?.data?.countOfWaves ?: -1
                recreateBitmap()
            }
            canvas.drawBitmap(bitmap, null, area, paint)
        }

        fun recreateBitmap() {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            displayHeader(canvas, Rect(0,0, area.width(), area.height()), game.resources.getString(R.string.scoreboard_waves), centered = false)
            val rect = Rect(0, divider, area.width(), area.height())
            val bounds = Rect()
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.style = Paint.Style.FILL
            paint.color = myColor
            paint.textAlign = Paint.Align.LEFT
            game.currentStage?.let {
                val currentWave = "%d".format(it.data.countOfWaves)
                paint.textSize = Game.scoreTextSize * game.resources.displayMetrics.scaledDensity
                paint.getTextBounds(currentWave, 0, currentWave.length, bounds)
                val verticalMargin = (rect.height()-bounds.height())/2
                val rectLeft = Rect(0, rect.top+verticalMargin, bounds.width(), rect.bottom-verticalMargin)
                val rectRight = Rect(rectLeft.right, rectLeft.top, rect.right, rectLeft.bottom)
                canvas.drawText(currentWave, rectLeft.left.toFloat(), rectLeft.bottom.toFloat(), paint)
                paint.textSize *= 0.6f
                canvas.drawText("  / %d".format(it.data.maxWaves), rectRight.left.toFloat(), rectRight.bottom.toFloat(), paint)
            }
        }
    }

    inner class Lives {
        var area = Rect()
        var divider = 0

        var lastValue = -1   // used to detect value changes
        lateinit var bitmap: Bitmap
        val paint = Paint()
        var ledAreaHeight: Int = 0
        var ledAreaWidth: Int = 0
        val preferredSizeLedX = (preferredSizeOfLED * game.resources.displayMetrics.density).toInt()
        private var sizeLedX = preferredSizeLedX
        private var sizeLedY = 0 // will be calculated in setSize
        private var deltaX = 0


        fun setSize(area: Rect, divider: Int): Rect
        {
            this.area = Rect(area.left, area.top, (area.left+area.width()*0.7f).toInt(), area.bottom)
            bitmap = Bitmap.createBitmap(this.area.width(), this.area.height(), Bitmap.Config.ARGB_8888)
            this.divider = divider
            // calculate size and spacing of LEDs
            sizeLedY = (area.height()-divider-2*margin)*74/100
            val maxPossibleDeltaX = area.width()/(game.state.currentMaxLives + 0.0f)
            deltaX = kotlin.math.min(preferredSizeLedX * 1.2f, maxPossibleDeltaX).toInt()
            ledAreaWidth = (game.state.currentMaxLives + 1) * deltaX
            sizeLedX = kotlin.math.min(preferredSizeLedX.toFloat(), deltaX / 1.2f).toInt()
            return Rect(this.area.right, area.top, area.right, area.bottom)
        }

        fun display(canvas: Canvas)
        {
            if (game.state.lives != lastValue)
            {
                /* only render the display if value has changed, otherwise re-use bitmap */
                lastValue = game.state.lives
                recreateBitmap()
            }
            canvas.drawBitmap(bitmap, null, area, paint)
        }

        fun recreateBitmap()
        {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            ledAreaHeight = (area.height()-divider) - 2*margin
            // ledAreaWidth = (game.state.currentMaxLives + 1) * deltaX
            ledAreaWidth = this.area.width()- 2*margin
            val ledArea = Rect(0, 0, ledAreaWidth, ledAreaHeight)
            // var ledArea = Rect(0, divider+(area.height()-ledAreaHeight)/2, ledAreaWidth, ledAreaHeight)
            // determine the exact position of the LEDs. This is a bit frickelig
            ledArea.setCenter(area.width()/2, (area.height()+divider)/2)
            val resources = game.resources
            if (game.state.lives <= 0)
                return
            val paint = Paint()
            paint.style = Paint.Style.FILL
            paint.color = resources.getColor(R.color.led_panel)
            val glowPaint = Paint(paint)
            canvas.drawRect(ledArea, paint)
            for (i in 1..game.state.currentMaxLives) {
                val glowRect = Rect(0, 0, sizeLedX, sizeLedY)
                glowRect.setCenter(ledArea.right - i * deltaX, ledArea.centerY())
                val ledRect = Rect(glowRect).inflate(-4)
                if (i <= game.state.lives)
                    when (game.currentStage?.getSeries())
                    {
                        1 -> {
                            paint.color = resources.getColor(R.color.led_green)
                            glowPaint.color = resources.getColor(R.color.led_green)
                        }
                        2 -> {
                            paint.color = resources.getColor(R.color.led_turbo)
                            glowPaint.color = resources.getColor(R.color.led_turbo_glow)
                        }
                        3 -> {
                            paint.color = resources.getColor(R.color.led_red)
                            glowPaint.color = resources.getColor(R.color.led_red_glow)
                        }
                    }
                else if (i > game.state.lives)
                {
                    paint.color = resources.getColor(R.color.led_off)
                    glowPaint.color = resources.getColor(R.color.led_off_glow)
                }
                canvas.drawRect(glowRect, glowPaint)
                canvas.drawRect(ledRect, paint)
            }
            displayHeader(canvas, Rect(0,0, area.width(), area.height()), game.resources.getString(R.string.scoreboard_status))
        }
    }

    inner class Coins {
        var area = Rect()
        var divider = 0
        var coins: Int = 0
        var actualSize = Game.coinSizeOnScoreboard

        var lastValue = -1   // used to detect value changes
        lateinit var bitmap: Bitmap
        val paint = Paint()

        fun setSize(area: Rect, divider: Int): Rect
        {
            actualSize = (Game.coinSizeOnScoreboard * game.resources.displayMetrics.scaledDensity).toInt()
            this.area = Rect(area.left, area.top, (area.left+area.width()*0.36).toInt(), area.bottom)
            bitmap =
                Bitmap.createBitmap(this.area.width(), this.area.height(), Bitmap.Config.ARGB_8888)
            this.divider = divider
            return Rect(this.area.right, area.top, area.right, area.bottom)
        }

        fun display(canvas: Canvas) {
            if (game.currentStage?.summary?.coinsMaxAvailable == 0)
                return  // levels where you can't get coins
            coins = game.state.coinsInLevel + game.state.coinsExtra
            if (coins<0)
                return  // something went wrong, shouldn't happen
            if (coins != lastValue) {
                /* only render the display if value has changed, otherwise re-use bitmap */
                lastValue = coins
                recreateBitmap()
            }
            canvas.drawBitmap(bitmap, null, area, paint)
        }

        fun recreateBitmap() {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val y = (divider + area.height()) / 2
            val deltaX = if (coins > 1)
                (area.width() - (2 * actualSize)) / (coins - 1)
            else 0

            val x = area.width() - actualSize
            val rect = Rect(0, 0, actualSize, actualSize)
            val paint = Paint()
            displayHeader(canvas, Rect(0,0, area.width(), area.height()), game.resources.getString(R.string.scoreboard_coins))
            for (i in 0 until coins) {
                rect.setCenter(x - i * deltaX, y)
                canvas.drawBitmap(game.coinIcon, null, rect, paint)
            }
        }
    }

    inner class Temperature {
        var area = Rect()
        var divider = 0
        var temperature: Int = Game.baseTemperature
        var lastValue = -1   // used to detect value changes
        var actualSize = Game.coinSizeOnScoreboard
        var sevenSegmentDisplay: SevenSegmentDisplay? = null

        lateinit var bitmap: Bitmap
        val paint = Paint()

        fun setSize(area: Rect, divider: Int): Rect
        {
            this.divider = divider
            this.area = Rect(area.left, area.top, area.right, area.bottom)
            actualSize = this.area.height() - divider
            sevenSegmentDisplay = SevenSegmentDisplay(2, actualSize, game.gameActivity)
            sevenSegmentDisplay?.let {
                bitmap = it.getDisplayBitmap(0, SevenSegmentDisplay.LedColors.WHITE)
            }
            return Rect(this.area.right, area.top, area.right, area.bottom)
        }

        fun display(canvas: Canvas) {
        temperature = (game.state.heat/Game.heatPerDegree + Game.baseTemperature).toInt()
        if (temperature != lastValue) {
            lastValue = temperature
            recreateBitmap()
            }
            bitmap.let { canvas.drawBitmap(it, null, area, paint) }
        }

        fun recreateBitmap() {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            sevenSegmentDisplay?.let {
                val displayRect = Rect(0, divider, area.width(), area.height())
                val headerRect = Rect(0, 0, area.width(), area.height())
                displayRect.shrink(margin)
                when (temperature)
                {
                    in 0 until Game.temperatureWarnThreshold -> canvas.drawBitmap(it.getDisplayBitmap(temperature, SevenSegmentDisplay.LedColors.WHITE), null, displayRect, paint)
                    in Game.temperatureWarnThreshold until Game.temperatureLimit -> canvas.drawBitmap(it.getDisplayBitmap(temperature, SevenSegmentDisplay.LedColors.YELLOW), null, displayRect, paint)
                    else -> canvas.drawBitmap(it.getDisplayBitmap(temperature, SevenSegmentDisplay.LedColors.RED), null, displayRect, paint)
                }
                displayHeader(canvas, headerRect, "Temp")
            }
        }
    }

    inner class DebugStatusLine()
    /** this is an additional text displayed at every tick.
     * It is meant to hold additional debug info, e. g. the current frame rate
     */
    {
        var area = Rect()
        var divider: Int = 0
        val paint = Paint()
        var bitmap: Bitmap? = null
        var lastValue = 0.0

        fun setSize(area: Rect, divider: Int) {
            this.divider = divider
            this.area = Rect(area.left, 0, area.right, divider)
            paint.color = Color.YELLOW
            paint.style = Paint.Style.FILL
        }

        fun display(canvas: Canvas) {
            if (game.timeBetweenTicks != lastValue)
              recreateBitmap()
            bitmap?.let { canvas.drawBitmap(it, null, area, paint) }
        }

        fun recreateBitmap() {
            if (area.width() >0 && area.height() > 0)
                bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
            var textToDisplay = "time per frame: %.2f ms.".format(game.timeBetweenTicks)
            bitmap?.let {
                val canvas = Canvas(it)
                displayHeader(canvas, Rect(0, 0, area.width(), area.height()), textToDisplay)
            }
            lastValue = game.timeBetweenTicks
        }
    }
}