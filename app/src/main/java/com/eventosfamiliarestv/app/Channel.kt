package com.eventosfamiliarestv.app

data class Channel(
    var id: String = "",
    var nombre: String = "",
    var url: String = "",
    var activo: Boolean = false,
    var claveHash: String = ""
)
