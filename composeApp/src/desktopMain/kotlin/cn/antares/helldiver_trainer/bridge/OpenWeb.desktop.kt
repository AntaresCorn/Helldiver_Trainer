package cn.antares.helldiver_trainer.bridge

import java.awt.Desktop
import java.net.URI

actual fun openWebPage(url: String) {
    Desktop.getDesktop().browse(URI.create(url))
}