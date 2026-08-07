package com.eventosfamiliarestv.app

data class Channel(
    var nombre: String = "",
    var url: String = "",
    var activo: Boolean = false,
    var claveHash: String = ""
)
