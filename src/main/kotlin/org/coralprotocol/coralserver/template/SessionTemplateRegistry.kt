package org.coralprotocol.coralserver.template

import org.coralprotocol.coralserver.template.templates.QotdTemplate
import org.coralprotocol.coralserver.template.templates.QotdVoteTemplate

class SessionTemplateRegistry {
    private val templates: Map<String, SessionTemplate> = mapOf(
        QotdTemplate.info.slug to QotdTemplate,
        QotdVoteTemplate.info.slug to QotdVoteTemplate,
    )

    fun list(): List<SessionTemplateInfo> = templates.values.map { it.info }

    fun get(slug: String): SessionTemplate? = templates[slug]
}
