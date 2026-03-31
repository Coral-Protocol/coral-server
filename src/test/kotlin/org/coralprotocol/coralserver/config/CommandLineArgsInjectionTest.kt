package org.coralprotocol.coralserver.config

import com.sksamuel.hoplite.ExperimentalHoplite
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.modules.configModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

@OptIn(ExperimentalHoplite::class)
class CommandLineArgsInjectionTest : FunSpec(), KoinTest {
    init {
        afterEach {
            stopKoin()
        }

        test("CommandLineArgs should be correctly injected into ConfigLoader") {
            val args = arrayOf("--auth.keys=test-key")
            startKoin {
                modules(
                    module { single { CommandLineArgs(args) } },
                    configModule
                )
            }

            val config: RootConfig by inject()
            config.authConfig.keys shouldBe setOf("test-key")
        }

        test("ConfigLoader should handle missing CommandLineArgs") {
            startKoin {
                modules(
                    configModule
                )
            }

            val config: RootConfig by inject()
            config.authConfig.keys shouldBe setOf()
        }
    }
}
