package com.banco.fidc.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FidcGatewayApplication

fun main(args: Array<String>) {
	runApplication<FidcGatewayApplication>(*args)
}
