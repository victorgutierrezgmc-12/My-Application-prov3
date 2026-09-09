package com.example.main

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

data class Cliente(val nombre: String, val direccion: String)

open class Producto(val nombre: String, val precioBase: Double) {
    init {
        require(precioBase >= 0) { "El precio base no puede ser negativo" }
    }

    open fun calcularPrecioFinal(): Double = precioBase
}

class Plato(nombre: String, precioBase: Double, val tamanoPorcion: String) : Producto(nombre, precioBase) {
    override fun calcularPrecioFinal(): Double {
        return if (tamanoPorcion == "Grande") precioBase * 1.25 else precioBase
    }
}

class Bebida(nombre: String, precioBase: Double, val esAlcoholica: Boolean) : Producto(nombre, precioBase) {
    override fun calcularPrecioFinal(): Double {

        return if (esAlcoholica) precioBase + 800.0 else precioBase
    }
}

sealed class EstadoPedido {
    object Preparando : EstadoPedido()
    data class EnCamino(val repartidor: String) : EstadoPedido()
    object Entregado : EstadoPedido()
    data class Cancelado(val motivo: String) : EstadoPedido()
}

suspend fun confirmarPago(monto: Double): Boolean {
    println("Procesando pago de $$monto...")
    delay(1.seconds)
    return true
}

suspend fun asignarRepartidor(): String {
    delay(1.seconds)
    return "Repartidor Asignado"
}

fun main() {
    runBlocking {

        val cliente = Cliente("Camila Rojas", "Av. Siempre Viva 742, Santiago")
        val horaPedido = 23
        val esClienteFrecuente = true

        val pedido = mutableListOf<Producto>()
        val productosPrueba = listOf(
            Triple("Pastel de Choclo", 6500.0, "Grande"),
            Triple("Empanada de Pino", 1800.0, "Chica"),
            Triple("Pisco Sour", 3200.0, true),
            Triple("Mote con Huesillo", 1500.0, false),
            Triple("Ceviche", -2000.0, "Chica")
        )

        for (item in productosPrueba) {
            try {
                if (item.third is String) {
                    pedido.add(Plato(item.first, item.second, item.third as String))
                } else if (item.third is Boolean) {
                    pedido.add(Bebida(item.first, item.second, item.third as Boolean))
                }
            } catch (e: IllegalArgumentException) {
                println("AVISO: Producto excluido por error -> ${e.message} en '${item.first}'")
            }
        }

        val subtotal = pedido.sumOf { it.calcularPrecioFinal() }

        val bebidasAlcoholicas = pedido.filter { it is Bebida && it.esAlcoholica }
            .also { println("\nINFO: Quedaron ${it.size} bebidas alcoholicas validas tras el filtro.") }
            .map { it.nombre }

        val recargoNocturno = if (horaPedido !in 6..21) 1000.0 else 0.0
        val envioGratis = subtotal >= 15000.0 && esClienteFrecuente
        val totalAPagar = subtotal + recargoNocturno

        with(cliente) {
            println("\n--- Datos del Cliente ---")
            println("Nombre: $nombre")
            println("Direccion: $direccion")
        }

        val resumenCliente = cliente.run {
            "El cliente $nombre califica para envio gratis: $envioGratis"
        }
        println(resumenCliente)

        totalAPagar.let { total ->
            println("\n--- Resumen Economico ---")
            println("Subtotal: $$subtotal")
            println("Recargo Nocturno: $$recargoNocturno")
            println("Total a pagar: $$total")
        }
        println("Bebidas alcoholicas del pedido: $bebidasAlcoholicas")

        coroutineScope {

            launch {
                println("\nTu pedido esta siendo preparado...")
            }

            val pagoAprobadoAsync = async { confirmarPago(totalAPagar) }
            val repartidorAsync = async { asignarRepartidor() }

            val estadoFinal = if (pagoAprobadoAsync.await()) {
                EstadoPedido.EnCamino(repartidorAsync.await())
            } else {
                EstadoPedido.Cancelado("El pago fue rechazado")
            }

            println("\n--- Estado Final del Pedido ---")
            when (estadoFinal) {
                is EstadoPedido.Preparando -> println("El pedido se encuentra en preparacion.")
                is EstadoPedido.EnCamino -> println("Estado: En camino con el repartidor '${estadoFinal.repartidor}'.")
                is EstadoPedido.Entregado -> println("Estado: Entregado exitosamente.")
                is EstadoPedido.Cancelado -> println("Estado: Cancelado por el motivo: ${estadoFinal.motivo}.")
            }
        }
    }
}