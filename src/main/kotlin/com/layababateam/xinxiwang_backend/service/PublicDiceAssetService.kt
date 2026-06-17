package com.layababateam.xinxiwang_backend.service

import org.springframework.stereotype.Service
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class PublicDiceAssetService {
    private val cache = VALID_VALUES.associateWith { renderDicePng(it) }

    fun getDicePng(value: Int): ByteArray? = cache[value]

    private fun renderDicePng(value: Int): ByteArray {
        val image = BufferedImage(DICE_SIZE, DICE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(0xFF, 0xFF, 0xFF)
            graphics.fillRoundRect(FACE_MARGIN, FACE_MARGIN, FACE_SIZE, FACE_SIZE, FACE_RADIUS, FACE_RADIUS)
            graphics.color = Color(0xD8, 0xDE, 0xE6)
            graphics.stroke = BasicStroke(BORDER_WIDTH)
            graphics.drawRoundRect(FACE_MARGIN, FACE_MARGIN, FACE_SIZE, FACE_SIZE, FACE_RADIUS, FACE_RADIUS)
            graphics.color = Color(0x1D, 0x24, 0x2D)
            pipPositions(value).forEach { (x, y) ->
                graphics.fillOval(x - PIP_RADIUS, y - PIP_RADIUS, PIP_DIAMETER, PIP_DIAMETER)
            }
        } finally {
            graphics.dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, PNG_FORMAT, output)
        return output.toByteArray()
    }

    private fun pipPositions(value: Int): List<Pair<Int, Int>> {
        return when (value) {
            1 -> listOf(CENTER to MIDDLE)
            2 -> listOf(LEFT to TOP, RIGHT to BOTTOM)
            3 -> listOf(LEFT to TOP, CENTER to MIDDLE, RIGHT to BOTTOM)
            4 -> listOf(LEFT to TOP, RIGHT to TOP, LEFT to BOTTOM, RIGHT to BOTTOM)
            5 -> listOf(LEFT to TOP, RIGHT to TOP, CENTER to MIDDLE, LEFT to BOTTOM, RIGHT to BOTTOM)
            else -> listOf(LEFT to TOP, RIGHT to TOP, LEFT to MIDDLE, RIGHT to MIDDLE, LEFT to BOTTOM, RIGHT to BOTTOM)
        }
    }

    private companion object {
        val VALID_VALUES = 1..6
        const val PNG_FORMAT = "png"
        const val DICE_SIZE = 240
        const val FACE_MARGIN = 18
        const val FACE_SIZE = DICE_SIZE - FACE_MARGIN * 2
        const val FACE_RADIUS = 42
        const val BORDER_WIDTH = 8f
        const val PIP_RADIUS = 18
        const val PIP_DIAMETER = PIP_RADIUS * 2
        const val LEFT = 76
        const val CENTER = 120
        const val RIGHT = 164
        const val TOP = 76
        const val MIDDLE = 120
        const val BOTTOM = 164
    }
}
