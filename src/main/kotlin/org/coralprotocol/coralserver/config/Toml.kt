package org.coralprotocol.coralserver.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig

internal val toml = Toml(
    inputConfig = TomlInputConfig(
        ignoreUnknownNames = true,
        allowEmptyValues = true,
        allowNullValues = true,
        allowEscapedQuotesInLiteralStrings = true,
        allowEmptyToml = true,
        ignoreDefaultValues = false,
    )
)