package com.wafflestudio.draft

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DraftApplication

fun main(args: Array<String>) {
    runApplication<DraftApplication>(*args)
}

/*
M: repository
V: service
C: controller
 */
